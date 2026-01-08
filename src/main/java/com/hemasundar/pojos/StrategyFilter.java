package com.hemasundar.pojos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyFilter {
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
}
