package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.supabase.*;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import lombok.extern.log4j.Log4j2;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
     * Computes the IV statistics (including IV Rank and IV Percentile) for a symbol.
     *
     * @param symbol stock ticker
     * @return map of IV stats, or null if fewer than 20 data points are available
     * @throws IOException if the Supabase API call fails
     */
    public Map<String, Object> getIVStats(String symbol) throws IOException {
        return ivDataRepository.getIVStats(symbol);
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
     * Enriches them with IV metrics (IV Percentile, IV Rank, Current IV) so they are
     * pre-cached into Supabase latest_security_indicators.
     */
    public void saveSecurityIndicators(List<ScreeningResult> results) throws IOException {
        if (CollectionUtils.isNotEmpty(results)) {
            enrichListWithIVData(results);
        }
        securityIndicatorsRepository.saveSecurityIndicators(results);
    }

    /**
     * Refreshes and caches IV metrics into latest_security_indicators table for the specified symbols.
     *
     * @param symbols collection of stock tickers
     * @throws IOException if Supabase API calls fail
     */
    public void updateSecurityIndicatorsIV(Collection<String> symbols) throws IOException {
        if (CollectionUtils.isEmpty(symbols)) {
            return;
        }
        Map<String, ScreeningResult> existing = securityIndicatorsRepository.getSecurityIndicatorsForSymbols(symbols);
        if (MapUtils.isNotEmpty(existing)) {
            List<ScreeningResult> toUpdate = new ArrayList<>(existing.values());
            enrichListWithIVData(toUpdate);
            securityIndicatorsRepository.saveSecurityIndicators(toUpdate);
            log.info("Refreshed IV metrics in latest_security_indicators for {} securities", toUpdate.size());
        }
    }

    /**
     * Retrieves all saved security indicators from latest_security_indicators table.
     */
    public List<ScreeningResult> getAllSecurityIndicators() throws IOException {
        return securityIndicatorsRepository.getAllSecurityIndicators();
    }

    /**
     * Retrieves saved security indicators for a specified set of ticker symbols.
     * All indicators (technical filters and IV values) are read directly from the
     * latest_security_indicators table with no on-the-fly calculations.
     */
    public Map<String, ScreeningResult> getSecurityIndicatorsForSymbols(
            Collection<String> symbols) throws IOException {
        return securityIndicatorsRepository.getSecurityIndicatorsForSymbols(symbols);
    }

    /**
     * Enriches a list of screening results with IV data before persistence.
     *
     * @param results list of ScreeningResult objects
     */
    private void enrichListWithIVData(List<ScreeningResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            return;
        }
        try {
            Set<String> symbols = results.stream()
                    .filter(Objects::nonNull)
                    .map(ScreeningResult::getSymbol)
                    .filter(StringUtils::isNotBlank)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            Map<String, Map<String, Object>> ivStatsMap = ivDataRepository.getIVStatsForSymbols(symbols);
            if (MapUtils.isNotEmpty(ivStatsMap)) {
                for (ScreeningResult res : results) {
                    if (res != null && res.getSymbol() != null) {
                        Map<String, Object> stats = ivStatsMap.get(res.getSymbol().toUpperCase());
                        applyStatsToResult(res, stats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich securities list with IV data: {}", e.getMessage());
        }
    }

    private void applyStatsToResult(ScreeningResult res, Map<String, Object> stats) {
        if (res == null || stats == null) {
            return;
        }
        if (stats.get("ivPercentile") instanceof Number num) {
            res.setIvPercentile(num.doubleValue());
        }
        if (stats.get("ivRank") instanceof Number num) {
            res.setIvRank(num.doubleValue());
        }
        if (stats.get("currentIV") instanceof Number num) {
            res.setCurrentIV(num.doubleValue());
        }
        if (stats.get("recordCount") instanceof Number num) {
            res.setIvDays(num.intValue());
        }
    }
}
