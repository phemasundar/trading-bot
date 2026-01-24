package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Iron Condor strategies with independent put and call leg filters.
 * Allows specifying different maxDelta values for the put short leg vs call
 * short leg.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IronCondorFilter extends OptionsStrategyFilter {

    /**
     * Filter for the short put leg (the put option being sold).
     */
    private LegFilter putShortLeg;

    /**
     * Filter for the long put leg (the put option being bought for protection).
     */
    private LegFilter putLongLeg;

    /**
     * Filter for the short call leg (the call option being sold).
     */
    private LegFilter callShortLeg;

    /**
     * Filter for the long call leg (the call option being bought for protection).
     */
    private LegFilter callLongLeg;
}
