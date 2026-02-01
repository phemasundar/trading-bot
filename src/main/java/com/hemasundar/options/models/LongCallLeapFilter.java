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

    /**
     * Minimum cost savings percentage required for the option route compared to
     * buying stock.
     * Example: If set to 10.0, the option must be at least 10% cheaper than buying
     * stock.
     */
    private Double minCostSavingsPercent;
}
