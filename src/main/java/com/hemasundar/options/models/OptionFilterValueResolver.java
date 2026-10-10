package com.hemasundar.options.models;

import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Utility class to resolve numeric variable values from options legs, candidates, and trades
 * for evaluation against {@link com.hemasundar.technical.MathExpression}.
 */
@UtilityClass
public class OptionFilterValueResolver {

    /**
     * Resolves a numeric value from an {@link OptionChainResponse.OptionData} leg for a given variable name.
     *
     * @param leg      the option leg data
     * @param property the variable name (e.g. DELTA, OPEN_INTEREST, PREMIUM, IV)
     * @return the resolved Double value, or null if unresolvable
     */
    public static Double resolveLegValue(OptionChainResponse.OptionData leg, String property) {
        if (leg == null || StringUtils.isBlank(property)) {
            return null;
        }

        String normalized = property.trim().toUpperCase();

        // Strip leg prefix if passed directly
        if (normalized.contains(".")) {
            normalized = normalized.substring(normalized.indexOf('.') + 1);
        }

        return switch (normalized) {
            case "DELTA" -> leg.getAbsDelta();
            case "SIGNED_DELTA" -> leg.getDelta();
            case "GAMMA" -> leg.getGamma();
            case "THETA" -> leg.getTheta();
            case "VEGA" -> leg.getVega();
            case "RHO" -> leg.getRho();
            case "MARK" -> leg.getMark();
            case "BID" -> leg.getBid();
            case "ASK" -> leg.getAsk();
            case "LAST" -> leg.getLast();
            case "VOLUME" -> (double) leg.getTotalVolume();
            case "OPEN_INTEREST" -> (double) leg.getOpenInterest();
            case "IV" -> leg.getVolatility();
            case "STRIKE_PRICE" -> leg.getStrikePrice();
            case "DTE" -> (double) leg.getDaysToExpiration();
            case "INTRINSIC_VALUE" -> leg.getIntrinsicValue();
            case "EXTRINSIC_VALUE" -> leg.getExtrinsicValue();
            default -> null;
        };
    }

    /**
     * Resolves a numeric value from a {@link TradeSetup} for a given variable name.
     * Supports both trade-level metrics (e.g. MAX_LOSS, ROR, CAGR) and dotted leg metrics
     * (e.g. SHORT_LEG.DELTA, LONG_LEG.OPEN_INTEREST).
     *
     * @param trade    the trade setup
     * @param property the variable name
     * @return the resolved Double value, or null if unresolvable
     */
    public static Double resolveTradeValue(TradeSetup trade, String property) {
        if (trade == null || StringUtils.isBlank(property)) {
            return null;
        }

        String normalized = property.trim().toUpperCase();

        // Handle dotted leg navigation (e.g. SHORT_LEG.DELTA)
        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.", 2);
            String legPrefix = parts[0];
            String legProperty = parts[1];

            OptionChainResponse.OptionData legData = findLegInTrade(trade.getLegs(), legPrefix);
            return resolveLegValue(legData, legProperty);
        }

        // Check strategy-specific models
        if (trade instanceof LongCallLeap leap) {
            switch (normalized) {
                case "COST_SAVINGS_PCT" -> { return leap.getCostSavingsPercent(); }
                case "OPTION_PRICE_PCT" -> { return leap.getOptionPricePercent(); }
                case "OPTION_PRICE" -> { return leap.getOptionPrice(); }
                case "FINAL_COST_OF_OPTION" -> { return leap.getFinalCostOfOption(); }
                case "FINAL_COST_OF_BUYING" -> { return leap.getFinalCostOfBuying(); }
                default -> {}
            }
        } else if (trade instanceof BrokenWingButterfly bwb) {
            switch (normalized) {
                case "TOTAL_DEBIT" -> { return bwb.getTotalDebit(); }
                case "MAX_LOSS_UPSIDE" -> { return bwb.getMaxLossUpside(); }
                case "MAX_LOSS_DOWNSIDE" -> { return bwb.getMaxLossDownside(); }
                case "LOWER_WING_WIDTH" -> { return bwb.getLowerWingWidth(); }
                case "UPPER_WING_WIDTH" -> { return bwb.getUpperWingWidth(); }
                case "UPPER_BREAK_EVEN_DELTA" -> { return bwb.getUpperBreakEvenDelta(); }
                default -> {}
            }
        }

        return switch (normalized) {
            case "MAX_LOSS" -> trade.getMaxLoss();
            case "NET_CREDIT" -> trade.getNetCredit();
            case "TOTAL_DEBIT" -> (trade.getNetCredit() < 0 ? -trade.getNetCredit() : 0.0);
            case "RETURN_ON_RISK" -> trade.getReturnOnRisk();
            case "CAGR" -> trade.getReturnOnRiskCAGR();
            case "BREAK_EVEN_PRICE" -> trade.getBreakEvenPrice();
            case "BREAK_EVEN_PCT" -> trade.getBreakEvenPercentage();
            case "UPPER_BREAK_EVEN_PRICE" -> trade.getUpperBreakEvenPrice();
            case "UPPER_BREAK_EVEN_PCT" -> trade.getUpperBreakEvenPercentage();
            case "UPPER_BREAK_EVEN_DELTA" -> trade.getUpperBreakEvenDelta();
            case "CURRENT_PRICE" -> trade.getCurrentPrice();
            case "DTE" -> (double) trade.getDaysToExpiration();
            case "NET_EXTRINSIC_VALUE" -> trade.getNetExtrinsicValue();
            case "ANNUALIZED_EXTRINSIC_PCT" ->
                    trade.getAnnualizedNetExtrinsicValueToCapitalPercentage();
            case "BREAKEVEN_CAGR" -> trade.getBreakevenCAGR();
            default -> null;
        };
    }

    /**
     * Resolves a numeric value from candidate metrics and legs.
     *
     * @param metrics  map of calculated metrics for the candidate
     * @param legs     map of leg identifier to OptionData
     * @param property the variable name
     * @return the resolved Double value, or null if unresolvable
     */
    public static Double resolveCandidateValue(Map<String, Double> metrics,
                                               Map<String, OptionChainResponse.OptionData> legs,
                                               String property) {
        if (StringUtils.isBlank(property)) {
            return null;
        }

        String normalized = property.trim().toUpperCase();

        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.", 2);
            String legName = parts[0];
            String legProp = parts[1];

            if (legs != null && legs.containsKey(legName)) {
                return resolveLegValue(legs.get(legName), legProp);
            }
            return null;
        }

        if (metrics != null && metrics.containsKey(normalized)) {
            return metrics.get(normalized);
        }

        return null;
    }

    private static OptionChainResponse.OptionData findLegInTrade(List<TradeLeg> legs, String legPrefix) {
        if (CollectionUtils.isEmpty(legs)) {
            return null;
        }

        String clean = legPrefix.replace("_", "").toUpperCase();

        // Positional prefixes
        if (clean.equals("LEG1") && legs.size() >= 1) return legs.get(0).getOptionData();
        if (clean.equals("LEG2") && legs.size() >= 2) return legs.get(1).getOptionData();
        if (clean.equals("LEG3") && legs.size() >= 3) return legs.get(2).getOptionData();
        if (clean.equals("LEG4") && legs.size() >= 4) return legs.get(3).getOptionData();

        for (TradeLeg leg : legs) {
            boolean isSell = "SELL".equalsIgnoreCase(leg.getAction());
            boolean isBuy = "BUY".equalsIgnoreCase(leg.getAction());
            boolean isCall = "CALL".equalsIgnoreCase(leg.getOptionType());
            boolean isPut = "PUT".equalsIgnoreCase(leg.getOptionType());

            switch (clean) {
                case "SHORTLEG" -> {
                    if (isSell) return leg.getOptionData();
                }
                case "LONGLEG" -> {
                    if (isBuy) return leg.getOptionData();
                }
                case "PUTSHORT" -> {
                    if (isSell && isPut) return leg.getOptionData();
                }
                case "PUTLONG" -> {
                    if (isBuy && isPut) return leg.getOptionData();
                }
                case "CALLSHORT" -> {
                    if (isSell && isCall) return leg.getOptionData();
                }
                case "CALLLONG" -> {
                    if (isBuy && isCall) return leg.getOptionData();
                }
                default -> {}
            }
        }

        return null;
    }

    /**
     * Validates whether a variable name is a supported options metric (trade or leg).
     *
     * @param property property name to check
     * @return true if supported
     */
    public static boolean isSupportedVariable(String property) {
        if (StringUtils.isBlank(property)) {
            return false;
        }
        String normalized = property.trim().toUpperCase();
        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.", 2);
            return isSupportedLegPrefix(parts[0]) && isSupportedLegVariable(parts[1]);
        }
        return isSupportedTradeVariable(normalized) || isSupportedLegVariable(normalized);
    }

    public static boolean isSupportedLegPrefix(String prefix) {
        if (StringUtils.isBlank(prefix)) return false;
        String clean = prefix.replace("_", "").toUpperCase();
        return clean.equals("SHORTLEG") ||
               clean.equals("LONGLEG") ||
               clean.equals("PUTSHORT") ||
               clean.equals("PUTLONG") ||
               clean.equals("CALLSHORT") ||
               clean.equals("CALLLONG") ||
               clean.equals("LEG1") ||
               clean.equals("LEG2") ||
               clean.equals("LEG3") ||
               clean.equals("LEG4");
    }

    public static boolean isSupportedLegVariable(String var) {
        if (StringUtils.isBlank(var)) return false;
        return switch (var.trim().toUpperCase()) {
            case "DELTA", "SIGNED_DELTA",
                 "GAMMA", "THETA", "VEGA", "RHO",
                 "MARK", "BID", "ASK", "LAST",
                 "VOLUME", "OPEN_INTEREST",
                 "IV", "STRIKE_PRICE",
                 "DTE", "INTRINSIC_VALUE", "EXTRINSIC_VALUE" -> true;
            default -> false;
        };
    }

    public static boolean isSupportedTradeVariable(String var) {
        if (StringUtils.isBlank(var)) return false;
        return switch (var.trim().toUpperCase()) {
            case "MAX_LOSS",
                 "NET_CREDIT",
                 "TOTAL_DEBIT",
                 "RETURN_ON_RISK", "CAGR",
                 "BREAK_EVEN_PRICE",
                 "BREAK_EVEN_PCT",
                 "UPPER_BREAK_EVEN_PRICE",
                 "UPPER_BREAK_EVEN_PCT",
                 "UPPER_BREAK_EVEN_DELTA",
                 "CURRENT_PRICE",
                 "DTE",
                 "NET_EXTRINSIC_VALUE",
                 "ANNUALIZED_EXTRINSIC_PCT",
                 "BREAKEVEN_CAGR",
                 "IV_RANK", "IV_PERCENTILE",
                 "DAYS_TO_NEXT_EARNINGS", "EARNINGS_NEAREST_TO_DTE",
                 "COST_SAVINGS_PCT",
                 "OPTION_PRICE_PCT", "OPTION_PRICE",
                 "FINAL_COST_OF_OPTION", "FINAL_COST_OF_BUYING",
                 "MAX_LOSS_UPSIDE", "MAX_LOSS_DOWNSIDE",
                 "LOWER_WING_WIDTH", "UPPER_WING_WIDTH" -> true;
            default -> false;
        };
    }
}
