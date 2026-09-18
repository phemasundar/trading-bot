package com.hemasundar.api;

import com.hemasundar.dto.SecuritiesDataRequest;
import com.hemasundar.dto.SecuritiesGroupDto;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.technical.SecuritiesFilterConfig;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import com.hemasundar.utils.SecuritiesResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * REST controller for securities map lookups, securities group blocks,
 * filter configurations, and precalculated indicator data from Supabase.
 */
@Log4j2
@RestController
@RequestMapping("/api")
public class SecuritiesController {

    private final SecuritiesResolver securitiesResolver;
    private final SupabaseService supabaseService;

    @Autowired
    public SecuritiesController(SecuritiesResolver securitiesResolver, SupabaseService supabaseService) {
        this.securitiesResolver = securitiesResolver;
        this.supabaseService = supabaseService;
    }

    public SecuritiesController(SecuritiesResolver securitiesResolver) {
        this(securitiesResolver, null);
    }

    /**
     * Returns a map of securities file keys to their respective lists of securities.
     */
    @GetMapping("/securities")
    public ResponseEntity<?> getSecuritiesMaps() {
        try {
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(securitiesResolver.loadSecuritiesMaps());
        } catch (Exception e) {
            log.error("Failed to load securities map", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load securities map: " + e.getMessage()));
        }
    }

    /**
     * Returns the dynamically discovered list of securities file groups.
     */
    @GetMapping("/securities/groups")
    public ResponseEntity<?> getSecuritiesGroups() {
        try {
            List<SecuritiesGroupDto> groups = securitiesResolver.loadSecuritiesGroups();
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(groups);
        } catch (Exception e) {
            log.error("Failed to load securities groups", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load securities groups: " + e.getMessage()));
        }
    }

    /**
     * Returns the securities filter configuration from {@code securities-filters.yml}.
     */
    @GetMapping("/securities/filter-config")
    public ResponseEntity<?> getSecuritiesFilterConfig() {
        try {
            SecuritiesFilterConfig config = securitiesResolver.loadSecuritiesFiltersConfig();
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(config);
        } catch (Exception e) {
            log.error("Failed to load securities filter config", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to load securities filter config: " + e.getMessage()));
        }
    }

    /**
     * Fetches saved technical indicators for the requested symbols from Supabase.
     */
    @PostMapping("/securities/data")
    public ResponseEntity<?> getSecuritiesData(@RequestBody(required = false) SecuritiesDataRequest request) {
        try {
            if (request == null || request.getSymbols() == null || request.getSymbols().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyMap());
            }
            Map<String, ScreeningResult> results = supabaseService.getSecurityIndicatorsForSymbols(request.getSymbols());
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(results);
        } catch (Exception e) {
            log.error("Failed to load securities indicator data from Supabase", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve securities data: " + e.getMessage()));
        }
    }
}

