package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for handling custom executions and historical execution results with Supabase.
 */
@Log4j2
@Component
public class CustomExecutionRepository {
    private static final String EXECUTIONS_PATH = "/rest/v1/strategy_executions";
    private static final String CUSTOM_EXECUTION_RESULTS_PATH = "/rest/v1/custom_execution_results";

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public CustomExecutionRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves a full strategy execution batch result to Supabase.
     */
    public void saveExecutionResult(ExecutionResult result) throws IOException {
        try {
            String jsonResults = mapper.writeValueAsString(result.getResults());
            String[] strategyIds = result.getResults().stream()
                    .map(StrategyResult::getStrategyId)
                    .toArray(String[]::new);

            String payload = String.format(
                    "{\"execution_id\":\"%s\",\"executed_at\":\"%s\",\"strategy_ids\":[\"%s\"],\"results\":%s,\"total_trades_found\":%d,\"execution_time_ms\":%d,\"telegram_sent\":%b}",
                    result.getExecutionId(),
                    result.getTimestamp(),
                    String.join("\",\"", strategyIds),
                    jsonResults,
                    result.getTotalTradesFound(),
                    result.getTotalExecutionTimeMs(),
                    result.isTelegramSent());

            String url = client.getUrl(EXECUTIONS_PATH);

            Response response = client.request()
                    .header("Prefer", "return=minimal")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Successfully saved execution result: {} with {} trades",
                        result.getExecutionId(), result.getTotalTradesFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to save execution result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save execution result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the latest strategy execution result.
     */
    public Optional<ExecutionResult> getLatestExecutionResult() throws IOException {
        try {
            String url = client.getUrl(EXECUTIONS_PATH + "?select=*&order=executed_at.desc&limit=1");
            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();
                if (body.equals("[]") || body.isEmpty()) {
                    log.info("No execution results found in database");
                    return Optional.empty();
                }

                JsonNode arrayNode = mapper.readTree(body);
                if (arrayNode.isArray() && arrayNode.size() > 0) {
                    JsonNode resultNode = arrayNode.get(0);
                    ExecutionResult executionResult = parseExecutionResult(resultNode);
                    log.info("Retrieved latest execution result: {} from {}",
                            executionResult.getExecutionId(), executionResult.getTimestamp());
                    return Optional.of(executionResult);
                }
                return Optional.empty();
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to retrieve execution results: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve execution result: " + e.getMessage(), e);
        }
    }

    private ExecutionResult parseExecutionResult(JsonNode node) throws IOException {
        try {
            String executionId = node.get("execution_id").asText();
            String timestamp = node.get("executed_at").asText();
            int totalTrades = node.get("total_trades_found").asInt();
            long executionTime = node.get("execution_time_ms").asLong();
            boolean telegramSent = node.get("telegram_sent").asBoolean();

            JsonNode resultsNode = node.get("results");
            List<StrategyResult> strategyResults = mapper.readValue(
                    resultsNode.toString(),
                    mapper.getTypeFactory().constructCollectionType(List.class, StrategyResult.class));

            return ExecutionResult.builder()
                    .executionId(executionId)
                    .timestamp(LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME))
                    .results(strategyResults)
                    .totalTradesFound(totalTrades)
                    .totalExecutionTimeMs(executionTime)
                    .telegramSent(telegramSent)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse execution result: " + e.getMessage(), e);
        }
    }

    /**
     * Saves a custom execution result.
     */
    public void saveCustomExecutionResult(StrategyResult result, List<String> securities) throws IOException {
        try {
            String tradesJson = mapper.writeValueAsString(result.getTrades());

            ObjectNode payloadNode = mapper.createObjectNode();
            payloadNode.put("strategy_name", result.getStrategyName());
            payloadNode.put("execution_time_ms", result.getExecutionTimeMs());
            payloadNode.put("trades_found", result.getTradesFound());
            payloadNode.set("trades", mapper.readTree(tradesJson));

            if (result.getFilterConfig() != null && !result.getFilterConfig().isEmpty()) {
                payloadNode.set("filter_config", mapper.readTree(result.getFilterConfig()));
            }

            ArrayNode securitiesArray = mapper.createArrayNode();
            if (securities != null) {
                securities.forEach(securitiesArray::add);
            }
            payloadNode.set("securities", securitiesArray);

            String payload = mapper.writeValueAsString(payloadNode);
            String url = client.getUrl(CUSTOM_EXECUTION_RESULTS_PATH);

            Response response = client.request()
                    .header("Prefer", "return=minimal")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Saved custom execution result: {} with {} trades",
                        result.getStrategyName(), result.getTradesFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to save custom execution result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save custom execution result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the most recent custom execution results.
     */
    public List<StrategyResult> getRecentCustomExecutions(int limit) throws IOException {
        try {
            String url = client.getUrl(CUSTOM_EXECUTION_RESULTS_PATH + "?order=created_at.desc&limit=" + limit);

            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Failed to retrieve custom executions: " + statusCode);
            }

            String body = response.getBody().asString();
            JsonNode rootArray = mapper.readTree(body);

            List<StrategyResult> results = new ArrayList<>();
            for (JsonNode node : rootArray) {
                results.add(parseCustomExecutionResult(node));
            }

            log.info("Retrieved {} custom execution results from database", results.size());
            return results;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve custom executions: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a custom execution result by its database ID.
     *
     * @param id the record ID (primary key) to delete
     * @throws IOException if the delete request fails
     */
    public void deleteCustomExecution(String id) throws IOException {
        try {
            String url = client.getUrl(CUSTOM_EXECUTION_RESULTS_PATH + "?id=eq." + id);
            Response response = client.request().delete(url);
            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 204) {
                log.info("Deleted custom execution result with id={}", id);
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to delete custom execution result id=%s: %d - %s. Body: %s",
                        id, statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to delete custom execution result: " + e.getMessage(), e);
        }
    }

    private StrategyResult parseCustomExecutionResult(JsonNode node) throws IOException {
        try {
            String strategyName = node.get("strategy_name").asText();
            long executionTimeMs = node.get("execution_time_ms").asLong();
            int tradesFound = node.get("trades_found").asInt();

            JsonNode tradesNode = node.get("trades");
            List<Trade> trades = mapper.readValue(
                    tradesNode.toString(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Trade.class));

            String createdAtStr = node.get("created_at").asText();
            Instant createdAt = Instant.parse(createdAtStr);

            String filterConfig = null;
            JsonNode filterNode = node.get("filter_config");
            if (filterNode != null && !filterNode.isNull()) {
                filterConfig = filterNode.toString();
            }

            return StrategyResult.builder()
                    .strategyId(String.valueOf(node.get("id").asLong()))
                    .strategyName(strategyName)
                    .executionTimeMs(executionTimeMs)
                    .tradesFound(tradesFound)
                    .trades(trades)
                    .updatedAt(createdAt)
                    .filterConfig(filterConfig)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse custom execution result: " + e.getMessage(), e);
        }
    }
}
