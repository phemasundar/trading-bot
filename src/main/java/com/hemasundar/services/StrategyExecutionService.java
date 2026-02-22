package com.hemasundar.services;

import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.pojos.Securities;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.OptionChainCache;
import com.hemasundar.utils.TelegramUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for executing trading strategies.
 * Encapsulates the business logic previously in SampleTestNG.
 */
@Service
@Log4j2
public class StrategyExecutionService {

    @Autowired
    private SupabaseService supabaseService;

    // Execution state tracking (visible across page refreshes)
    private final java.util.concurrent.atomic.AtomicBoolean executionRunning = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private final java.util.concurrent.atomic.AtomicBoolean cancellationRequested = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private volatile long executionStartTimeMs;

    public boolean isExecutionRunning() {
        return executionRunning.get();
    }

    public long getExecutionStartTimeMs() {
        return executionStartTimeMs;
    }

    public void cancelExecution() {
        if (executionRunning.get()) {
            cancellationRequested.set(true);
            log.info("Cancellation requested for ongoing execution");
        }
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    /**
     * Loads all enabled strategies from strategies-config.json
     *
     * @return List of enabled strategy configurations
     */
    public List<OptionsConfig> getEnabledStrategies() throws IOException {
        // Load securities maps
        Map<String, List<String>> securitiesMap = loadSecuritiesMaps();

        // Load and return enabled strategies
        return StrategiesConfigLoader.load(FilePaths.strategiesConfig, securitiesMap);
    }

    /**
     * Executes selected strategies and returns structured results.
     *
     * @param strategyIndices Set of strategy indices to execute (0-based)
     * @return ExecutionResult containing results from all executed strategies
     */
    public ExecutionResult executeStrategies(Set<Integer> strategyIndices) throws IOException {
        executionRunning.set(true);
        cancellationRequested.set(false);
        executionStartTimeMs = System.currentTimeMillis();
        long startTime = executionStartTimeMs;
        String executionId = "exec_" + startTime;

        try {
            log.info("Starting execution: {}", executionId);

            // Load all enabled strategies
            List<OptionsConfig> allStrategies = getEnabledStrategies();

            // Filter selected strategies
            List<OptionsConfig> selectedStrategies = strategyIndices.stream()
                    .filter(i -> i >= 0 && i < allStrategies.size())
                    .map(allStrategies::get)
                    .collect(Collectors.toList());

            if (selectedStrategies.isEmpty()) {
                log.warn("No valid strategies selected for execution");
                return ExecutionResult.builder()
                        .executionId(executionId)
                        .timestamp(LocalDateTime.now())
                        .results(Collections.emptyList())
                        .totalTradesFound(0)
                        .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                        .telegramSent(false)
                        .build();
            }

            // Shared cache for option chains
            OptionChainCache cache = new OptionChainCache();

            // Execute each strategy
            List<StrategyResult> results = new ArrayList<>();
            int totalTrades = 0;

            for (int i = 0; i < selectedStrategies.size(); i++) {
                if (cancellationRequested.get()) {
                    log.info("Execution cancelled after {}/{} strategies", i, selectedStrategies.size());
                    break;
                }
                OptionsConfig config = selectedStrategies.get(i);
                log.info("Executing strategy {}/{}: {}", i + 1, selectedStrategies.size(), config.getName());

                StrategyResult result = executeStrategy(config, cache);
                results.add(result);
                totalTrades += result.getTradesFound();
            }

            // Build execution result
            ExecutionResult executionResult = ExecutionResult.builder()
                    .executionId(executionId)
                    .timestamp(LocalDateTime.now())
                    .results(results)
                    .totalTradesFound(totalTrades)
                    .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                    .telegramSent(true) // Telegram is sent during strategy execution
                    .build();

            // Save to Supabase
            try {
                supabaseService.saveExecutionResult(executionResult);
                log.info("Saved execution result to Supabase: {}", executionId);
            } catch (IOException e) {
                log.error("Failed to save execution result to Supabase: {}", e.getMessage());
                // Don't fail the entire execution, just log the error
            }

            // Print cache statistics
            cache.printStats();

            log.info("Execution completed: {} strategies, {} total trades, {}ms",
                    results.size(), totalTrades, executionResult.getTotalExecutionTimeMs());

            return executionResult;
        } finally {
            executionRunning.set(false);
        }
    }

    /**
     * Executes a single strategy and returns its result.
     */
    private StrategyResult executeStrategy(OptionsConfig config, OptionChainCache cache) {
        long strategyStartTime = System.currentTimeMillis();

        List<String> securities = config.getSecurities();

        // Apply technical filter if configured
        if (config.hasTechnicalFilter()) {
            List<TechnicalScreener.ScreeningResult> screeningResults = TechnicalScreener.screenStocks(
                    securities, config.getTechnicalFilterChain());
            securities = screeningResults.stream()
                    .map(TechnicalScreener.ScreeningResult::getSymbol)
                    .collect(Collectors.toList());
            log.info("[{}] Found {} stocks matching technical criteria: {}",
                    config.getName(), securities.size(), securities);
        }

        // Find trades using the strategy
        Map<String, List<TradeSetup>> allTrades = new LinkedHashMap<>();

        if (!securities.isEmpty()) {
            allTrades = findTradesForStrategy(cache, securities, config);

            // Send to Telegram
            if (!allTrades.isEmpty()) {
                TelegramUtils.sendTradeAlerts(config.getName(), allTrades);
            }
        }

        // Build StrategyResult from trades map (uses shared Trade.fromTradeSetup)
        long executionTime = System.currentTimeMillis() - strategyStartTime;
        StrategyResult result = StrategyResult.fromTrades(config.getName(), allTrades, executionTime,
                config.getFilter());

        // Save individual strategy result to database for per-strategy persistence
        try {
            supabaseService.saveStrategyResult(result);
            log.info("[{}] Saved strategy result to database", config.getName());
        } catch (IOException e) {
            log.error("[{}] Failed to save strategy result to database: {}", config.getName(), e.getMessage());
            // Don't fail the entire execution, just log the error
        }

        return result;
    }

    /**
     * Finds trades for a strategy across all symbols.
     * Extracted from SampleTestNG.findTradesForStrategy()
     */
    private Map<String, List<TradeSetup>> findTradesForStrategy(
            OptionChainCache cache,
            List<String> symbols,
            OptionsConfig config) {

        AbstractTradingStrategy strategy = config.getStrategy();
        int maxTradesToSend = config.getMaxTradesToSend();

        log.info("\\n" +
                "******************************************************************\\n" +
                "************* {} **************\\n" +
                "****************************************************************",
                strategy.getStrategyName());

        Map<String, List<TradeSetup>> allTrades = new LinkedHashMap<>();

        for (String symbol : symbols) {
            try {
                OptionChainResponse optionChainResponse = cache.get(symbol);
                log.info("Processing symbol: {}", symbol);

                List<TradeSetup> trades = strategy.findTrades(optionChainResponse, config.getFilter());
                trades.forEach(trade -> log.info("Trade: {}", trade));

                if (!trades.isEmpty()) {
                    // Sort by Return on Risk (Descending)
                    trades.sort((t1, t2) -> Double.compare(t2.getReturnOnRisk(), t1.getReturnOnRisk()));

                    // Limit trades for Telegram
                    List<TradeSetup> topTrades = trades;
                    if (trades.size() > maxTradesToSend) {
                        topTrades = trades.subList(0, maxTradesToSend);
                        log.info("[{}] Found {} trades, limiting to top {} for Telegram",
                                symbol, trades.size(), maxTradesToSend);
                    }

                    // Group by expiry date
                    Map<String, List<TradeSetup>> tradesByExpiry = topTrades.stream()
                            .collect(Collectors.groupingBy(
                                    TradeSetup::getExpiryDate,
                                    LinkedHashMap::new,
                                    Collectors.toList()));

                    for (Map.Entry<String, List<TradeSetup>> entry : tradesByExpiry.entrySet()) {
                        allTrades.put(symbol + "_" + entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing {}: {}", symbol, e.getMessage());
            }
        }

        return allTrades;
    }

    /**
     * Retrieves the latest execution result from Supabase.
     */
    public Optional<ExecutionResult> getLatestExecutionResult() {
        try {
            return supabaseService.getLatestExecutionResult();
        } catch (IOException e) {
            log.error("Failed to retrieve latest execution result: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retrieves all latest strategy results from the database (per-strategy
     * persistence).
     *
     * @return List of all latest strategy results
     */
    public List<StrategyResult> getAllLatestStrategyResults() {
        try {
            return supabaseService.getAllLatestStrategyResults();
        } catch (IOException e) {
            log.error("Failed to retrieve strategy results: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Loads all securities maps from YAML files.
     */
    private Map<String, List<String>> loadSecuritiesMaps() throws IOException {
        return Map.of(
                "portfolio", loadSecurities(FilePaths.portfolioSecurities),
                "top100", loadSecurities(FilePaths.top100Securities),
                "bullish", loadSecurities(FilePaths.bullishSecurities),
                "2026", loadSecurities(FilePaths.securities2026),
                "tracking", loadSecurities(FilePaths.trackingSecurities));
    }

    /**
     * Loads securities from a YAML file.
     */
    private List<String> loadSecurities(java.nio.file.Path path) throws IOException {
        Securities securities = JavaUtils.convertYamlToPojo(Files.readString(path), Securities.class);
        log.info("Loading securities from: {} - Found {} symbols", path, securities.securities().size());
        return securities.securities();
    }
}
