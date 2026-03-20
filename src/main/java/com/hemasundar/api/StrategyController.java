package com.hemasundar.api;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.utils.OptionChainCache;
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
            List<ScreenerConfig> screeners = executionService.getEnabledScreeners();
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
                    .body(executionService.getLatestScreenerResults());
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
                    .body(executionService.loadSecuritiesMaps());
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
            try {
                executionService.executeStrategies(indices, screenerIndices);
            } catch (IOException e) {
                log.error("Strategy execution failed", e);
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
                    Map<String, List<String>> securitiesMap = executionService.loadSecuritiesMaps();
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
            OptionsStrategyFilter filter = buildFilter(type, request.getFilter());

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

    @SuppressWarnings("unchecked")
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

        if (filterMap == null) return filter;

        // ── Common filter fields ──
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
        applyIfPresent(filterMap, "minNetExtrinsicValueToPricePercentage",
                v -> filter.setMinNetExtrinsicValueToPricePercentage(toDouble(v)));
        applyIfPresent(filterMap, "ignoreEarnings",
                v -> filter.setIgnoreEarnings(Boolean.parseBoolean(v.toString())));
        applyIfPresent(filterMap, "maxTotalDebit", v -> filter.setMaxTotalDebit(toDouble(v)));
        applyIfPresent(filterMap, "maxTotalCredit", v -> filter.setMaxTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "minTotalCredit", v -> filter.setMinTotalCredit(toDouble(v)));
        applyIfPresent(filterMap, "priceVsMaxDebitRatio", v -> filter.setPriceVsMaxDebitRatio(toDouble(v)));
        applyIfPresent(filterMap, "maxCAGRForBreakEven", v -> filter.setMaxCAGRForBreakEven(toDouble(v)));
        applyIfPresent(filterMap, "maxOptionPricePercent", v -> filter.setMaxOptionPricePercent(toDouble(v)));
        applyIfPresent(filterMap, "marginInterestRate", v -> filter.setMarginInterestRate(toDouble(v)));
        applyIfPresent(filterMap, "savingsInterestRate", v -> filter.setSavingsInterestRate(toDouble(v)));

        // ── Strategy-specific fields ──
        if (filter instanceof CreditSpreadFilter csFilter) {
            applyLegFilter(filterMap, "shortLeg", csFilter::setShortLeg);
            applyLegFilter(filterMap, "longLeg", csFilter::setLongLeg);
        } else if (filter instanceof IronCondorFilter icFilter) {
            applyLegFilter(filterMap, "putShortLeg", icFilter::setPutShortLeg);
            applyLegFilter(filterMap, "putLongLeg", icFilter::setPutLongLeg);
            applyLegFilter(filterMap, "callShortLeg", icFilter::setCallShortLeg);
            applyLegFilter(filterMap, "callLongLeg", icFilter::setCallLongLeg);
        } else if (filter instanceof LongCallLeapFilter leapFilter) {
            applyLegFilter(filterMap, "longCall", leapFilter::setLongCall);
            applyIfPresent(filterMap, "minCostSavingsPercent", v -> leapFilter.setMinCostSavingsPercent(toDouble(v)));
            applyIfPresent(filterMap, "minCostEfficiencyPercent", v -> leapFilter.setMinCostEfficiencyPercent(toDouble(v)));
            applyIfPresent(filterMap, "topTradesCount", v -> leapFilter.setTopTradesCount(toInt(v)));
            applyIfPresent(filterMap, "relaxationPriority", v -> leapFilter.setRelaxationPriority(toStringList(v)));
            applyIfPresent(filterMap, "sortPriority", v -> leapFilter.setSortPriority(toStringList(v)));
        } else if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            applyLegFilter(filterMap, "leg1Long", bwbFilter::setLeg1Long);
            applyLegFilter(filterMap, "leg2Short", bwbFilter::setLeg2Short);
            applyLegFilter(filterMap, "leg3Long", bwbFilter::setLeg3Long);
        } else if (filter instanceof ZebraFilter zebraFilter) {
            applyLegFilter(filterMap, "shortCall", zebraFilter::setShortCall);
            applyLegFilter(filterMap, "longCall", zebraFilter::setLongCall);
        }

        return filter;
    }

    /**
     * Builds a LegFilter from a nested map (e.g., "shortLeg": {"minDelta": 0.1, ...}).
     */
    @SuppressWarnings("unchecked")
    private void applyLegFilter(Map<String, Object> filterMap, String key, java.util.function.Consumer<LegFilter> setter) {
        if (!filterMap.containsKey(key) || filterMap.get(key) == null) return;

        Object legObj = filterMap.get(key);
        if (!(legObj instanceof Map)) return;

        Map<String, Object> legMap = (Map<String, Object>) legObj;
        if (legMap.isEmpty()) return;

        LegFilter.LegFilterBuilder builder = LegFilter.builder();
        applyIfPresent(legMap, "minDelta", v -> builder.minDelta(toDouble(v)));
        applyIfPresent(legMap, "maxDelta", v -> builder.maxDelta(toDouble(v)));
        applyIfPresent(legMap, "minPremium", v -> builder.minPremium(toDouble(v)));
        applyIfPresent(legMap, "maxPremium", v -> builder.maxPremium(toDouble(v)));
        applyIfPresent(legMap, "minOpenInterest", v -> builder.minOpenInterest(toInt(v)));
        applyIfPresent(legMap, "minVolume", v -> builder.minVolume(toInt(v)));
        applyIfPresent(legMap, "minVolatility", v -> builder.minVolatility(toDouble(v)));
        applyIfPresent(legMap, "maxVolatility", v -> builder.maxVolatility(toDouble(v)));

        setter.accept(builder.build());
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        if (value instanceof List) {
            return ((List<Object>) value).stream().map(Object::toString).collect(Collectors.toList());
        }
        // Handle comma-separated string
        return Arrays.stream(value.toString().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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
        private List<Integer> screenerIndices;
    }

    @lombok.Data
    public static class CustomExecuteRequest {
        private String strategyType;
        private String securitiesFile; // e.g., "portfolio, top100"
        private String securities;     // e.g., "AAPL, MSFT, GOOG"
        private String alias;
        private Integer maxTradesToSend;
        private Map<String, Object> filter;
    }
}
