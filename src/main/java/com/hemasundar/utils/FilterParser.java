package com.hemasundar.utils;

import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FilterParser {

    private FilterParser() {
        // Private constructor for utility class
    }

    public static OptionsStrategyFilter buildFilter(StrategyType type, Map<String, Object> filterMap) {
        OptionsStrategyFilter filter;

        // Create the appropriate filter subclass based on strategy type
        switch (type) {
            case PUT_CREDIT_SPREAD:
            case BULLISH_LONG_PUT_CREDIT_SPREAD:
            case TECH_PUT_CREDIT_SPREAD:
            case CALL_CREDIT_SPREAD:
            case TECH_CALL_CREDIT_SPREAD:
                filter = new CreditSpreadFilter();
                break;
            case IRON_CONDOR:
            case BULLISH_LONG_IRON_CONDOR:
                filter = new IronCondorFilter();
                break;
            case LONG_CALL_LEAP:
                filter = new LongCallLeapFilter();
                break;
            case BULLISH_BROKEN_WING_BUTTERFLY:
                filter = new BrokenWingButterflyFilter();
                break;
            case BULLISH_ZEBRA:
                filter = new ZebraFilter();
                break;
            default:
                filter = new OptionsStrategyFilter();
        }

        if (filterMap == null) return filter;

        // ── Common filter fields ──
        applyIfPresent(filterMap, "targetDTE", v -> filter.setTargetDTE(toInt(v)));
        applyIfPresent(filterMap, "minDTE", v -> filter.setMinDTE(toInt(v)));
        applyIfPresent(filterMap, "maxDTE", v -> filter.setMaxDTE(toInt(v)));
        applyIfPresent(filterMap, "maxLossLimit", v -> filter.setMaxLossLimit(toDouble(v)));
        applyIfPresent(filterMap, "minReturnOnRisk", v -> filter.setMinReturnOnRisk(toInt(v)));
        applyIfPresent(filterMap, "minHistoricalVolatility", v -> filter.setMinHistoricalVolatility(toDouble(v)));
        applyIfPresent(filterMap, "maxBreakEvenPercentage", v -> filter.setMaxBreakEvenPercentage(toDouble(v)));
        applyIfPresent(filterMap, "maxUpperBreakevenDelta", v -> filter.setMaxUpperBreakevenDelta(toDouble(v)));
        applyIfPresent(filterMap, "maxNetExtrinsicValueToPricePercentage",
                v -> filter.setMaxNetExtrinsicValueToPricePercentage(toDouble(v)));
        applyIfPresent(filterMap, "minNetExtrinsicValueToPricePercentage",
                v -> filter.setMinNetExtrinsicValueToPricePercentage(toDouble(v)));
        applyIfPresent(filterMap, "ignoreEarnings",
                v -> filter.setIgnoreEarnings(Boolean.parseBoolean(v.toString())));
        applyIfPresent(filterMap, "maxTotalDebit", v -> filter.setMaxTotalDebit(toDouble(v)));
        applyIfPresent(filterMap, "maxTotalCredit", v -> filter.setMaxTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "minTotalCredit", v -> filter.setMinTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "priceVsMaxDebitRatio", v -> filter.setPriceVsMaxDebitRatio(toDouble(v)));
        applyIfPresent(filterMap, "maxCAGRForBreakEven", v -> filter.setMaxCAGRForBreakEven(toDouble(v)));
        applyIfPresent(filterMap, "maxOptionPricePercent", v -> filter.setMaxOptionPricePercent(toDouble(v)));
        applyIfPresent(filterMap, "marginInterestRate", v -> filter.setMarginInterestRate(toDouble(v)));
        applyIfPresent(filterMap, "savingsInterestRate", v -> filter.setSavingsInterestRate(toDouble(v)));

        // ── Strategy-specific fields ──
        if (filter instanceof CreditSpreadFilter csFilter) {
            applyLegFilter(filterMap, "shortLeg", csFilter::setShortLeg);
            applyLegFilter(filterMap, "longLeg", csFilter::setLongLeg);
        } else if (filter instanceof IronCondorFilter icFilter) {
            applyLegFilter(filterMap, "putShortLeg", icFilter::setPutShortLeg);
            applyLegFilter(filterMap, "putLongLeg", icFilter::setPutLongLeg);
            applyLegFilter(filterMap, "callShortLeg", icFilter::setCallShortLeg);
            applyLegFilter(filterMap, "callLongLeg", icFilter::setCallLongLeg);
        } else if (filter instanceof LongCallLeapFilter leapFilter) {
            applyLegFilter(filterMap, "longCall", leapFilter::setLongCall);
            applyIfPresent(filterMap, "minCostSavingsPercent", v -> leapFilter.setMinCostSavingsPercent(toDouble(v)));
            applyIfPresent(filterMap, "minCostEfficiencyPercent", v -> leapFilter.setMinCostEfficiencyPercent(toDouble(v)));
            applyIfPresent(filterMap, "topTradesCount", v -> leapFilter.setTopTradesCount(toInt(v)));
            applyIfPresent(filterMap, "relaxationPriority", v -> leapFilter.setRelaxationPriority(toStringList(v)));
            applyIfPresent(filterMap, "sortPriority", v -> leapFilter.setSortPriority(toStringList(v)));
        } else if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            applyLegFilter(filterMap, "leg1Long", bwbFilter::setLeg1Long);
            applyLegFilter(filterMap, "leg2Short", bwbFilter::setLeg2Short);
            applyLegFilter(filterMap, "leg3Long", bwbFilter::setLeg3Long);
        } else if (filter instanceof ZebraFilter zebraFilter) {
            applyLegFilter(filterMap, "shortCall", zebraFilter::setShortCall);
            applyLegFilter(filterMap, "longCall", zebraFilter::setLongCall);
        }

        return filter;
    }

    /**
     * Builds a LegFilter from a nested map (e.g., "shortLeg": {"minDelta": 0.1, ...}).
     */
    @SuppressWarnings("unchecked")
    private static void applyLegFilter(Map<String, Object> filterMap, String key, java.util.function.Consumer<LegFilter> setter) {
        if (!filterMap.containsKey(key) || filterMap.get(key) == null) return;

        Object legObj = filterMap.get(key);
        if (!(legObj instanceof Map)) return;

        Map<String, Object> legMap = (Map<String, Object>) legObj;
        if (legMap.isEmpty()) return;

        LegFilter.LegFilterBuilder builder = LegFilter.builder();
        applyIfPresent(legMap, "minDelta", v -> builder.minDelta(toDouble(v)));
        applyIfPresent(legMap, "maxDelta", v -> builder.maxDelta(toDouble(v)));
        applyIfPresent(legMap, "minPremium", v -> builder.minPremium(toDouble(v)));
        applyIfPresent(legMap, "maxPremium", v -> builder.maxPremium(toDouble(v)));
        applyIfPresent(legMap, "minOpenInterest", v -> builder.minOpenInterest(toInt(v)));
        applyIfPresent(legMap, "minVolume", v -> builder.minVolume(toInt(v)));
        applyIfPresent(legMap, "minVolatility", v -> builder.minVolatility(toDouble(v)));
        applyIfPresent(legMap, "maxVolatility", v -> builder.maxVolatility(toDouble(v)));

        setter.accept(builder.build());
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List) {
            return ((List<Object>) value).stream().map(Object::toString).collect(Collectors.toList());
        }
        // Handle comma-separated string
        return Arrays.stream(value.toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static void applyIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<Object> setter) {
        if (map.containsKey(key) && map.get(key) != null) {
            setter.accept(map.get(key));
        }
    }

    private static int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
    }

    private static double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }
}
