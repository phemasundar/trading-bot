package com.hemasundar.options.models;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OptionsStrategyFilter {
    private int targetDTE;
    private double maxDelta;
    private double maxLossLimit;
    private int minReturnOnRisk;
    @Builder.Default
    private boolean ignoreEarnings = true;
    private int minDTE;
    private double minDelta;
    @Builder.Default
    private double marginInterestRate = 6.0;
    @Builder.Default
    private double savingsInterestRate = 10.0;
    @Builder.Default
    private double maxOptionPricePercent = 50.0;

    // Bullish Broken Wing Butterfly specific filters
    private double longCallMaxDelta; // Max delta for Leg 1 (Long Call)
    private double shortCallsMaxDelta; // Max delta for Leg 2 (Short Calls)
    private double maxTotalDebit; // Max total debit for the strategy
}
