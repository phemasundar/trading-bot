package com.hemasundar.options.strategies;

import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.ZebraFilter;
import com.hemasundar.options.models.ZebraTrade;
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

@Log4j2
public class ZebraStrategy extends AbstractTradingStrategy {

    public ZebraStrategy(StrategyType strategyType,
                        FinnHubAPIs finnHubAPIs,
                        ThinkOrSwinAPIs thinkOrSwinAPIs,
                        java.util.Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, supabaseService);
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

        if (filter instanceof ZebraFilter zFilter) {
            shortLegFilter = zFilter.getShortCall();
            longLegFilter = zFilter.getLongCall();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();

        List<ZebraCandidate> candidates = generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.GENERATED_CANDIDATES.displayName(), candidates.size(), candidates.size());

        List<ZebraCandidate> survived = FilterPipeline
                .<ZebraCandidate>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.DELTA_FILTER,              deltaFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_PREMIUM_FILTER,        legPremiumFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.VOLUME_FILTER,             volumeFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.OPEN_INTEREST_FILTER,      openInterestFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.LEG_VOLATILITY_FILTER,     volatilityFilter(shortLegFilter, longLegFilter))
                .step(FilterStage.MAX_LOSS_FILTER,           commonMaxLossFilter(filter, ZebraCandidate::maxLoss))
                .step(FilterStage.MAX_DEBIT_FILTER,          commonMaxTotalDebitFilter(filter, ZebraCandidate::netDebit))
                .step(FilterStage.MIN_RETURN_ON_RISK_FILTER, commonMinReturnOnRiskFilter(filter, candidate -> candidate.maxLoss() > 0 ? 0.0 : 100.0, ZebraCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_CAGR_FILTER, commonMinReturnOnRiskCAGRFilter(filter, candidate -> candidate.maxLoss() > 0 ? 0.0 : 100.0, ZebraCandidate::maxLoss, c -> c.shortLeg().getDaysToExpiration()))
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
     * Generates all valid 2-leg combinations (representing the 3 legs, as the 2
     * longs use the same strike).
     */
    private Stream<ZebraCandidate> generateCandidates(Map<String, List<OptionData>> callMap,
            List<Double> strikes, double currentPrice) {
        // ZEBRA requires selling a call (close to ATM, e.g. 50 Delta)
        // and buying 2 calls further ITM (e.g. 70 Delta)
        // Since Calls ITM have lower strikes, Long Strike < Short Strike
        return IntStream.range(0, strikes.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, strikes.size()).boxed()
                        .map(j -> createCandidate(callMap, strikes, i, j, currentPrice)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<ZebraCandidate> createCandidate(Map<String, List<OptionData>> callMap,
            List<Double> strikes, int longIndex, int shortIndex, double currentPrice) {
        double longStrike = strikes.get(longIndex); // Lower strike -> Higher Delta -> ITM
        double shortStrike = strikes.get(shortIndex); // Higher strike -> Lower Delta -> ATM

        OptionData longLeg = getOption(callMap, longStrike);
        OptionData shortLeg = getOption(callMap, shortStrike);

        if (longLeg == null || shortLeg == null) {
            return Optional.empty();
        }

        return Optional.of(new ZebraCandidate(shortLeg, longLeg, currentPrice));
    }

    private OptionData getOption(Map<String, List<OptionData>> map, Double strike) {
        List<OptionData> options = map.get(String.valueOf(strike));
        return CollectionUtils.isEmpty(options) ? null : options.get(0);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<ZebraCandidate> deltaFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesDelta(shortFilter, c.shortLeg()) && LegFilter.passesDelta(longFilter, c.longLeg());
    }

    private Predicate<ZebraCandidate> legPremiumFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesPremium(shortFilter, c.shortLeg()) && LegFilter.passesPremium(longFilter, c.longLeg());
    }

    private Predicate<ZebraCandidate> volumeFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolume(shortFilter, c.shortLeg()) && LegFilter.passesVolume(longFilter, c.longLeg());
    }

    private Predicate<ZebraCandidate> openInterestFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesOpenInterest(shortFilter, c.shortLeg()) && LegFilter.passesOpenInterest(longFilter, c.longLeg());
    }

    private Predicate<ZebraCandidate> volatilityFilter(LegFilter shortFilter, LegFilter longFilter) {
        return c -> LegFilter.passesVolatility(shortFilter, c.shortLeg()) && LegFilter.passesVolatility(longFilter, c.longLeg());
    }

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(ZebraCandidate c) {
        return ZebraTrade.builder()
                .shortCall(c.shortLeg())
                .longCall(c.longLeg())
                .netDebit(c.netDebit())
                .maxLoss(c.maxLoss())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record ZebraCandidate(OptionData shortLeg, OptionData longLeg, double currentPrice) {

        double netDebit() {
            // Buying 2 calls, Selling 1 call
            return ((longLeg.getAsk() * 2) - shortLeg.getBid()) * 100;
        }

        double maxLoss() {
            // The max loss for a Zebra is theoretically the net debit paid.
            return netDebit();
        }

        double returnOnRisk() {
            // Return on Risk isn't easily defined for a stock replacement strategy like
            // ZEBRA
            // without knowing the target exit price. Often people do not use this filter.
            return 0; // Return 0 to avoid breaking generic filtering unless customized
        }

        double breakEvenPrice() {
            // Cost basis per share equivalent.
            // In a ZEBRA the 1 short call cancels out the extrinsic value of 1 long call.
            // Leaving you with the intrinsic value of 1 long call plus some minor extrinsic
            // differences.
            // Effectively, B/E = Long Strike + (Net Debit / 100)
            return longLeg.getStrikePrice() + (netDebit() / 100);
        }

        double breakEvenPercentage() {
            return ((breakEvenPrice() - currentPrice) / currentPrice) * 100;
        }
    }
}
