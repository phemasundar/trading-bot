package com.hemasundar.services;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.cache.QuotesCache;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.*;
import com.hemasundar.events.StrategyExecutionCompletedEvent;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.OptionChainCache;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.TelegramUtils;
import com.hemasundar.utils.VolatilityCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Service layer for executing trading strategies.
 * <p>
 * Uses an inverted symbol-to-strategy micro-batched streaming pipeline:
 * <ol>
 *   <li>Phase 1: Pre-warms lightweight quotes and technical indicators, then runs technical
 *       screening per strategy to determine each strategy's surviving symbol universe.</li>
 *   <li>Phase 2: Streams the union of surviving symbols in micro-batches ({@link #OPTION_CHAIN_BATCH_SIZE}),
 *       fetching each symbol's {@link OptionChainResponse} at most once, evaluating all subscribed
 *       strategies against that symbol, and immediately evicting the micro-batch's option chains
 *       from memory before advancing to the next batch.</li>
 * </ol>
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class StrategyExecutionService {

    /**
     * Number of option chains fetched and held in memory per micro-batch during Phase 2 streaming.
     * Bounds peak option-chain heap usage to at most 16 symbols (~80-160 MB) regardless of universe size.
     */
    public static final int OPTION_CHAIN_BATCH_SIZE = 16;

    private final SupabaseService supabaseService;
    private final SecuritiesResolver securitiesResolver;
    private final ThinkOrSwimAPIs ThinkOrSwimAPIs;
    private final FinnHubAPIs finnHubAPIs;
    private final TelegramUtils telegramUtils;
    private final TechnicalScreener technicalScreener;
    private final VolatilityCalculator volatilityCalculator;
    private final StrategiesConfigLoader strategiesConfigLoader;
    private final SchwabApiExecutor schwabApiExecutor;
    private final TechnicalIndicatorPreCalculationService technicalIndicatorPreCalculationService;
    private final ApplicationEventPublisher eventPublisher;

    // Execution state tracking (visible across page refreshes)
    private final AtomicBoolean executionRunning = new AtomicBoolean(false);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final AtomicReference<String> currentExecutionTask = new AtomicReference<>("");

    /**
     * Ordered alert groups keyed by (severity:message) — deduplicates same-message
     * alerts across symbols.
     */
    private final Map<String, AlertGroup> alertGroups = new LinkedHashMap<>();
    private volatile long executionStartTimeMs;
    /**
     * Set to true on the first auth failure; stops processing further
     * symbols/strategies.
     */
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
        synchronized (alertGroups) {
            alertGroups.clear();
        } // Clear prior alerts on new execution
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
        Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
        return strategiesConfigLoader.load(FilePaths.strategiesConfig, securitiesMap);
    }

    /**
     * Executes selected strategies using the micro-batched symbol-to-strategy streaming pipeline.
     *
     * @param strategyIndices Set of strategy indices to execute (0-based)
     * @return ExecutionResult containing results from all executed strategies
     */
    public ExecutionResult executeStrategies(Set<Integer> strategyIndices) throws IOException {
        long startTime = executionStartTimeMs > 0 ? executionStartTimeMs : System.currentTimeMillis();
        String executionId = "exec_" + startTime;

        log.info("Starting execution: {}", executionId);
        if (supabaseService != null) {
            supabaseService.clearIVStatsCache();
        }

        List<OptionsConfig> allStrategies = getEnabledStrategies();

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

        OptionChainCache cache = new OptionChainCache(ThinkOrSwimAPIs);
        List<StrategyResult> results = executeStrategyBatch(selectedStrategies, null, cache, false);
        int totalTrades = results.stream().mapToInt(StrategyResult::getTradesFound).sum();

        ExecutionResult executionResult = ExecutionResult.builder()
                .executionId(executionId)
                .timestamp(LocalDateTime.now())
                .results(results)
                .totalTradesFound(totalTrades)
                .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                .telegramSent(true)
                .build();

        cache.printStats();

        log.info("Execution completed: {} strategies, {} total trades, {}ms",
                results.size(), totalTrades, executionResult.getTotalExecutionTimeMs());

        return executionResult;
    }

    /**
     * Executes a custom strategy with inline dynamically built OptionsConfig.
     *
     * @param config The custom OptionsConfig
     * @return ExecutionResult containing the single strategy's result
     */
    public ExecutionResult executeCustomStrategy(OptionsConfig config) {
        return executeCustomStrategy(config, null);
    }

    /**
     * Executes a single custom strategy configured via the Execute Strategy screen,
     * bypassing the predefined strategies and saving the result to
     * custom_execution_results.
     * If customResultId is provided and positive, updates the existing execution
     * result in-place.
     *
     * @param config         the strategy configuration to execute
     * @param customResultId optional database ID of existing custom result to
     *                       update in-place
     * @return ExecutionResult containing the single custom StrategyResult
     */
    public ExecutionResult executeCustomStrategy(OptionsConfig config, Long customResultId) {
        return executeCustomStrategies(
                List.of(config),
                customResultId != null ? List.of(customResultId) : Collections.emptyList()
        );
    }

    /**
     * Executes a batch of custom strategies in a single global execution using the
     * micro-batched symbol-to-strategy streaming pipeline.
     *
     * @param configs list of resolved strategy configurations to execute
     * @param customResultIds corresponding list of database IDs to update in place (or null/0 to save)
     * @return ExecutionResult containing all strategy results
     */
    public ExecutionResult executeCustomStrategies(List<OptionsConfig> configs, List<Long> customResultIds) {
        if (CollectionUtils.isEmpty(configs)) {
            log.warn("executeCustomStrategies called with empty configs list");
            return null;
        }

        String initialTask = configs.size() > 1
                ? String.format("Custom Strategy: %s (1 of %d)", configs.get(0).getName(), configs.size())
                : configs.get(0).getName();
        startGlobalExecution(initialTask);
        long startTime = executionStartTimeMs;
        String executionId = "exec_custom_" + startTime;

        try {
            log.info("Starting custom execution batch: {} ({} strategies)", executionId, configs.size());
            if (supabaseService != null) {
                supabaseService.clearIVStatsCache();
            }

            OptionChainCache cache = new OptionChainCache(ThinkOrSwimAPIs);
            List<StrategyResult> results = executeStrategyBatch(configs, customResultIds, cache, true);
            int totalTrades = results.stream().mapToInt(StrategyResult::getTradesFound).sum();

            cache.printStats();

            ExecutionResult executionResult = ExecutionResult.builder()
                    .executionId(executionId)
                    .timestamp(LocalDateTime.now())
                    .results(results)
                    .totalTradesFound(totalTrades)
                    .totalExecutionTimeMs(System.currentTimeMillis() - startTime)
                    .telegramSent(true)
                    .build();

            log.info("Custom execution completed: {} total trades, {}ms",
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
     * Executes a batch of strategies using the inverted symbol-to-strategy micro-batched streaming pipeline.
     * <p>
     * Phase 1 pre-calculates lightweight quotes and technical indicators, then runs technical screening per
     * strategy to resolve each strategy's surviving symbol list. Phase 2 streams unique surviving symbols in
     * micro-batches of {@link #OPTION_CHAIN_BATCH_SIZE}, evaluating all subscribed strategies per symbol and
     * immediately evicting the batch's option chains from {@link OptionChainCache}.
     */
    private List<StrategyResult> executeStrategyBatch(
            List<OptionsConfig> strategies,
            List<Long> customResultIds,
            OptionChainCache cache,
            boolean isCustomExecution) {

        List<String> allSymbols = strategies.stream()
                .filter(c -> c.getSecurities() != null)
                .flatMap(c -> c.getSecurities().stream())
                .distinct()
                .collect(Collectors.toList());

        if (!allSymbols.isEmpty()) {
            log.debug("Pre-warming quotes cache for {} unique symbols", allSymbols.size());
            QuotesCache.getInstance().prewarm(allSymbols, schwabApiExecutor,
                    symbol -> ThinkOrSwimAPIs.getQuote(symbol, null),
                    (sourceContext, errorMsg) -> log.warn("Quotes prewarm error: {}", errorMsg));

            technicalIndicatorPreCalculationService.preCalculateAll(allSymbols,
                    (sourceContext, errorMsg) -> log.warn("Technical pre-calc error: {}", errorMsg));
        }

        List<StrategyExecutionContext> contexts = new ArrayList<>(strategies.size());
        Map<String, List<StrategyExecutionContext>> symbolToContexts = new LinkedHashMap<>();

        // Phase 1: Prepare strategy contexts (upfront earnings + technical screening)
        for (int i = 0; i < strategies.size(); i++) {
            OptionsConfig config = strategies.get(i);
            Long customResultId = (customResultIds != null && i < customResultIds.size()) ? customResultIds.get(i) : null;
            StrategyExecutionContext ctx = new StrategyExecutionContext(i, config, customResultId, isCustomExecution);
            contexts.add(ctx);

            try {
                List<String> securities = config.getSecurities() != null
                        ? config.getSecurities().stream().distinct().collect(Collectors.toList())
                        : Collections.emptyList();

                pullEarningsForSymbols(securities, false);

                if (config.hasTechnicalFilter()) {
                    BiConsumer<String, String> alertCallback = (symbol, errorMsg) -> {
                        String source = String.format("Technical Filter: %s (%s)", config.getName(), symbol);
                        addAlert(ExecutionAlert.Severity.ERROR, source,
                                String.format(AlertMessages.SYMBOL_PROCESSING_FAILED_FMT, errorMsg));
                    };

                    List<TechnicalScreener.ScreeningResult> screeningResults = technicalScreener.screenStocks(
                            securities, config.getTechnicalFilterChain(), alertCallback, config.getStrategyId());

                    for (TechnicalScreener.ScreeningResult sr : screeningResults) {
                        ctx.techResultsMap.put(sr.getSymbol(), sr);
                    }

                    securities = screeningResults.stream()
                            .map(TechnicalScreener.ScreeningResult::getSymbol)
                            .collect(Collectors.toList());
                    log.debug("[{}] Found {} stocks matching technical criteria: {}",
                            config.getName(), securities.size(), securities);
                }

                ctx.targetSecurities = securities;
                ctx.remainingSymbols = securities.size();

                for (String symbol : securities) {
                    symbolToContexts.computeIfAbsent(symbol, k -> new ArrayList<>()).add(ctx);
                }
            } catch (Throwable t) {
                log.error("Error preparing strategy {}: {}", config.getName(), t.getMessage(), t);
                ctx.failedDuringPrep = true;
                if (isCustomExecution) {
                    addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_EXECUTION,
                            String.format("Error executing %s: %s", config.getName(), t.getMessage()));
                }
            }
        }

        // Finalize any leading strategies that already have 0 surviving symbols before option-chain streaming starts
        finalizeReadyZeroSymbolStrategies(contexts, strategies.size());

        // Phase 2: Micro-batched option chain streaming across unique surviving symbols
        List<String> orderedSymbols = new ArrayList<>(symbolToContexts.keySet());
        for (int batchStart = 0; batchStart < orderedSymbols.size(); batchStart += OPTION_CHAIN_BATCH_SIZE) {
            if (cancellationRequested.get() || authFailed.get()) {
                break;
            }

            int batchEnd = Math.min(batchStart + OPTION_CHAIN_BATCH_SIZE, orderedSymbols.size());
            List<String> microBatch = orderedSymbols.subList(batchStart, batchEnd);

            try {
                log.debug("Streaming option chain micro-batch {}-{} of {} symbols",
                        batchStart + 1, batchEnd, orderedSymbols.size());
                cache.prewarm(microBatch, schwabApiExecutor);

                for (String symbol : microBatch) {
                    if (cancellationRequested.get() || authFailed.get()) {
                        break;
                    }

                    List<StrategyExecutionContext> subscribedContexts = symbolToContexts.getOrDefault(symbol, Collections.emptyList());
                    for (StrategyExecutionContext ctx : subscribedContexts) {
                        if (ctx.finalized || ctx.failedDuringPrep) {
                            continue;
                        }
                        if (cancellationRequested.get() || authFailed.get()) {
                            break;
                        }

                        updateCurrentExecutionTask(ctx, strategies.size());
                        evaluateSymbolForStrategy(ctx, symbol, cache);

                        if (ctx.remainingSymbols <= 0) {
                            finalizeStrategyContext(ctx);
                            finalizeReadyZeroSymbolStrategies(contexts, strategies.size());
                        }
                    }
                }
            } finally {
                // Immediately evict all option chains in this micro-batch so they can be garbage-collected
                cache.evictAll(microBatch);
            }
        }

        // If cancellation or auth failure occurred mid-strategy, finalize any partially evaluated active strategy
        for (StrategyExecutionContext ctx : contexts) {
            if (!ctx.finalized && !ctx.failedDuringPrep && ctx.evaluatedAtLeastOneSymbol) {
                finalizeStrategyContext(ctx);
            }
        }
        finalizeReadyZeroSymbolStrategies(contexts, strategies.size());

        return contexts.stream()
                .filter(ctx -> ctx.result != null)
                .map(ctx -> ctx.result)
                .collect(Collectors.toList());
    }

    private void updateCurrentExecutionTask(StrategyExecutionContext ctx, int totalStrategies) {
        if (ctx.isCustomExecution) {
            setCurrentExecutionTask(String.format("Custom Strategy: %s (%d of %d)",
                    ctx.config.getName(), ctx.index + 1, totalStrategies));
        } else {
            setCurrentExecutionTask(ctx.config.getName());
        }
    }

    /**
     * Finalizes strategies with zero surviving symbols only once all preceding strategies have
     * completed and neither cancellation nor auth failure has been triggered.
     */
    private void finalizeReadyZeroSymbolStrategies(List<StrategyExecutionContext> contexts, int totalStrategies) {
        for (int i = 0; i < contexts.size(); i++) {
            if (cancellationRequested.get() || authFailed.get()) {
                break;
            }
            StrategyExecutionContext ctx = contexts.get(i);
            if (ctx.failedDuringPrep) {
                continue;
            }
            if (!ctx.finalized) {
                if (ctx.remainingSymbols == 0) {
                    updateCurrentExecutionTask(ctx, totalStrategies);
                    finalizeStrategyContext(ctx);
                } else {
                    // Wait until this strategy finishes its symbols before finalizing subsequent 0-symbol strategies
                    break;
                }
            }
        }
    }

    /**
     * Evaluates a single symbol against a single subscribed strategy using the micro-batch's cached option chain.
     */
    private void evaluateSymbolForStrategy(StrategyExecutionContext ctx, String symbol, OptionChainCache cache) {
        OptionsConfig config = ctx.config;
        AbstractTradingStrategy strategy = config.getStrategy();
        int maxTradesToSend = config.getMaxTradesToSend();

        ctx.evaluatedAtLeastOneSymbol = true;
        ctx.remainingSymbols--;

        try {
            OptionChainResponse optionChainResponse = cache.get(symbol);

            log.debug("[{}] Processing symbol: {}", strategy.getStrategyName(), symbol);

            OptionsStrategyFilter filter = config.getFilter();
            if (filter != null) {
                filter.setStrategyId(config.getStrategyId());
            }

            List<TradeSetup> trades = strategy.findTrades(optionChainResponse, filter);

            if (CollectionUtils.isNotEmpty(trades)) {
                List<TradeSetup> mutableTrades = new ArrayList<>(trades);
                mutableTrades.sort((t1, t2) -> Double.compare(t2.getReturnOnRisk(), t1.getReturnOnRisk()));

                List<TradeSetup> topTrades = mutableTrades;
                if (mutableTrades.size() > maxTradesToSend) {
                    topTrades = mutableTrades.subList(0, maxTradesToSend);
                    log.debug("[{}] Found {} trades, limiting to top {} for Telegram",
                            symbol, mutableTrades.size(), maxTradesToSend);
                }

                Map<String, List<TradeSetup>> tradesByExpiry = topTrades.stream()
                        .collect(Collectors.groupingBy(
                                TradeSetup::getExpiryDate,
                                LinkedHashMap::new,
                                Collectors.toList()));

                for (Map.Entry<String, List<TradeSetup>> entry : tradesByExpiry.entrySet()) {
                    ctx.allTrades.put(symbol + "_" + entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.error("Error processing {}: {}", symbol, e.getMessage());
            String source = String.format(AlertMessages.SRC_STRATEGY_SYMBOL_FMT, strategy.getStrategyName(), symbol);
            if (isAuthError(e)) {
                authFailed.set(true);
                addAlert(ExecutionAlert.Severity.ERROR, source, AlertMessages.AUTH_FAILED);
            } else {
                addAlert(ExecutionAlert.Severity.ERROR, source,
                        String.format(AlertMessages.SYMBOL_PROCESSING_FAILED_FMT, e.getMessage()));
            }
        }
    }

    /**
     * Finalizes a strategy's execution once all of its symbols have been evaluated:
     * builds {@link StrategyResult}, enriches technical indicator summaries, sends Telegram alerts,
     * persists results to Supabase, and releases transient trade maps.
     */
    private void finalizeStrategyContext(StrategyExecutionContext ctx) {
        if (ctx.finalized) {
            return;
        }
        ctx.finalized = true;

        OptionsConfig config = ctx.config;
        long executionTime = System.currentTimeMillis() - ctx.strategyStartTime;

        try {
            if (config.hasTechnicalFilter() && config.getFilter() != null) {
                config.getFilter().setTechnicalFilterSummary(
                        config.getTechnicalFilterChain().getConditions() != null
                                ? config.getTechnicalFilterChain().getConditions().getSummary()
                                : null);
            }

            StrategyResult result = StrategyResult.fromTrades(
                    config.getStrategyId(),
                    config.getName(),
                    ctx.allTrades,
                    executionTime,
                    config.getFilter(),
                    config.getDescriptionFile());

            if (!ctx.techResultsMap.isEmpty()) {
                for (Trade trade : result.getTrades()) {
                    TechnicalScreener.ScreeningResult sr = ctx.techResultsMap.get(trade.getSymbol());
                    if (sr != null) {
                        trade.setTechIndicators(
                                sr.getAllTechnicalIndicatorsSummary() != null
                                        ? sr.getAllTechnicalIndicatorsSummary()
                                        : sr.getFormattedSummary());
                    }
                }
            }

            if (!ctx.allTrades.isEmpty()) {
                try {
                    telegramUtils.sendTradeAlerts(result);
                } catch (Exception e) {
                    addAlert(ExecutionAlert.Severity.WARNING, config.getName(),
                            AlertMessages.TELEGRAM_SEND_FAILED);
                }
            }

            if (!ctx.isCustomExecution) {
                try {
                    supabaseService.saveStrategyResult(result);
                    log.debug("[{}] Saved strategy result to database", config.getName());
                } catch (IOException e) {
                    addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                            AlertMessages.SAVE_STRATEGY_RESULT_FAILED + " (" + e.getMessage() + ")");
                }
            } else {
                try {
                    if (ctx.customResultId != null && ctx.customResultId > 0) {
                        supabaseService.updateCustomExecutionResult(ctx.customResultId, result, config.getSecurities());
                        log.debug("Updated custom execution result in Supabase: id={}", ctx.customResultId);
                    } else {
                        supabaseService.saveCustomExecutionResult(result, config.getSecurities());
                        log.debug("Saved custom execution result to Supabase: {}", config.getName());
                    }
                } catch (IOException e) {
                    addAlert(ExecutionAlert.Severity.WARNING, AlertMessages.SRC_SUPABASE,
                            ctx.customResultId != null && ctx.customResultId > 0
                                    ? AlertMessages.UPDATE_CUSTOM_RESULT_FAILED
                                    : AlertMessages.SAVE_CUSTOM_RESULT_FAILED);
                }
            }

            if (result.getTradesFound() > 0) {
                try {
                    eventPublisher.publishEvent(
                            new StrategyExecutionCompletedEvent(result, ctx.isCustomExecution));
                } catch (Exception e) {
                    log.error("[{}] Failed to publish strategy execution completed event: {}", config.getName(),
                            e.getMessage());
                    addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                            AlertMessages.SAVE_HISTORICAL_TRADES_FAILED + ": " + config.getName() + " (" + e.getMessage()
                                    + ")");
                }
            }

            ctx.result = result;
        } catch (Throwable t) {
            log.error("Error finalizing strategy {}: {}", config.getName(), t.getMessage(), t);
            if (ctx.isCustomExecution) {
                addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_EXECUTION,
                        String.format("Error executing %s: %s", config.getName(), t.getMessage()));
            }
        } finally {
            ctx.allTrades.clear();
            ctx.techResultsMap.clear();
        }
    }

    /**
     * Per-strategy execution state used during inverted symbol-to-strategy micro-batched streaming.
     */
    private static final class StrategyExecutionContext {
        private final int index;
        private final OptionsConfig config;
        private final Long customResultId;
        private final boolean isCustomExecution;
        private final long strategyStartTime = System.currentTimeMillis();
        private final Map<String, TechnicalScreener.ScreeningResult> techResultsMap = new HashMap<>();
        private final Map<String, List<TradeSetup>> allTrades = new LinkedHashMap<>();
        private List<String> targetSecurities = Collections.emptyList();
        private int remainingSymbols;
        private boolean evaluatedAtLeastOneSymbol;
        private boolean failedDuringPrep;
        private boolean finalized;
        private StrategyResult result;

        StrategyExecutionContext(int index, OptionsConfig config, Long customResultId, boolean isCustomExecution) {
            this.index = index;
            this.config = config;
            this.customResultId = customResultId;
            this.isCustomExecution = isCustomExecution;
        }
    }

    /**
     * Pulls earnings data for the provided symbols. When {@code forceRefresh} is true,
     * bypasses the local cache age check to fetch fresh data for all symbols.
     *
     * @param symbols      list of stock symbols
     * @param forceRefresh true to force an API call regardless of cache age
     */
    public void pullEarningsForSymbols(List<String> symbols, boolean forceRefresh) {
        if (finnHubAPIs == null || CollectionUtils.isEmpty(symbols)) {
            return;
        }
        LocalDate oneYearAhead = LocalDate.now().plusYears(1);
        for (int i = 0; i < symbols.size(); i++) {
            if (cancellationRequested.get() || authFailed.get()) {
                break;
            }
            String symbol = symbols.get(i);
            if (forceRefresh) {
                setCurrentExecutionTask(String.format("Refreshing Earnings: %s (%d/%d)", symbol, i + 1, symbols.size()));
            }
            try {
                if (forceRefresh) {
                    finnHubAPIs.getEarningsByTicker(symbol, oneYearAhead, true);
                } else {
                    finnHubAPIs.getEarningsByTicker(symbol, oneYearAhead);
                }
            } catch (Exception e) {
                log.warn("Failed to pull earnings for {}: {}", symbol, e.getMessage());
            }
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
            return Collections.emptyList();
        }
    }

    /**
     * Returns true if the exception indicates a Schwab API authentication failure
     * (expired token).
     */
    private boolean isAuthError(Exception e) {
        String msg = e.getMessage();
        if (msg == null)
            return false;
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
            if (!sources.contains(source))
                sources.add(source);
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
     * Returns a snapshot of all filter-stage log entries captured during the
     * current/last execution.
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
