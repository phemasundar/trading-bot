package com.hemasundar.pojos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Override
    public String toString() {
        return String.format("--- Valid Iron Condor Found ---\n" +
                "Expiry: %s\n" +
                "Put Leg: Sell %.1f / Buy %.1f | Call Leg: Sell %.1f / Buy %.1f\n" +
                "Net Credit: $%.2f | Max Loss: $%.2f | Return on Risk: %.2f%%\n" +
                "Break Evens: $%.2f (%.2f%%) - $%.2f (%.2f%%)\n",
                putLeg.getShortPut().getExpirationDate(),
                putLeg.getShortPut().getStrikePrice(), putLeg.getLongPut().getStrikePrice(),
                callLeg.getShortCall().getStrikePrice(), callLeg.getLongCall().getStrikePrice(),
                netCredit, maxLoss, returnOnRisk,
                lowerBreakEven, lowerBreakEvenPercentage, upperBreakEven, upperBreakEvenPercentage);
    }
}
