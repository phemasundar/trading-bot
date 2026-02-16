package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Iron Condor strategies with independent put and call leg filters.
 * Allows specifying different maxDelta values for the put short leg vs call
 * short leg.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IronCondorFilter extends OptionsStrategyFilter {
    private LegFilter putShortLeg;
    private LegFilter putLongLeg;
    private LegFilter callShortLeg;
    private LegFilter callLongLeg;
}
