package com.hemasundar.api;

import com.hemasundar.services.StrategyExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for filter log inspection and clearing.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LogController {

    private final StrategyExecutionService executionService;

    /**
     * Returns all filter-stage log entries captured during the current or most recent execution.
     */
    @GetMapping("/filter-logs")
    public ResponseEntity<?> getFilterLogs() {
        return ResponseEntity.ok(executionService.getFilterLogs());
    }

    /**
     * Clears the in-memory filter log store on demand.
     */
    @PostMapping("/filter-logs/clear")
    public ResponseEntity<?> clearFilterLogs() {
        executionService.clearFilterLogs();
        return ResponseEntity.ok(Map.of("cleared", true));
    }
}
