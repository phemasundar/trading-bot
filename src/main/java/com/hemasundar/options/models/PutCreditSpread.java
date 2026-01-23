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
public class PutCreditSpread implements TradeSetup {
    private OptionChainResponse.OptionData shortPut;
    private OptionChainResponse.OptionData longPut;
    private double netCredit;
    private double maxLoss;
    private double breakEvenPrice;
    private double breakEvenPercentage;
    private double returnOnRisk;
    private double currentPrice; // Underlying stock price

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
                        .build(),
                TradeLeg.builder()
                        .action("BUY")
                        .optionType("PUT")
                        .strike(longPut.getStrikePrice())
                        .delta(longPut.getDelta())
                        .premium(longPut.getMark())
                        .build());
    }

}
