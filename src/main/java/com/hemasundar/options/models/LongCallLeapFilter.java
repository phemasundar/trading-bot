package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Long Call LEAP strategy.
 * Extends OptionsStrategyFilter with leg-specific filter for the long call.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LongCallLeapFilter extends OptionsStrategyFilter {
    private LegFilter longCall; // The long call option (optional)
}
