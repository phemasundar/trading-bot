package com.hemasundar.api;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.CustomExecuteRequest;
import com.hemasundar.dto.ExecuteRequest;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.pojos.MarketHoursResponse;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.services.ScreenerExecutionService;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.supabase.IVDataRepository;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.utils.AuthErrorUtils;
import com.hemasundar.utils.FilterParser;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.WikipediaSecuritiesFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * REST controller for strategy execution and market data querying.
 */
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StrategyExecutionController {

    private final StrategyExecutionService executionService;
    private final ScreenerExecutionService screenerExecutionService;
    private final SecuritiesResolver securitiesResolver;
    private final ThinkOrSwimAPIs thinkOrSwimAPIs;
    private final StrategiesConfigLoader strategiesConfigLoader;
    private final AuthErrorUtils authErrorUtils;
    private final Optional<IVDataRepository> ivDataRepository;
    private final WikipediaSecuritiesFetcher wikipediaFetcher;

    /**
     * Returns all enabled strategies with index, name, and type.
     */
    @GetMapping("/strategies")
    public ResponseEntity<?> getEnabledStrategies() {
        try {
            List<OptionsConfig> strategies = executionService.getEnabledStrategies();
            List<Map<String, Object>> response = IntStream.range(0, strategies.size())
                    .mapToObj(i -> {
                        OptionsConfig config = strategies.get(i);
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("index", i);
                        map.put("name", config.getName());
                        map.put("type", config.getStrategy().getStrategyType().name());
                        map.put("displayType", config.getStrategy().getStrategyType().getDisplayName());
                        map.put("descriptionFile", config.getDescriptionFile());
                        String securitiesFile = config.getFilter() != null ? config.getFilter().getSecuritiesFile() : null;
                        map.put("securitiesFile", securitiesFile);
                        map.put("termType", config.getTermType());
                        map.put("greeks", config.getGreeks());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (IllegalStateException e) {
            log.error("Dynamic securities fetch failed (Wikipedia): {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "error",   "Failed to load dynamic securities from Wikipedia.",
                            "details", e.getMessage(),
                            "hint",    "Wikipedia may be unreachable or the page structure has changed. " +
                                       "Check the application logs for details."));
        } catch (Exception e) {
            log.error("Failed to load strategies", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load strategies: " + e.getMessage()));
        }
    }

    /**
     * Returns all latest strategy results from the database.
     */
    @GetMapping("/results")
    public ResponseEntity<?> getLatestResults() {
        try {
            List<StrategyResult> results = executionService.getAllLatestStrategyResults();
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load results", e);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                    "Failed to load results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load results: " + e.getMessage()));
        }
    }

    /**
     * Returns recent custom execution results (last 10).
     */
    @GetMapping("/results/custom")
    public ResponseEntity<?> getRecentCustomResults(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<StrategyResult> results = executionService.getRecentCustomExecutions(limit);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load custom execution results", e);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                    "Failed to load custom execution results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load custom execution results: " + e.getMessage()));
        }
    }

    /**
     * Executes predefined strategies by their indices.
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeStrategies(@RequestBody ExecuteRequest request) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        Set<Integer> indices = new HashSet<>(request.getStrategyIndices());
        Set<Integer> screenerIndices = request.getScreenerIndices() != null
                ? new HashSet<>(request.getScreenerIndices())
                : null;
        log.info("REST: Execute strategies with indices: {}, screener indices: {}", indices, screenerIndices);

        CompletableFuture.runAsync(() -> {
            executionService.startGlobalExecution("Initializing execution...");
            try {
                if (indices != null && !indices.isEmpty()) {
                    executionService.executeStrategies(indices);
                }
                if (screenerIndices != null && !screenerIndices.isEmpty()) {
                    List<ScreenerConfig> allScreeners = screenerExecutionService.getEnabledScreeners();
                    executionService.setCurrentExecutionTask("Initializing Screeners...");
                    screenerExecutionService.executeScreeners(screenerIndices, allScreeners);
                }
            } catch (Exception e) {
                log.error("Strategy execution failed", e);
                executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_EXECUTION,
                        String.format(AlertMessages.UNEXPECTED_FAILURE_FMT, e.getMessage()));
            } finally {
                executionService.finishGlobalExecution();
            }
        });

        int total = indices.size() + (screenerIndices != null ? screenerIndices.size() : 0);
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Execution started for " + total + " items"));
    }

    /**
     * Executes a custom strategy with user-provided parameters.
     */
    @PostMapping("/execute/custom")
    public ResponseEntity<?> executeCustomStrategy(@RequestBody CustomExecuteRequest request) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        try {
            StrategyType type = StrategyType.fromString(request.getStrategyType());
            Set<String> symbolSet = new LinkedHashSet<>();

            if (request.getSecuritiesFile() != null && !request.getSecuritiesFile().isBlank()) {
                try {
                    Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
                    String[] fileNames = request.getSecuritiesFile().split(",");
                    for (String fileName : fileNames) {
                        String key = fileName.trim();
                        String keyLower = key.toLowerCase();
                        List<String> fileSymbols = securitiesMap.get(keyLower);
                        if (fileSymbols != null) {
                            symbolSet.addAll(fileSymbols);
                        } else if (key.equalsIgnoreCase("SPY") || key.equalsIgnoreCase("QQQ")) {
                            log.info("Lazily fetching dynamic securities for custom execution: {}", key);
                            try {
                                symbolSet.addAll(wikipediaFetcher.fetch(key.toUpperCase()));
                            } catch (IllegalStateException e) {
                                return ResponseEntity.status(503)
                                        .body(Map.of("error", "Failed to load " + key + " from Wikipedia: " + e.getMessage()));
                            }
                        } else {
                            log.warn("Securities file '{}' not found. Available: {}", key, securitiesMap.keySet());
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to load securities maps: {}", e.getMessage());
                    return ResponseEntity.internalServerError()
                            .body(Map.of("error", "Failed to load securities files: " + e.getMessage()));
                }
            }

            if (request.getSecurities() != null && !request.getSecurities().isBlank()) {
                Arrays.stream(request.getSecurities().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toUpperCase)
                        .forEach(symbolSet::add);
            }

            if (symbolSet.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Provide a securities file, inline tickers, or both"));
            }

            List<String> symbols = new ArrayList<>(symbolSet);
            OptionsStrategyFilter filter = FilterParser.buildFilter(type, request.getFilter());
            if (filter != null) {
                filter.setStrategyType(type.name());
                filter.setSecuritiesFile(request.getSecuritiesFile());
                filter.setSecurities(request.getSecurities());
            }

            com.hemasundar.technical.TechnicalFilterChain technicalFilterChain = null;
            if (request.getTechnicalFilters() != null && !request.getTechnicalFilters().isEmpty()) {
                technicalFilterChain = strategiesConfigLoader.parseTechnicalFilters(request.getTechnicalFilters());
                if (filter != null) {
                    filter.setTechnicalFilters(request.getTechnicalFilters());
                }
            }

            OptionsConfig config = OptionsConfig.builder()
                    .alias(request.getAlias())
                    .strategy(strategiesConfigLoader.getStrategy(type))
                    .securities(symbols)
                    .maxTradesToSend(request.getMaxTradesToSend() != null ? request.getMaxTradesToSend() : 30)
                    .filter(filter)
                    .technicalFilterChain(technicalFilterChain)
                    .build();

            log.info("REST: Custom execute {} on {} securities", type.getDisplayName(), symbols.size());

            CompletableFuture.runAsync(() -> {
                executionService.executeCustomStrategy(config);
            });

            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message",
                    "Custom execution started: " + type.getDisplayName() + " on " + symbols.size() + " securities"));
        } catch (IllegalArgumentException e) {
            String errorMsg = e.getMessage() != null && e.getMessage().contains("No enum constant")
                    ? "Invalid strategy type: " + request.getStrategyType()
                    : e.getMessage();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", errorMsg));
        }
    }

    /**
     * Returns current execution status.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        boolean running = executionService.isExecutionRunning();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("running", running);
        if (running) {
            response.put("startTimeMs", executionService.getExecutionStartTimeMs());
            response.put("elapsedMs", System.currentTimeMillis() - executionService.getExecutionStartTimeMs());
            response.put("currentTask", executionService.getCurrentExecutionTask());
        }
        List<ExecutionAlert> alerts = executionService.getAlerts();
        if (!alerts.isEmpty()) {
            response.put("alerts", alerts);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Clears all execution alerts.
     */
    @PostMapping("/clear-errors")
    public ResponseEntity<?> clearErrors() {
        executionService.clearAlerts();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    /**
     * Cancels a running execution.
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelExecution() {
        if (!executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No execution is currently running"));
        }
        executionService.cancelExecution();
        return ResponseEntity.ok(Map.of("cancelled", true));
    }

    /**
     * Fetches the current live status of Equity and Options markets.
     */
    @GetMapping("/market-status")
    public ResponseEntity<?> getMarketStatus() {
        try {
            MarketHoursResponse hours = thinkOrSwimAPIs.getMarketHours();
            String equityStatus = "CLOSED";
            String optionsStatus = "CLOSED";

            if (hours.getEquity() != null && hours.getEquity().containsKey("EQ")) {
                equityStatus = resolveMarketStatus(hours.getEquity().get("EQ"));
            }
            if (hours.getOption() != null && hours.getOption().containsKey("EQO")) {
                optionsStatus = resolveMarketStatus(hours.getOption().get("EQO"));
            }

            return ResponseEntity.ok(Map.of("equityStatus", equityStatus, "optionsStatus", optionsStatus));
        } catch (Exception e) {
            log.error("Failed to fetch market hours", e);
            if (authErrorUtils.isAuthError(e)) {
                executionService.addAlert(ExecutionAlert.Severity.ERROR, "Market Status", AlertMessages.AUTH_FAILED);
            } else {
                executionService.addAlert(ExecutionAlert.Severity.WARNING, "Market Status",
                        String.format(AlertMessages.UNEXPECTED_FAILURE_FMT, e.getMessage()));
            }
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "equityStatus", "CLOSED", "optionsStatus", "CLOSED"));
        }
    }

    private String resolveMarketStatus(MarketHoursResponse.MarketData data) {
        if (data == null || data.getSessionHours() == null) {
            return data != null && Boolean.TRUE.equals(data.getIsOpen()) ? "OPEN" : "CLOSED";
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        MarketHoursResponse.SessionHours sh = data.getSessionHours();

        if (isWithinWindows(now, sh.getRegularMarket()))
            return "OPEN";
        if (isWithinWindows(now, sh.getPreMarket()))
            return "PRE_MARKET";
        if (isWithinWindows(now, sh.getPostMarket()))
            return "POST_MARKET";
        return "CLOSED";
    }

    private boolean isWithinWindows(java.time.OffsetDateTime now,
            List<MarketHoursResponse.TimeWindow> windows) {
        if (CollectionUtils.isEmpty(windows))
            return false;
        for (MarketHoursResponse.TimeWindow w : windows) {
            try {
                java.time.OffsetDateTime start = java.time.OffsetDateTime.parse(w.getStart());
                java.time.OffsetDateTime end = java.time.OffsetDateTime.parse(w.getEnd());
                if (!now.isBefore(start) && now.isBefore(end))
                    return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Deletes a custom execution result by primary key.
     */
    @DeleteMapping("/results/custom/{id}")
    public ResponseEntity<?> deleteCustomResult(@PathVariable String id) {
        try {
            executionService.deleteCustomExecution(id);
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        } catch (IOException e) {
            log.error("Failed to delete custom execution result id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete result: " + e.getMessage()));
        }
    }

    /**
     * Returns today's price performance for a comma-separated list of symbols.
     */
    @GetMapping("/quotes")
    public ResponseEntity<?> getQuotes(@RequestParam String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbols parameter is required"));
        }
        try {
            List<String> symbolList = Arrays.stream(symbols.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (symbolList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No valid symbols provided"));
            }

            Map<String, QuotesResponse.QuoteData> quoteMap = thinkOrSwimAPIs.getQuotes(symbolList, "quote", null);

            List<Map<String, Object>> response = new ArrayList<>();
            for (String symbol : symbolList) {
                QuotesResponse.QuoteData data = quoteMap.get(symbol);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("symbol", symbol);
                if (data != null && data.getQuote() != null) {
                    QuotesResponse.Quote q = data.getQuote();
                    entry.put("lastPrice", q.getLastPrice());
                    entry.put("netChange", q.getNetChange());
                    entry.put("netPercentChange", q.getNetPercentChange());
                } else {
                    entry.put("lastPrice", null);
                    entry.put("netChange", null);
                    entry.put("netPercentChange", null);
                }
                response.add(entry);
            }
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to fetch quotes for symbols={}: {}", symbols, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the current IV Rank for a given symbol.
     */
    @GetMapping("/iv-rank")
    public ResponseEntity<?> getIVRank(@RequestParam String symbol) {
        if (ivDataRepository.isEmpty()) {
            return ResponseEntity.status(503).body(Map.of("error", "Supabase is not configured"));
        }
        try {
            IVDataRepository repo = ivDataRepository.get();
            Double ivRank = repo.getIVRank(symbol);
            if (ivRank == null) {
                return ResponseEntity.noContent().build();
            }
            Map<String, Object> ivStats = repo.getIVStats(symbol);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("symbol", symbol);
            response.put("ivRank", Math.round(ivRank * 10.0) / 10.0);
            if (ivStats != null) {
                response.putAll(ivStats);
            }
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error fetching IV Rank for {}: {}", symbol, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns cached earnings calendar data.
     */
    @GetMapping("/earnings-calendar")
    public ResponseEntity<?> getEarningsCalendar() {
        try {
            Path cachePath = Path.of(System.getProperty("user.home"), ".trading-bot", "earnings_cache.json");
            if (!Files.exists(cachePath)) {
                cachePath = Path.of("src/main/resources/earnings_cache.json");
            }
            if (!Files.exists(cachePath)) {
                return ResponseEntity.ok(Map.of("events", Map.of()));
            }
            String json = Files.readString(cachePath);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            com.hemasundar.pojos.EarningsCache cache = mapper.readValue(json,
                    com.hemasundar.pojos.EarningsCache.class);

            Map<String, List<Map<String, Object>>> eventsByDate = new TreeMap<>();
            if (cache != null && cache.getCache() != null) {
                cache.getCache().forEach((symbol, entry) -> {
                    if (entry.getEarnings() == null) return;
                    for (var earning : entry.getEarnings()) {
                        if (earning.getDate() == null) continue;
                        String dateKey = earning.getDate().toString();
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("symbol", earning.getSymbol());
                        event.put("date", dateKey);
                        event.put("quarter", earning.getQuarter());
                        event.put("year", earning.getYear());
                        event.put("hour", earning.getHour());
                        event.put("epsEstimate", earning.getEpsEstimate());
                        event.put("epsActual", earning.getEpsActual());
                        event.put("revenueEstimate", earning.getRevenueEstimate());
                        event.put("revenueActual", earning.getRevenueActual());
                        eventsByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(event);
                    }
                });
            }

            return ResponseEntity.ok(Map.of("events", eventsByDate));
        } catch (Exception e) {
            log.error("Error reading earnings calendar: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
