package com.hemasundar.options.models;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Credit Spread strategies (Put Credit Spread, Call Credit Spread).
 * Extends OptionsStrategyFilter with leg-specific filters.
 */
@Getter
@SuperBuilder
public class CreditSpreadFilter extends OptionsStrategyFilter {
    private LegFilter shortLeg; // The option being sold (optional)
    private LegFilter longLeg; // The option being bought for protection (optional)
}
