package com.hemasundar.services;

import com.hemasundar.pojos.IVDataPoint;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Service to interact with Supabase REST API for storing IV data.
 * Uses RestAssured client to make HTTP requests to Supabase's PostgREST API.
 */
@Log4j2
public class SupabaseService {

    private static final String REST_API_PATH = "/rest/v1/iv_data";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

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
        String url = projectUrl + REST_API_PATH + "?select=id&limit=1";

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
        String url = projectUrl + REST_API_PATH + "?on_conflict=symbol,date";

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
}
