package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
}
