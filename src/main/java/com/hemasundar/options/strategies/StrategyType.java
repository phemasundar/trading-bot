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

    // Credit Spread Strategies
    PUT_CREDIT_SPREAD("Put Credit Spread"),
    CALL_CREDIT_SPREAD("Call Credit Spread"),

    // Delta-Neutral & Spread Strategies
    IRON_CONDOR("Iron Condor"),
    LONG_CALL_LEAP("Long Call LEAP"),
    BULLISH_BROKEN_WING_BUTTERFLY("Bullish Broken Wing Butterfly"),
    BULLISH_ZEBRA("Bullish ZEBRA"),

    // Short (Naked) Strategies
    SHORT_PUT("Short Put"),
    SHORT_STRANGLE("Short Strangle");

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
