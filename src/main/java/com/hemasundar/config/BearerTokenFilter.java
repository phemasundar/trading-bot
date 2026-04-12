package com.hemasundar.config;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.hemasundar.config.properties.SecurityConfig;
import com.hemasundar.config.properties.SupabaseConfig;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servlet filter that protects all /api/* endpoints using Supabase-issued JWTs.
 *
 * <p>Verification uses the Supabase JWKS endpoint so no shared secret needs
 * to be managed. The public keys are cached for 24 hours with rate-limiting.
 *
 * <p>Local dev bypass: when {@code supabase.url} is not set, all requests
 * pass through without authentication.
 */
@Log4j2
@Component
@Order(1)
public class BearerTokenFilter implements Filter {

    /** Paths exempt from authentication — must be accessible before login. */
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/config");

    private final SupabaseConfig supabaseConfig;
    private final SecurityConfig securityConfig;
    private final boolean isProduction;

    /** Lazily initialized — null when supabase.url is not configured (local dev). */
    private JwkProvider jwkProvider;

    public BearerTokenFilter(Environment env, SupabaseConfig supabaseConfig, SecurityConfig securityConfig) {
        this.isProduction = Arrays.asList(env.getActiveProfiles()).contains("production");
        this.supabaseConfig = supabaseConfig;
        this.securityConfig = securityConfig;
    }

    /** Allows test code to inject a mock JwkProvider without network access. */
    void setJwkProvider(JwkProvider jwkProvider) {
        this.jwkProvider = jwkProvider;
    }

    @PostConstruct
    public void initJwkProvider() {
        String supabaseUrl = supabaseConfig.getUrl();
        if (supabaseUrl != null && !supabaseUrl.isBlank()) {
            try {
                String baseUrl  = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
                String jwksUrl  = baseUrl + "/auth/v1/.well-known/jwks.json";
                this.jwkProvider = new JwkProviderBuilder(new URL(jwksUrl))
                        .cached(10, 24, TimeUnit.HOURS)
                        .rateLimited(10, 1, TimeUnit.MINUTES)
                        .build();
                log.info("JWKS-based JWT verification configured: {}", jwksUrl);
            } catch (Exception e) {
                log.error("Failed to initialize JWKS provider. JWT verification will fail.", e);
            }
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();

        // Non-API routes — static files, login page, etc.
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // Public API paths that must work before login
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        // No JWKS provider — local dev bypass or misconfigured production
        if (jwkProvider == null) {
            if (isProduction) {
                log.error("JWKS provider not configured in production! Ensure SUPABASE_URL is set.");
                sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Authentication is not configured on the server.");
                return;
            }
            log.debug("No JWKS provider — bypassing auth for local dev on: {}", path);
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header.");
            return;
        }

        String token = authHeader.substring(7).trim();

        // [DEBUG] Check token structure since we are getting "> 3 parts" errors
        long dotCount = token.chars().filter(ch -> ch == '.').count();
        log.info("[DEBUG] Received Token Length: {}, Dot count: {}", token.length(), dotCount);

        try {
            DecodedJWT decoded  = JWT.decode(token);
            Jwk        jwk      = jwkProvider.get(decoded.getKeyId());
            Algorithm  algorithm = resolveAlgorithm(jwk);

            JWT.require(algorithm).build().verify(token);

            // Email allowlist — optional gate for invite-only access
            String email = decoded.getClaim("email").asString();
            String allowedEmailsConfig = securityConfig.getEmails();
            if (allowedEmailsConfig != null && !allowedEmailsConfig.isBlank()) {
                Set<String> allowed = Arrays.stream(allowedEmailsConfig.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

                if (email == null || !allowed.contains(email.toLowerCase())) {
                    log.info("[AUTH ERROR] Email '{}' not in allowlist — denying {}", email, path);
                    sendError(response, HttpServletResponse.SC_FORBIDDEN,
                            "User not authorized. Contact the administrator.");
                    return;
                }
            }

            log.info("[AUTH SUCCESS] JWT verified for {} on {}", email, path);
            chain.doFilter(request, response);

        } catch (JWTVerificationException e) {
            log.info("[AUTH ERROR] JWT verification failed for {}: {}", path, e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired token. Please sign in again.");
        } catch (Exception e) {
            log.info("[AUTH ERROR] Unexpected error during JWT verification for {}: {}", path, e.getMessage(), e);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token verification failed.");
        }
    }

    /**
     * Resolves the JWT {@link Algorithm} from a JWK.
     * Supports both ECC P-256 (ES256) and RSA (RS256) keys so the filter
     * works regardless of future Supabase key rotations.
     */
    private Algorithm resolveAlgorithm(Jwk jwk) throws Exception {
        return switch (jwk.getPublicKey().getAlgorithm()) {
            case "EC"  -> Algorithm.ECDSA256((ECPublicKey)  jwk.getPublicKey(), null);
            case "RSA" -> Algorithm.RSA256((RSAPublicKey)   jwk.getPublicKey(), null);
            default    -> throw new IllegalArgumentException(
                    "Unsupported JWK algorithm: " + jwk.getPublicKey().getAlgorithm());
        };
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
