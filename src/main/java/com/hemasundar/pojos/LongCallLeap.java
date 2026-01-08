package com.hemasundar.pojos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Override
    public String toString() {
        return "LongCallLeap{" +
                "symbol='" + longCall.symbol + '\'' +
                ", description='" + longCall.description + '\'' +
                ", strikePrice=" + longCall.strikePrice +
                ", expirationDate='" + longCall.expirationDate + '\'' +
                ", daysToExpiration=" + longCall.daysToExpiration +
                ", debit=" + String.format("%.2f", -netCredit) +
                ", breakEvenPrice=" + String.format("%.2f", breakEvenPrice) +
                ", breakEvenPercentage=" + String.format("%.2f", breakEvenPercentage) +
                ", breakevenCAGR=" + String.format("%.2f", calculateBreakevenCAGR()) +
                ", extrinsicValue=" + String.format("%.2f", extrinsicValue) +
                ", finalCostOfBuying=" + String.format("%.2f", finalCostOfBuying) +
                ", finalCostOfOption=" + String.format("%.2f", finalCostOfOption) +
                ", dividendYield=" + dividendYield +
                ", interestRatePaidForMargin=" + interestRatePaidForMargin +
                ", maxLoss=" + String.format("%.2f", maxLoss) +
                '}';
    }
}
