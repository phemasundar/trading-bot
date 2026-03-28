package com.hemasundar.api;

import com.hemasundar.dto.ExecuteRequest;
import com.hemasundar.dto.CustomExecuteRequest;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
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
            // Note: Update to call the new service method
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(screenerExecutionService.getLatestScreenerResults());
        } catch (Exception e) {
            log.error("Failed to load screener results", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load screener results: " + e.getMessage()));
        }
    }

    /**
     * Returns recent custom execution results (last 20).
     */
    @GetMapping("/results/custom")
    public ResponseEntity<?> getCustomResults(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<StrategyResult> results = executionService.getRecentCustomExecutions(limit);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load custom results", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load custom results: " + e.getMessage()));
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

            OptionsConfig config = OptionsConfig.builder()
                    .alias(request.getAlias())
                    .strategy(type.createStrategy())
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
        return ResponseEntity.ok(response);
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
     * Fetches the current live status of the Equity and Options markets.
     * Returns status strings: OPEN, CLOSED, PRE_MARKET, or POST_MARKET.
     */
    @GetMapping("/market-status")
    public ResponseEntity<?> getMarketStatus() {
        try {
            MarketHoursResponse hours = ThinkOrSwinAPIs.getMarketHours();
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
            return ResponseEntity.ok(Map.of("equityStatus", "CLOSED", "optionsStatus", "CLOSED", "error", true));
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

}
