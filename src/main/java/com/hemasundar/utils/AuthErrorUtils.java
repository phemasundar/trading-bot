package com.hemasundar.utils;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Utility class to identify authentication-related errors from exception messages.
 */
@Log4j2
@Component
public class AuthErrorUtils {

    /**
     * Checks if the given exception or its cause indicates an authentication failure.
     * Searches for common status codes (401, 403) and descriptive OAuth2 error strings.
     *
     * @param e The exception to analyze
     * @return true if it's an authentication error, false otherwise
     */
    public boolean isAuthError(Exception e) {
        if (e == null) return false;
        
        String msg = e.getMessage();
        if (msg == null) return false;
        
        String lower = msg.toLowerCase();
        
        // HTTP Status Codes
        boolean is401 = lower.contains("401");
        boolean is403 = lower.contains("403");
        
        // Common OAuth2 / Token / API Key error messages
        boolean isTokenError = lower.contains("access token") || 
                               lower.contains("refresh_token") ||
                               lower.contains("invalid_grant") ||
                               lower.contains("invalid_client") ||
                               lower.contains("unauthorized") ||
                               lower.contains("forbidden") ||
                               lower.contains("api key") ||
                               lower.contains("invalid key") ||
                               lower.contains("missing token");

        boolean result = is401 || is403 || isTokenError;
        
        if (result) {
            log.warn("Authentication error detected: {}", msg);
        }
        
        return result;
    }
}
