package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base filter class containing common strategy-level filters.
 * Strategy-specific filters should extend this class.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionsStrategyFilter {
    // Time-based filters
    private int targetDTE; // If > 0, uses single nearest expiry (backward compatible)
    private int minDTE; // Used with maxDTE when targetDTE == 0
    private int maxDTE; // Used with minDTE when targetDTE == 0

    // Risk/Return filters
    private double maxLossLimit;
    private int minReturnOnRisk;
    private double maxTotalDebit;
    private double maxTotalCredit;

    // Behavior flags
    @lombok.Builder.Default
    private boolean ignoreEarnings = true;

    // Interest rates (for LEAP calculations)
    @lombok.Builder.Default
    private double marginInterestRate = 6.0;
    @lombok.Builder.Default
    private double savingsInterestRate = 10.0;
    @lombok.Builder.Default
    private double maxOptionPricePercent = 50.0;

    /**
     * Static helper for null-safe leg filter delta checks.
     * Returns true if filter is null or delta passes the filter.
     */
    public static boolean passesFilter(LegFilter legFilter, double absDelta) {
        return legFilter == null || legFilter.passesDeltaFilter(absDelta);
    }
}
