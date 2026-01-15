package com.hemasundar.pojos.technicalfilters;

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
 *         .rsiCondition(RSICondition.OVERSOLD) // RSI < 30
 *         .bollingerCondition(BollingerCondition.LOWER_BAND) // Price at lower band
 *         .minVolume(1_000_000L) // Minimum 1M shares
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class FilterConditions {

    /**
     * RSI condition to check: OVERSOLD (RSI < threshold) or OVERBOUGHT (RSI >
     * threshold).
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
     * Returns a readable summary of the conditions.
     */
    public String getSummary() {
        String volumeStr = minVolume != null && minVolume > 0
                ? String.format("%,d", minVolume)
                : "N/A";
        return String.format("RSI: %s | Bollinger: %s | Volume >= %s",
                rsiCondition != null ? rsiCondition.name() : "N/A",
                bollingerCondition != null ? bollingerCondition.name() : "N/A",
                volumeStr);
    }
}
