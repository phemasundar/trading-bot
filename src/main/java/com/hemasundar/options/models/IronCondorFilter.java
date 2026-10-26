package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.function.BiConsumer;
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

    @Override
    public void initLegs(BiConsumer<LegFilter, String> legInitializer) {
        legInitializer.accept(putShortLeg, "putShortLeg");
        legInitializer.accept(putLongLeg, "putLongLeg");
        legInitializer.accept(callShortLeg, "callShortLeg");
        legInitializer.accept(callLongLeg, "callLongLeg");
    }

    @Override
    public LegFilter getOrCreateLegByPrefix(String cleanPrefix) {
        switch (cleanPrefix) {
            case "PUTSHORT":
                if (putShortLeg == null) putShortLeg = new LegFilter();
                return putShortLeg;
            case "PUTLONG":
                if (putLongLeg == null) putLongLeg = new LegFilter();
                return putLongLeg;
            case "CALLSHORT":
                if (callShortLeg == null) callShortLeg = new LegFilter();
                return callShortLeg;
            case "CALLLONG":
                if (callLongLeg == null) callLongLeg = new LegFilter();
                return callLongLeg;
            default:
                return null;
        }
    }
}
