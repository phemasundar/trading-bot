package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Bullish ZEBRA strategy.
 * Extends OptionsStrategyFilter with leg-specific filters.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZebraFilter extends OptionsStrategyFilter {
    private LegFilter shortCall; // The option being sold (usually ~0.5 Delta)
    private LegFilter longCall; // The options being bought (usually 2 at ~0.7 Delta)
}
