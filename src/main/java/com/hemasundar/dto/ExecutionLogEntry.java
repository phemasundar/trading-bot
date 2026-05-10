package com.hemasundar.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a single filter-stage log entry captured during strategy execution.
 * Tracks how many trade candidates pass each filter condition for debugging purposes.
 */
@Data
@Builder
public class ExecutionLogEntry {

    /** Display name of the strategy (e.g. "My Put Credit Spread"). */
    private String strategyName;

    /** The ticker symbol being processed (e.g. "AAPL"). */
    private String symbol;

    /**
     * The option expiry date this filter stage applies to (e.g. "2025-01-17").
     * Null for symbol-level filters (e.g. Historical Volatility, DTE Filter)
     * that run before the per-expiry loop. These appear in the UI "Other" block.
     */
    private String expiry;

    /** The name of the filter stage (e.g. "Delta Filter", "Max Loss Filter"). */
    private String filterStage;

    /** Number of candidates entering this filter stage. */
    private int tradesIn;

    /** Number of candidates that passed this filter stage. */
    private int tradesOut;

    /** Epoch millis when this log entry was captured. */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
}
