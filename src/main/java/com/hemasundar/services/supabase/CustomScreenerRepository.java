package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for handling custom manual screener executions with Supabase.
 */
@Log4j2
@Component
public class CustomScreenerRepository {
    private static final String CUSTOM_SCREENER_RESULTS_PATH = "/rest/v1/custom_screener_results";

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public CustomScreenerRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves a custom screener execution result to the dedicated table.
     *
     * @param result       the screener execution result
     * @param securities   the list of securities that were scanned
     * @param requestParams the original request parameters (serialized for Load Filters)
     */
    public void saveCustomScreenerResult(ScreenerExecutionResult result, List<String> securities, Map<String, Object> requestParams) throws IOException {
        try {
            String resultsJson = mapper.writeValueAsString(result.getResults());

            ObjectNode payloadNode = mapper.createObjectNode();
            payloadNode.put("screener_name", result.getScreenerName());
            payloadNode.put("execution_time_ms", result.getExecutionTimeMs());
            payloadNode.put("results_found", result.getResultsFound());
            payloadNode.set("results", mapper.readTree(resultsJson));

            // Persist the original request parameters so the UI can offer "Load Filters".
            if (requestParams != null && !requestParams.isEmpty()) {
                payloadNode.set("request_params", mapper.convertValue(requestParams, com.fasterxml.jackson.databind.node.ObjectNode.class));
            }
            
            ArrayNode securitiesArray = mapper.createArrayNode();
            if (securities != null) {
                securities.forEach(securitiesArray::add);
            }
            payloadNode.set("securities", securitiesArray);

            String payload = mapper.writeValueAsString(payloadNode);
            String url = client.getUrl(CUSTOM_SCREENER_RESULTS_PATH);

            Response response = client.request()
                    .header("Prefer", "return=minimal")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Saved custom screener result: {} with {} results",
                        result.getScreenerName(), result.getResultsFound());
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to save custom screener result: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save custom screener result: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the most recent custom screener execution results.
     */
    public List<ScreenerExecutionResult> getRecentCustomScreenerExecutions(int limit) throws IOException {
        try {
            String url = client.getUrl(CUSTOM_SCREENER_RESULTS_PATH + "?order=created_at.desc&limit=" + limit);

            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Failed to retrieve custom screener executions: " + statusCode);
            }

            String body = response.getBody().asString();
            JsonNode rootArray = mapper.readTree(body);

            List<ScreenerExecutionResult> results = new ArrayList<>();
            for (JsonNode node : rootArray) {
                results.add(parseCustomScreenerResult(node));
            }

            log.info("Retrieved {} custom screener execution results from database", results.size());
            return results;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve custom screener executions: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a custom screener execution result by its database ID.
     */
    public void deleteCustomScreenerExecution(String id) throws IOException {
        try {
            String url = client.getUrl(CUSTOM_SCREENER_RESULTS_PATH + "?id=eq." + id);
            Response response = client.request().delete(url);
            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 204) {
                log.info("Deleted custom screener result with id={}", id);
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format(
                        "Failed to delete custom screener result id=%s: %d - %s. Body: %s",
                        id, statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to delete custom screener result: " + e.getMessage(), e);
        }
    }

    private ScreenerExecutionResult parseCustomScreenerResult(JsonNode node) throws IOException {
        try {
            String screenerName = node.get("screener_name").asText();
            long executionTimeMs = node.get("execution_time_ms").asLong();
            int resultsFound = node.get("results_found").asInt();

            JsonNode resultsNode = node.get("results");
            List<ScreeningResult> results = mapper.readValue(
                    resultsNode.toString(),
                    mapper.getTypeFactory().constructCollectionType(List.class, ScreeningResult.class));

            String createdAtStr = node.get("created_at").asText();
            Instant createdAt = Instant.parse(createdAtStr);

            // Parse stored request_params (may be absent for older rows)
            Map<String, Object> requestParams = null;
            JsonNode requestParamsNode = node.get("request_params");
            if (requestParamsNode != null && !requestParamsNode.isNull()) {
                requestParams = mapper.convertValue(requestParamsNode,
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            }

            return ScreenerExecutionResult.builder()
                    .screenerId(String.valueOf(node.get("id").asLong()))
                    .screenerName(screenerName)
                    .executionTimeMs(executionTimeMs)
                    .resultsFound(resultsFound)
                    .results(results)
                    .updatedAt(createdAt)
                    .requestParams(requestParams)
                    .build();
        } catch (Exception e) {
            throw new IOException("Failed to parse custom screener result: " + e.getMessage(), e);
        }
    }
}
