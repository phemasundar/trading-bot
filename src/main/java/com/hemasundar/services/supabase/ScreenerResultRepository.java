package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for handling Technical Screener execution results with Supabase.
 */
@Log4j2
public class ScreenerResultRepository {
    private static final String LATEST_SCREENER_RESULTS_PATH = "/rest/v1/latest_screener_results";

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public ScreenerResultRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves or updates the latest result for a single technical screener.
     *
     * @param result Screener execution result to save
     * @throws IOException if API call fails
     */
    public void saveScreenerResult(ScreenerExecutionResult result) throws IOException {
        try {
            String resultsJson = mapper.writeValueAsString(result.getResults());
            String updatedAt = Instant.now().toString();

            ObjectNode payloadNode = mapper.createObjectNode();
            payloadNode.put("screener_id", result.getScreenerId());
            payloadNode.put("screener_name", result.getScreenerName());
            payloadNode.put("execution_time_ms", result.getExecutionTimeMs());
            payloadNode.put("results_found", result.getResultsFound());
            payloadNode.set("results", mapper.readTree(resultsJson));
            payloadNode.put("updated_at", updatedAt);

            String payload = mapper.writeValueAsString(payloadNode);
            String url = client.getUrl(LATEST_SCREENER_RESULTS_PATH);

            Response response = client.request()
                    .header("Prefer", "resolution=merge-duplicates")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Successfully saved screener result: {} finding {} stocks",
                        result.getScreenerId(), result.getResultsFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to save screener result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save screener result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all latest screener results from Supabase.
     */
    public List<ScreenerExecutionResult> getAllLatestScreenerResults() throws IOException {
        try {
            String url = client.getUrl(LATEST_SCREENER_RESULTS_PATH + "?select=*&order=updated_at.desc");

            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();

                if (body.equals("[]") || body.isEmpty()) {
                    log.info("No screener results found in database");
                    return Collections.emptyList();
                }

                JsonNode arrayNode = mapper.readTree(body);
                List<ScreenerExecutionResult> results = new ArrayList<>();

                for (JsonNode node : arrayNode) {
                    results.add(parseScreenerResult(node));
                }

                log.info("Retrieved {} screener results from database", results.size());
                return results;
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to retrieve screener results: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve screener results: " + e.getMessage(), e);
        }
    }

    private ScreenerExecutionResult parseScreenerResult(JsonNode node) throws IOException {
        try {
            String screenerId = node.get("screener_id").asText();
            String screenerName = node.get("screener_name").asText();
            long executionTimeMs = node.get("execution_time_ms").asLong();
            int resultsFound = node.get("results_found").asInt();

            JsonNode resultsNode = node.get("results");
            List<ScreeningResult> results = mapper.readValue(
                    resultsNode.toString(),
                    mapper.getTypeFactory().constructCollectionType(List.class, ScreeningResult.class));

            String updatedAtStr = node.get("updated_at").asText();
            Instant updatedAt = Instant.parse(updatedAtStr);

            return ScreenerExecutionResult.builder()
                    .screenerId(screenerId)
                    .screenerName(screenerName)
                    .executionTimeMs(executionTimeMs)
                    .resultsFound(resultsFound)
                    .results(results)
                    .updatedAt(updatedAt)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse screener result: " + e.getMessage(), e);
        }
    }
}
