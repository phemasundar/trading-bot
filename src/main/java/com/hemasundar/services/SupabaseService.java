package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.pojos.IVDataPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Service to interact with Supabase REST API for storing IV data and strategy
 * execution results.
 * Uses RestAssured client to make HTTP requests to Supabase's PostgREST API.
 */
@Log4j2
public class SupabaseService {
    private static final String IV_DATA_PATH = "/rest/v1/iv_data";
    private static final String EXECUTIONS_PATH = "/rest/v1/strategy_executions";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final String projectUrl;
    private final String apiKey;

    /**
     * Constructor initializes Supabase service with authentication.
     *
     * @param projectUrl Supabase project URL (e.g., https://abcdefg.supabase.co)
     * @param apiKey     Supabase API key (Publishable/anon key)
     */
    public SupabaseService(String projectUrl, String apiKey) {
        if (projectUrl == null || projectUrl.isEmpty()) {
            throw new IllegalArgumentException("Supabase project URL cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Supabase API key cannot be null or empty");
        }

        this.projectUrl = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        this.apiKey = apiKey;

        log.info("Supabase Service initialized for project: {}", projectUrl);
    }

    /**
     * Tests connection to Supabase by making a simple GET request.
     *
     * @return true if connection is successful
     * @throws IOException if connection fails
     */
    public boolean testConnection() throws IOException {
        String url = projectUrl + IV_DATA_PATH + "?select=id&limit=1";

        try {
            Response response = RestAssured.given()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(url);

            if (response.getStatusCode() == 200) {
                log.info("Supabase connection test successful");
                return true;
            } else {
                log.error("Supabase connection test failed: {} - {}",
                        response.getStatusCode(), response.getStatusLine());
                return false;
            }
        } catch (Exception e) {
            log.error("Supabase connection test failed with exception: {}", e.getMessage());
            throw new IOException("Failed to connect to Supabase: " + e.getMessage(), e);
        }
    }

    /**
     * Upserts (inserts or updates) IV data point to Supabase.
     * Uses PostgreSQL's ON CONFLICT to update if entry exists for same symbol and
     * date.
     *
     * @param dataPoint IV data point to upsert
     * @throws IOException if API call fails after retries
     */
    public void upsertIVData(IVDataPoint dataPoint) throws IOException {
        String symbol = dataPoint.getSymbol();
        String date = dataPoint.getCurrentDate().format(DATE_FORMATTER);

        // Build JSON payload
        String jsonPayload = buildJsonPayload(dataPoint);

        // Upsert URL with on_conflict parameter (PostgREST syntax)
        // This tells Supabase to update if symbol+date already exists
        String url = projectUrl + IV_DATA_PATH + "?on_conflict=symbol,date";

        // Retry logic for transient errors
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                RequestSpecification request = RestAssured.given()
                        .header("apikey", apiKey)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "resolution=merge-duplicates") // UPSERT behavior
                        .body(jsonPayload);

                Response response = request.post(url);
                int statusCode = response.getStatusCode();

                if (statusCode == 200 || statusCode == 201) {
                    log.info("[{}] Successfully upserted IV data for {} - PUT: {}%, CALL: {}%",
                            symbol, date, dataPoint.getAtmPutIV(), dataPoint.getAtmCallIV());
                    return; // Success
                } else if (statusCode == 429 && retryCount < maxRetries) {
                    // Rate limit - retry with exponential backoff
                    retryCount++;
                    long waitTime = (long) Math.pow(2, retryCount) * 1000; // 2s, 4s, 8s
                    log.warn("[{}] Rate limit exceeded (429), retry {}/{} after {}ms",
                            symbol, retryCount, maxRetries, waitTime);
                    Thread.sleep(waitTime);
                } else {
                    // Other error
                    String errorBody = response.getBody().asString();
                    throw new IOException(String.format(
                            "[%s] Supabase API error: %d - %s. Body: %s",
                            symbol, statusCode, response.getStatusLine(), errorBody));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for retry", e);
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException("Failed to upsert data: " + e.getMessage(), e);
            }
        }

        throw new IOException(String.format("[%s] Failed to upsert data after %d retries", symbol, maxRetries));
    }

    /**
     * Builds JSON payload for IV data point.
     * Maps to Supabase table columns: symbol, date, strike, dte, expiry_date,
     * put_iv, call_iv, underlying_price
     *
     * @param dataPoint IV data point
     * @return JSON string
     */
    private String buildJsonPayload(IVDataPoint dataPoint) {
        return String.format(
                "{\"symbol\":\"%s\",\"date\":\"%s\",\"strike\":%s,\"dte\":%d,\"expiry_date\":\"%s\",\"put_iv\":%s,\"call_iv\":%s,\"underlying_price\":%s}",
                dataPoint.getSymbol(),
                dataPoint.getCurrentDate().format(DATE_FORMATTER),
                dataPoint.getStrike(),
                dataPoint.getDte(),
                dataPoint.getExpiryDate(),
                dataPoint.getAtmPutIV(),
                dataPoint.getAtmCallIV(),
                dataPoint.getUnderlyingPrice());
    }

    /**
     * Saves a strategy execution result to Supabase.
     *
     * @param result Execution result to save
     * @throws IOException if API call fails
     */
    public void saveExecutionResult(ExecutionResult result) throws IOException {
        try {
            // Convert ExecutionResult to JSON
            String jsonResults = OBJECT_MAPPER.writeValueAsString(result.getResults());

            // Extract strategy IDs
            String[] strategyIds = result.getResults().stream()
                    .map(com.hemasundar.dto.StrategyResult::getStrategyId)
                    .toArray(String[]::new);

            // Build JSON payload
            String payload = String.format(
                    "{\"execution_id\":\"%s\",\"executed_at\":\"%s\",\"strategy_ids\":[\"%s\"],\"results\":%s,\"total_trades_found\":%d,\"execution_time_ms\":%d,\"telegram_sent\":%b}",
                    result.getExecutionId(),
                    result.getTimestamp(),
                    String.join("\",\"", strategyIds),
                    jsonResults,
                    result.getTotalTradesFound(),
                    result.getTotalExecutionTimeMs(),
                    result.isTelegramSent());

            String url = projectUrl + EXECUTIONS_PATH;

            Response response = RestAssured.given()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Successfully saved execution result: {} with {} trades",
                        result.getExecutionId(), result.getTotalTradesFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to save execution result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to save execution result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the latest strategy execution result from Supabase.
     *
     * @return Optional containing the latest execution result, or empty if none
     *         found
     * @throws IOException if API call fails
     */
    public Optional<ExecutionResult> getLatestExecutionResult() throws IOException {
        try {
            String url = projectUrl + EXECUTIONS_PATH + "?select=*&order=executed_at.desc&limit=1";

            Response response = RestAssured.given()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();

                // Supabase returns array, even for single result
                if (body.equals("[]") || body.isEmpty()) {
                    log.info("No execution results found in database");
                    return Optional.empty();
                }

                // Parse JSON array and get first element
                com.fasterxml.jackson.databind.JsonNode arrayNode = OBJECT_MAPPER.readTree(body);
                if (arrayNode.isArray() && arrayNode.size() > 0) {
                    com.fasterxml.jackson.databind.JsonNode resultNode = arrayNode.get(0);

                    // Parse back to ExecutionResult DTO
                    ExecutionResult executionResult = parseExecutionResult(resultNode);
                    log.info("Retrieved latest execution result: {} from {}",
                            executionResult.getExecutionId(), executionResult.getTimestamp());
                    return Optional.of(executionResult);
                }

                return Optional.empty();
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to retrieve execution results: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to retrieve execution result: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a JSON node into an ExecutionResult DTO.
     */
    private ExecutionResult parseExecutionResult(com.fasterxml.jackson.databind.JsonNode node) throws IOException {
        try {
            String executionId = node.get("execution_id").asText();
            String timestamp = node.get("executed_at").asText();
            int totalTrades = node.get("total_trades_found").asInt();
            long executionTime = node.get("execution_time_ms").asLong();
            boolean telegramSent = node.get("telegram_sent").asBoolean();

            // Parse results JSONB back to List<StrategyResult>
            com.fasterxml.jackson.databind.JsonNode resultsNode = node.get("results");
            java.util.List<com.hemasundar.dto.StrategyResult> strategyResults = OBJECT_MAPPER.readValue(
                    resultsNode.toString(),
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                            java.util.List.class,
                            com.hemasundar.dto.StrategyResult.class));

            return ExecutionResult.builder()
                    .executionId(executionId)
                    .timestamp(
                            java.time.LocalDateTime.parse(timestamp, java.time.format.DateTimeFormatter.ISO_DATE_TIME))
                    .results(strategyResults)
                    .totalTradesFound(totalTrades)
                    .totalExecutionTimeMs(executionTime)
                    .telegramSent(telegramSent)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse execution result: " + e.getMessage(), e);
        }
    }

    // ==================== Per-Strategy Result Persistence ====================

    private static final String LATEST_STRATEGY_RESULTS_PATH = "/rest/v1/latest_strategy_results";

    /**
     * Saves or updates the latest result for a single strategy.
     *
     * @param result Strategy result to save
     * @throws IOException if API call fails
     */
    public void saveStrategyResult(com.hemasundar.dto.StrategyResult result) throws IOException {
        try {
            // Convert trades to JSON
            String tradesJson = OBJECT_MAPPER.writeValueAsString(result.getTrades());

            // Current UTC timestamp for updated_at (DEFAULT only works on INSERT, not
            // UPSERT update)
            String updatedAt = java.time.Instant.now().toString();

            // Build JSON payload
            String payload = String.format(
                    "{\"strategy_id\":\"%s\",\"strategy_name\":\"%s\",\"execution_time_ms\":%d,\"trades_found\":%d,\"trades\":%s,\"updated_at\":\"%s\"}",
                    result.getStrategyId(),
                    result.getStrategyName(),
                    result.getExecutionTimeMs(),
                    result.getTradesFound(),
                    tradesJson,
                    updatedAt);

            String url = projectUrl + LATEST_STRATEGY_RESULTS_PATH;

            // Use UPSERT operation (insert or update based on primary key)
            Response response = RestAssured.given()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Successfully saved strategy result: {} with {} trades",
                        result.getStrategyId(), result.getTradesFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to save strategy result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to save strategy result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all latest strategy results from Supabase.
     *
     * @return List of all strategy results, empty list if none found
     * @throws IOException if API call fails
     */
    public java.util.List<com.hemasundar.dto.StrategyResult> getAllLatestStrategyResults() throws IOException {
        try {
            String url = projectUrl + LATEST_STRATEGY_RESULTS_PATH + "?select=*&order=updated_at.desc";

            Response response = RestAssured.given()
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();

                if (body.equals("[]") || body.isEmpty()) {
                    log.info("No strategy results found in database");
                    return java.util.Collections.emptyList();
                }

                // Parse JSON array
                com.fasterxml.jackson.databind.JsonNode arrayNode = OBJECT_MAPPER.readTree(body);
                java.util.List<com.hemasundar.dto.StrategyResult> results = new java.util.ArrayList<>();

                for (com.fasterxml.jackson.databind.JsonNode node : arrayNode) {
                    results.add(parseStrategyResult(node));
                }

                log.info("Retrieved {} strategy results from database", results.size());
                return results;
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to retrieve strategy results: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to retrieve strategy results: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a JSON node into a StrategyResult DTO.
     */
    private com.hemasundar.dto.StrategyResult parseStrategyResult(com.fasterxml.jackson.databind.JsonNode node)
            throws IOException {
        try {
            String strategyId = node.get("strategy_id").asText();
            String strategyName = node.get("strategy_name").asText();
            long executionTimeMs = node.get("execution_time_ms").asLong();
            int tradesFound = node.get("trades_found").asInt();

            // Parse trades JSONB back to List<Trade>
            com.fasterxml.jackson.databind.JsonNode tradesNode = node.get("trades");
            java.util.List<com.hemasundar.dto.Trade> trades = OBJECT_MAPPER.readValue(
                    tradesNode.toString(),
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(
                            java.util.List.class,
                            com.hemasundar.dto.Trade.class));

            // Parse updated_at timestamp
            String updatedAtStr = node.get("updated_at").asText();
            java.time.Instant updatedAt = java.time.Instant.parse(updatedAtStr);

            return com.hemasundar.dto.StrategyResult.builder()
                    .strategyId(strategyId)
                    .strategyName(strategyName)
                    .executionTimeMs(executionTimeMs)
                    .tradesFound(tradesFound)
                    .trades(trades)
                    .updatedAt(updatedAt)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse strategy result: " + e.getMessage(), e);
        }
    }
}
