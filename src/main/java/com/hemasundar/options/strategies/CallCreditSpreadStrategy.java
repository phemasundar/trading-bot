package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CallCreditSpread;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
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
public class CallCreditSpreadStrategy extends AbstractTradingStrategy {

    public CallCreditSpreadStrategy(StrategyType strategyType,
                                   FinnHubAPIs finnHubAPIs,
                                   ThinkOrSwinAPIs thinkOrSwinAPIs,
                                   VolatilityCalculator volatilityCalculator,
                                   java.util.Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator, supabaseService);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        // Extract leg filters
        LegFilter shortLegFilter = null, longLegFilter = null;
        if (filter instanceof CreditSpreadFilter csFilter) {
            shortLegFilter = csFilter.getShortLeg();
            longLegFilter = csFilter.getLongLeg();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();

        List<CallSpreadCandidate> candidates = generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.GENERATED_CANDIDATES.displayName(), candidates.size(), candidates.size());

        List<CallSpreadCandidate> survived = FilterPipeline
                .<CallSpreadCandidate>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.DELTA_FILTER,              deltaFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_PREMIUM_FILTER,        legPremiumFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.VOLUME_FILTER,             volumeFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.OPEN_INTEREST_FILTER,      openInterestFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_VOLATILITY_FILTER,     volatilityFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.POSITIVE_CREDIT_FILTER,    creditFilter())
                .step(FilterStage.MAX_CREDIT_FILTER,         commonMaxTotalCreditFilter(filter, CallSpreadCandidate::netCredit))
                .step(FilterStage.MIN_CREDIT_FILTER,         commonMinTotalCreditFilter(filter, CallSpreadCandidate::netCredit))
                .step(FilterStage.MAX_LOSS_FILTER,           commonMaxLossFilter(filter, CallSpreadCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_FILTER,  commonMinReturnOnRiskFilter(filter, CallSpreadCandidate::netCredit, CallSpreadCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_CAGR_FILTER, commonMinReturnOnRiskCAGRFilter(filter, CallSpreadCandidate::netCredit, CallSpreadCandidate::maxLoss, c -> c.shortLeg().getDaysToExpiration()))
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
     * Generates all valid 2-leg combinations as a stream of CallSpreadCandidate
     * records.
     * For Call Spreads: Short Strike (i) < Long Strike (j)
     */
    private Stream<CallSpreadCandidate> generateCandidates(Map<String, List<OptionData>> callMap,
            List<Double> strikes, double currentPrice) {
        return IntStream.range(0, strikes.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, strikes.size()).boxed()
                        .map(j -> createCandidate(callMap, strikes, i, j, currentPrice)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<CallSpreadCandidate> createCandidate(Map<String, List<OptionData>> callMap,
            List<Double> strikes, int shortIndex, int longIndex, double currentPrice) {
        double shortStrike = strikes.get(shortIndex);

        // OTM Check: For Call Credit Spread, Short Strike must be > Current Price
        // Wait, logic in original file was: if (shortStrikePrice <= currentPrice)
        // continue
        // So Short Strike MUST be > Current Price (OTM)
        if (shortStrike <= currentPrice) {
            return Optional.empty();
        }

        OptionData shortLeg = getOption(callMap, shortStrike);
        OptionData longLeg = getOption(callMap, strikes.get(longIndex));

        if (shortLeg == null || longLeg == null) {
            return Optional.empty();
        }
        return Optional.of(new CallSpreadCandidate(shortLeg, longLeg, currentPrice));
    }

    private OptionData getOption(Map<String, List<OptionData>> map, Double strike) {
        List<OptionData> options = map.get(String.valueOf(strike));
        return CollectionUtils.isEmpty(options) ? null : options.get(0);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<CallSpreadCandidate> deltaFilter(LegFilter shortLegFilter, LegFilter longLegFilter) {
        return candidate -> LegFilter.passesDelta(shortLegFilter, candidate.shortLeg())
                && LegFilter.passesDelta(longLegFilter, candidate.longLeg());
    }

    private Predicate<CallSpreadCandidate> legPremiumFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesPremium(shortFilter, c.shortLeg()) && LegFilter.passesPremium(longFilter, c.longLeg());
    }

    private Predicate<CallSpreadCandidate> volumeFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolume(shortFilter, c.shortLeg()) && LegFilter.passesVolume(longFilter, c.longLeg());
    }

    private Predicate<CallSpreadCandidate> openInterestFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesOpenInterest(shortFilter, c.shortLeg()) && LegFilter.passesOpenInterest(longFilter, c.longLeg());
    }

    private Predicate<CallSpreadCandidate> volatilityFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolatility(shortFilter, c.shortLeg()) && LegFilter.passesVolatility(longFilter, c.longLeg());
    }

    private Predicate<CallSpreadCandidate> creditFilter() {
        return candidate -> candidate.netCredit() > 0;
    }

    // Note: maxLossFilter and minReturnOnRiskFilter now use common helpers from
    // AbstractTradingStrategy

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(CallSpreadCandidate c) {
        return CallCreditSpread.builder()
                .shortCall(c.shortLeg())
                .longCall(c.longLeg())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record CallSpreadCandidate(OptionData shortLeg, OptionData longLeg, double currentPrice) {

        double netCredit() {
            return (shortLeg.getBid() - longLeg.getAsk()) * 100;
        }

        double strikeWidth() {
            return (longLeg.getStrikePrice() - shortLeg.getStrikePrice()) * 100;
        }

        double maxLoss() {
            return strikeWidth() - netCredit();
        }

        double returnOnRisk() {
            return (maxLoss() > 0) ? (netCredit() / maxLoss()) * 100 : 0;
        }

        double breakEvenPrice() {
            return shortLeg.getStrikePrice() + (netCredit() / 100);
        }

        double breakEvenPercentage() {
            return ((breakEvenPrice() - currentPrice) / currentPrice) * 100;
        }
    }
}
