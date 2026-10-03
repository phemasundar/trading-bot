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
import org.apache.commons.collections4.CollectionUtils;
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

    private ScreenerConfig buildScreenerConfig(CustomScreenerRequest request) throws IOException {
        ScreenerType screenerType = ScreenerType.fromString(request.getScreenerType());
        Set<String> symbolSet = new LinkedHashSet<>();
        if (request.getSecuritiesFile() != null && !request.getSecuritiesFile().isBlank()) {
            Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
            for (String fileName : request.getSecuritiesFile().split(",")) {
                String key = fileName.trim();
                String keyLower = key.toLowerCase();
                List<String> fileSymbols = securitiesMap.get(keyLower);
                if (fileSymbols != null) {
                    symbolSet.addAll(fileSymbols);
                } else if (key.equalsIgnoreCase("SPY") || key.equalsIgnoreCase("QQQ")) {
                    log.debug("Lazily fetching dynamic securities for custom screener: {}", key);
                    symbolSet.addAll(wikipediaFetcher.fetch(key.toUpperCase()));
                } else {
                    log.warn("Securities file '{}' not found. Available: {}", key, securitiesMap.keySet());
                }
            }
        }
        if (request.getSecurities() != null && !request.getSecurities().isBlank()) {
            Arrays.stream(request.getSecurities().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase)
                    .forEach(symbolSet::add);
        }
        if (symbolSet.isEmpty()) {
            throw new IllegalArgumentException("Provide a securities file, inline tickers, or both");
        }

        TechnicalFilterChain technicalFilterChain = null;
        if (request.getTechnicalFilters() != null && !request.getTechnicalFilters().isEmpty()) {
            technicalFilterChain = strategiesConfigLoader.parseTechnicalFilters(request.getTechnicalFilters());
        }

        return ScreenerConfig.builder()
                .screenerType(screenerType)
                .alias(request.getAlias() != null ? request.getAlias() : screenerType.getDisplayName())
                .securities(new ArrayList<>(symbolSet))
                .filterChain(technicalFilterChain)
                .build();
    }

    private Map<String, Object> buildRequestParams(CustomScreenerRequest request) {
        Map<String, Object> requestParams = new LinkedHashMap<>();
        requestParams.put("screenerType", request.getScreenerType());
        if (request.getAlias() != null) requestParams.put("alias", request.getAlias());
        if (request.getSecuritiesFile() != null) requestParams.put("securitiesFile", request.getSecuritiesFile());
        if (request.getSecurities() != null) requestParams.put("securities", request.getSecurities());
        if (request.getTechnicalFilters() != null) requestParams.put("technicalFilters", request.getTechnicalFilters());
        return requestParams;
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

        try {
            ScreenerConfig screenerConfig = buildScreenerConfig(request);
            log.info("REST: Custom screener {} on {} securities", screenerConfig.getScreenerType().getDisplayName(), screenerConfig.getSecurities().size());

            Map<String, Object> requestParams = buildRequestParams(request);
            Long customResultId = request.getCustomResultId();

            CompletableFuture.runAsync(() -> {
                executionService.startGlobalExecution("Custom Screener: " + screenerConfig.getName());
                try {
                    screenerExecutionService.executeCustomScreener(screenerConfig, requestParams, customResultId);
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
                    "message", "Custom screener started: " + screenerConfig.getScreenerType().getDisplayName() + " on " + screenerConfig.getSecurities().size()
                            + " securities"));
        } catch (IllegalArgumentException e) {
            String errorMsg = e.getMessage() != null && e.getMessage().contains("No enum constant")
                    ? "Invalid screener type: " + request.getScreenerType()
                    : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", errorMsg));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", "Failed to load securities: " + e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to load securities maps: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to load securities files: " + e.getMessage()));
        }
    }

    /**
     * Executes a batch of custom screeners sequentially in background.
     */
    @PostMapping("/execute/custom-screener/batch")
    public ResponseEntity<?> executeCustomScreenerBatch(@RequestBody(required = false) List<CustomScreenerRequest> requests) {
        if (executionService.isExecutionRunning()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An execution is already running"));
        }

        try {
            List<CustomScreenerRequest> executionRequests = requests;
            if (CollectionUtils.isEmpty(executionRequests)) {
                List<ScreenerExecutionResult> recents = supabaseService.getRecentCustomScreenerExecutions(100);
                if (CollectionUtils.isEmpty(recents)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "No custom screeners found to execute"));
                }
                executionRequests = new ArrayList<>();
                for (ScreenerExecutionResult r : recents) {
                    if (r.getRequestParams() != null && !r.getRequestParams().isEmpty()) {
                        Map<String, Object> p = r.getRequestParams();
                        CustomScreenerRequest req = new CustomScreenerRequest();
                        if (r.getScreenerId() != null) {
                            try {
                                req.setCustomResultId(Long.parseLong(r.getScreenerId()));
                            } catch (NumberFormatException ignored) {}
                        }
                        req.setScreenerType((String) p.get("screenerType"));
                        req.setAlias((String) p.get("alias"));
                        req.setSecuritiesFile((String) p.get("securitiesFile"));
                        req.setSecurities((String) p.get("securities"));
                        if (p.get("technicalFilters") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tf = (Map<String, Object>) p.get("technicalFilters");
                            req.setTechnicalFilters(tf);
                        }
                        executionRequests.add(req);
                    }
                }
            }

            if (CollectionUtils.isEmpty(executionRequests)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No valid custom screeners found to execute"));
            }

            List<ScreenerConfig> configs = new ArrayList<>();
            List<Map<String, Object>> requestParamsList = new ArrayList<>();
            List<Long> customResultIds = new ArrayList<>();

            for (CustomScreenerRequest req : executionRequests) {
                configs.add(buildScreenerConfig(req));
                requestParamsList.add(buildRequestParams(req));
                customResultIds.add(req.getCustomResultId());
            }

            log.info("REST: Batch custom screener execution started for {} screeners", configs.size());

            CompletableFuture.runAsync(() -> {
                executionService.startGlobalExecution("Custom Screener Batch (1 of " + configs.size() + ")");
                try {
                    screenerExecutionService.executeCustomScreeners(configs, requestParamsList, customResultIds);
                } catch (Exception e) {
                    log.error("Custom screener batch execution failed", e);
                    executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_EXECUTION,
                            String.format(AlertMessages.UNEXPECTED_FAILURE_FMT, e.getMessage()));
                } finally {
                    executionService.finishGlobalExecution();
                }
            });

            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Custom screener batch execution started for " + configs.size() + " screeners"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", "Failed to load securities: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to start custom screener batch execution", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start custom screener batch execution: " + e.getMessage()));
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
}
