package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
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

    // ========== NULL-SAFE STATIC HELPERS ==========

    /**
     * Null-safe helper for minimum delta check.
     * Returns true if filter is null (no restriction) or passes minDelta.
     */
    public static boolean passesMinDelta(LegFilter filter, double absDelta) {
        return filter == null || filter.passesMinDelta(absDelta);
    }

    /**
     * Null-safe helper for maximum delta check.
     * Returns true if filter is null (no restriction) or passes maxDelta.
     */
    public static boolean passesMaxDelta(LegFilter filter, double absDelta) {
        return filter == null || filter.passesMaxDelta(absDelta);
    }

    /**
     * Null-safe helper for full delta filter (both min and max).
     * Returns true if filter is null (no restriction) or passes the filter.
     */
    public static boolean passes(LegFilter filter, double absDelta) {
        return filter == null || filter.passesDeltaFilter(absDelta);
    }
}
