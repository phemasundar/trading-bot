package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Broken Wing Butterfly strategies (Call or Put).
 * Extends OptionsStrategyFilter with leg-specific filters for each of the 3
 * legs.
 * Field names are generic to support both Bullish (Call) and Bearish (Put)
 * variants.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrokenWingButterflyFilter extends OptionsStrategyFilter {
    private LegFilter leg1Long; // First long option at lower strike (optional)
    private LegFilter leg2Short; // Middle short options (optional)
    private LegFilter leg3Long; // Protection long option at higher strike (optional)

    @Override
    public void initLegs(BiConsumer<LegFilter, String> legInitializer) {
        legInitializer.accept(leg1Long, "leg1Long");
        legInitializer.accept(leg2Short, "leg2Short");
        legInitializer.accept(leg3Long, "leg3Long");
    }

    @Override
    public LegFilter getOrCreateLegByPrefix(String cleanPrefix) {
        switch (cleanPrefix) {
            case "LEG1":
                if (leg1Long == null) leg1Long = new LegFilter();
                return leg1Long;
            case "LEG2":
                if (leg2Short == null) leg2Short = new LegFilter();
                return leg2Short;
            case "LEG3":
                if (leg3Long == null) leg3Long = new LegFilter();
                return leg3Long;
            default:
                return null;
        }
    }
}
