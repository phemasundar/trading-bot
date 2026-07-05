package com.hemasundar.dto;

import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import java.util.List;


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

    /** RSI condition: OVERSOLD | OVERBOUGHT | BULLISH_CROSSOVER | BEARISH_CROSSOVER | CUSTOM_RANGE */
    String rsiCondition;

    /** Minimum RSI value for CUSTOM_RANGE condition */
    Double minRsi;

    /** Maximum RSI value for CUSTOM_RANGE condition */
    Double maxRsi;

    /** Bollinger condition: LOWER_BAND | UPPER_BAND */
    String bollingerCondition;

    /** List of volume rules, e.g., ">= 1000000" or "SMA20 >= SMA50 * 90%" */
    List<String> volumeRules;

    /** List of moving average rules, e.g., "PRICE_ABOVE_SMA50" */
    List<String> movingAverageRules;

    /** Rolling period (in days) for Historical Volatility calculation. */
    Integer hvPeriod;

    /** List of historical volatility rules, e.g., ">= 25" */
    List<String> historicalVolatilityRules;

    /** Minimum drop % — used for PRICE_DROP and HIGH_52W_DROP screeners. */
    Double minDropPercent;

    /** Look-back days — used for PRICE_DROP screener (0 = intraday). */
    Integer lookbackDays;
}
