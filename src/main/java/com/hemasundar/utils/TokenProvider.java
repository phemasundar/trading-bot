package com.hemasundar.utils;

import com.hemasundar.config.properties.SchwabConfig;
import com.hemasundar.pojos.RefreshToken;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring-managed access token provider for the Schwab API.
 * Handles token refresh with thread-safe caching.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TokenProvider {

    private final SchwabConfig schwabConfig;
    private final AtomicReference<TokenData> tokenReference = new AtomicReference<>();

    public void clearToken() {
        tokenReference.set(null);
    }

    public String getAccessToken() {
        TokenData current = tokenReference.get();

        // If no token or it's expired, trigger a refresh
        if (current == null || current.isExpired()) {
            synchronized (this) {
                // Re-check inside synchronized block to prevent multiple refreshes
                current = tokenReference.get();
                if (current == null || current.isExpired()) {
                    current = refreshFromApi();
                    tokenReference.set(current);
                }
            }
        }
        return current.accessToken();
    }

    private TokenData refreshFromApi() {
        log.info("Refreshing access token...");
        Response response = RestAssured.given()
                .auth()
                .preemptive()
                .basic(schwabConfig.getAppKey(), schwabConfig.getAppSecret())
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "refresh_token")
                .formParam("refresh_token", schwabConfig.getRefreshToken())
                .when()
                .post("https://api.schwabapi.com/v1/oauth/token");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching Access token: " + response.statusLine()
                    + "\n"
                    + response.asPrettyString());
        } else {
            log.info("Refresh token API successful");
        }

        RefreshToken refreshToken = new ObjectMapper().readValue(response.asPrettyString(), RefreshToken.class);
        int newExpiresIn = refreshToken.getExpires_in() - 300;
        Instant newExpiry = Instant.now().plusSeconds(newExpiresIn);

        return new TokenData(refreshToken.getAccess_token(), newExpiry);
    }

    /**
     * Immutable carrier for token data.
     */
    public record TokenData(String accessToken, Instant expiryTime) {
        public boolean isExpired() {
            return Instant.now().plusSeconds(30).isAfter(expiryTime);
        }
    }
}
