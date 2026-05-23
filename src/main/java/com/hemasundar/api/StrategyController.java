package com.hemasundar.api;

import com.hemasundar.config.properties.SupabaseConfig;
import com.hemasundar.dto.CustomExecuteRequest;
import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.ExecuteRequest;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.services.supabase.IVDataRepository;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.utils.FilterParser;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.MarketHoursResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
 * REST API controller for the trading bot.
 * Wraps all Supabase and strategy execution operations so the frontend
 * never talks to Supabase directly.
 */
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyExecutionService executionService;
    private final com.hemasundar.services.ScreenerExecutionService screenerExecutionService;
    private final com.hemasundar.utils.SecuritiesResolver securitiesResolver;
    private final com.hemasundar.apis.ThinkOrSwinAPIs thinkOrSwinAPIs;
    private final com.hemasundar.config.StrategiesConfigLoader strategiesConfigLoader;
    private final SupabaseConfig supabaseConfig;
    private final com.hemasundar.utils.AuthErrorUtils authErrorUtils;
    private final com.hemasundar.services.SupabaseService supabaseService;
    private final Optional<IVDataRepository> ivDataRepository;

    // ────────────────────────────────────────────
    // AUTH CONFIG (public — excluded from filter)
    // ────────────────────────────────────────────

    /**
     * Returns Supabase project URL and anon key so the frontend can
     * initialize the Supabase JS client for OAuth login.
     * This endpoint is public (not behind the JWT filter).
     */
    @GetMapping("/auth/config")
    public ResponseEntity<?> getAuthConfig() {
        return ResponseEntity.ok(Map.of(
                "supabaseUrl", supabaseConfig.getUrl() != null ? supabaseConfig.getUrl() : "",
                "supabaseAnonKey", supabaseConfig.getAnonKey() != null ? supabaseConfig.getAnonKey() : ""
        ));
    }

    // ────────────────────────────────────────────
    // READ endpoints (wrappers around Supabase)
    // ────────────────────────────────────────────

    /**
     * Returns all enabled strategies with index, name, and type.
     * Used by the dashboard to populate strategy checkboxes.
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
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (IOException e) {
            log.error("Failed to load strategies", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load strategies: " + e.getMessage()));
        }
    }

    /**
     * Returns all enabled technical screeners with index, name, and type.
     * Used by the dashboard to populate screener checkboxes.
     */
    @GetMapping("/screeners")
    public ResponseEntity<?> getEnabledScreeners() {
        try {
            List<ScreenerConfig> screeners = screenerExecutionService.getEnabledScreeners();
            List<Map<String, Object>> response = IntStream.range(0, screeners.size())
                    .mapToObj(i -> {
                        ScreenerConfig config = screeners.get(i);
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("index", i);
                        map.put("name", config.getName());
                        map.put("type", config.getScreenerType().name());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (IOException e) {
            log.error("Failed to load screeners", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load screeners: " + e.getMessage()));
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
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE, "Failed to load results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load results: " + e.getMessage()));
        }
    }

    /**
     * Returns all latest screener results from the database.
     */
    @GetMapping("/results/screeners")
    public ResponseEntity<?> getScreenerResults() {
        try {
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(screenerExecutionService.getLatestScreenerResults());
        } catch (Exception e) {
            log.error("Failed to load screener results", e);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE, "Failed to load screener results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load screener results: " + e.getMessage()));
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
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE, "Failed to load custom execution results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load custom execution results: " + e.getMessage()));
        }
    }

    /**
     * Returns the most recent manual custom technical screener execution results.
     */
    @GetMapping("/results/custom/screeners")
    public ResponseEntity<?> getRecentCustomScreenerResults(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<com.hemasundar.dto.ScreenerExecutionResult> results = supabaseService.getRecentCustomScreenerExecutions(limit);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load custom screener results", e);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE, "Failed to load custom screener results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load custom screener results: " + e.getMessage()));
        }
    }

    /**
     * Returns the raw strategies-config.json content.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            String configPath = "strategies-config.json";
            Path path = Path.of(configPath);
            if (!Files.exists(path)) {
                // Try classpath
                var resource = getClass().getClassLoader().getResource(configPath);
                if (resource != null) {
                    path = Path.of(resource.toURI());
                }
            }
            String content = Files.readString(path);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(content);
        } catch (Exception e) {
            log.error("Failed to read config", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to read config: " + e.getMessage()));
        }
    }

    /**
     * Returns a map of securities file keys to their respective lists of securities.
     * Used by the config viewer to display actual tickers instead of just file names.
     */
    @GetMapping("/securities")
    public ResponseEntity<?> getSecuritiesMaps() {
        try {
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(securitiesResolver.loadSecuritiesMaps());
        } catch (IOException e) {
            log.error("Failed to load securities map", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load securities map: " + e.getMessage()));
        }
    }

    // ────────────────────────────────────────────
    // WRITE endpoints (strategy execution)
    // ────────────────────────────────────────────

    /**
     * Executes predefined strategies by their indices.
     * Runs asynchronously and returns immediately.
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
     * Runs asynchronously and returns immediately.
     */
    @PostMapping("/execute/custom")
    public ResponseEntity<?> executeCustomStrategy(@RequestBody CustomExecuteRequest request) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        try {
            StrategyType type = StrategyType.fromString(request.getStrategyType());

            // Resolve securities from file names and/or inline symbols
            Set<String> symbolSet = new LinkedHashSet<>();

            // 1. Resolve from securitiesFile (e.g., "portfolio, top100")
            if (request.getSecuritiesFile() != null && !request.getSecuritiesFile().isBlank()) {
                try {
                    Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
                    String[] fileNames = request.getSecuritiesFile().split(",");
                    for (String fileName : fileNames) {
                        String key = fileName.trim().toLowerCase();
                        List<String> fileSymbols = securitiesMap.get(key);
                        if (fileSymbols != null) {
                            symbolSet.addAll(fileSymbols);
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

            // 2. Add inline securities
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

            // Build filter from request
            OptionsStrategyFilter filter = FilterParser.buildFilter(type, request.getFilter());
            if (filter != null) {
                filter.setStrategyType(type.name());
                filter.setSecuritiesFile(request.getSecuritiesFile());
                filter.setSecurities(request.getSecurities());
            }

            OptionsConfig config = OptionsConfig.builder()
                    .alias(request.getAlias())
                    .strategy(strategiesConfigLoader.getStrategy(type))
                    .securities(symbols)
                    .maxTradesToSend(request.getMaxTradesToSend() != null ? request.getMaxTradesToSend() : 30)
                    .filter(filter)
                    .build();

            log.info("REST: Custom execute {} on {} securities", type.getDisplayName(), symbols.size());

            CompletableFuture.runAsync(() -> {
                executionService.executeCustomStrategy(config);
            });

            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Custom execution started: " + type.getDisplayName() + " on " + symbols.size() + " securities"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid strategy type: " + request.getStrategyType()));
        }
    }

    /**
     * Executes a custom technical screener with user-provided parameters.
     * Runs asynchronously and returns immediately.
     */
    @PostMapping("/execute/custom-screener")
    public ResponseEntity<?> executeCustomScreener(@RequestBody com.hemasundar.dto.CustomScreenerRequest request) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        // Validate screener type
        com.hemasundar.technical.ScreenerType screenerType;
        try {
            screenerType = com.hemasundar.technical.ScreenerType.fromString(request.getScreenerType());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid screener type: " + request.getScreenerType()));
        }

        // Resolve securities
        Set<String> symbolSet = new LinkedHashSet<>();
        if (request.getSecuritiesFile() != null && !request.getSecuritiesFile().isBlank()) {
            try {
                Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
                for (String fileName : request.getSecuritiesFile().split(",")) {
                    String key = fileName.trim().toLowerCase();
                    List<String> fileSymbols = securitiesMap.get(key);
                    if (fileSymbols != null) {
                        symbolSet.addAll(fileSymbols);
                    } else {
                        log.warn("Securities file '{}' not found. Available: {}", key, securitiesMap.keySet());
                    }
                }
            } catch (java.io.IOException e) {
                log.error("Failed to load securities maps: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to load securities files: " + e.getMessage()));
            }
        }
        if (request.getSecurities() != null && !request.getSecurities().isBlank()) {
            Arrays.stream(request.getSecurities().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase)
                    .forEach(symbolSet::add);
        }
        if (symbolSet.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Provide a securities file, inline tickers, or both"));
        }

        // Build TechFilterConditions from request
        com.hemasundar.technical.TechFilterConditions.TechFilterConditionsBuilder condBuilder =
                com.hemasundar.technical.TechFilterConditions.builder();

        if (request.getRsiCondition() != null && !request.getRsiCondition().isBlank()) {
            try { condBuilder.rsiCondition(com.hemasundar.technical.RSICondition.valueOf(request.getRsiCondition())); } catch (Exception ignored) {}
        }
        if (request.getBollingerCondition() != null && !request.getBollingerCondition().isBlank()) {
            try { condBuilder.bollingerCondition(com.hemasundar.technical.BollingerCondition.valueOf(request.getBollingerCondition())); } catch (Exception ignored) {}
        }
        if (request.getMinVolume() != null)          condBuilder.minVolume(request.getMinVolume());
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA20()))  condBuilder.requirePriceBelowMA20(true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA20()))  condBuilder.requirePriceAboveMA20(true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA50()))  condBuilder.requirePriceBelowMA50(true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA50()))  condBuilder.requirePriceAboveMA50(true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA100())) condBuilder.requirePriceBelowMA100(true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA100())) condBuilder.requirePriceAboveMA100(true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA200())) condBuilder.requirePriceBelowMA200(true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA200())) condBuilder.requirePriceAboveMA200(true);
        if (request.getMinDropPercent() != null)     condBuilder.minDropPercent(request.getMinDropPercent());
        if (request.getLookbackDays() != null)       condBuilder.lookbackDays(request.getLookbackDays());

        com.hemasundar.technical.ScreenerConfig screenerConfig = com.hemasundar.technical.ScreenerConfig.builder()
                .screenerType(screenerType)
                .alias(request.getAlias() != null ? request.getAlias() : screenerType.getDisplayName())
                .securities(new ArrayList<>(symbolSet))
                .conditions(condBuilder.build())
                .build();

        log.info("REST: Custom screener {} on {} securities", screenerType.getDisplayName(), symbolSet.size());

        // Capture the request parameters as a plain map so they can be stored
        // alongside the result and used by the UI "Load Filters" feature.
        Map<String, Object> requestParams = new LinkedHashMap<>();
        requestParams.put("screenerType", request.getScreenerType());
        if (request.getAlias() != null)              requestParams.put("alias", request.getAlias());
        if (request.getSecuritiesFile() != null)     requestParams.put("securitiesFile", request.getSecuritiesFile());
        if (request.getSecurities() != null)         requestParams.put("securities", request.getSecurities());
        if (request.getRsiCondition() != null)       requestParams.put("rsiCondition", request.getRsiCondition());
        if (request.getBollingerCondition() != null) requestParams.put("bollingerCondition", request.getBollingerCondition());
        if (request.getMinVolume() != null)          requestParams.put("minVolume", request.getMinVolume());
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA20()))  requestParams.put("requirePriceBelowMA20", true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA20()))  requestParams.put("requirePriceAboveMA20", true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA50()))  requestParams.put("requirePriceBelowMA50", true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA50()))  requestParams.put("requirePriceAboveMA50", true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA100())) requestParams.put("requirePriceBelowMA100", true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA100())) requestParams.put("requirePriceAboveMA100", true);
        if (Boolean.TRUE.equals(request.getRequirePriceBelowMA200())) requestParams.put("requirePriceBelowMA200", true);
        if (Boolean.TRUE.equals(request.getRequirePriceAboveMA200())) requestParams.put("requirePriceAboveMA200", true);
        if (request.getMinDropPercent() != null)     requestParams.put("minDropPercent", request.getMinDropPercent());
        if (request.getLookbackDays() != null)       requestParams.put("lookbackDays", request.getLookbackDays());

        CompletableFuture.runAsync(() -> {
            executionService.startGlobalExecution("Custom Screener: " + screenerConfig.getName());
            try {
                screenerExecutionService.executeCustomScreener(screenerConfig, requestParams);
            } catch (Exception e) {
                log.error("Custom screener execution failed", e);
                executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_EXECUTION,
                        String.format(AlertMessages.UNEXPECTED_FAILURE_FMT, e.getMessage()));
            } finally {
                executionService.finishGlobalExecution();
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Custom screener started: " + screenerType.getDisplayName() + " on " + symbolSet.size() + " securities"));
    }

    // ────────────────────────────────────────────
    // STATUS / CONTROL endpoints
    // ────────────────────────────────────────────

    /**
     * Returns current execution status, including any alerts from the last execution.
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
        // Always include alerts so the UI can surface warnings/errors after execution completes
        List<ExecutionAlert> alerts = executionService.getAlerts();
        if (!alerts.isEmpty()) {
            response.put("alerts", alerts);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Clears all execution alerts (called by the UI dismiss button).
     */
    @PostMapping("/clear-errors")
    public ResponseEntity<?> clearErrors() {
        executionService.clearAlerts();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    /** @deprecated Use /api/clear-errors instead. Kept for backward compatibility. */
    @PostMapping("/clear-error")
    public ResponseEntity<?> clearLastError() {
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

    // ────────────────────────────────────────────
    // MARKET STATUS endpoint
    // ────────────────────────────────────────────

    /**
     * Fetches the current live status of the Equity and Options markets.
     * Returns status strings: OPEN, CLOSED, PRE_MARKET, or POST_MARKET.
     */
    @GetMapping("/market-status")
    public ResponseEntity<?> getMarketStatus() {
        try {
            MarketHoursResponse hours = thinkOrSwinAPIs.getMarketHours();
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
                executionService.addAlert(ExecutionAlert.Severity.WARNING, "Market Status", String.format(AlertMessages.UNEXPECTED_FAILURE_FMT, e.getMessage()));
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage(), "equityStatus", "CLOSED", "optionsStatus", "CLOSED"));
        }
    }

    /**
     * Resolves the textual market status by comparing current time against session windows.
     */
    private String resolveMarketStatus(MarketHoursResponse.MarketData data) {
        if (data == null || data.getSessionHours() == null) {
            return data != null && Boolean.TRUE.equals(data.getIsOpen()) ? "OPEN" : "CLOSED";
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        MarketHoursResponse.SessionHours sh = data.getSessionHours();

        if (isWithinWindows(now, sh.getRegularMarket())) return "OPEN";
        if (isWithinWindows(now, sh.getPreMarket())) return "PRE_MARKET";
        if (isWithinWindows(now, sh.getPostMarket())) return "POST_MARKET";
        return "CLOSED";
    }

    private boolean isWithinWindows(java.time.OffsetDateTime now,
                                     java.util.List<MarketHoursResponse.TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) return false;
        for (MarketHoursResponse.TimeWindow w : windows) {
            try {
                java.time.OffsetDateTime start = java.time.OffsetDateTime.parse(w.getStart());
                java.time.OffsetDateTime end   = java.time.OffsetDateTime.parse(w.getEnd());
                if (!now.isBefore(start) && now.isBefore(end)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ────────────────────────────────────────────
    // CUSTOM EXECUTION CRUD endpoints
    // ────────────────────────────────────────────

    /**
     * Deletes a custom execution result by its database primary key.
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
     * Deletes a specific custom screener execution result by its Supabase ID.
     */
    @DeleteMapping("/results/custom/screeners/{id}")
    public ResponseEntity<?> deleteCustomScreenerResult(@PathVariable String id) {
        try {
            supabaseService.deleteCustomScreenerExecution(id);
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        } catch (IOException e) {
            log.error("Failed to delete custom screener result id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete result: " + e.getMessage()));
        }
    }

    // ────────────────────────────────────────────
    // FILTER LOGS endpoint
    // ────────────────────────────────────────────

    /**
     * Returns all filter-stage log entries captured during the current or most recent execution.
     * Used by the /logs.html page to display per-filter trade counts in real-time.
     */
    @GetMapping("/filter-logs")
    public ResponseEntity<?> getFilterLogs() {
        return ResponseEntity.ok(executionService.getFilterLogs());
    }
    /**
     * Clears the in-memory filter log store on demand (e.g., from the Logs UI page).
     */
    @PostMapping("/filter-logs/clear")
    public ResponseEntity<?> clearFilterLogs() {
        executionService.clearFilterLogs();
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    // ────────────────────────────────────────────
    // IV RANK
    // ────────────────────────────────────────────

    /**
     * Returns the current IV Rank for a given symbol, calculated from up to 1 year
     * of historical iv_data stored in Supabase.
     *
     * <p>Response JSON:
     * <pre>{"symbol": "AAPL", "ivRank": 62.3, "minIV": 18.4, "maxIV": 45.6, "currentIV": 34.1}</pre>
     *
     * <p>Returns 503 when Supabase is disabled, and 204 (no content) when insufficient data exists.
     *
     * @param symbol stock ticker, e.g. {@code AAPL}
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

}
