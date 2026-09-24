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

import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;

@Log4j2
public class PutCreditSpreadStrategy extends AbstractTradingStrategy {

    public PutCreditSpreadStrategy(StrategyType strategyType,
                                  FinnHubAPIs finnHubAPIs,
                                  ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                  java.util.Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);

        if (MapUtils.isEmpty(putMap))
            return new ArrayList<>();

        // Extract leg filters
        LegFilter shortLegFilter = (filter instanceof CreditSpreadFilter csFilter) ? csFilter.getShortLeg() : null;
        LegFilter longLegFilter = (filter instanceof CreditSpreadFilter csFilter) ? csFilter.getLongLeg() : null;

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        String strategyName = getStrategyName(filter);
        String symbol = chain.getSymbol();

        List<PutSpreadCandidate> candidates = generateCandidates(putMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.GENERATED_CANDIDATES.displayName(), candidates.size(), candidates.size());

        FilterPipeline<PutSpreadCandidate> candidatePipeline = FilterPipeline
                .<PutSpreadCandidate>forContext(strategyName, symbol, expiryDate);

        if (hasLegacyDelta(shortLegFilter, longLegFilter)) {
            candidatePipeline.step(FilterStage.DELTA_FILTER, deltaFilter(shortLegFilter, longLegFilter));
        }
        if (hasLegacyPremium(shortLegFilter, longLegFilter)) {
            candidatePipeline.step(FilterStage.LEG_PREMIUM_FILTER, legPremiumFilter(shortLegFilter, longLegFilter));
        }
        if (hasLegacyVolume(shortLegFilter, longLegFilter)) {
            candidatePipeline.step(FilterStage.VOLUME_FILTER, volumeFilter(shortLegFilter, longLegFilter));
        }
        if (hasLegacyOpenInterest(shortLegFilter, longLegFilter)) {
            candidatePipeline.step(FilterStage.OPEN_INTEREST_FILTER, openInterestFilter(shortLegFilter, longLegFilter));
        }
        if (hasLegacyVolatility(shortLegFilter, longLegFilter)) {
            candidatePipeline.step(FilterStage.LEG_VOLATILITY_FILTER, volatilityFilter(shortLegFilter, longLegFilter));
        }

        applyLegFilterExpressions(candidatePipeline, shortLegFilter, "shortLeg", PutSpreadCandidate::shortLeg);
        applyLegFilterExpressions(candidatePipeline, longLegFilter, "longLeg", PutSpreadCandidate::longLeg);

        candidatePipeline.step("NET_CREDIT > 0", creditFilter());

        if (filter.getMaxTotalCredit() != null) {
            candidatePipeline.step(FilterStage.MAX_CREDIT_FILTER, commonMaxTotalCreditFilter(filter, PutSpreadCandidate::netCredit));
        }
        if (filter.getMinTotalCredit() != null) {
            candidatePipeline.step(FilterStage.MIN_CREDIT_FILTER, commonMinTotalCreditFilter(filter, PutSpreadCandidate::netCredit));
        }
        if (filter.getMaxLossLimit() != null) {
            candidatePipeline.step(FilterStage.MAX_LOSS_FILTER, commonMaxLossFilter(filter, PutSpreadCandidate::maxLoss));
        }
        if (filter.getMinReturnOnRisk() != null) {
            candidatePipeline.step(FilterStage.MIN_RETURN_ON_RISK_FILTER, commonMinReturnOnRiskFilter(filter, PutSpreadCandidate::netCredit, PutSpreadCandidate::maxLoss));
        }
        if (filter.getMinReturnOnRiskCAGR() != null) {
            candidatePipeline.step(FilterStage.MIN_RETURN_ON_RISK_CAGR_FILTER, commonMinReturnOnRiskCAGRFilter(filter, PutSpreadCandidate::netCredit, PutSpreadCandidate::maxLoss, c -> c.shortLeg().getDaysToExpiration()));
        }

        List<PutSpreadCandidate> survived = candidatePipeline.run(candidates);

        List<TradeSetup> mapped = survived.stream().map(this::buildTradeSetup).toList();

        FilterPipeline<TradeSetup> tradePipeline = FilterPipeline
                .<TradeSetup>forContext(strategyName, symbol, expiryDate);

        if (filter.getMaxNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter));
        }
        if (filter.getMinNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter));
        }
        if (filter.getMaxBreakEvenPercentage() != null) {
            tradePipeline.step(FilterStage.BREAK_EVEN_FILTER, trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()));
        }

        return applyTradeMathFilterExpressions(tradePipeline, filter).run(mapped);
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
