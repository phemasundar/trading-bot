package com.hemasundar.pojos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Data point representing At-The-Money (ATM) Implied Volatility for a stock.
 * Captures both PUT and CALL IV at the same strike for IV rank calculations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IVDataPoint {

    /**
     * Stock symbol
     */
    private String symbol;

    /**
     * Date of data collection
     */
    private LocalDate currentDate;

    /**
     * At-The-Money Put Implied Volatility (as percentage, e.g., 45.5 for 45.5%)
     */
    private Double atmPutIV;

    /**
     * At-The-Money Call Implied Volatility (as percentage, e.g., 45.5 for 45.5%)
     */
    private Double atmCallIV;

    /**
     * Days to expiration (target ~30)
     */
    private Integer dte;

    /**
     * Option expiry date
     */
    private String expiryDate;

    /**
     * Strike price (same for both PUT and CALL)
     */
    private Double strike;

    /**
     * Current underlying stock price
     */
    private Double underlyingPrice;
}
