package com.hemasundar.options.models;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Long Call LEAP strategy.
 * Extends OptionsStrategyFilter with leg-specific filter for the long call.
 */
@Getter
@SuperBuilder
public class LongCallLeapFilter extends OptionsStrategyFilter {
    private LegFilter longCall; // The long call option (optional)
}
