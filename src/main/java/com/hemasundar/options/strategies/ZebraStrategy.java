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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Log4j2
public class ZebraStrategy extends AbstractTradingStrategy {

    public ZebraStrategy() {
        super(StrategyType.BULLISH_ZEBRA);
    }

    public ZebraStrategy(StrategyType strategyType) {
        super(strategyType);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        // Extract leg filters and extrinsic limit
        LegFilter shortLegFilter = null, longLegFilter = null;
        Double maxNetExtrinsicValue = null;

        if (filter instanceof ZebraFilter zFilter) {
            shortLegFilter = zFilter.getShortCall();
            longLegFilter = zFilter.getLongCall();
            maxNetExtrinsicValue = zFilter.getMaxNetExtrinsicValue();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        final Double finalMaxNetExtrinsicValue = maxNetExtrinsicValue;

        return generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice())
                .filter(deltaFilter(shortLegFilter, longLegFilter))
                .filter(extrinsicValueFilter(finalMaxNetExtrinsicValue))
                .filter(commonMaxLossFilter(filter, ZebraCandidate::maxLoss))
                // For ZEBRA, return on risk might be less standard, but we apply if defined
                .filter(commonMinReturnOnRiskFilter(filter, candidate -> candidate.maxLoss() > 0 ? 0.0 : 100.0,
                        ZebraCandidate::maxLoss))
                .map(this::buildTradeSetup)
                .filter(trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .toList();
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

    private Predicate<ZebraCandidate> deltaFilter(LegFilter shortLegFilter, LegFilter longLegFilter) {
        return candidate -> LegFilter.passes(shortLegFilter, candidate.shortLeg())
                && LegFilter.passes(longLegFilter, candidate.longLeg());
    }

    private Predicate<ZebraCandidate> extrinsicValueFilter(Double maxNetExtrinsicValue) {
        return candidate -> {
            if (maxNetExtrinsicValue == null)
                return true; // Filter disabled
            return candidate.netExtrinsicValue() < maxNetExtrinsicValue;
        };
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
                .netExtrinsicValue(c.netExtrinsicValue())
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record ZebraCandidate(OptionData shortLeg, OptionData longLeg, double currentPrice) {

        double netDebit() {
            // Buying 2 calls, Selling 1 call
            return ((longLeg.getAsk() * 2) - shortLeg.getBid()) * 100;
        }

        double netExtrinsicValue() {
            // Net extrinsic value = (Extrinsic Value of 2 Longs) - (Extrinsic Value of 1
            // Short)
            return (longLeg.getExtrinsicValue() * 2) - shortLeg.getExtrinsicValue();
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
