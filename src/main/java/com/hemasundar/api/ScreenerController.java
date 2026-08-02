package com.hemasundar.api;

import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.CustomScreenerRequest;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.services.ScreenerExecutionService;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.technical.ScreenerType;
import com.hemasundar.technical.TechnicalFilterChain;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.WikipediaSecuritiesFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * REST controller for technical screeners and screener results.
 */
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScreenerController {

    private final ScreenerExecutionService screenerExecutionService;
    private final StrategyExecutionService executionService;
    private final SupabaseService supabaseService;
    private final SecuritiesResolver securitiesResolver;
    private final StrategiesConfigLoader strategiesConfigLoader;
    private final WikipediaSecuritiesFetcher wikipediaFetcher;

    /**
     * Returns all enabled technical screeners with index, name, and type.
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
                        map.put("descriptionFile", config.getDescriptionFile());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to load screeners", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load screeners: " + e.getMessage()));
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
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                    "Failed to load screener results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load screener results: " + e.getMessage()));
        }
    }

    /**
     * Returns the most recent manual custom technical screener execution results.
     */
    @GetMapping("/results/custom/screeners")
    public ResponseEntity<?> getRecentCustomScreenerResults(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<ScreenerExecutionResult> results = supabaseService.getRecentCustomScreenerExecutions(limit);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load custom screener results", e);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_SUPABASE,
                    "Failed to load custom screener results: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load custom screener results: " + e.getMessage()));
        }
    }

    /**
     * Executes a custom technical screener with user-provided parameters.
     */
    @PostMapping("/execute/custom-screener")
    public ResponseEntity<?> executeCustomScreener(@RequestBody CustomScreenerRequest request) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        ScreenerType screenerType;
        try {
            screenerType = ScreenerType.fromString(request.getScreenerType());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid screener type: " + request.getScreenerType()));
        }

        Set<String> symbolSet = new LinkedHashSet<>();
        if (request.getSecuritiesFile() != null && !request.getSecuritiesFile().isBlank()) {
            try {
                Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
                for (String fileName : request.getSecuritiesFile().split(",")) {
                    String key = fileName.trim();
                    String keyLower = key.toLowerCase();
                    List<String> fileSymbols = securitiesMap.get(keyLower);
                    if (fileSymbols != null) {
                        symbolSet.addAll(fileSymbols);
                    } else if (key.equalsIgnoreCase("SPY") || key.equalsIgnoreCase("QQQ")) {
                        log.info("Lazily fetching dynamic securities for custom screener: {}", key);
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
                    .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase)
                    .forEach(symbolSet::add);
        }
        if (symbolSet.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Provide a securities file, inline tickers, or both"));
        }

        TechnicalFilterChain technicalFilterChain = null;
        try {
            if (request.getTechnicalFilters() != null && !request.getTechnicalFilters().isEmpty()) {
                technicalFilterChain = strategiesConfigLoader.parseTechnicalFilters(request.getTechnicalFilters());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        ScreenerConfig screenerConfig = ScreenerConfig.builder()
                .screenerType(screenerType)
                .alias(request.getAlias() != null ? request.getAlias() : screenerType.getDisplayName())
                .securities(new ArrayList<>(symbolSet))
                .filterChain(technicalFilterChain)
                .build();

        log.info("REST: Custom screener {} on {} securities", screenerType.getDisplayName(), symbolSet.size());

        Map<String, Object> requestParams = new LinkedHashMap<>();
        requestParams.put("screenerType", request.getScreenerType());
        if (request.getAlias() != null) requestParams.put("alias", request.getAlias());
        if (request.getSecuritiesFile() != null) requestParams.put("securitiesFile", request.getSecuritiesFile());
        if (request.getSecurities() != null) requestParams.put("securities", request.getSecurities());
        if (request.getTechnicalFilters() != null) requestParams.put("technicalFilters", request.getTechnicalFilters());

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
                "message", "Custom screener started: " + screenerType.getDisplayName() + " on " + symbolSet.size()
                        + " securities"));
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
}
