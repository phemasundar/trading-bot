package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.OptionType;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.VolatilityCalculator;

@Log4j2
public class PutCreditSpreadStrategy extends AbstractTradingStrategy {

    public PutCreditSpreadStrategy(StrategyType strategyType,
                                  FinnHubAPIs finnHubAPIs,
                                  ThinkOrSwinAPIs thinkOrSwinAPIs,
                                  VolatilityCalculator volatilityCalculator,
                                  java.util.Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator, supabaseService);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);

        if (putMap == null || putMap.isEmpty())
            return new ArrayList<>();

        // Extract leg filters
        LegFilter shortLegFilter = null, longLegFilter = null;
        if (filter instanceof CreditSpreadFilter csFilter) {
            shortLegFilter = csFilter.getShortLeg();
            longLegFilter = csFilter.getLongLeg();
        }

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();

        List<PutSpreadCandidate> candidates = generateCandidates(putMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.GENERATED_CANDIDATES.displayName(), candidates.size(), candidates.size());

        List<PutSpreadCandidate> survived = FilterPipeline
                .<PutSpreadCandidate>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.DELTA_FILTER,              deltaFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_PREMIUM_FILTER,        legPremiumFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.VOLUME_FILTER,             volumeFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.OPEN_INTEREST_FILTER,      openInterestFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_VOLATILITY_FILTER,     volatilityFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.POSITIVE_CREDIT_FILTER,   creditFilter())
                .step(FilterStage.MAX_CREDIT_FILTER,        commonMaxTotalCreditFilter(filter, PutSpreadCandidate::netCredit))
                .step(FilterStage.MIN_CREDIT_FILTER,        commonMinTotalCreditFilter(filter, PutSpreadCandidate::netCredit))
                .step(FilterStage.MAX_LOSS_FILTER,          commonMaxLossFilter(filter, PutSpreadCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_FILTER, commonMinReturnOnRiskFilter(filter, PutSpreadCandidate::netCredit, PutSpreadCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_CAGR_FILTER, commonMinReturnOnRiskCAGRFilter(filter, PutSpreadCandidate::netCredit, PutSpreadCandidate::maxLoss, c -> c.shortLeg().getDaysToExpiration()))
                .run(candidates);

        List<TradeSetup> mapped = survived.stream().map(this::buildTradeSetup).toList();

        return FilterPipeline
                .<TradeSetup>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.BREAK_EVEN_FILTER,          trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .run(mapped);
    }

    /**
     * Generates all valid 2-leg combinations as a stream of PutSpreadCandidate
     * records.
     * For Put Spreads: Short Strike > Long Strike
     */
    private Stream<PutSpreadCandidate> generateCandidates(Map<String, List<OptionData>> putMap,
            List<Double> strikes, double currentPrice) {
        // Iterate short leg (i) from high to low?
        // Logic: Short Strike (i) must be higher than Long Strike (j) for Put Credit
        // So j < i
        return IntStream.range(0, strikes.size()).boxed()
                .flatMap(i -> IntStream.range(0, i).boxed()
                        .map(j -> createCandidate(putMap, strikes, i, j, currentPrice)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<PutSpreadCandidate> createCandidate(Map<String, List<OptionData>> putMap,
            List<Double> strikes, int shortIndex, int longIndex, double currentPrice) {
        OptionData shortLeg = getOption(putMap, strikes.get(shortIndex));
        OptionData longLeg = getOption(putMap, strikes.get(longIndex));

        if (shortLeg == null || longLeg == null) {
            return Optional.empty();
        }
        return Optional.of(new PutSpreadCandidate(shortLeg, longLeg, currentPrice));
    }

    private OptionData getOption(Map<String, List<OptionData>> map, Double strike) {
        List<OptionData> options = map.get(String.valueOf(strike));
        return CollectionUtils.isEmpty(options) ? null : options.get(0);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<PutSpreadCandidate> deltaFilter(LegFilter shortLegFilter, LegFilter longLegFilter) {
        return candidate -> LegFilter.passesDelta(shortLegFilter, candidate.shortLeg())
                && LegFilter.passesDelta(longLegFilter, candidate.longLeg());
    }

    private Predicate<PutSpreadCandidate> legPremiumFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesPremium(shortFilter, c.shortLeg()) && LegFilter.passesPremium(longFilter, c.longLeg());
    }

    private Predicate<PutSpreadCandidate> volumeFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolume(shortFilter, c.shortLeg()) && LegFilter.passesVolume(longFilter, c.longLeg());
    }

    private Predicate<PutSpreadCandidate> openInterestFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesOpenInterest(shortFilter, c.shortLeg()) && LegFilter.passesOpenInterest(longFilter, c.longLeg());
    }

    private Predicate<PutSpreadCandidate> volatilityFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolatility(shortFilter, c.shortLeg()) && LegFilter.passesVolatility(longFilter, c.longLeg());
    }

    private Predicate<PutSpreadCandidate> creditFilter() {
        return candidate -> candidate.netCredit() > 0;
    }

    // Note: maxLossFilter and minReturnOnRiskFilter now use common helpers from
    // AbstractTradingStrategy

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(PutSpreadCandidate c) {
        return PutCreditSpread.builder()
                .shortPut(c.shortLeg())
                .longPut(c.longLeg())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record PutSpreadCandidate(OptionData shortLeg, OptionData longLeg, double currentPrice) {

        double netCredit() {
            return (shortLeg.getBid() - longLeg.getAsk()) * 100;
        }

        double strikeWidth() {
            return (shortLeg.getStrikePrice() - longLeg.getStrikePrice()) * 100;
        }

        double maxLoss() {
            return strikeWidth() - netCredit();
        }

        double returnOnRisk() {
            return (maxLoss() > 0) ? (netCredit() / maxLoss()) * 100 : 0;
        }

        double breakEvenPrice() {
            return shortLeg.getStrikePrice() - (netCredit() / 100);
        }

        double breakEvenPercentage() {
            return ((currentPrice - breakEvenPrice()) / currentPrice) * 100;
        }
    }
}
