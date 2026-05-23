package com.hemasundar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hemasundar.technical.TechnicalScreener;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * The original request parameters that produced this result.
     * Populated only for custom screener executions so the UI can offer
     * a "Load Filters" button. Null for scheduled global screener results.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> requestParams;
}
