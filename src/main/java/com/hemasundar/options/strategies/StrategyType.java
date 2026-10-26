package com.hemasundar.options.strategies;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.hemasundar.options.models.BrokenWingButterflyFilter;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.LongCallLeapFilter;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.ShortStrangleFilter;
import com.hemasundar.options.models.ZebraFilter;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining available trading strategy types with their display names and filter factories.
 */
@Getter
@RequiredArgsConstructor
public enum StrategyType {

    // Credit Spread Strategies
    PUT_CREDIT_SPREAD("Put Credit Spread", CreditSpreadFilter::new),
    CALL_CREDIT_SPREAD("Call Credit Spread", CreditSpreadFilter::new),

    // Delta-Neutral & Spread Strategies
    IRON_CONDOR("Iron Condor", IronCondorFilter::new),
    LONG_CALL_LEAP("Long Call LEAP", LongCallLeapFilter::new),
    BULLISH_BROKEN_WING_BUTTERFLY("Bullish Broken Wing Butterfly", BrokenWingButterflyFilter::new),
    BULLISH_ZEBRA("Bullish ZEBRA", ZebraFilter::new),

    // Short (Naked) Strategies
    SHORT_PUT("Short Put", CreditSpreadFilter::new),
    SHORT_STRANGLE("Short Strangle", ShortStrangleFilter::new);

    private final String displayName;
    private final Supplier<? extends OptionsStrategyFilter> filterFactory;

    /**
     * Instantiates the concrete {@link OptionsStrategyFilter} subclass for this strategy type.
     */
    public OptionsStrategyFilter createFilter() {
        return filterFactory.get();
    }

    /**
     * Jackson deserializer - allows parsing from enum name (e.g.,
     * "PUT_CREDIT_SPREAD").
     */
    @JsonCreator
    public static StrategyType fromString(String value) {
        return StrategyType.valueOf(value);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
