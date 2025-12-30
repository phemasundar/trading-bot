package com.hemasundar;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public enum TokenProvider {
    INSTANCE; // The single global instance
    // AtomicReference ensures all threads see the most up-to-date token object
    private final AtomicReference<TokenData> tokenReference = new AtomicReference<>();

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
        System.out.println("Getting new access token...");
// 4. REST-ASSURED REQUEST
        Response response = RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConfig.getInstance().appKey(), TestConfig.getInstance().ppSecret()) // Automatically handles Base64 encoding
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "refresh_token")
                .formParam("refresh_token", TestConfig.getInstance().refreshToken())
                .when()
//                .log().all()
                .post("https://api.schwabapi.com/v1/oauth/token");

        System.out.println("Response Code for Access Token API: " + response.statusCode());
//        System.out.println("Response Body: \n" + response.asPrettyString());
        RefreshToken refreshToken = new ObjectMapper().readValue(response.asPrettyString(), RefreshToken.class);
        // Setting expiry 5 min earlier than the actual expiry (just to be on safer side)
        int newExpiresIn = refreshToken.getExpires_in() - 300;
        Instant newExpiry = Instant.now().plusSeconds(newExpiresIn);

        return new TokenData(refreshToken.getAccess_token(), newExpiry);
    }

    /**
     * Immutable carrier for token data.
     */
    public record TokenData(String accessToken, Instant expiryTime) {
        // Check if the token is expired or about to expire (e.g., within 30 seconds)
        public boolean isExpired() {
            return Instant.now().plusSeconds(30).isAfter(expiryTime);
        }
    }

}

