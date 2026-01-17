package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for a technical stock screener.
 * Encapsulates the screener name and the filter conditions to apply.
 * 
 * Example usage:
 * 
 * <pre>
 * List&lt;ScreenerConfig&gt; screeners = List.of(
 *         ScreenerConfig.builder()
 *                 .name("RSI BB Crossover")
 *                 .conditions(FilterConditions.builder()
 *                         .rsiCondition(RSICondition.BULLISH_CROSSOVER)
 *                         .bollingerCondition(BollingerCondition.LOWER_BAND)
 *                         .build())
 *                 .build(),
 *         ScreenerConfig.builder()
 *                 .name("Below 50 Day MA")
 *                 .conditions(FilterConditions.builder()
 *                         .requirePriceBelowMA50(true)
 *                         .build())
 *                 .build());
 * </pre>
 */
@Getter
@Builder
public class ScreenerConfig {

    /**
     * Display name for the screener (used in logs and Telegram alerts).
     */
    private final String name;

    /**
     * Filter conditions defining what to screen for.
     */
    private final FilterConditions conditions;
}
