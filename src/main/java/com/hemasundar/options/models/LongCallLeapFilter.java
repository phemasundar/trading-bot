package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Filter for Long Call LEAP strategy.
 * Extends OptionsStrategyFilter with leg-specific filter for the long call.
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LongCallLeapFilter extends OptionsStrategyFilter {
    private LegFilter longCall; // The long call option (optional)

    /**
     * Minimum cost savings percentage required for the option route compared to
     * buying stock.
     * Example: If set to 10.0, the option must be at least 10% cheaper than buying
     * stock.
     */
    private Double minCostSavingsPercent;

    /**
     * Minimum cost efficiency percentage (optional).
     * If set, option must cost at most this % of stock buying cost.
     * Example: If set to 90.0, option cost must be ≤ 90% of stock buying cost.
     */
    private Double minCostEfficiencyPercent;

    /**
     * Number of top trades to return per stock (used by LONG_CALL_LEAP_TOP_N
     * strategy).
     * If null or not set, defaults to 3.
     */
    private Integer topTradesCount;

    /**
     * Controls the order in which filters are relaxed when fewer than N trades are
     * found.
     * List field names in order of relaxation (first = relax first, last = relax
     * last).
     * 
     * Valid values: "maxCAGRForBreakEven", "maxOptionPricePercent",
     * "minCostSavingsPercent"
     * 
     * Example: ["maxCAGRForBreakEven", "maxOptionPricePercent",
     * "minCostSavingsPercent"]
     * 
     * If null or not set, defaults to the order above.
     * Filters NOT in this list are considered hard filters and never relaxed.
     */
    private java.util.List<String> relaxationPriority;

    /**
     * Controls the order in which trades are sorted (most important first).
     * List field names in priority order.
     * 
     * Valid values: "daysToExpiration", "costSavingsPercent", "optionPricePercent",
     * "breakevenCAGR"
     * 
     * Example: ["daysToExpiration", "costSavingsPercent", "optionPricePercent",
     * "breakevenCAGR"]
     * 
     * If null or not set, defaults to the order above.
     */
    private java.util.List<String> sortPriority;
}
