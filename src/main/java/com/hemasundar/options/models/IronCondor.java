package com.hemasundar.options.models;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IronCondor implements TradeSetup {
    private PutCreditSpread putLeg;
    private CallCreditSpread callLeg;
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
        return lowerBreakEven; // Return lower break-even as primary
    }

    @Override
    public double getBreakEvenPercentage() {
        return lowerBreakEvenPercentage;
    }

    @Override
    public String getExpiryDate() {
        return putLeg != null && putLeg.getShortPut() != null
                ? putLeg.getShortPut().getExpirationDate()
                : null;
    }

    @Override
    public int getDaysToExpiration() {
        return putLeg != null && putLeg.getShortPut() != null
                ? putLeg.getShortPut().getDaysToExpiration()
                : 0;
    }

    @Override
    public List<TradeLeg> getLegs() {
        List<TradeLeg> legs = new ArrayList<>();
        // Put side
        legs.add(TradeLeg.builder()
                .action("SELL")
                .optionType("PUT")
                .strike(putLeg.getShortPut().getStrikePrice())
                .delta(putLeg.getShortPut().getDelta())
                .premium(putLeg.getShortPut().getMark())
                .build());
        legs.add(TradeLeg.builder()
                .action("BUY")
                .optionType("PUT")
                .strike(putLeg.getLongPut().getStrikePrice())
                .delta(putLeg.getLongPut().getDelta())
                .premium(putLeg.getLongPut().getMark())
                .build());
        // Call side
        legs.add(TradeLeg.builder()
                .action("SELL")
                .optionType("CALL")
                .strike(callLeg.getShortCall().getStrikePrice())
                .delta(callLeg.getShortCall().getDelta())
                .premium(callLeg.getShortCall().getMark())
                .build());
        legs.add(TradeLeg.builder()
                .action("BUY")
                .optionType("CALL")
                .strike(callLeg.getLongCall().getStrikePrice())
                .delta(callLeg.getLongCall().getDelta())
                .premium(callLeg.getLongCall().getMark())
                .build());
        return legs;
    }

}
