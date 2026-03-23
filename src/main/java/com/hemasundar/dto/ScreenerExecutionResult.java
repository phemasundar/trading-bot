package com.hemasundar.dto;

import com.hemasundar.technical.TechnicalScreener;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a technical screener execution.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ScreenerExecutionResult {
    private String screenerId;
    private String screenerName;
    private long executionTimeMs;
    private int resultsFound;
    private List<TechnicalScreener.ScreeningResult> results;
    private java.time.Instant updatedAt;
}
