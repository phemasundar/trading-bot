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
public class ZebraTrade implements TradeSetup {
    private OptionChainResponse.OptionData shortCall;
    private OptionChainResponse.OptionData longCall; // Represents the 2 long call legs (same strike)
    private double netDebit;
    private double maxLoss;
    private double breakEvenPrice;
    private double breakEvenPercentage;
    private double returnOnRisk;
    private double currentPrice; // Underlying stock price
    private double netExtrinsicValue;

    @Override
    public double getNetCredit() {
        return -netDebit; // ZEBRA is a debit spread, so credit is negative debit
    }

    @Override
    public String getExpiryDate() {
        return shortCall != null ? shortCall.getExpirationDate() : null;
    }

    @Override
    public int getDaysToExpiration() {
        return shortCall != null ? shortCall.getDaysToExpiration() : 0;
    }

    @Override
    public List<TradeLeg> getLegs() {
        return List.of(
                TradeLeg.builder()
                        .action("SELL")
                        .quantity(1)
                        .optionType("CALL")
                        .strike(shortCall.getStrikePrice())
                        .delta(shortCall.getDelta())
                        .premium(shortCall.getMark())
                        .build(),
                TradeLeg.builder()
                        .action("BUY")
                        .quantity(2)
                        .optionType("CALL")
                        .strike(longCall.getStrikePrice())
                        .delta(longCall.getDelta())
                        .premium(longCall.getMark())
                        .build());
    }

}
