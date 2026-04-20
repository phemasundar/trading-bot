package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a warning or error captured during strategy/screener execution.
 * These are collected in a thread-safe list and surfaced to the UI
 * via the /api/status endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionAlert {

    public enum Severity {
        WARNING,
        ERROR
    }

    /** WARNING for degraded-but-non-fatal issues, ERROR for failures. */
    private Severity severity;

    /** Context about where the alert originated, e.g. strategy name, screener name, symbol. */
    private String source;

    /** Human-readable description of what went wrong. */
    private String message;

    /** Epoch millis when the alert was captured. */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
}
