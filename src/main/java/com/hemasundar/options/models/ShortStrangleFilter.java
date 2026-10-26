package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.function.BiConsumer;
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

    @Override
    public void initLegs(BiConsumer<LegFilter, String> legInitializer) {
        legInitializer.accept(putShortLeg, "putShortLeg");
        legInitializer.accept(callShortLeg, "callShortLeg");
    }

    @Override
    public LegFilter getOrCreateLegByPrefix(String cleanPrefix) {
        if ("PUTSHORT".equals(cleanPrefix)) {
            if (putShortLeg == null) putShortLeg = new LegFilter();
            return putShortLeg;
        }
        if ("CALLSHORT".equals(cleanPrefix)) {
            if (callShortLeg == null) callShortLeg = new LegFilter();
            return callShortLeg;
        }
        return null;
    }
}
