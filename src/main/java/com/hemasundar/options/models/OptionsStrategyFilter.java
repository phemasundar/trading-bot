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
    // Global filter constraints
    private String strategyType;
    private String securitiesFile;
    private String securities;

    /**
     * Optional Greek exposure map for this strategy.
     * Keys: "delta", "gamma", "theta", "vega".
     * Values: "positive", "negative", or "neutral".
     * Serialized into the filterConfig JSON blob for UI rendering.
     */
    private java.util.Map<String, String> greeks;

    // Time-based filters
    private Integer targetDTE; // If > 0, uses single nearest expiry (backward compatible)
    private Integer minDTE; // Used with maxDTE when targetDTE == null or 0
    private Integer maxDTE; // Used with minDTE when targetDTE == null or 0

    // Risk/Return filters
    private Double maxLossLimit;
    private Integer minReturnOnRisk;
    /**
     * Minimum annualized Return-on-Risk (CAGR) as a percentage.
     * Applies the compound annual growth rate formula to the raw Return-on-Risk,
     * scaling it to a full year based on the trade's DTE:
     * {@code CAGR = ((profit / maxLoss + 1)^(365.0 / dte) - 1) * 100}
     * Example: 10 means only trades with an annualized RoR of at least 10%.
     */
    private Integer minReturnOnRiskCAGR;
    private Double maxTotalDebit;
    private Double maxTotalCredit;
    private Double minTotalCredit;
    private Double maxCAGRForBreakEven;

    // Advanced Filters
    private Double maxUpperBreakevenDelta;
    private Double maxBreakEvenPercentage;

    // Extrinsic Value Constraint (Relative to underlying price)
    private Double maxNetExtrinsicValueToPricePercentage;
    private Double minNetExtrinsicValueToPricePercentage;

    // Behavior flags
    @lombok.Builder.Default
    private boolean ignoreEarnings = true;

    private java.util.List<String> includeOnly;
    private java.util.List<String> excludeIf;

    /**
     * Number of top trades to return per stock. If null, returns all matching
     * results.
     * Ranks trades by priority criteria (DTE, Cost Savings, etc.).
     */
    private Integer topTradesCount;

    private Double priceVsMaxDebitRatio;

    // Interest rates (for LEAP calculations)
    private Double marginInterestRate;
    private Double savingsInterestRate;

    private Double maxOptionPricePercent; // No default - must be explicitly set or left null

    // Volatility filters


    /**
     * Minimum IV Rank threshold (0–100) for the symbol.
     * Symbol is skipped if its current IV Rank is below this value.
     * Example: 30.0 means only trade symbols whose current IV is in the top 70% of
     * their 52-week (or available) range.
     * If null, the IV Rank filter is not applied.
     */
    private Double minIVRank;

    /**
     * Maximum IV Rank threshold (0–100) for the symbol.
     * Symbol is skipped if its current IV Rank is above this value.
     * Example: 80.0 means avoid symbols with extremely elevated IV.
     * If null, no upper bound is enforced.
     */
    private Double maxIVRank;

    /**
     * Human-readable summary of the technical filter conditions applied during execution.
     * Serialized into the filterConfig JSON blob so the UI can display it in Filter Details.
     * Populated by StrategyExecutionService when a TechnicalFilterChain is active.
     * Example: "RSI: BULLISH_CROSSOVER | BB: LOWER_BAND | Volume >= 1,000,000"
     */
    private String technicalFilterSummary;

    /**
     * Structured technical filter configuration used during execution.
     * Serialized into the filterConfig JSON blob so the UI can restore
     * technical filter form fields when "Load Filters" is clicked.
     * Keys: "RSI", "BOLLINGER_BAND", "VOLUME", "HISTORICAL_VOLATILITY", "PRICE_DROP", "SIMPLE_MOVING_AVERAGE".
     */
    private java.util.Map<String, Object> technicalFilters;

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
        if (this.minReturnOnRisk == null || this.minReturnOnRisk <= 0) return true;
        if (maxLoss <= 0)
            return true; // Avoid division by zero
        double requiredProfit = maxLoss * (this.minReturnOnRisk / 100.0);
        return profit >= requiredProfit;
    }

    /**
     * Checks if profit meets the minimum annualized return on risk (CAGR) requirement.
     * Uses the compound annual growth rate formula to scale the raw RoR to a full year:
     * {@code CAGR = ((profit / maxLoss + 1)^(365.0 / dte) - 1) * 100}
     *
     * @param profit  the net profit (credit) for the trade
     * @param maxLoss the maximum loss for the trade
     * @param dte     the days to expiration for the trade (must be > 0)
     * @return true if annualized return on risk meets the minimum threshold
     */
    public boolean passesMinReturnOnRiskCAGR(double profit, double maxLoss, int dte) {
        if (this.minReturnOnRiskCAGR == null || this.minReturnOnRiskCAGR <= 0) return true;
        if (maxLoss <= 0) return true; // Avoid division by zero
        if (dte <= 0) return true;     // Avoid invalid exponent
        double rawRoR = profit / maxLoss; // e.g. 0.12 for 12%
        double cagrPct = (Math.pow(1.0 + rawRoR, 365.0 / dte) - 1.0) * 100.0;
        return cagrPct >= this.minReturnOnRiskCAGR;
    }

    /**
     * Checks if a break-even percentage passes the maxBreakEvenPercentage filter.
     * 
     * @param breakEvenPercentage the calculated break-even percentage for a trade
     * @return true if percentage is within limit or no limit is set
     */
    public boolean passesMaxBreakEvenPercentage(double breakEvenPercentage) {
        return this.maxBreakEvenPercentage == null || breakEvenPercentage <= this.maxBreakEvenPercentage;
    }

    public boolean passesMaxNetExtrinsicValueToPricePercentage(double netExtrinsicValueToPricePercentage) {
        return this.maxNetExtrinsicValueToPricePercentage == null ||
                netExtrinsicValueToPricePercentage <= this.maxNetExtrinsicValueToPricePercentage;
    }

    /**
     * Checks if a net extrinsic value percentage passes the
     * minNetExtrinsicValueToPricePercentage filter.
     * 
     * @param netExtrinsicValueToPricePercentage the calculated net extrinsic value
     *                                           relative to underlying price
     * @return true if percentage is within limit or no limit is set
     */
    public boolean passesMinNetExtrinsicValueToPricePercentage(double netExtrinsicValueToPricePercentage) {
        return this.minNetExtrinsicValueToPricePercentage == null ||
                netExtrinsicValueToPricePercentage >= this.minNetExtrinsicValueToPricePercentage;
    }

    /**
     * Checks if the symbol's IV Rank passes the configured min/max thresholds.
     *
     * <p>Fail-open: if {@code ivRank} is {@code null} (insufficient historical data),
     * the filter is skipped and the symbol is allowed through.
     *
     * @param ivRank computed IV Rank (0–100), or {@code null} if unavailable
     * @return true if IV Rank is within bounds, or if no bounds are set, or if ivRank is null
     */
    public boolean passesIVRank(Double ivRank) {
        if (this.minIVRank == null && this.maxIVRank == null) return true;
        if (ivRank == null) return true; // fail-open: no data → allow trade
        if (this.minIVRank != null && ivRank < this.minIVRank) return false;
        if (this.maxIVRank != null && ivRank > this.maxIVRank) return false;
        return true;
    }
}
