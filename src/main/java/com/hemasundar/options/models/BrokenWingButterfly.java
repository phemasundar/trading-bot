package com.hemasundar.options.models;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokenWingButterfly implements TradeSetup {
    private OptionChainResponse.OptionData leg1LongCall; // Buy 1 Call (Lower Strike)
    private OptionChainResponse.OptionData leg2ShortCalls; // Sell 2 Calls (Middle Strike)
    private OptionChainResponse.OptionData leg3LongCall; // Buy 1 Call (Higher Strike, Protection)

    private double lowerWingWidth; // (Leg2 Strike - Leg1 Strike) * 100
    private double upperWingWidth; // (Leg3 Strike - Leg2 Strike) * 100
    private double totalDebit;
    private double maxLossUpside; // (Upper Wing Width − Lower Wing Width) + Debit Paid
    private double maxLossDownside; // Debit Paid
    private double maxLoss; // Max of Upside & Downside
    private double returnOnRisk;

    @Override
    public double getNetCredit() {
        // This is a debit strategy, return negative debit
        return -totalDebit;
    }

    @Override
    public String toString() {
        return String.format("--- Valid Bullish Broken Wing Butterfly Found ---\n" +
                "Expiry: %s\n" +
                "Leg 1 (Buy): %s (Strike %.1f, Delta %.3f)\n" +
                "Leg 2 (Sell 2x): %s (Strike %.1f, Delta %.3f)\n" +
                "Leg 3 (Buy): %s (Strike %.1f, Delta %.3f)\n" +
                "Lower Wing: $%.2f | Upper Wing: $%.2f\n" +
                "Total Debit: $%.2f | Max Loss: $%.2f | Return on Risk: %.2f%%\n",
                leg1LongCall.getExpirationDate(),
                leg1LongCall.getSymbol(), leg1LongCall.getStrikePrice(), leg1LongCall.getDelta(),
                leg2ShortCalls.getSymbol(), leg2ShortCalls.getStrikePrice(), leg2ShortCalls.getDelta(),
                leg3LongCall.getSymbol(), leg3LongCall.getStrikePrice(), leg3LongCall.getDelta(),
                lowerWingWidth, upperWingWidth,
                totalDebit, maxLoss, returnOnRisk);
    }
}
