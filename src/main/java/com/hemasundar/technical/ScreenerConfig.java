package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for a technical stock screener.
 * Encapsulates the screener type and the filter conditions to apply.
 * Screener name is derived from screenerType.toString().
 * 
 * Example usage:
 * 
 * <pre>
 * List&lt;ScreenerConfig&gt; screeners = List.of(
 *         ScreenerConfig.builder()
 *                 .screenerType(ScreenerType.RSI_BB_BULLISH_CROSSOVER)
 *                 .conditions(FilterConditions.builder()
 *                         .rsiCondition(RSICondition.BULLISH_CROSSOVER)
 *                         .bollingerCondition(BollingerCondition.LOWER_BAND)
 *                         .build())
 *                 .build());
 * </pre>
 */
@Getter
@Builder
public class ScreenerConfig {

    /**
     * The screener type (enum value).
     */
    private final ScreenerType screenerType;

    /**
     * Optional custom display name.
     */
    private final String alias;

    /**
     * List of stock tickers to run the screener against.
     */
    private final java.util.List<String> securities;

    /**
     * Filter conditions defining what to screen for.
     */
    private final TechFilterConditions conditions;

    /**
     * Gets the display name from the alias if present, otherwise screener type.
     */
    public String getName() {
        return (alias != null && !alias.trim().isEmpty()) ? alias : screenerType.toString();
    }
}
