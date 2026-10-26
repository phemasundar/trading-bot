package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for handling custom strategy executions with Supabase.
 */
@Log4j2
@Component
public class CustomExecutionRepository {
    private static final String CUSTOM_EXECUTION_RESULTS_PATH = "/rest/v1/custom_execution_results";

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public CustomExecutionRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
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
                log.debug("Saved custom execution result: {} with {} trades",
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
     * Updates an existing custom execution result in Supabase by its database ID.
     *
     * @param id the record ID (primary key) to update
     * @param result the updated StrategyResult
     * @param securities list of securities
     * @throws IOException if the update request fails
     */
    public void updateCustomExecutionResult(Long id, StrategyResult result, List<String> securities) throws IOException {
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
            payloadNode.put("created_at", Instant.now().toString());

            String payload = mapper.writeValueAsString(payloadNode);
            String url = client.getUrl(CUSTOM_EXECUTION_RESULTS_PATH + "?id=eq." + id);

            Response response = client.request()
                    .header("Prefer", "return=representation")
                    .body(payload)
                    .patch(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 204) {
                String responseBody = response.getBody() != null ? response.getBody().asString() : "";
                if (responseBody.startsWith("[") && responseBody.trim().equals("[]")) {
                    log.warn("Custom execution result id={} not found for update, falling back to insert", id);
                    saveCustomExecutionResult(result, securities);
                } else {
                    log.debug("Updated custom execution result: id={} {} with {} trades",
                            id, result.getStrategyName(), result.getTradesFound());
                }
            } else {
                String errorBody = response.getBody() != null ? response.getBody().asString() : "";
                throw new IOException(String.format("Failed to update custom execution result id=%d: %d - %s. Body: %s",
                        id, statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to update custom execution result: " + e.getMessage(), e);
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

            log.debug("Retrieved {} custom execution results from database", results.size());
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
                log.debug("Deleted custom execution result with id={}", id);
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

            String createdAtStr = node.hasNonNull("created_at")
                    ? node.get("created_at").asText()
                    : (node.hasNonNull("executed_at") ? node.get("executed_at").asText() : Instant.now().toString());
            Instant createdAt;
            try {
                createdAt = Instant.parse(createdAtStr);
            } catch (Exception e) {
                createdAt = Instant.now();
            }

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
