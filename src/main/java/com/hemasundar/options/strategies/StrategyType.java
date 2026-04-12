package com.hemasundar.options.strategies;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enum defining available trading strategy types with their display names.
 * Each type knows how to create its own strategy instance (Factory Pattern).
 */
@Getter
@RequiredArgsConstructor
public enum StrategyType {

    // Put Credit Spread Strategies
    PUT_CREDIT_SPREAD("Put Credit Spread"),
    TECH_PUT_CREDIT_SPREAD("Technical Put Credit Spread"),
    BULLISH_LONG_PUT_CREDIT_SPREAD("Bullish Long Put Credit Spread"),

    // Call Credit Spread Strategies
    CALL_CREDIT_SPREAD("Call Credit Spread"),
    TECH_CALL_CREDIT_SPREAD("Technical Call Credit Spread"),

    // Other Strategies
    IRON_CONDOR("Iron Condor"),
    BULLISH_LONG_IRON_CONDOR("Bullish Long Iron Condor"),
    LONG_CALL_LEAP("Long Call LEAP"),
    BULLISH_BROKEN_WING_BUTTERFLY("Bullish Broken Wing Butterfly"),
    BULLISH_ZEBRA("Bullish ZEBRA");

    private final String displayName;

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
