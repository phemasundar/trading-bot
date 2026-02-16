package com.hemasundar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single trade opportunity found by a strategy.
 * Contains common fields and per-leg details for strategy-specific display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {

    /**
     * Stock symbol (e.g., "AAPL")
     */
    private String symbol;

    /**
     * Current underlying stock price
     */
    private double underlyingPrice;

    /**
     * Option expiration date (YYYY-MM-DD)
     */
    private String expiryDate;

    /**
     * Days to expiration
     */
    private int dte;

    /**
     * Individual legs of the trade (BUY/SELL actions with strikes, deltas,
     * premiums).
     * Number of legs varies by strategy type.
     */
    private List<TradeLegDTO> legs;

    /**
     * Net credit received (positive) or debit paid (negative)
     */
    private double netCredit;

    /**
     * Maximum potential loss
     */
    private double maxLoss;

    /**
     * Return on risk percentage
     */
    private double returnOnRisk;

    /**
     * Breakeven price for the underlying
     */
    private double breakEvenPrice;

    /**
     * Breakeven as percentage from current price
     */
    private double breakEvenPercent;

    /**
     * Upper breakeven price (for strategies like BWB, Iron Condor)
     */
    private double upperBreakEvenPrice;

    /**
     * Upper breakeven as percentage from current price
     */
    private double upperBreakEvenPercent;

    /**
     * Full trade details text (matching Telegram format) for expandable view.
     */
    private String tradeDetails;
}
