package com.hemasundar.api;

import com.hemasundar.config.properties.SupabaseConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hemasundar.config.RolesConfigLoader;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * REST controller for public auth configuration endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthConfigController {

    private final SupabaseConfig supabaseConfig;
    private final RolesConfigLoader rolesConfigLoader;

    /**
     * Returns Supabase project URL and anon key so the frontend can
     * initialize the Supabase JS client for OAuth login.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getAuthConfig() {
        return ResponseEntity.ok(Map.of(
                "supabaseUrl", supabaseConfig.getUrl() != null ? supabaseConfig.getUrl() : "",
                "supabaseAnonKey", supabaseConfig.getAnonKey() != null ? supabaseConfig.getAnonKey() : ""));
    }

    /**
     * Returns the authenticated user's role and allowed pages.
     * Role is resolved by {@link com.hemasundar.config.BearerTokenFilter} and set as a request attribute.
     */
    @GetMapping("/role")
    public ResponseEntity<?> getUserRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        List<String> allowedPages = rolesConfigLoader.getReadonlyAllowedPages();
        return ResponseEntity.ok(Map.of(
                "role", role != null ? role : "ADMIN",
                "allowedPages", allowedPages));
    }
}
