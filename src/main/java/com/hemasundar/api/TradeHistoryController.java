package com.hemasundar.api;

import com.hemasundar.dto.Trade;
import com.hemasundar.services.history.TradeHistoryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * REST controller for strategy historical trade lookups and similarity matching.
 */
@Log4j2
@RestController
@RequestMapping("/api/strategies/history")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradeHistoryService tradeHistoryService;

    /**
     * Request payload for fetching similar historical trades.
     */
    @Data
    public static class SimilarTradesRequest {
        private String strategyId;
        private Trade trade;
        private Integer limit;
    }

    /**
     * Fetches historical trades similar to the provided trade.
     *
     * @param request SimilarTradesRequest containing strategyId and target Trade
     * @return List of matching historical Trade DTOs
     */
    @PostMapping("/similar-trades")
    public ResponseEntity<?> getSimilarTrades(@RequestBody SimilarTradesRequest request) {
        if (request == null || request.getTrade() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Request must include valid trade details"));
        }

        try {
            int maxResults = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 20;
            List<Trade> matches = tradeHistoryService.findSimilarTrades(
                    request.getStrategyId(),
                    request.getTrade(),
                    maxResults
            );

            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(matches);
        } catch (Exception e) {
            log.error("Error retrieving similar historical trades: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve similar trades: " + e.getMessage()));
        }
    }
}
