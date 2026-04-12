package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for handling Options Strategy execution results with Supabase.
 */
@Log4j2
@Component
public class StrategyResultRepository {
    private static final String LATEST_STRATEGY_RESULTS_PATH = "/rest/v1/latest_strategy_results";

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public StrategyResultRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves or updates the latest result for a single strategy.
     *
     * @param result Strategy result to save
     * @throws IOException if API call fails
     */
    public void saveStrategyResult(StrategyResult result) throws IOException {
        try {
            String tradesJson = mapper.writeValueAsString(result.getTrades());
            String updatedAt = Instant.now().toString();

            ObjectNode payloadNode = mapper.createObjectNode();
            payloadNode.put("strategy_id", result.getStrategyId());
            payloadNode.put("strategy_name", result.getStrategyName());
            payloadNode.put("execution_time_ms", result.getExecutionTimeMs());
            payloadNode.put("trades_found", result.getTradesFound());
            payloadNode.set("trades", mapper.readTree(tradesJson));
            payloadNode.put("updated_at", updatedAt);

            if (result.getFilterConfig() != null && !result.getFilterConfig().isEmpty()) {
                payloadNode.set("filter_config", mapper.readTree(result.getFilterConfig()));
            }

            String payload = mapper.writeValueAsString(payloadNode);
            String url = client.getUrl(LATEST_STRATEGY_RESULTS_PATH);

            Response response = client.request()
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
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save strategy result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all latest strategy results from Supabase.
     */
    public List<StrategyResult> getAllLatestStrategyResults() throws IOException {
        try {
            String url = client.getUrl(LATEST_STRATEGY_RESULTS_PATH + "?select=*&order=updated_at.desc");

            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();

                if (body.equals("[]") || body.isEmpty()) {
                    log.info("No strategy results found in database");
                    return Collections.emptyList();
                }

                JsonNode arrayNode = mapper.readTree(body);
                List<StrategyResult> results = new ArrayList<>();

                for (JsonNode node : arrayNode) {
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
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve strategy results: " + e.getMessage(), e);
        }
    }

    private StrategyResult parseStrategyResult(JsonNode node) throws IOException {
        try {
            String strategyId = node.get("strategy_id").asText();
            String strategyName = node.get("strategy_name").asText();
            long executionTimeMs = node.get("execution_time_ms").asLong();
            int tradesFound = node.get("trades_found").asInt();

            JsonNode tradesNode = node.get("trades");
            List<Trade> trades = mapper.readValue(
                    tradesNode.toString(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Trade.class));

            String updatedAtStr = node.get("updated_at").asText();
            Instant updatedAt = Instant.parse(updatedAtStr);

            String filterConfig = null;
            JsonNode filterNode = node.get("filter_config");
            if (filterNode != null && !filterNode.isNull()) {
                filterConfig = filterNode.toString();
            }

            return StrategyResult.builder()
                    .strategyId(strategyId)
                    .strategyName(strategyName)
                    .executionTimeMs(executionTimeMs)
                    .tradesFound(tradesFound)
                    .trades(trades)
                    .updatedAt(updatedAt)
                    .filterConfig(filterConfig)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse strategy result: " + e.getMessage(), e);
        }
    }
}
