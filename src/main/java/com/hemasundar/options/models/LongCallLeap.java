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
    private Double breakevenCAGR; // CAGR needed to reach breakeven
    private double optionPrice;

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
                        .optionData(longCall)
                        .build());
    }

    /**
     * Resolves the option purchase price per share.
     * Uses explicit optionPrice if set, falling back to longCall ask, maxLoss / 100, or mark.
     *
     * @return option price per share
     */
    public double getOptionPrice() {
        if (optionPrice > 0) {
            return optionPrice;
        }
        if (longCall != null && longCall.getAsk() > 0) {
            return longCall.getAsk();
        }
        if (maxLoss > 0) {
            return maxLoss / 100.0;
        }
        if (longCall != null && longCall.getMark() > 0) {
            return longCall.getMark();
        }
        return 0.0;
    }

    /**
     * Calculates the option price as a percentage of the current stock price.
     * Used for ranking trades in the Top N strategy and evaluating OPTION_PRICE_PCT filter.
     *
     * @return option price percentage
     */
    public double getOptionPricePercent() {
        if (currentPrice <= 0) {
            return 0.0;
        }
        return (getOptionPrice() / currentPrice) * 100.0;
    }

    @Override
    public String getStrategyType() {
        return "LONG_CALL_LEAP";
    }

    @Override
    public Double getCostSavingsPercent() {
        return costSavingsPercent;
    }
}
