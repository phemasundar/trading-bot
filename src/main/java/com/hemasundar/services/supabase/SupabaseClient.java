package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

import com.hemasundar.config.properties.SupabaseConfig;
import org.springframework.stereotype.Component;

/**
 * Core client for interacting with Supabase REST API.
 * Handles authentication, base URL configuration, and provides ObjectMapper.
 */
@Log4j2
@Component
public class SupabaseClient {
    private final String projectUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public SupabaseClient(SupabaseConfig supabaseConfig) {
        String url = supabaseConfig.getUrl();
        String key = supabaseConfig.getServiceRoleKey();

        if (url == null || url.isEmpty()) {
            log.warn("Supabase project URL is null or empty. Supabase features may be disabled.");
        }
        if (key == null || key.isEmpty()) {
            log.warn("Supabase API key is null or empty. Supabase features may be disabled.");
        }

        this.projectUrl = (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
        this.apiKey = key;
        
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        log.info("Supabase Client initialized for project: {}", this.projectUrl);
    }

    /**
     * Provides a pre-configured RequestSpecification with authentication headers.
     * @return RequestSpecification
     */
    public RequestSpecification request() {
        return RestAssured.given()
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json");
    }

    /**
     * Helper to construct full URL for an endpoint path.
     * @param path endpoint path (e.g., /rest/v1/iv_data)
     * @return full URL
     */
    public String getUrl(String path) {
        return projectUrl + path;
    }

    /**
     * Provides the configured ObjectMapper instance.
     * @return ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Tests connection to Supabase by making a simple GET request.
     *
     * @return true if connection is successful
     * @throws IOException if connection fails
     */
    public boolean testConnection() throws IOException {
        String url = getUrl("/rest/v1/iv_data?select=id&limit=1");

        try {
            Response response = request().get(url);

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
}
