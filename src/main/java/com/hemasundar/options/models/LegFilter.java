package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reusable filter for any single option leg.
 * All fields are optional - null values mean no restriction.
 */
@Getter
@Setter
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
    private Double minVolatility;
    private Double maxVolatility;

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

    /**
     * Comprehensive filter check - validates ALL fields in this filter.
     * Returns true if the option leg passes all defined filter criteria.
     *
     * @param leg The option leg to validate
     * @return true if leg passes all filters (or if all filters are null)
     */
    public boolean passes(OptionChainResponse.OptionData leg) {
        if (leg == null)
            return false;

        // Delta filters
        if (minDelta != null && leg.getAbsDelta() < minDelta)
            return false;
        if (maxDelta != null && leg.getAbsDelta() > maxDelta)
            return false;

        // Premium filters
        if (minPremium != null && leg.getMark() < minPremium)
            return false;
        if (maxPremium != null && leg.getMark() > maxPremium)
            return false;

        // Volume filter
        if (minVolume != null && leg.getTotalVolume() < minVolume)
            return false;

        // Open Interest filter
        if (minOpenInterest != null && leg.getOpenInterest() < minOpenInterest)
            return false;

        // Volatility filters
        if (minVolatility != null && leg.getVolatility() < minVolatility)
            return false;
        if (maxVolatility != null && leg.getVolatility() > maxVolatility)
            return false;

        return true;
    }

    // ========== NULL-SAFE STATIC HELPERS ==========

    /**
     * Comprehensive null-safe filter - checks ALL filter fields.
     * Returns true if filter is null (no restriction) or leg passes all criteria.
     *
     * @param filter The LegFilter to apply (can be null)
     * @param leg    The option leg to validate
     * @return true if passes all filters or filter is null
     */
    public static boolean passes(LegFilter filter, OptionChainResponse.OptionData leg) {
        return filter == null || filter.passes(leg);
    }
}
