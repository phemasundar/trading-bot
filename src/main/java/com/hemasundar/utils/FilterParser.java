package com.hemasundar.utils;

import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.hemasundar.technical.MathExpression;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterParser {

    public static OptionsStrategyFilter buildFilter(StrategyType type, Map<String, Object> filterMap) {
        OptionsStrategyFilter filter;

        // Create the appropriate filter subclass based on strategy type
        switch (type) {
            case PUT_CREDIT_SPREAD:
            case BULLISH_LONG_PUT_CREDIT_SPREAD:
            case TECH_PUT_CREDIT_SPREAD:
            case CALL_CREDIT_SPREAD:
            case TECH_CALL_CREDIT_SPREAD:
            case SHORT_PUT:
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
            case SHORT_STRANGLE:
                filter = new ShortStrangleFilter();
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
        applyIfPresent(filterMap, "minReturnOnRiskCAGR", v -> filter.setMinReturnOnRiskCAGR(toInt(v)));
        applyIfPresent(filterMap, "maxBreakEvenPercentage", v -> filter.setMaxBreakEvenPercentage(toDouble(v)));
        applyIfPresent(filterMap, "maxUpperBreakevenDelta", v -> filter.setMaxUpperBreakevenDelta(toDouble(v)));
        applyIfPresent(filterMap, "maxNetExtrinsicValueToPricePercentage",
                v -> filter.setMaxNetExtrinsicValueToPricePercentage(toDouble(v)));
        applyIfPresent(filterMap, "minNetExtrinsicValueToPricePercentage",
                v -> filter.setMinNetExtrinsicValueToPricePercentage(toDouble(v)));

        // ── Math formula-like conditions ──
        if (filterMap.containsKey("conditions") && filterMap.get("conditions") != null) {
            List<String> stringRules = toStringList(filterMap.get("conditions"));
            filter.setConditions(stringRules);
            if (!stringRules.isEmpty()) {
                filter.setFilterExpressions(MathExpressionParser.parseRules(stringRules));
            }
        }

        if (filterMap.containsKey("earningsFilters") && filterMap.get("earningsFilters") != null) {
            Object earningsFiltersObj = filterMap.get("earningsFilters");
            if (earningsFiltersObj instanceof Map<?, ?> earningsMap) {
                if (earningsMap.containsKey("conditions") && earningsMap.get("conditions") != null) {
                    List<String> stringRules = toStringList(earningsMap.get("conditions"));
                    if (!stringRules.isEmpty()) {
                        filter.setEarningsFilterExpressions(MathExpressionParser.parseRules(stringRules));
                    }
                }
            }
            // Also store the raw map for serialization/UI rendering if needed
            filter.setEarningsFilters((Map<String, Object>) earningsFiltersObj);
        }

        // Also route any earnings conditions in filter.filterExpressions to earningsFilterExpressions
        if (filter.getFilterExpressions() != null) {
            for (MathExpression expr : filter.getFilterExpressions()) {
                String left = expr.getLeftVariable() != null ? expr.getLeftVariable().toUpperCase() : "";
                String right = expr.getRightVariable() != null ? expr.getRightVariable().toUpperCase() : "";
                if (left.contains("EARNINGS") || right.contains("EARNINGS")) {
                    if (!filter.getEarningsFilterExpressions().contains(expr)) {
                        filter.getEarningsFilterExpressions().add(expr);
                    }
                }
            }
        }

        applyIfPresent(filterMap, "maxTotalDebit", v -> filter.setMaxTotalDebit(toDouble(v)));
        applyIfPresent(filterMap, "maxTotalCredit", v -> filter.setMaxTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "minTotalCredit", v -> filter.setMinTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "priceVsMaxDebitRatio", v -> filter.setPriceVsMaxDebitRatio(toDouble(v)));
        applyIfPresent(filterMap, "maxCAGRForBreakEven", v -> filter.setMaxCAGRForBreakEven(toDouble(v)));
        applyIfPresent(filterMap, "maxOptionPricePercent", v -> filter.setMaxOptionPricePercent(toDouble(v)));
        applyIfPresent(filterMap, "marginInterestRate", v -> filter.setMarginInterestRate(toDouble(v)));
        applyIfPresent(filterMap, "savingsInterestRate", v -> filter.setSavingsInterestRate(toDouble(v)));
        applyIfPresent(filterMap, "includeOnly", v -> filter.setIncludeOnly(toStringList(v)));
        applyIfPresent(filterMap, "excludeIf", v -> filter.setExcludeIf(toStringList(v)));
        applyIfPresent(filterMap, "topTradesCount", v -> filter.setTopTradesCount(toInt(v)));
        applyIfPresent(filterMap, "minIVRank", v -> filter.setMinIVRank(toDouble(v)));
        applyIfPresent(filterMap, "maxIVRank", v -> filter.setMaxIVRank(toDouble(v)));
        applyIfPresent(filterMap, "minIVPercentile", v -> filter.setMinIVPercentile(toDouble(v)));
        applyIfPresent(filterMap, "maxIVPercentile", v -> filter.setMaxIVPercentile(toDouble(v)));

        // ── Strategy-specific fields ──
        if (filter instanceof CreditSpreadFilter csFilter) {
            applyLegFilter(filterMap, "shortLeg", csFilter::setShortLeg);
            if (csFilter.getShortLeg() == null) {
                applyLegFilter(filterMap, "shortPut", csFilter::setShortLeg);
                applyLegFilter(filterMap, "shortCall", csFilter::setShortLeg);
            }
            applyLegFilter(filterMap, "longLeg", csFilter::setLongLeg);
            if (csFilter.getLongLeg() == null) {
                applyLegFilter(filterMap, "longPut", csFilter::setLongLeg);
                applyLegFilter(filterMap, "longCall", csFilter::setLongLeg);
            }
        } else if (filter instanceof IronCondorFilter icFilter) {
            applyLegFilter(filterMap, "putShortLeg", icFilter::setPutShortLeg);
            applyLegFilter(filterMap, "putLongLeg", icFilter::setPutLongLeg);
            applyLegFilter(filterMap, "callShortLeg", icFilter::setCallShortLeg);
            applyLegFilter(filterMap, "callLongLeg", icFilter::setCallLongLeg);
        } else if (filter instanceof LongCallLeapFilter leapFilter) {
            applyLegFilter(filterMap, "longCall", leapFilter::setLongCall);
            if (leapFilter.getLongCall() == null) {
                applyLegFilter(filterMap, "longLeg", leapFilter::setLongCall);
            }
            applyIfPresent(filterMap, "minCostSavingsPercent", v -> leapFilter.setMinCostSavingsPercent(toDouble(v)));
            applyIfPresent(filterMap, "relaxationPriority", v -> leapFilter.setRelaxationPriority(toStringList(v)));
            applyIfPresent(filterMap, "sortPriority", v -> leapFilter.setSortPriority(toStringList(v)));
        } else if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            applyLegFilter(filterMap, "leg1Long", bwbFilter::setLeg1Long);
            applyLegFilter(filterMap, "leg2Short", bwbFilter::setLeg2Short);
            applyLegFilter(filterMap, "leg3Long", bwbFilter::setLeg3Long);
        } else if (filter instanceof ZebraFilter zebraFilter) {
            applyLegFilter(filterMap, "shortCall", zebraFilter::setShortCall);
            if (zebraFilter.getShortCall() == null) {
                applyLegFilter(filterMap, "shortLeg", zebraFilter::setShortCall);
            }
            applyLegFilter(filterMap, "longCall", zebraFilter::setLongCall);
            if (zebraFilter.getLongCall() == null) {
                applyLegFilter(filterMap, "longLeg", zebraFilter::setLongCall);
            }
        } else if (filter instanceof ShortStrangleFilter strangleFilter) {
            applyLegFilter(filterMap, "putShortLeg", strangleFilter::setPutShortLeg);
            applyLegFilter(filterMap, "callShortLeg", strangleFilter::setCallShortLeg);
        }

        initFilterExpressions(filter);

        return filter;
    }

    /**
     * Initializes mathematical filter expressions, earnings expressions, and leg expressions
     * on the given filter from its configured string conditions.
     *
     * @param filter The options strategy filter to initialize
     */
    public static void initFilterExpressions(OptionsStrategyFilter filter) {
        if (filter == null) {
            return;
        }

        // 1. Root conditions -> filterExpressions
        if (CollectionUtils.isNotEmpty(filter.getConditions())) {
            List<MathExpression> parsed = MathExpressionParser.parseRules(filter.getConditions());
            if (parsed.size() != filter.getConditions().size()) {
                throw new IllegalStateException(String.format(
                        "Failed to parse all filter conditions: expected %d expressions from %s, but got %d",
                        filter.getConditions().size(), filter.getConditions(), parsed.size()));
            }
            for (int i = 0; i < parsed.size(); i++) {
                validateConditionVariables(parsed.get(i), filter.getConditions().get(i), false);
            }
            if (filter.getFilterExpressions() == null || filter.getFilterExpressions().isEmpty()) {
                filter.setFilterExpressions(parsed);
            } else {
                for (MathExpression expr : parsed) {
                    if (!filter.getFilterExpressions().contains(expr)) {
                        filter.getFilterExpressions().add(expr);
                    }
                }
            }
        }

        // 2. Earnings conditions
        if (filter.getEarningsFilters() != null) {
            Object earningsConditions = filter.getEarningsFilters().get("conditions");
            if (earningsConditions != null) {
                List<String> stringRules = toStringList(earningsConditions);
                if (CollectionUtils.isNotEmpty(stringRules)) {
                    List<MathExpression> parsed = MathExpressionParser.parseRules(stringRules);
                    if (parsed.size() != stringRules.size()) {
                        throw new IllegalStateException(String.format(
                                "Failed to parse all earnings filter conditions: expected %d expressions from %s, but got %d",
                                stringRules.size(), stringRules, parsed.size()));
                    }
                    for (int i = 0; i < parsed.size(); i++) {
                        validateConditionVariables(parsed.get(i), stringRules.get(i), false);
                    }
                    if (filter.getEarningsFilterExpressions() == null || filter.getEarningsFilterExpressions().isEmpty()) {
                        filter.setEarningsFilterExpressions(parsed);
                    } else {
                        for (MathExpression expr : parsed) {
                            if (!filter.getEarningsFilterExpressions().contains(expr)) {
                                filter.getEarningsFilterExpressions().add(expr);
                            }
                        }
                    }
                }
            }
        }

        // 3. Route any earnings conditions in filterExpressions to earningsFilterExpressions
        if (filter.getFilterExpressions() != null) {
            if (filter.getEarningsFilterExpressions() == null) {
                filter.setEarningsFilterExpressions(new ArrayList<>());
            }
            for (MathExpression expr : filter.getFilterExpressions()) {
                String left = expr.getLeftVariable() != null ? expr.getLeftVariable().toUpperCase() : "";
                String right = expr.getRightVariable() != null ? expr.getRightVariable().toUpperCase() : "";
                if (left.contains("EARNINGS") || right.contains("EARNINGS")) {
                    if (!filter.getEarningsFilterExpressions().contains(expr)) {
                        filter.getEarningsFilterExpressions().add(expr);
                    }
                }
            }
        }

        // 4. Leg conditions
        if (filter instanceof CreditSpreadFilter csFilter) {
            setLegNameIfPresent(csFilter.getShortLeg(), "shortLeg");
            setLegNameIfPresent(csFilter.getLongLeg(), "longLeg");
            initLegExpressions(csFilter.getShortLeg());
            initLegExpressions(csFilter.getLongLeg());
        } else if (filter instanceof IronCondorFilter icFilter) {
            setLegNameIfPresent(icFilter.getPutShortLeg(), "putShortLeg");
            setLegNameIfPresent(icFilter.getPutLongLeg(), "putLongLeg");
            setLegNameIfPresent(icFilter.getCallShortLeg(), "callShortLeg");
            setLegNameIfPresent(icFilter.getCallLongLeg(), "callLongLeg");
            initLegExpressions(icFilter.getPutShortLeg());
            initLegExpressions(icFilter.getPutLongLeg());
            initLegExpressions(icFilter.getCallShortLeg());
            initLegExpressions(icFilter.getCallLongLeg());
        } else if (filter instanceof LongCallLeapFilter leapFilter) {
            setLegNameIfPresent(leapFilter.getLongCall(), "longCall");
            initLegExpressions(leapFilter.getLongCall());
        } else if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            setLegNameIfPresent(bwbFilter.getLeg1Long(), "leg1Long");
            setLegNameIfPresent(bwbFilter.getLeg2Short(), "leg2Short");
            setLegNameIfPresent(bwbFilter.getLeg3Long(), "leg3Long");
            initLegExpressions(bwbFilter.getLeg1Long());
            initLegExpressions(bwbFilter.getLeg2Short());
            initLegExpressions(bwbFilter.getLeg3Long());
        } else if (filter instanceof ZebraFilter zebraFilter) {
            setLegNameIfPresent(zebraFilter.getShortCall(), "shortCall");
            setLegNameIfPresent(zebraFilter.getLongCall(), "longCall");
            initLegExpressions(zebraFilter.getShortCall());
            initLegExpressions(zebraFilter.getLongCall());
        } else if (filter instanceof ShortStrangleFilter strangleFilter) {
            setLegNameIfPresent(strangleFilter.getPutShortLeg(), "putShortLeg");
            setLegNameIfPresent(strangleFilter.getCallShortLeg(), "callShortLeg");
            initLegExpressions(strangleFilter.getPutShortLeg());
            initLegExpressions(strangleFilter.getCallShortLeg());
        }

        // 5. Route dotted expressions from root filter to legs
        routeLegExpressions(filter);
    }

    private static void setLegNameIfPresent(LegFilter leg, String name) {
        if (leg != null && StringUtils.isBlank(leg.getLegName())) {
            leg.setLegName(name);
        }
    }

    private static void initLegExpressions(LegFilter leg) {
        if (leg == null || CollectionUtils.isEmpty(leg.getConditions())) {
            return;
        }
        List<MathExpression> parsed = MathExpressionParser.parseRules(leg.getConditions());
        if (parsed.size() != leg.getConditions().size()) {
            throw new IllegalStateException(String.format(
                    "Failed to parse all leg conditions: expected %d expressions from %s, but got %d",
                    leg.getConditions().size(), leg.getConditions(), parsed.size()));
        }
        for (int i = 0; i < parsed.size(); i++) {
            validateConditionVariables(parsed.get(i), leg.getConditions().get(i), true);
        }
        if (leg.getFilterExpressions() == null || leg.getFilterExpressions().isEmpty()) {
            leg.setFilterExpressions(parsed);
        } else {
            for (MathExpression expr : parsed) {
                if (!leg.getFilterExpressions().contains(expr)) {
                    leg.getFilterExpressions().add(expr);
                }
            }
        }
    }

    private static void validateConditionVariables(MathExpression expr, String rule, boolean isLegCondition) {
        if (expr == null) return;
        String left = expr.getLeftVariable();
        if (isLegCondition) {
            if (!OptionFilterValueResolver.isSupportedLegVariable(left)) {
                throw new IllegalArgumentException("Unknown leg filter variable '" + left + "' in condition: " + rule);
            }
        } else {
            if (!OptionFilterValueResolver.isSupportedVariable(left)) {
                throw new IllegalArgumentException("Unknown filter variable '" + left + "' in condition: " + rule);
            }
        }
        String right = expr.getRightVariable();
        if (StringUtils.isNotBlank(right) && !isNumeric(right)) {
            if (!OptionFilterValueResolver.isSupportedVariable(right)) {
                throw new IllegalArgumentException("Unknown filter variable '" + right + "' on right side of condition: " + rule);
            }
        }
    }

    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
        builder.legName(key);
        if (legMap.containsKey("conditions") && legMap.get("conditions") != null) {
            List<String> stringRules = toStringList(legMap.get("conditions"));
            builder.conditions(stringRules);
            if (!stringRules.isEmpty()) {
                builder.filterExpressions(MathExpressionParser.parseRules(stringRules));
            }
        }
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

    private static void routeLegExpressions(OptionsStrategyFilter filter) {
        if (filter.getFilterExpressions() == null || filter.getFilterExpressions().isEmpty()) {
            return;
        }

        for (MathExpression expr : filter.getFilterExpressions()) {
            String left = expr.getLeftVariable();
            if (left != null && left.contains(".")) {
                String[] parts = left.split("\\.", 2);
                String legPrefix = parts[0].toUpperCase();
                String legProp = parts[1];

                MathExpression legExpr = MathExpression.builder()
                        .leftVariable(legProp)
                        .operator(expr.getOperator())
                        .rightVariable(expr.getRightVariable())
                        .rightScale(expr.getRightScale())
                        .rightOffset(expr.getRightOffset())
                        .build();

                attachExprToLeg(filter, legPrefix, legExpr);
            }
        }
    }

    private static void attachExprToLeg(OptionsStrategyFilter filter, String legPrefix, MathExpression expr) {
        String clean = legPrefix.replace("_", "").toUpperCase();
        if (filter instanceof CreditSpreadFilter csFilter) {
            if (clean.startsWith("SHORT")) {
                if (csFilter.getShortLeg() == null) csFilter.setShortLeg(new LegFilter());
                addExprIfAbsent(csFilter.getShortLeg(), expr);
            } else if (clean.startsWith("LONG")) {
                if (csFilter.getLongLeg() == null) csFilter.setLongLeg(new LegFilter());
                addExprIfAbsent(csFilter.getLongLeg(), expr);
            }
        } else if (filter instanceof IronCondorFilter icFilter) {
            if (clean.equals("PUTSHORT") || clean.equals("PUTSHORTLEG") || clean.equals("SHORTPUT") || clean.equals("SHORTPUTLEG")) {
                if (icFilter.getPutShortLeg() == null) icFilter.setPutShortLeg(new LegFilter());
                addExprIfAbsent(icFilter.getPutShortLeg(), expr);
            } else if (clean.equals("PUTLONG") || clean.equals("PUTLONGLEG") || clean.equals("LONGPUT") || clean.equals("LONGPUTLEG")) {
                if (icFilter.getPutLongLeg() == null) icFilter.setPutLongLeg(new LegFilter());
                addExprIfAbsent(icFilter.getPutLongLeg(), expr);
            } else if (clean.equals("CALLSHORT") || clean.equals("CALLSHORTLEG") || clean.equals("SHORTCALL") || clean.equals("SHORTCALLLEG")) {
                if (icFilter.getCallShortLeg() == null) icFilter.setCallShortLeg(new LegFilter());
                addExprIfAbsent(icFilter.getCallShortLeg(), expr);
            } else if (clean.equals("CALLLONG") || clean.equals("CALLLONGLEG") || clean.equals("LONGCALL") || clean.equals("LONGCALLLEG")) {
                if (icFilter.getCallLongLeg() == null) icFilter.setCallLongLeg(new LegFilter());
                addExprIfAbsent(icFilter.getCallLongLeg(), expr);
            }
        } else if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            if (clean.startsWith("LEG1")) {
                if (bwbFilter.getLeg1Long() == null) bwbFilter.setLeg1Long(new LegFilter());
                addExprIfAbsent(bwbFilter.getLeg1Long(), expr);
            } else if (clean.startsWith("LEG2")) {
                if (bwbFilter.getLeg2Short() == null) bwbFilter.setLeg2Short(new LegFilter());
                addExprIfAbsent(bwbFilter.getLeg2Short(), expr);
            } else if (clean.startsWith("LEG3")) {
                if (bwbFilter.getLeg3Long() == null) bwbFilter.setLeg3Long(new LegFilter());
                addExprIfAbsent(bwbFilter.getLeg3Long(), expr);
            }
        } else if (filter instanceof ZebraFilter zebraFilter) {
            if (clean.startsWith("SHORT")) {
                if (zebraFilter.getShortCall() == null) zebraFilter.setShortCall(new LegFilter());
                addExprIfAbsent(zebraFilter.getShortCall(), expr);
            } else if (clean.startsWith("LONG")) {
                if (zebraFilter.getLongCall() == null) zebraFilter.setLongCall(new LegFilter());
                addExprIfAbsent(zebraFilter.getLongCall(), expr);
            }
        } else if (filter instanceof ShortStrangleFilter strangleFilter) {
            if (clean.startsWith("PUT")) {
                if (strangleFilter.getPutShortLeg() == null) strangleFilter.setPutShortLeg(new LegFilter());
                addExprIfAbsent(strangleFilter.getPutShortLeg(), expr);
            } else if (clean.startsWith("CALL")) {
                if (strangleFilter.getCallShortLeg() == null) strangleFilter.setCallShortLeg(new LegFilter());
                addExprIfAbsent(strangleFilter.getCallShortLeg(), expr);
            }
        } else if (filter instanceof LongCallLeapFilter leapFilter) {
            if (clean.startsWith("LONG")) {
                if (leapFilter.getLongCall() == null) leapFilter.setLongCall(new LegFilter());
                addExprIfAbsent(leapFilter.getLongCall(), expr);
            }
        }
    }

    private static void addExprIfAbsent(LegFilter leg, MathExpression expr) {
        if (leg.getFilterExpressions() == null) {
            leg.setFilterExpressions(new ArrayList<>());
        }
        if (!leg.getFilterExpressions().contains(expr)) {
            leg.getFilterExpressions().add(expr);
        }
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
