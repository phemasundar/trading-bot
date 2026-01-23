package com.hemasundar.options.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single leg of an options trade.
 * Used for OO-based Telegram message formatting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLeg {
    private String action; // "SELL" or "BUY"
    private String optionType; // "CALL" or "PUT"
    private double strike;
    private double delta;
    private double premium; // mark price
}
