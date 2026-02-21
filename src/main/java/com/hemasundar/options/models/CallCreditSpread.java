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
public class CallCreditSpread implements TradeSetup {
    private OptionChainResponse.OptionData shortCall;
    private OptionChainResponse.OptionData longCall;
    private double netCredit;
    private double maxLoss;
    private double breakEvenPrice;
    private double breakEvenPercentage;
    private double returnOnRisk;
    private double currentPrice; // Underlying stock price

    @Override
    public double getNetExtrinsicValue() {
        return longCall.getExtrinsicValue() - shortCall.getExtrinsicValue();
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
                        .optionType("CALL")
                        .strike(shortCall.getStrikePrice())
                        .delta(shortCall.getDelta())
                        .premium(shortCall.getMark())
                        .build(),
                TradeLeg.builder()
                        .action("BUY")
                        .optionType("CALL")
                        .strike(longCall.getStrikePrice())
                        .delta(longCall.getDelta())
                        .premium(longCall.getMark())
                        .build());
    }

}
