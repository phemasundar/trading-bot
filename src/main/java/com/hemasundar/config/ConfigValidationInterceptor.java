package com.hemasundar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interceptor that validates application configuration requirements before request execution.
 * Intercepts execution endpoints, earnings calendar, and IV metrics to fail fast in a single place.
 */
@Component
@RequiredArgsConstructor
public class ConfigValidationInterceptor implements HandlerInterceptor {

    private final ApplicationConfigValidator configValidator;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/execute")) {
            List<String> errors = configValidator.validateConfig();
            if (!errors.isEmpty()) {
                sendError(response, String.join("; ", errors));
                return false;
            }
        } else if (uri.startsWith("/api/earnings-calendar")) {
            Optional<String> error = configValidator.validateEarningsCache();
            if (error.isPresent()) {
                sendError(response, error.get());
                return false;
            }
        } else if (uri.startsWith("/api/iv-rank")) {
            Optional<String> error = configValidator.validateIVData();
            if (error.isPresent()) {
                sendError(response, error.get());
                return false;
            }
        }

        return true;
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }
}
