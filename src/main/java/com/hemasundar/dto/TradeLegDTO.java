package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single leg of an options trade for UI display.
 * Mirrors {@link com.hemasundar.options.models.TradeLeg} but in the DTO layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLegDTO {

    /**
     * Action: "SELL" or "BUY"
     */
    private String action;

    /**
     * Option type: "CALL" or "PUT"
     */
    private String optionType;

    /**
     * Strike price
     */
    private double strike;

    /**
     * Delta value
     */
    private double delta;

    /**
     * Premium (mark price)
     */
    private double premium;

    /**
     * Quantity of the leg (e.g., 2 for the short legs in BWB/Zebra)
     * Defaults to 1 if not explicitly set.
     */
    @Builder.Default
    private int quantity = 1;
}
