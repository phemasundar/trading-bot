package com.hemasundar.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Request body for POST /api/execute/custom-screener.
 * Allows the UI to run a one-off technical screener with custom conditions
 * without modifying strategies-config.json.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CustomScreenerRequest {

    /** The screener type enum name, e.g. "RSI_BB_BULLISH_CROSSOVER". */
    String screenerType;

    /** Optional friendly label for the result card. */
    String alias;

    /** Named securities file(s), comma-separated, e.g. "portfolio, top100". */
    String securitiesFile;

    /** Inline ticker list, comma-separated, e.g. "AAPL, MSFT". */
    String securities;

    // ── Technical Filters ──────────────────────────────────────────

    /** 
     * Flexible map of technical filters matching the UI payload structure.
     * e.g., { "RSI": { "condition": { "type": "OVERSOLD" } }, "VOLUME": { "conditions": [ ">= 1000000" ] } }
     */
    java.util.Map<String, Object> technicalFilters;
}
