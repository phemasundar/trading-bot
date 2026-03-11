package com.hemasundar.dto;

import com.hemasundar.technical.TechnicalScreener;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a technical screener execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerExecutionResult {
    private String screenerId;
    private String screenerName;
    private long executionTimeMs;
    private int resultsFound;
    private List<TechnicalScreener.ScreeningResult> results;
    private java.time.Instant updatedAt;
}
