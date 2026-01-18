package com.hemasundar.technical;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum defining available technical screener types with their display names.
 * Used for consistent screener naming across the application.
 */
@Getter
@RequiredArgsConstructor
public enum ScreenerType {

    RSI_BB_BULLISH_CROSSOVER("RSI BB Bullish Crossover"),
    RSI_BB_BEARISH_CROSSOVER("RSI BB Bearish Crossover"),
    BELOW_200_DAY_MA("Below 200 Day MA");

    private final String displayName;

    @Override
    public String toString() {
        return displayName;
    }
}
