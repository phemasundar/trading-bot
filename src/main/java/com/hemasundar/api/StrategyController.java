package com.hemasundar.api;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.services.StrategyExecutionService;
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
            return ResponseEntity.ok(response);
        } catch (IOException e) {
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
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to load results", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load results: " + e.getMessage()));
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
            return ResponseEntity.ok(results);
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
            return ResponseEntity.ok(executionService.loadSecuritiesMaps());
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
        log.info("REST: Execute strategies with indices: {}", indices);

        CompletableFuture.runAsync(() -> {
            try {
                executionService.executeStrategies(indices);
            } catch (IOException e) {
                log.error("Strategy execution failed", e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Execution started for " + indices.size() + " strategies"));
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

            // Parse securities
            List<String> symbols = Arrays.stream(request.getSecurities().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());

            if (symbols.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "At least one security is required"));
            }

            // Build filter from request
            OptionsStrategyFilter filter = buildFilter(type, request.getFilter());

            OptionsConfig config = OptionsConfig.builder()
                    .alias(request.getAlias())
                    .strategy(type.createStrategy())
                    .securities(symbols)
                    .maxTradesToSend(request.getMaxTradesToSend() != null ? request.getMaxTradesToSend() : 30)
                    .filter(filter)
                    .build();

            log.info("REST: Custom execute {} on {}", type.getDisplayName(), symbols);

            CompletableFuture.runAsync(() -> {
                executionService.executeCustomStrategy(config);
            });

            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Custom execution started: " + type.getDisplayName()));
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

    // ────────────────────────────────────────────
    // Helper: build filter from request JSON
    // ────────────────────────────────────────────

    private OptionsStrategyFilter buildFilter(StrategyType type, Map<String, Object> filterMap) {
        OptionsStrategyFilter filter;

        // Create the appropriate filter subclass based on strategy type
        switch (type) {
            case PUT_CREDIT_SPREAD:
            case BULLISH_LONG_PUT_CREDIT_SPREAD:
            case TECH_PUT_CREDIT_SPREAD:
            case CALL_CREDIT_SPREAD:
            case TECH_CALL_CREDIT_SPREAD:
                filter = new CreditSpreadFilter();
                break;
            case IRON_CONDOR:
            case BULLISH_LONG_IRON_CONDOR:
                filter = new IronCondorFilter();
                break;
            case LONG_CALL_LEAP:
            case LONG_CALL_LEAP_TOP_N:
                filter = new LongCallLeapFilter();
                break;
            case BULLISH_BROKEN_WING_BUTTERFLY:
                filter = new BrokenWingButterflyFilter();
                break;
            case BULLISH_ZEBRA:
                filter = new ZebraFilter();
                break;
            default:
                filter = new OptionsStrategyFilter();
        }

        // Apply common filter fields from request map
        if (filterMap != null) {
            applyIfPresent(filterMap, "targetDTE", v -> filter.setTargetDTE(toInt(v)));
            applyIfPresent(filterMap, "minDTE", v -> filter.setMinDTE(toInt(v)));
            applyIfPresent(filterMap, "maxDTE", v -> filter.setMaxDTE(toInt(v)));
            applyIfPresent(filterMap, "maxLossLimit", v -> filter.setMaxLossLimit(toDouble(v)));
            applyIfPresent(filterMap, "minReturnOnRisk", v -> filter.setMinReturnOnRisk(toInt(v)));
            applyIfPresent(filterMap, "minHistoricalVolatility", v -> filter.setMinHistoricalVolatility(toDouble(v)));
            applyIfPresent(filterMap, "maxBreakEvenPercentage", v -> filter.setMaxBreakEvenPercentage(toDouble(v)));
            applyIfPresent(filterMap, "maxUpperBreakevenDelta", v -> filter.setMaxUpperBreakevenDelta(toDouble(v)));
            applyIfPresent(filterMap, "maxNetExtrinsicValueToPricePercentage",
                    v -> filter.setMaxNetExtrinsicValueToPricePercentage(toDouble(v)));
            applyIfPresent(filterMap, "ignoreEarnings",
                    v -> filter.setIgnoreEarnings(Boolean.parseBoolean(v.toString())));
        }

        return filter;
    }

    private void applyIfPresent(Map<String, Object> map, String key, java.util.function.Consumer<Object> setter) {
        if (map.containsKey(key) && map.get(key) != null) {
            setter.accept(map.get(key));
        }
    }

    private int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }

    // ────────────────────────────────────────────
    // Request DTOs
    // ────────────────────────────────────────────

    @lombok.Data
    public static class ExecuteRequest {
        private List<Integer> strategyIndices;
    }

    @lombok.Data
    public static class CustomExecuteRequest {
        private String strategyType;
        private String securities;
        private String alias;
        private Integer maxTradesToSend;
        private Map<String, Object> filter;
    }
}
