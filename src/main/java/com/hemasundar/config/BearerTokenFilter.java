package com.hemasundar.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

/**
 * Simple Bearer token authentication filter for /api/* endpoints.
 * Static files (/, /static/*) are public — no auth required.
 *
 * Token is configured via the "api.bearer.token" property or
 * API_BEARER_TOKEN environment variable.
 *
 * In the "production" profile, a token MUST be configured — otherwise
 * all /api/* requests are rejected with 503.
 */
@Log4j2
@Component
@Order(1)
public class BearerTokenFilter implements Filter {

    @Value("${api.bearer.token:#{null}}")
    private String expectedToken;

    private final boolean isProduction;

    public BearerTokenFilter(Environment env) {
        this.isProduction = Arrays.asList(env.getActiveProfiles()).contains("production");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // Only protect /api/* endpoints
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // If no token is configured...
        if (expectedToken == null || expectedToken.isBlank()) {
            if (isProduction) {
                // PRODUCTION: reject — misconfiguration, token is required
                log.error("API_BEARER_TOKEN is NOT configured in production! Rejecting request to {}", path);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter()
                        .write("{\"error\": \"API authentication is not configured. Set API_BEARER_TOKEN.\"}");
                return;
            }
            // LOCAL DEV: allow without auth for convenience
            log.debug("No API bearer token configured — skipping auth for {}", path);
            chain.doFilter(request, response);
            return;
        }

        // Validate Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (!expectedToken.equals(token)) {
            log.warn("Invalid bearer token from {}", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid bearer token\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
