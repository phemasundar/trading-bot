package com.hemasundar.api;

import com.hemasundar.dto.OptionGreekDto;
import com.hemasundar.dto.StrategyDescriptionDto;
import com.hemasundar.services.LearningCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Learning Center strategy guides, descriptions, and Option Greeks.
 */
@Log4j2
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningCenterController {

    private final LearningCenterService learningCenterService;

    /**
     * Returns all option strategy guides available in the Learning Center.
     */
    @GetMapping("/strategies")
    public ResponseEntity<List<StrategyDescriptionDto>> getOptionStrategies() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(learningCenterService.getOptionStrategies());
    }

    /**
     * Returns details for a specific strategy by ID or filename.
     */
    @GetMapping("/strategies/{id}")
    public ResponseEntity<?> getStrategy(@PathVariable String id) {
        return learningCenterService.getStrategyById(id)
                .map(dto -> ResponseEntity.ok()
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .body((Object) dto))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns all Option Greek guides available in the Learning Center.
     */
    @GetMapping("/greeks")
    public ResponseEntity<List<OptionGreekDto>> getOptionGreeks() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .body(learningCenterService.getOptionGreeks());
    }

    /**
     * Returns details for a specific Option Greek by ID or filename.
     */
    @GetMapping("/greeks/{id}")
    public ResponseEntity<?> getGreek(@PathVariable String id) {
        return learningCenterService.getGreekById(id)
                .map(dto -> ResponseEntity.ok()
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .body((Object) dto))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
