package com.hemasundar.options.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single-leg Short Put (naked/cash-secured put) trade.
 * <p>
 * The seller of the put receives the premium immediately. Maximum loss occurs
 * if the underlying price falls to zero at expiration:
 * maxLoss = (strikePrice − premiumReceived) × 100
 * Break-even = strikePrice − premiumPerShare
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortPut implements TradeSetup {

    /** The single put option being sold (short leg). */
    private OptionChainResponse.OptionData shortPut;

    /** Net credit collected (positive = received), in dollars (×100 multiplier applied). */
    private double netCredit;

    /** Maximum theoretical loss (strike − credit) × 100. */
    private double maxLoss;

    /** Underlying price at the time the trade is scanned. */
    private double currentPrice;

    /** Price at which the position breaks even at expiration. */
    private double breakEvenPrice;

    /** Percentage distance from the current price to the break-even price. */
    private double breakEvenPercentage;

    /** Net credit as a percentage of maximum risk (= netCredit / maxLoss × 100). */
    private double returnOnRisk;

    // ── TradeSetup interface ──────────────────────────────────────────────

    @Override
    public double getNetExtrinsicValue() {
        // For a single short put, extrinsic value collected is just the option's extrinsic value
        return shortPut != null ? shortPut.getExtrinsicValue() : 0;
    }

    @Override
    public String getExpiryDate() {
        return shortPut != null ? shortPut.getExpirationDate() : null;
    }

    @Override
    public int getDaysToExpiration() {
        return shortPut != null ? shortPut.getDaysToExpiration() : 0;
    }

    @Override
    public List<TradeLeg> getLegs() {
        return List.of(
                TradeLeg.builder()
                        .action("SELL")
                        .optionType("PUT")
                        .strike(shortPut.getStrikePrice())
                        .delta(shortPut.getDelta())
                        .premium(shortPut.getMark())
                        .build());
    }
}
