package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.*;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.TelegramUtils;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.utils.VolatilityCalculator;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.cache.QuotesCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class ScreenerExecutionService {

    private final SupabaseService supabaseService;
    private final SecuritiesResolver securitiesResolver;
    private final StrategyExecutionService strategyExecutionService;
    private final ThinkOrSwimAPIs ThinkOrSwimAPIs;
    private final TelegramUtils telegramUtils;
    private final TechnicalScreener technicalScreener;
    private final PriceDropScreener priceDropScreener;
    private final StrategiesConfigLoader strategiesConfigLoader;
    private final SchwabApiExecutor schwabApiExecutor;
    private final VolatilityCalculator volatilityCalculator;
    private final TechnicalIndicatorPreCalculationService technicalIndicatorPreCalculationService;

    /**
     * Loads all enabled technical screeners from strategies-config.json
     */
    public List<ScreenerConfig> getEnabledScreeners() throws IOException {
        return strategiesConfigLoader.loadScreeners(securitiesResolver.loadSecuritiesMaps());
    }

    /**
     * Retrieves all latest technical screener results from Supabase.
     */
    public List<ScreenerExecutionResult> getLatestScreenerResults() throws IOException {
        List<ScreenerExecutionResult> results = supabaseService.getAllLatestScreenerResults();
        if (CollectionUtils.isEmpty(results)) {
            return results;
        }
        try {
            List<ScreenerConfig> configs = getEnabledScreeners();
            if (CollectionUtils.isEmpty(configs)) {
                return results;
            }
            Map<String, ScreenerConfig> configByName = new HashMap<>();
            for (ScreenerConfig c : configs) {
                configByName.put(c.getName(), c);
                if (c.getScreenerType() != null) {
                    configByName.putIfAbsent(c.getScreenerType().name(), c);
                    configByName.putIfAbsent(c.getScreenerType().getDisplayName(), c);
                }
            }
            return results.stream().map(r -> {
                if (r.getFilterConfig() != null) {
                    return r;
                }
                ScreenerConfig cfg = configByName.get(r.getScreenerName());
                if (cfg == null) {
                    cfg = configByName.get(r.getScreenerId());
                }
                if (cfg != null && cfg.getFilterConfig() != null) {
                    return r.toBuilder().filterConfig(cfg.getFilterConfig()).build();
                }
                return r;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to enrich screener results with filter configs: {}", e.getMessage());
            return results;
        }
    }

    public void executeScreeners(Set<Integer> screenerIndices, List<ScreenerConfig> allScreeners) {
        executeScreenersInternal(screenerIndices, allScreeners, false, null, null);
    }

    /**
     * Executes a one-off custom screener and saves the result with the original
     * request parameters so the UI can offer a "Load Filters" button.
     *
     * @param config        the resolved screener configuration
     * @param requestParams the raw request parameter map to persist alongside the result
     */
    public void executeCustomScreener(ScreenerConfig config, Map<String, Object> requestParams) {
        executeCustomScreener(config, requestParams, null);
    }

    /**
     * Executes a one-off custom screener and updates an existing record if customResultId is provided.
     *
     * @param config         the resolved screener configuration
     * @param requestParams  the raw request parameter map to persist alongside the result
     * @param customResultId optional database ID of existing custom result to update in place
     */
    public void executeCustomScreener(ScreenerConfig config, Map<String, Object> requestParams, Long customResultId) {
        executeCustomScreeners(
                List.of(config),
                requestParams != null ? List.of(requestParams) : Collections.emptyList(),
                customResultId != null ? List.of(customResultId) : Collections.emptyList()
        );
    }

    /**
     * Executes a batch of custom screeners sequentially.
     *
     * @param configs           list of resolved screener configurations
     * @param requestParamsList list of raw request parameter maps to persist alongside results
     * @param customResultIds   list of existing database IDs to update in place (or null/0 to save)
     */
    public void executeCustomScreeners(List<ScreenerConfig> configs, List<Map<String, Object>> requestParamsList, List<Long> customResultIds) {
        if (CollectionUtils.isEmpty(configs)) {
            return;
        }

        // PREWARM CACHE (Parallel Fetch) for all unique symbols
        List<String> allSymbolsToPrewarm = configs.stream()
                .filter(s -> s.getSecurities() != null)
                .flatMap(s -> s.getSecurities().stream())
                .distinct()
                .toList();

        if (!allSymbolsToPrewarm.isEmpty()) {
            java.util.function.BiConsumer<String, String> prewarmAlertCallback = (sourceContext, errorMsg) -> {
                if (strategyExecutionService != null) {
                    strategyExecutionService.addAlert(ExecutionAlert.Severity.ERROR, "Prewarm: " + sourceContext, errorMsg);
                }
            };
            PriceHistoryCache.getInstance().prewarm(allSymbolsToPrewarm, schwabApiExecutor,
                    symbol -> PriceHistoryCache.getInstance().getHistoricalData(symbol, ThinkOrSwimAPIs),
                    prewarmAlertCallback);

            QuotesCache.getInstance().prewarm(allSymbolsToPrewarm, schwabApiExecutor,
                    symbol -> ThinkOrSwimAPIs.getQuote(symbol, null),
                    prewarmAlertCallback);

            technicalIndicatorPreCalculationService.preCalculateAll(allSymbolsToPrewarm, prewarmAlertCallback);
        }

        for (int i = 0; i < configs.size(); i++) {
            if (strategyExecutionService != null && strategyExecutionService.isCancellationRequested()) {
                log.info("Custom screener batch cancelled by user request");
                break;
            }

            ScreenerConfig screenerConfig = configs.get(i);
            Map<String, Object> requestParams = (requestParamsList != null && i < requestParamsList.size()) ? requestParamsList.get(i) : null;
            Long customResultId = (customResultIds != null && i < customResultIds.size()) ? customResultIds.get(i) : null;

            log.info("Running custom screener ({}/{}): {}", i + 1, configs.size(), screenerConfig.getName());
            if (strategyExecutionService != null) {
                strategyExecutionService.setCurrentExecutionTask(
                        String.format("Custom Screener: %s (%d of %d)", screenerConfig.getName(), i + 1, configs.size()));
            }

            executeSingleScreener(screenerConfig, true, requestParams, customResultId);
        }
    }

    private void executeScreenersInternal(Set<Integer> screenerIndices, List<ScreenerConfig> allScreeners, boolean isCustom, Map<String, Object> requestParams, Long customResultId) {
        if (CollectionUtils.isEmpty(screenerIndices) || allScreeners == null) {
            log.debug("No screener indices provided, skipping technical screeners");
            return;
        }

        // Filter to only selected screener indices
        List<ScreenerConfig> selectedScreeners = screenerIndices.stream()
                .filter(i -> i >= 0 && i < allScreeners.size())
                .map(i -> allScreeners.get(i))
                .collect(Collectors.toList());

        // PREWARM CACHE (Parallel Fetch) - Performance Optimization
        List<String> allSymbolsToPrewarm = selectedScreeners.stream()
                .filter(s -> s.getSecurities() != null)
                .flatMap(s -> s.getSecurities().stream())
                .distinct()
                .toList();

        if (!allSymbolsToPrewarm.isEmpty()) {
            java.util.function.BiConsumer<String, String> prewarmAlertCallback = (sourceContext, errorMsg) -> {
                if (strategyExecutionService != null) {
                    strategyExecutionService.addAlert(ExecutionAlert.Severity.ERROR, "Prewarm: " + sourceContext, errorMsg);
                }
            };
            PriceHistoryCache.getInstance().prewarm(allSymbolsToPrewarm, schwabApiExecutor, 
                    symbol -> PriceHistoryCache.getInstance().getHistoricalData(symbol, ThinkOrSwimAPIs),
                    prewarmAlertCallback);

            // Prewarm QuotesCache — parallel fetch using single-symbol Quote API
            log.debug("[Prewarm] Starting QuotesCache prewarm for {} symbols", allSymbolsToPrewarm.size());
            QuotesCache.getInstance().prewarm(allSymbolsToPrewarm, schwabApiExecutor,
                    symbol -> ThinkOrSwimAPIs.getQuote(symbol, null),
                    prewarmAlertCallback);

            // Pre-calculate indicators universally so screeners/strategies can fetch from cache
            technicalIndicatorPreCalculationService.preCalculateAll(allSymbolsToPrewarm, prewarmAlertCallback);
        }

        for (ScreenerConfig screenerConfig : selectedScreeners) {
            if (strategyExecutionService != null && strategyExecutionService.isCancellationRequested()) {
                log.info("Screener execution cancelled by user request");
                break;
            }
            log.info("Running screener: {}", screenerConfig.getName());
            if (strategyExecutionService != null) {
                strategyExecutionService.setCurrentExecutionTask("Screener: " + screenerConfig.getName());
            }
            executeSingleScreener(screenerConfig, isCustom, requestParams, customResultId);
        }
    }

    private void executeSingleScreener(ScreenerConfig screenerConfig, boolean isCustom, Map<String, Object> requestParams, Long customResultId) {
        long screenerStartTime = System.currentTimeMillis();

        // Get securities from config
        List<String> securitiesToScan = screenerConfig.getSecurities();
        if (CollectionUtils.isEmpty(securitiesToScan)) {
            strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                    String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                    AlertMessages.NO_SECURITIES_CONFIGURED);
            return;
        }

        java.util.function.BiConsumer<String, String> alertCallback = (sourceContext, errorMsg) -> {
            if (strategyExecutionService != null) {
                String source = String.format("Screener %s (%s)", screenerConfig.getName(), sourceContext);
                strategyExecutionService.addAlert(ExecutionAlert.Severity.ERROR, source, errorMsg);
            }
        };

        List<TechnicalScreener.ScreeningResult> screenerResults;
        try {
            // Route to appropriate screener based on type
            screenerResults = switch (screenerConfig.getScreenerType()) {
                case PRICE_DROP -> {
                    TechFilterConditions cond = screenerConfig.getConditions();
                    List<com.hemasundar.technical.MathExpression> dropRules = extractDropExpressions(cond, 5.0);
                    int days = cond.getLookbackDays() != null ? cond.getLookbackDays() : 0;
                    yield priceDropScreener.screenPriceDrop(securitiesToScan, dropRules, days, alertCallback, screenerConfig.getName());
                }
                case HIGH_52W_DROP -> {
                    TechFilterConditions cond = screenerConfig.getConditions();
                    List<com.hemasundar.technical.MathExpression> dropRules = extractDropExpressions(cond, 20.0);
                    yield priceDropScreener.screen52WeekHighDrop(securitiesToScan, dropRules, alertCallback, screenerConfig.getName());
                }
                default -> {
                    yield technicalScreener.screenStocks(
                            securitiesToScan,
                            screenerConfig.getFilterChain(),
                            screenerConfig.getFundamentalConditions(),
                            alertCallback,
                            screenerConfig.getName());
                }
            };
        } catch (Exception e) {
            strategyExecutionService.addAlert(ExecutionAlert.Severity.ERROR,
                    String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                    String.format(AlertMessages.SCREENER_EXEC_FAILED_FMT, e.getMessage()));
            return;
        }

        log.info("[{}] Found {} stocks matching criteria", screenerConfig.getName(), screenerResults.size());

        if (!screenerResults.isEmpty()) {
            log.debug("[{}] Matching stocks: {}", screenerConfig.getName(),
                    screenerResults.stream().map(TechnicalScreener.ScreeningResult::getSymbol)
                            .toList());
            try {
                telegramUtils.sendTechnicalScreenerAlert(screenerConfig.getName(), screenerResults);
            } catch (Exception e) {
                strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                        String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                        AlertMessages.TELEGRAM_SEND_FAILED);
            }
        }

        // Save screener result
        long screenerExecutionTime = System.currentTimeMillis() - screenerStartTime;
        Map<String, Object> resolvedFilterConfig = (isCustom && requestParams != null)
                ? requestParams
                : (screenerConfig != null ? screenerConfig.getFilterConfig() : null);

        ScreenerExecutionResult scrResult = ScreenerExecutionResult.builder()
                .screenerId(screenerConfig.getName())
                .screenerName(screenerConfig.getName())
                .executionTimeMs(screenerExecutionTime)
                .resultsFound(screenerResults.size())
                .results(screenerResults)
                .filterConfig(resolvedFilterConfig)
                .build();
        try {
            if (isCustom) {
                if (customResultId != null && customResultId > 0) {
                    supabaseService.updateCustomScreenerResult(customResultId, scrResult, securitiesToScan, requestParams);
                    log.debug("[{}] Updated custom screener result in Supabase: id={}", screenerConfig.getName(), customResultId);
                } else {
                    supabaseService.saveCustomScreenerResult(scrResult, securitiesToScan, requestParams);
                    log.debug("[{}] Saved custom screener result to Supabase", screenerConfig.getName());
                }
            } else {
                supabaseService.saveScreenerResult(scrResult);
                log.debug("[{}] Saved global screener result to Supabase", screenerConfig.getName());
            }
        } catch (Exception e) {
            strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                    String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                    AlertMessages.SAVE_SCREENER_RESULT_FAILED);
        }
    }

    /**
     * Extracts DROP_PCT math expressions from the conditions, falling back to a
     * default threshold expression when none are configured.
     */
    private List<com.hemasundar.technical.MathExpression> extractDropExpressions(
            com.hemasundar.technical.TechFilterConditions cond, double defaultThreshold) {

        List<com.hemasundar.technical.MathExpression> expressions = new java.util.ArrayList<>();
        if (cond.getFilterExpressions() != null) {
            for (com.hemasundar.technical.MathExpression expr : cond.getFilterExpressions()) {
                if ("DROP_PCT".equalsIgnoreCase(expr.getLeftVariable())) {
                    expressions.add(expr);
                }
            }
        }
        if (expressions.isEmpty()) {
            expressions.add(com.hemasundar.technical.MathExpression.builder()
                    .leftVariable("DROP_PCT")
                    .operator(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL)
                    .rightVariable(String.valueOf(defaultThreshold))
                    .build());
        }
        return expressions;
    }
}
