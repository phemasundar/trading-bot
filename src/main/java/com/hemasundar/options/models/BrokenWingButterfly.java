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
    private double currentPrice; // Underlying stock price
    private double breakEvenPrice;
    private double breakEvenPercentage;
    private double upperBreakEvenPrice;
    private double upperBreakEvenPercentage;

    @Override
    public double getNetCredit() {
        // This is a debit strategy, return negative debit
        return -totalDebit;
    }

    @Override
    public String getExpiryDate() {
        return leg1LongCall != null ? leg1LongCall.getExpirationDate() : null;
    }

    @Override
    public int getDaysToExpiration() {
        return leg1LongCall != null ? leg1LongCall.getDaysToExpiration() : 0;
    }

    @Override
    public List<TradeLeg> getLegs() {
        return List.of(
                TradeLeg.builder()
                        .action("BUY")
                        .optionType("CALL")
                        .strike(leg1LongCall.getStrikePrice())
                        .delta(leg1LongCall.getDelta())
                        .premium(leg1LongCall.getMark())
                        .build(),
                TradeLeg.builder()
                        .action("SELL 2x")
                        .optionType("CALL")
                        .strike(leg2ShortCalls.getStrikePrice())
                        .delta(leg2ShortCalls.getDelta())
                        .premium(leg2ShortCalls.getMark())
                        .build(),
                TradeLeg.builder()
                        .action("BUY")
                        .optionType("CALL")
                        .strike(leg3LongCall.getStrikePrice())
                        .delta(leg3LongCall.getDelta())
                        .premium(leg3LongCall.getMark())
                        .build());
    }

}
