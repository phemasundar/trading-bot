package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.supabase.*;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Optional;

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
     * Retrieves the most recent custom execution results.
     */
    public java.util.List<com.hemasundar.dto.StrategyResult> getRecentCustomExecutions(int limit) throws IOException {
        return customExecutionRepository.getRecentCustomExecutions(limit);
    }
}
