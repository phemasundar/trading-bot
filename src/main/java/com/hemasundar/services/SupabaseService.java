package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.supabase.*;
import lombok.extern.log4j.Log4j2;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Optional;

/**
 * Service to interact with Supabase REST API for storing IV data and strategy
 * execution results.
 * This class now acts as a facade, delegating to specific repositories.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SupabaseService {
    private final SupabaseClient client;
    private final IVDataRepository ivDataRepository;
    private final StrategyResultRepository strategyResultRepository;
    private final ScreenerResultRepository screenerResultRepository;
    private final CustomExecutionRepository customExecutionRepository;
    private final CustomScreenerRepository customScreenerRepository;
    private final SecurityIndicatorsRepository securityIndicatorsRepository;

    /**
     * Tests connection to Supabase by making a simple GET request.
     *
     * @return true if connection is successful
     * @throws IOException if connection fails
     */
    public boolean testConnection() throws IOException {
        return client.testConnection();
    }

    /**
     * Upserts (inserts or updates) IV data point to Supabase.
     */
    public void upsertIVData(IVDataPoint dataPoint) throws IOException {
        ivDataRepository.upsertIVData(dataPoint);
    }

    /**
     * Computes the IV Rank for a symbol using up to 1 year of historical iv_data.
     * Returns null when data is insufficient (fail-open semantics).
     *
     * @param symbol stock ticker
     * @return IV Rank in [0, 100], or null if fewer than 20 data points are available
     * @throws IOException if the Supabase API call fails
     */
    public Double getIVRank(String symbol) throws IOException {
        return ivDataRepository.getIVRank(symbol);
    }

    /**
     * Computes the IV Percentile for a symbol using up to 1 year of historical iv_data.
     * Returns null when data is insufficient (fail-open semantics).
     *
     * @param symbol stock ticker
     * @return IV Percentile in [0, 100], or null if fewer than 20 data points are available
     * @throws IOException if the Supabase API call fails
     */
    public Double getIVPercentile(String symbol) throws IOException {
        return ivDataRepository.getIVPercentile(symbol);
    }

    /**
     * Saves a strategy execution result to Supabase.
     */
    public void saveExecutionResult(ExecutionResult result) throws IOException {
        customExecutionRepository.saveExecutionResult(result);
    }

    /**
     * Retrieves the latest strategy execution result from Supabase.
     */
    public Optional<ExecutionResult> getLatestExecutionResult() throws IOException {
        return customExecutionRepository.getLatestExecutionResult();
    }

    // ==================== Per-Strategy Result Persistence ====================

    /**
     * Saves or updates the latest result for a single strategy.
     */
    public void saveStrategyResult(com.hemasundar.dto.StrategyResult result) throws IOException {
        strategyResultRepository.saveStrategyResult(result);
    }

    /**
     * Retrieves all latest strategy results from Supabase.
     */
    public java.util.List<com.hemasundar.dto.StrategyResult> getAllLatestStrategyResults() throws IOException {
        return strategyResultRepository.getAllLatestStrategyResults();
    }

    // ==================== Per-Screener Result Persistence ====================

    /**
     * Saves or updates the latest result for a single technical screener.
     */
    public void saveScreenerResult(com.hemasundar.dto.ScreenerExecutionResult result) throws IOException {
        screenerResultRepository.saveScreenerResult(result);
    }

    /**
     * Retrieves all latest screener results from Supabase.
     */
    public java.util.List<com.hemasundar.dto.ScreenerExecutionResult> getAllLatestScreenerResults() throws IOException {
        return screenerResultRepository.getAllLatestScreenerResults();
    }

    // ==================== Custom Execution Results (Execute View) ====================

    /**
     * Saves a custom execution result to the dedicated table.
     */
    public void saveCustomExecutionResult(com.hemasundar.dto.StrategyResult result,
            java.util.List<String> securities) throws IOException {
        customExecutionRepository.saveCustomExecutionResult(result, securities);
    }

    /**
     * Updates an existing custom execution result in Supabase by its database ID.
     */
    public void updateCustomExecutionResult(Long id, com.hemasundar.dto.StrategyResult result,
            java.util.List<String> securities) throws IOException {
        customExecutionRepository.updateCustomExecutionResult(id, result, securities);
    }

    /**
     * Retrieves the most recent custom execution results.
     */
    public java.util.List<com.hemasundar.dto.StrategyResult> getRecentCustomExecutions(int limit) throws IOException {
        return customExecutionRepository.getRecentCustomExecutions(limit);
    }

    /**
     * Deletes a custom execution result by its database ID.
     */
    public void deleteCustomExecution(String id) throws IOException {
        customExecutionRepository.deleteCustomExecution(id);
    }

    // ==================== Custom Screener Results ====================

    /**
     * Saves a custom screener execution result to the dedicated table,
     * including the original request parameters for the "Load Filters" feature.
     */
    public void saveCustomScreenerResult(com.hemasundar.dto.ScreenerExecutionResult result,
            java.util.List<String> securities, java.util.Map<String, Object> requestParams) throws IOException {
        customScreenerRepository.saveCustomScreenerResult(result, securities, requestParams);
    }

    /**
     * Retrieves the most recent custom screener execution results.
     */
    public java.util.List<com.hemasundar.dto.ScreenerExecutionResult> getRecentCustomScreenerExecutions(int limit) throws IOException {
        return customScreenerRepository.getRecentCustomScreenerExecutions(limit);
    }

    /**
     * Deletes a custom screener execution result by its database ID.
     */
    public void deleteCustomScreenerExecution(String id) throws IOException {
        customScreenerRepository.deleteCustomScreenerExecution(id);
    }

    // ==================== Security Technical Indicators Persistence ====================

    /**
     * Saves or updates calculated technical indicators for a collection of securities.
     * Replaces existing records for matching symbols in latest_security_indicators.
     */
    public void saveSecurityIndicators(java.util.List<com.hemasundar.technical.TechnicalScreener.ScreeningResult> results) throws IOException {
        securityIndicatorsRepository.saveSecurityIndicators(results);
    }

    /**
     * Retrieves all saved security indicators from latest_security_indicators table.
     */
    public java.util.List<com.hemasundar.technical.TechnicalScreener.ScreeningResult> getAllSecurityIndicators() throws IOException {
        return securityIndicatorsRepository.getAllSecurityIndicators();
    }

    /**
     * Retrieves saved security indicators for a specified set of ticker symbols.
     */
    public java.util.Map<String, com.hemasundar.technical.TechnicalScreener.ScreeningResult> getSecurityIndicatorsForSymbols(
            java.util.Collection<String> symbols) throws IOException {
        return securityIndicatorsRepository.getSecurityIndicatorsForSymbols(symbols);
    }
}
