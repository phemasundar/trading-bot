package com.hemasundar.api;

import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for strategies YAML configuration.
 */
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConfigController {

    /**
     * Returns the strategies configuration formatted as JSON.
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            String yamlContent = FilePaths.readResource(FilePaths.strategiesConfig);
            String jsonContent = JavaUtils.convertYamlToJson(yamlContent);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(jsonContent);
        } catch (Exception e) {
            log.error("Failed to read config", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to read config: " + e.getMessage()));
        }
    }
}
