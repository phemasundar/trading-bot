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
    private boolean ignoreEarnings= true;
}
