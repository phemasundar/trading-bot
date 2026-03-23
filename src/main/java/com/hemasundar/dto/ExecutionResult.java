package com.hemasundar.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents the complete result of a strategy execution session.
 * Contains results from multiple strategies executed together.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ExecutionResult {

    /**
     * Unique identifier for this execution (format: exec_<timestamp>)
     */
    private String executionId;

    /**
     * Timestamp when the execution started
     */
    private LocalDateTime timestamp;

    /**
     * List of results for each individual strategy
     */
    private List<StrategyResult> results;

    /**
     * Total number of trades found across all strategies
     */
    private int totalTradesFound;

    /**
     * Total execution time in milliseconds
     */
    private long totalExecutionTimeMs;

    /**
     * Whether Telegram notifications were sent for this execution
     */
    private boolean telegramSent;
}
