package com.hemasundar.options.expectedmove;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Waterfall tiers used by {@link ExpectedMoveCalculator} to compute the 1-standard-deviation
 * Expected Move for an underlying symbol and expiration date.
 */
@Getter
@RequiredArgsConstructor
public enum ExpectedMoveTier {

    LEAPS_LOGNORMAL("LEAPS"),
    TIER_1_FIVE_STRIKE_BLEND("Tier 1"),
    TIER_2_ATM_STRADDLE("Tier 2"),
    TIER_3_IV_LINEAR("Tier 3"),
    CIRCUIT_BREAKER("Unavailable");

    private final String shortLabel;
}
