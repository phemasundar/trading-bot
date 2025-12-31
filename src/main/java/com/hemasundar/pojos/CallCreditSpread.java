package com.hemasundar.pojos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Override
    public String toString() {
        return String.format("--- Valid Call Credit Spread Found ---\n" +
                "Strategy: Sell %s (Strike %.1f) / Buy %s (Strike %.1f)\n" +
                "Short Delta: %.3f | Max Profit: $%.2f | Max Loss: $%.2f\n" +
                "Return on Risk: %.2f%%\n" +
                "Break Even Price: $%.2f | Break Even Percentage: %.2f%%\n",
                shortCall.getSymbol(), shortCall.getStrikePrice(),
                longCall.getSymbol(), longCall.getStrikePrice(),
                shortCall.getDelta(), netCredit, maxLoss,
                returnOnRisk,
                breakEvenPrice, breakEvenPercentage);
    }
}
