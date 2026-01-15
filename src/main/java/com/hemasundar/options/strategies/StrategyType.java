package com.hemasundar.options.strategies;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining available trading strategy types with their display names.
 * Used for consistent strategy naming across the application.
 */
@Getter
@RequiredArgsConstructor
public enum StrategyType {

    // Put Credit Spread Strategies
    PUT_CREDIT_SPREAD("Put Credit Spread"),
    RSI_BOLLINGER_BULL_PUT_SPREAD("RSI Bollinger Bull Put Spread"),

    // Call Credit Spread Strategies
    CALL_CREDIT_SPREAD("Call Credit Spread"),
    RSI_BOLLINGER_BEAR_CALL_SPREAD("RSI Bollinger Bear Call Spread"),

    // Other Strategies
    IRON_CONDOR("Iron Condor"),
    LONG_CALL_LEAP("Long Call LEAP");

    private final String displayName;

    @Override
    public String toString() {
        return displayName;
    }
}
