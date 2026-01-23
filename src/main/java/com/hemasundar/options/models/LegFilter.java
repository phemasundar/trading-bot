package com.hemasundar.options.models;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Reusable filter for any single option leg.
 * All fields are optional - null values mean no restriction.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegFilter {
    private Double minDelta;
    private Double maxDelta;
    private Double minPremium;
    private Double maxPremium;
    private Integer minOpenInterest;
    private Integer minVolume;

    /**
     * Returns true if delta passes the minDelta filter.
     * If minDelta is null, returns true (no restriction).
     */
    public boolean passesMinDelta(double absDelta) {
        return minDelta == null || absDelta >= minDelta;
    }

    /**
     * Returns true if delta passes the maxDelta filter.
     * If maxDelta is null, returns true (no restriction).
     */
    public boolean passesMaxDelta(double absDelta) {
        return maxDelta == null || absDelta <= maxDelta;
    }

    /**
     * Returns true if delta passes both min and max delta filters.
     */
    public boolean passesDeltaFilter(double absDelta) {
        return passesMinDelta(absDelta) && passesMaxDelta(absDelta);
    }
}
