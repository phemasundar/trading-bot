package com.hemasundar.dto;

import com.hemasundar.options.models.TradeSetup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of executing a single trading strategy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyResult {

    /**
     * Unique identifier for the strategy (e.g., "strategy_0")
     */
    private String strategyId;

    /**
     * Display name/alias of the strategy
     */
    private String strategyName;

    /**
     * Execution time for this strategy in milliseconds
     */
    private long executionTimeMs;

    /**
     * Number of trades found by this strategy
     */
    private int tradesFound;

    /**
     * List of trades found by this strategy
     */
    private List<Trade> trades;

    /**
     * Timestamp when this result was last updated (from database)
     */
    private java.time.Instant updatedAt;

    /**
     * Builds a StrategyResult from a trades map (symbol_expiry → TradeSetup list).
     * Shared by StrategyExecutionService (Vaadin) and SampleTestNG (TestNG).
     *
     * @param strategyName    display name of the strategy
     * @param allTrades       map of "SYMBOL_expiryDate" → List of TradeSetup
     * @param executionTimeMs time taken to execute the strategy
     * @return StrategyResult ready for Supabase persistence
     */
    public static StrategyResult fromTrades(String strategyName,
            Map<String, List<TradeSetup>> allTrades,
            long executionTimeMs) {
        List<Trade> tradeDTOs = new ArrayList<>();
        for (Map.Entry<String, List<TradeSetup>> entry : allTrades.entrySet()) {
            String key = entry.getKey();
            String symbol = key.contains("_") ? key.substring(0, key.indexOf("_")) : key;
            for (TradeSetup setup : entry.getValue()) {
                tradeDTOs.add(Trade.fromTradeSetup(setup, symbol));
            }
        }

        return StrategyResult.builder()
                .strategyId(strategyName)
                .strategyName(strategyName)
                .executionTimeMs(executionTimeMs)
                .tradesFound(tradeDTOs.size())
                .trades(tradeDTOs)
                .build();
    }
}
