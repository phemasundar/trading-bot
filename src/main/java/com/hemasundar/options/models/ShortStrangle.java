package com.hemasundar.options.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a Short Strangle options trade setup.
 * A short strangle consists of selling an OTM put and an OTM call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortStrangle implements TradeSetup {
    private OptionChainResponse.OptionData shortPut;
    private OptionChainResponse.OptionData shortCall;
    private double netCredit;
    private double maxLoss;
    private double returnOnRisk;
    private double lowerBreakEven;
    private double upperBreakEven;
    private double lowerBreakEvenPercentage;
    private double upperBreakEvenPercentage;
    private double currentPrice; // Underlying stock price

    @Override
    public double getBreakEvenPrice() {
        return lowerBreakEven;
    }

    @Override
    public double getBreakEvenPercentage() {
        return lowerBreakEvenPercentage;
    }

    @Override
    public double getUpperBreakEvenPrice() {
        return upperBreakEven;
    }

    @Override
    public double getUpperBreakEvenPercentage() {
        return upperBreakEvenPercentage;
    }

    @Override
    public double getNetExtrinsicValue() {
        // Net extrinsic for sold options is their combined extrinsic value (negated if representing flow, but returning positive extrinsic here for filters)
        return (shortPut != null ? shortPut.getExtrinsicValue() : 0) + 
               (shortCall != null ? shortCall.getExtrinsicValue() : 0);
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
                .optionData(shortPut)
                .build(),
            TradeLeg.builder()
                .action("SELL")
                .optionType("CALL")
                .strike(shortCall.getStrikePrice())
                .delta(shortCall.getDelta())
                .premium(shortCall.getMark())
                .optionData(shortCall)
                .build()
        );
    }
}
