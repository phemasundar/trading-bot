package com.hemasundar.services;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.OptionChainCache;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.TelegramUtils;
import com.hemasundar.utils.VolatilityCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import com.hemasundar.dto.ExecutionLogEntry;

/**
 * Service layer for executing trading strategies.
 * Encapsulates the business logic previously in SampleTestNG.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class StrategyExecutionService {

    private final SupabaseService supabaseService;
    private final SecuritiesResolver securitiesResolver;
    private final ThinkOrSwinAPIs thinkOrSwinAPIs;
    private final FinnHubAPIs finnHubAPIs;
    private final TelegramUtils telegramUtils;
    private final TechnicalScreener technicalScreener;
    private final VolatilityCalculator volatilityCalculator;
    private final StrategiesConfigLoader strategiesConfigLoader;

    // Execution state tracking (visible across page refreshes)
    private final AtomicBoolean executionRunning = new AtomicBoolean(false);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final AtomicReference<String> currentExecutionTask = new AtomicReference<>("");

    /** Ordered alert groups keyed by (severity:message) — deduplicates same-message alerts across symbols. */
    private final Map<String, AlertGroup> alertGroups = new LinkedHashMap<>();
    private volatile long executionStartTimeMs;
    /** Set to true on the first auth failure; stops processing further symbols/strategies. */
    private final AtomicBoolean authFailed = new AtomicBoolean(false);

    public boolean isExecutionRunning() {
        return executionRunning.get();
    }

    public long getExecutionStartTimeMs() {
        return executionStartTimeMs;
    }

    public String getCurrentExecutionTask() {
        return currentExecutionTask.get();
    }

    public void setCurrentExecutionTask(String task) {
        currentExecutionTask.set(task);
    }

    public void startGlobalExecution(String initialTask) {
        executionRunning.set(true);
        cancellationRequested.set(false);
        synchronized (alertGroups) { alertGroups.clear(); } // Clear prior alerts on new execution
        authFailed.set(false);
        FilterLogStore.getInstance().clear(); // Reset filter logs for fresh execution
        executionStartTimeMs = System.currentTimeMillis();
        currentExecutionTask.set(initialTask);
    }

    public void finishGlobalExecution() {
        executionRunning.set(false);
        cancellationRequested.set(false);
        currentExecutionTask.set("");
    }

    // ── Alert Management ──

    /**
     * Adds an alert, deduplicating by (severity + message).
     * Multiple symbols with the same error are merged into one alert,
     * showing sources as "A, B, C (+N more)" for readability.
     */
    public void addAlert(ExecutionAlert.Severity severity, String source, String message) {
        String key = severity.name() + ":" + message;
        synchronized (alertGroups) {
            AlertGroup group = alertGroups.get(key);
            if (group == null) {
                alertGroups.put(key, new AlertGroup(severity, message, source));
            } else {
                group.addSource(source);
            }
        }
        if (severity == ExecutionAlert.Severity.ERROR) {
            log.error("[ALERT][ERROR] {}: {}", source, message);
        } else {
            log.warn("[ALERT][WARN] {}: {}", source, message);
        }
    }

    /** Returns an unmodifiable snapshot of deduplicated alerts. */
    public List<ExecutionAlert> getAlerts() {
        synchronized (alertGroups) {
            return alertGroups.values().stream()
                    .map(AlertGroup::toAlert)
                    .collect(Collectors.toList());
        }
    }

    /** Clears all captured alerts and resets the auth-fail flag. */
    public void clearAlerts() {
        synchronized (alertGroups) {
            alertGroups.clear();
        }
        authFailed.set(false);
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
        Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();

        // Load and return enabled strategies
        return strategiesConfigLoader.load(FilePaths.strategiesConfig, securitiesMap);
    }

    /**
     * Executes selected strategies.
     *
     * @param strategyIndices Set of strategy indices to execute (0-based)
     * @return ExecutionResult containing results from all executed strategies
     */
    public ExecutionResult executeStrategies(Set<Integer> strategyIndices) throws IOException {
        // The lock is now managed by the controller (via startGlobalExecution) to allow chained screener execution.
        long startTime = executionStartTimeMs > 0 ? executionStartTimeMs : System.currentTimeMillis();
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
            OptionChainCache cache = new OptionChainCache(thinkOrSwinAPIs);

            // Execute each strategy
            List<StrategyResult> results = new ArrayList<>();
            int totalTrades = 0;

            for (int i = 0; i < selectedStrategies.size(); i++) {
                if (cancellationRequested.get()) {
                    log.info("Execution cancelled after {}/{} strategies", i, selectedStrategies.size());
                    break;
                }
                if (authFailed.get()) {
                    log.warn("Auth failure — stopping after {}/{} strategies", i, selectedStrategies.size());
                    break;
                }
                OptionsConfig config = selectedStrategies.get(i);
                setCurrentExecutionTask(config.getName());
                log.info("Executing strategy {}/{}: {}", i + 1, selectedStrategies.size(), config.getName());

                StrategyResult result = executeStrategy(config, cache, false);
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
                addAlert(ExecutionAlert.Severity.WARNING, AlertMessages.SRC_SUPABASE,
                        AlertMessages.SAVE_EXEC_RESULT_FAILED);
            }



            // Print cache statistics
            cache.printStats();

            log.info("Execution completed: {} strategies, {} total trades, {}ms",
                    results.size(), totalTrades, executionResult.getTotalExecutionTimeMs());

            return executionResult;
        } finally {
            // Lock handled externally
        }
    }

    /**
     * Executes a custom strategy with inline dynamically built OptionsConfig.
     * This is useful for UI-driven execution rather than reading from JSON.
     *
     * @param config The custom OptionsConfig
     * @return ExecutionResult containing the single strategy's result
     */
    public ExecutionResult executeCustomStrategy(OptionsConfig config) {
        startGlobalExecution(config.getName());
        long startTime = executionStartTimeMs;
        String executionId = "exec_custom_" + startTime;

        try {
            log.info("Starting custom execution: {}", executionId);

            OptionChainCache cache = new OptionChainCache(thinkOrSwinAPIs);

            // Execute the single custom strategy
            StrategyResult result = executeStrategy(config, cache, true);

            // Build execution result wrapper
            ExecutionResult executionResult = ExecutionResult.builder()
                    .executionId(executionId)
                    .timestamp(LocalDateTime.now())
                    .results(List.of(result))
                    .totalTradesFound(result.getTradesFound())
                    .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                    .telegramSent(true) // Telegram is sent during strategy execution
                    .build();

            // Save to Supabase custom_execution_results table (NOT the dashboard table)
            try {
                supabaseService.saveCustomExecutionResult(result, config.getSecurities());
                log.info("Saved custom execution result to Supabase: {}", executionId);
            } catch (IOException e) {
                addAlert(ExecutionAlert.Severity.WARNING, AlertMessages.SRC_SUPABASE,
                        AlertMessages.SAVE_CUSTOM_RESULT_FAILED);
            }

            // Print cache statistics
            cache.printStats();

            log.info("Custom Execution completed: {} total trades, {}ms",
                    executionResult.getTotalTradesFound(), executionResult.getTotalExecutionTimeMs());

            return executionResult;
        } finally {
            finishGlobalExecution();
        }
    }

    /**
     * Retrieves the most recent custom execution results from Supabase.
     *
     * @param limit Maximum number of results to return
     * @return List of StrategyResult, most recent first
     */
    public List<StrategyResult> getRecentCustomExecutions(int limit) throws IOException {
        return supabaseService.getRecentCustomExecutions(limit);
    }



    /**
     * Executes a single strategy and returns its result.
     */
    private StrategyResult executeStrategy(OptionsConfig config, OptionChainCache cache, boolean isCustomExecution) {
        long strategyStartTime = System.currentTimeMillis();

        List<String> securities = config.getSecurities();

        // Apply technical filter if configured
        if (config.hasTechnicalFilter()) {
            List<TechnicalScreener.ScreeningResult> screeningResults = technicalScreener.screenStocks(
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
        }

        // Build StrategyResult from trades map (uses shared Trade.fromTradeSetup)
        long executionTime = System.currentTimeMillis() - strategyStartTime;
        StrategyResult result = StrategyResult.fromTrades(config.getName(), allTrades, executionTime,
                config.getFilter(), config.getDescriptionFile());

        // Send to Telegram using pre-formatted Trade DTOs
        if (!allTrades.isEmpty()) {
            try {
                telegramUtils.sendTradeAlerts(result);
            } catch (Exception e) {
                addAlert(ExecutionAlert.Severity.WARNING, config.getName(),
                        AlertMessages.TELEGRAM_SEND_FAILED);
            }
        }

        // Save individual strategy result to database for per-strategy persistence if
        // not a custom execution
        if (!isCustomExecution) {
            try {
                supabaseService.saveStrategyResult(result);
                log.info("[{}] Saved strategy result to database", config.getName());
            } catch (IOException e) {
                addAlert(ExecutionAlert.Severity.WARNING, AlertMessages.SRC_SUPABASE,
                        AlertMessages.SAVE_STRATEGY_RESULT_FAILED);
            }
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
            if (authFailed.get()) {
                log.warn("[{}] Skipping {} — auth already failed", strategy.getStrategyName(), symbol);
                continue;
            }
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
                String source = String.format(AlertMessages.SRC_STRATEGY_SYMBOL_FMT, strategy.getStrategyName(), symbol);
                if (isAuthError(e)) {
                    authFailed.set(true);
                    addAlert(ExecutionAlert.Severity.ERROR, source, AlertMessages.AUTH_FAILED);
                    break; // Auth error is unrecoverable — stop processing remaining symbols
                } else {
                    addAlert(ExecutionAlert.Severity.ERROR, source,
                            String.format(AlertMessages.SYMBOL_PROCESSING_FAILED_FMT, e.getMessage()));
                }
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
     * Returns true if the exception indicates a Schwab API authentication failure (expired token).
     */
    private boolean isAuthError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("401") ||
               lower.contains("access token") ||
               lower.contains("error fetching access token") ||
               lower.contains("unauthorized");
    }

    // ── Private: Alert Grouping ───────────────────────────────────────────────

    /**
     * Groups multiple-source alerts under a single (severity, message) key.
     * Displays first 3 sources; appends "(+N more)" for the rest.
     */
    private static final class AlertGroup {
        private final ExecutionAlert.Severity severity;
        private final String message;
        private final long timestamp = System.currentTimeMillis();
        private final List<String> sources = new ArrayList<>();

        AlertGroup(ExecutionAlert.Severity severity, String message, String firstSource) {
            this.severity = severity;
            this.message = message;
            this.sources.add(firstSource);
        }

        void addSource(String source) {
            if (!sources.contains(source)) sources.add(source);
        }

        ExecutionAlert toAlert() {
            int n = sources.size();
            String combined;
            if (n <= 3) {
                combined = String.join(", ", sources);
            } else {
                combined = sources.get(0) + ", " + sources.get(1) + ", " + sources.get(2)
                        + " (+" + (n - 3) + " more)";
            }
            return ExecutionAlert.builder()
                    .severity(severity)
                    .source(combined)
                    .message(message)
                    .timestamp(timestamp)
                    .build();
        }
    }

    // ── Custom Execution CRUD ──

    /**
     * Deletes a custom execution result by its database ID.
     */
    public void deleteCustomExecution(String id) throws IOException {
        supabaseService.deleteCustomExecution(id);
    }

    // ── Filter Log Access ──

    /**
     * Returns a snapshot of all filter-stage log entries captured during the current/last execution.
     */
    public List<ExecutionLogEntry> getFilterLogs() {
        return FilterLogStore.getInstance().getEntries();
    }

    /**
     * Clears the in-memory filter log store (manual reset from the Logs UI page).
     */
    public void clearFilterLogs() {
        FilterLogStore.getInstance().clear();
    }

}
