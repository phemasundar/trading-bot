package com.hemasundar.dto;

import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Request body for POST /api/execute/custom-screener.
 * Allows the UI to run a one-off technical screener with custom conditions
 * without modifying strategies-config.json.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class CustomScreenerRequest {

    /** The screener type enum name, e.g. "RSI_BB_BULLISH_CROSSOVER". */
    String screenerType;

    /** Optional friendly label for the result card. */
    String alias;

    /** Named securities file(s), comma-separated, e.g. "portfolio, top100". */
    String securitiesFile;

    /** Inline ticker list, comma-separated, e.g. "AAPL, MSFT". */
    String securities;

    // ── TechFilterConditions fields ──────────────────────────────────────────

    /** RSI condition: OVERSOLD | OVERBOUGHT | BULLISH_CROSSOVER | BEARISH_CROSSOVER */
    String rsiCondition;

    /** Bollinger condition: LOWER_BAND | UPPER_BAND */
    String bollingerCondition;

    /** Minimum volume threshold (shares). */
    Long minVolume;

    Boolean requirePriceBelowMA20;
    Boolean requirePriceAboveMA20;
    Boolean requirePriceBelowMA50;
    Boolean requirePriceAboveMA50;
    Boolean requirePriceBelowMA100;
    Boolean requirePriceAboveMA100;
    Boolean requirePriceBelowMA200;
    Boolean requirePriceAboveMA200;

    /** Minimum drop % — used for PRICE_DROP and HIGH_52W_DROP screeners. */
    Double minDropPercent;

    /** Look-back days — used for PRICE_DROP screener (0 = intraday). */
    Integer lookbackDays;
}
