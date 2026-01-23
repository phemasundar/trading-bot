package com.hemasundar.options.models;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Broken Wing Butterfly strategies (Call or Put).
 * Extends OptionsStrategyFilter with leg-specific filters for each of the 3
 * legs.
 * Field names are generic to support both Bullish (Call) and Bearish (Put)
 * variants.
 */
@Getter
@SuperBuilder
public class BrokenWingButterflyFilter extends OptionsStrategyFilter {
    private LegFilter leg1Long; // First long option at lower strike (optional)
    private LegFilter leg2Short; // Middle short options (optional)
    private LegFilter leg3Long; // Protection long option at higher strike (optional)
}
