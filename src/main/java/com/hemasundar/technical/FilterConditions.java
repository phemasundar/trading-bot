package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;

/**
 * Container for filter conditions to check.
 * Defines WHAT CONDITIONS to look for in the indicators.
 * Separated from indicator definitions to allow reuse of indicators with
 * different conditions.
 * 
 * Example:
 * 
 * <pre>
 * FilterConditions oversoldConditions = FilterConditions.builder()
 *         .rsiCondition(RSICondition.BULLISH_CROSSOVER) // RSI crossed from <30 to >=30
 *         .bollingerCondition(BollingerCondition.LOWER_BAND) // Price at lower band
 *         .requirePriceBelowMA20(true) // Price below MA(20)
 *         .requirePriceBelowMA50(true) // Price below MA(50)
 *         .minVolume(1_000_000L) // Minimum 1M shares
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class FilterConditions {

    /**
     * RSI condition to check: OVERSOLD, OVERBOUGHT, BULLISH_CROSSOVER, or
     * BEARISH_CROSSOVER.
     */
    private final RSICondition rsiCondition;

    /**
     * Bollinger Band condition to check: LOWER_BAND or UPPER_BAND.
     */
    private final BollingerCondition bollingerCondition;

    /**
     * Minimum volume threshold. Stock must have at least this many shares traded.
     * Set to 0 or null to disable volume check.
     */
    private final Long minVolume;

    /**
     * When true, requires price to be below MA(20).
     */
    @Builder.Default
    private final boolean requirePriceBelowMA20 = false;

    /**
     * When true, requires price to be above MA(20).
     */
    @Builder.Default
    private final boolean requirePriceAboveMA20 = false;

    /**
     * When true, requires price to be below MA(50).
     */
    @Builder.Default
    private final boolean requirePriceBelowMA50 = false;

    /**
     * When true, requires price to be above MA(50).
     */
    @Builder.Default
    private final boolean requirePriceAboveMA50 = false;

    /**
     * When true, requires price to be below MA(100).
     */
    @Builder.Default
    private final boolean requirePriceBelowMA100 = false;

    /**
     * When true, requires price to be above MA(100).
     */
    @Builder.Default
    private final boolean requirePriceAboveMA100 = false;

    /**
     * When true, requires price to be below MA(200).
     */
    @Builder.Default
    private final boolean requirePriceBelowMA200 = false;

    /**
     * When true, requires price to be above MA(200).
     */
    @Builder.Default
    private final boolean requirePriceAboveMA200 = false;

    /**
     * Returns a readable summary of the conditions.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();

        if (rsiCondition != null) {
            sb.append("RSI: ").append(rsiCondition.name()).append(" | ");
        }
        if (bollingerCondition != null) {
            sb.append("BB: ").append(bollingerCondition.name()).append(" | ");
        }
        if (requirePriceBelowMA20) {
            sb.append("Price < MA20 | ");
        }
        if (requirePriceAboveMA20) {
            sb.append("Price > MA20 | ");
        }
        if (requirePriceBelowMA50) {
            sb.append("Price < MA50 | ");
        }
        if (requirePriceAboveMA50) {
            sb.append("Price > MA50 | ");
        }
        if (requirePriceBelowMA100) {
            sb.append("Price < MA100 | ");
        }
        if (requirePriceAboveMA100) {
            sb.append("Price > MA100 | ");
        }
        if (requirePriceBelowMA200) {
            sb.append("Price < MA200 | ");
        }
        if (requirePriceAboveMA200) {
            sb.append("Price > MA200 | ");
        }
        if (minVolume != null && minVolume > 0) {
            sb.append(String.format("Volume >= %,d", minVolume));
        }

        if (sb.length() == 0)
            return "No conditions set";
        String result = sb.toString();
        return result.endsWith(" | ") ? result.substring(0, result.length() - 3) : result;
    }
}
