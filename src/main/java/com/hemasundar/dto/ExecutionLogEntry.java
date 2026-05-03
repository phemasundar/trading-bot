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
