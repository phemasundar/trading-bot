package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base filter class containing common strategy-level filters.
 * Strategy-specific filters should extend this class.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionsStrategyFilter {
    // Time-based filters
    private int targetDTE; // If > 0, uses single nearest expiry (backward compatible)
    private int minDTE; // Used with maxDTE when targetDTE == 0

    @lombok.Builder.Default
    private Integer maxDTE = Integer.MAX_VALUE; // Used with minDTE when targetDTE == 0

    // Risk/Return filters
    private Double maxLossLimit;
    private int minReturnOnRisk;
    private Double maxTotalDebit;
    private Double maxTotalCredit;
    private Double minTotalCredit;
    private Double maxCAGRForBreakEven;

    // Advanced Filters
    private Double maxUpperBreakevenDelta;

    // Behavior flags
    @lombok.Builder.Default
    private boolean ignoreEarnings = true;

    private Double priceVsMaxDebitRatio;

    // Interest rates (for LEAP calculations)
    @lombok.Builder.Default
    private double marginInterestRate = 6.0;
    @lombok.Builder.Default
    private double savingsInterestRate = 10.0;

    private Double maxOptionPricePercent; // No default - must be explicitly set or left null

    // Volatility filters
    /**
     * Minimum historical volatility (annualized percentage).
     * Symbol is skipped if its historical volatility is below this threshold.
     * Example: 25.0 means only trade symbols with at least 25% annualized
     * volatility.
     */
    private Double minHistoricalVolatility;

    // ========== VALIDATION METHODS ==========

    /**
     * Checks if a max loss value passes the maxLossLimit filter.
     * 
     * @param maxLoss the calculated max loss for a trade
     * @return true if maxLoss is within the limit or limit is null
     */
    public boolean passesMaxLoss(double maxLoss) {
        return this.maxLossLimit == null || maxLoss <= this.maxLossLimit;
    }

    /**
     * Checks if a total debit passes the maxTotalDebit filter.
     * 
     * @param debit the calculated total debit for a trade
     * @return true if debit is within limit or no limit is set
     */
    public boolean passesDebitLimit(double debit) {
        return this.maxTotalDebit == null || debit <= this.maxTotalDebit;
    }

    /**
     * Checks if a total credit passes the maxTotalCredit filter.
     * 
     * @param credit the calculated total credit for a trade
     * @return true if credit is within limit or no limit is set
     */
    public boolean passesCreditLimit(double credit) {
        return this.maxTotalCredit == null || (this.maxTotalCredit > 0 && credit <= this.maxTotalCredit);
    }

    /**
     * Checks if a total credit passes the minTotalCredit filter.
     * 
     * @param credit the calculated total credit for a trade
     * @return true if credit is greater than or equal to limit or no limit is set
     */
    public boolean passesMinCredit(double credit) {
        return this.minTotalCredit == null || credit >= this.minTotalCredit;
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
