package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Short Strangle strategy with independent put and call leg filters.
 * Allows specifying different constraints for the put short leg vs call short leg.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortStrangleFilter extends OptionsStrategyFilter {
    private LegFilter putShortLeg;
    private LegFilter callShortLeg;
}
