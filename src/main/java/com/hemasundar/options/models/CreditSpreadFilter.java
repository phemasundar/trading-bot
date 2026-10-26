package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Credit Spread strategies (Put Credit Spread, Call Credit Spread).
 * Extends OptionsStrategyFilter with leg-specific filters.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditSpreadFilter extends OptionsStrategyFilter {
    private LegFilter shortLeg; // The option being sold (optional)
    private LegFilter longLeg; // The option being bought for protection (optional)

    @Override
    public void initLegs(BiConsumer<LegFilter, String> legInitializer) {
        legInitializer.accept(shortLeg, "shortLeg");
        legInitializer.accept(longLeg, "longLeg");
    }

    @Override
    public LegFilter getOrCreateLegByPrefix(String cleanPrefix) {
        if ("SHORTLEG".equals(cleanPrefix)) {
            if (shortLeg == null) shortLeg = new LegFilter();
            return shortLeg;
        }
        if ("LONGLEG".equals(cleanPrefix)) {
            if (longLeg == null) longLeg = new LegFilter();
            return longLeg;
        }
        return null;
    }
}
