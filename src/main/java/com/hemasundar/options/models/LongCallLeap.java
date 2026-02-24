package com.hemasundar.options.models;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LongCallLeap implements TradeSetup {
    private OptionChainResponse.OptionData longCall;
    private double breakEvenPrice;
    private double breakEvenPercentage;
    private double extrinsicValue;
    private double finalCostOfBuying;
    private double finalCostOfOption;
    private double dividendYield;
    private double interestRatePaidForMargin;
    private double netCredit; // Will be negative (debit)
    private double maxLoss;
    private double currentPrice; // Underlying stock price
    private double costSavingsPercent; // Cost savings compared to buying stock on margin
    private double breakevenCAGR; // CAGR needed to reach breakeven

    @Override
    public double getNetCredit() {
        return netCredit;
    }

    @Override
    public double getMaxLoss() {
        return maxLoss;
    }

    @Override
    public double getReturnOnRisk() {
        return 0.0; // Undefined for single leg buying
    }

    @Override
    public double getNetExtrinsicValue() {
        return extrinsicValue; // Value is calculated in strategy, just return it
    }

    @Override
    public String getExpiryDate() {
        return longCall != null ? longCall.getExpirationDate() : null;
    }

    @Override
    public int getDaysToExpiration() {
        return longCall != null ? longCall.getDaysToExpiration() : 0;
    }

    @Override
    public List<TradeLeg> getLegs() {
        return List.of(
                TradeLeg.builder()
                        .action("BUY")
                        .optionType("CALL")
                        .strike(longCall.getStrikePrice())
                        .delta(longCall.getDelta())
                        .premium(longCall.getMark())
                        .build());
    }

    /**
     * Calculates the option price as a percentage of the current stock price.
     * Used for ranking trades in the Top N strategy.
     *
     * @return option price percentage
     */
    public double getOptionPricePercent() {
        if (currentPrice <= 0) {
            return 0.0;
        }
        return (finalCostOfOption / currentPrice) * 100.0;
    }

    /**
     * Calculates the breakeven CAGR (Compound Annual Growth Rate).
     * This represents the annualized return rate needed for the underlying
     * to reach the breakeven price by expiration.
     *
     * @return the breakeven CAGR as a percentage
     */
    public double calculateBreakevenCAGR() {
        if (longCall == null || longCall.daysToExpiration <= 0) {
            return 0.0;
        }
        double yearsToExpiration = longCall.daysToExpiration / 365.0;
        double growthFactor = 1 + (breakEvenPercentage / 100.0);
        return (Math.pow(growthFactor, 1.0 / yearsToExpiration) - 1) * 100.0;
    }

}
