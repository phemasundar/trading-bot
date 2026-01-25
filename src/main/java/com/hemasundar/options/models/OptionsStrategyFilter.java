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

    // ========== VALIDATION METHODS ==========

    /**
     * Checks if a max loss value passes the maxLossLimit filter.
     * 
     * @param maxLoss the calculated max loss for a trade
     * @return true if maxLoss is within the limit
     */
    public boolean passesMaxLoss(double maxLoss) {
        return maxLoss <= this.maxLossLimit;
    }

    /**
     * Checks if a total debit passes the maxTotalDebit filter.
     * 
     * @param debit the calculated total debit for a trade
     * @return true if debit is within limit or no limit is set
     */
    public boolean passesDebitLimit(double debit) {
        return this.maxTotalDebit <= 0 || debit <= this.maxTotalDebit;
    }

    /**
     * Checks if a total credit passes the maxTotalCredit filter.
     * 
     * @param credit the calculated total credit for a trade
     * @return true if credit is within limit or no limit is set
     */
    public boolean passesCreditLimit(double credit) {
        return this.maxTotalCredit <= 0 || credit <= this.maxTotalCredit;
    }

    /**
     * Checks if profit meets the minimum return on risk requirement.
     * 
     * @param profit  the net profit (credit) for the trade
     * @param maxLoss the maximum loss for the trade
     * @return true if return on risk meets the minimum threshold
     */
    public boolean passesMinReturnOnRisk(double profit, double maxLoss) {
        if (maxLoss <= 0)
            return true; // Avoid division by zero
        double requiredProfit = maxLoss * (this.minReturnOnRisk / 100.0);
        return profit >= requiredProfit;
    }
}
