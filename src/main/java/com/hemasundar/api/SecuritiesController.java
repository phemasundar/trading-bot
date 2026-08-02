package com.hemasundar.api;

import com.hemasundar.utils.SecuritiesResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for securities map lookups.
 */
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SecuritiesController {

    private final SecuritiesResolver securitiesResolver;

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
}
