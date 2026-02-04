package com.hemasundar.options.strategies;

import com.hemasundar.options.models.*;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Top N variant of Long Call LEAP strategy.
 * Returns the best N trades per stock, with fallback mechanism when strict
 * filters yield insufficient trades.
 */
@Log4j2
public class LongCallLeapTopNStrategy extends LongCallLeapStrategy {

    public LongCallLeapTopNStrategy() {
        super(StrategyType.LONG_CALL_LEAP_TOP_N);
    }

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        // Get topTradesCount from filter (default to 3)
        int topN = 3; // default
        if (filter instanceof LongCallLeapFilter leapFilter) {
            if (leapFilter.getTopTradesCount() != null) {
                topN = leapFilter.getTopTradesCount();
            }
        }

        log.info("[{}] Finding Top {} Long LEAP trades", chain.getSymbol(), topN);

        // Step 1: Try with strict filters
        List<TradeSetup> trades = super.findTrades(chain, filter);
        log.info("[{}] Found {} trades with strict filters", chain.getSymbol(), trades.size());

        if (trades.size() >= topN) {
            return getTopNTrades(trades, topN);
        }

        // Step 2: Check if progressive relaxation is enabled
        java.util.List<String> relaxationOrder = getRelaxationPriority(filter);

        if (relaxationOrder == null || relaxationOrder.isEmpty()) {
            // No relaxation configured - return strict results only
            log.info("[{}] No relaxationPriority configured. Returning {} strict trades (target was {})",
                    chain.getSymbol(), trades.size(), topN);
            return getTopNTrades(trades, topN);
        }

        // Progressive relaxation is enabled
        log.info("[{}] Applying progressive relaxation with order: {}", chain.getSymbol(), relaxationOrder);
        List<TradeSetup> allTrades = new ArrayList<>(trades);

        // Apply relaxation levels based on configured priority
        for (int i = 0; i < relaxationOrder.size() && allTrades.size() < topN; i++) {
            String filterToRelax = relaxationOrder.get(i);
            log.info("[{}] Applying relaxation level {} ({})", chain.getSymbol(), i + 1, filterToRelax);

            List<TradeSetup> relaxedTrades = findTradesWithRelaxation(chain, filter, relaxationOrder.subList(0, i + 1));
            allTrades = combineAndDeduplicate(allTrades, relaxedTrades);
            log.info("[{}] Total trades after level {}: {}", chain.getSymbol(), i + 1, allTrades.size());
        }

        // Sort and return top N from combined list
        return getTopNTrades(allTrades, topN);
    }

    /**
     * Gets the relaxation priority from filter configuration.
     * Returns null if not configured (no relaxation should be applied).
     */
    private java.util.List<String> getRelaxationPriority(OptionsStrategyFilter filter) {
        if (filter instanceof LongCallLeapFilter leapFilter) {
            return leapFilter.getRelaxationPriority();
        }
        return null;
    }

    /**
     * Finds trades with relaxed quality filters based on the specified filters to
     * relax.
     * Hard requirements (minDTE, maxDTE, ignoreEarnings, delta) are preserved at
     * all levels.
     * 
     * @param chain          Option chain response
     * @param filter         Original filter
     * @param filtersToRelax List of filter names to relax (null values)
     */
    private List<TradeSetup> findTradesWithRelaxation(OptionChainResponse chain,
            OptionsStrategyFilter filter,
            java.util.List<String> filtersToRelax) {
        if (!(filter instanceof LongCallLeapFilter originalFilter)) {
            return new ArrayList<>();
        }

        // Apply relaxations based on configured list
        boolean relaxCAGR = filtersToRelax.contains("maxCAGRForBreakEven");
        boolean relaxOptionPrice = filtersToRelax.contains("maxOptionPricePercent");
        boolean relaxCostSavings = filtersToRelax.contains("minCostSavingsPercent");

        // Build relaxed filter in one chain (parent fields first, then child fields)
        LongCallLeapFilter relaxedFilter = LongCallLeapFilter.builder()
                // Parent class fields (OptionsStrategyFilter)
                .minDTE(filter.getMinDTE())
                .maxDTE(filter.getMaxDTE())
                .targetDTE(filter.getTargetDTE())
                .ignoreEarnings(filter.isIgnoreEarnings())
                .marginInterestRate(filter.getMarginInterestRate())
                .savingsInterestRate(filter.getSavingsInterestRate())
                .minHistoricalVolatility(filter.getMinHistoricalVolatility())
                .maxCAGRForBreakEven(relaxCAGR ? null : originalFilter.getMaxCAGRForBreakEven())
                .maxOptionPricePercent(relaxOptionPrice ? null : originalFilter.getMaxOptionPricePercent())
                // Child class fields (LongCallLeapFilter)
                .longCall(originalFilter.getLongCall())
                .minCostSavingsPercent(relaxCostSavings ? null : originalFilter.getMinCostSavingsPercent())
                .build();

        return super.findTrades(chain, relaxedFilter);
    }

    /**
     * Combines two lists and removes duplicates based on expiry date and strike
     * price.
     */
    private List<TradeSetup> combineAndDeduplicate(List<TradeSetup> list1, List<TradeSetup> list2) {
        List<TradeSetup> combined = new ArrayList<>(list1);

        for (TradeSetup trade : list2) {
            if (trade instanceof LongCallLeap relaxedLeap) {
                boolean isDuplicate = list1.stream()
                        .filter(t -> t instanceof LongCallLeap)
                        .map(t -> (LongCallLeap) t)
                        .anyMatch(strictLeap -> strictLeap.getExpiryDate().equals(relaxedLeap.getExpiryDate()) &&
                                strictLeap.getLongCall().getStrikePrice() == relaxedLeap.getLongCall()
                                        .getStrikePrice());

                if (!isDuplicate) {
                    combined.add(trade);
                }
            }
        }

        return combined;
    }

    /**
     * Sorts trades by priority criteria and returns top N.
     * Priority order is configurable via filter.sortPriority.
     * 
     * Default priority:
     * 1. daysToExpiration (descending - higher/longer is better)
     * 2. costSavingsPercent (descending - higher is better)
     * 3. optionPricePercent (ascending - lower is better)
     * 4. breakevenCAGR (ascending - lower is better)
     */
    private List<TradeSetup> getTopNTrades(List<TradeSetup> trades, int topN) {
        // Get sort priority from filter or use default
        java.util.List<String> sortOrder = getDefaultSortPriority();

        // Build dynamic comparator
        Comparator<LongCallLeap> comparator = buildSortComparator(sortOrder);

        return trades.stream()
                .filter(t -> t instanceof LongCallLeap)
                .map(t -> (LongCallLeap) t)
                .sorted(comparator)
                .limit(topN)
                .collect(Collectors.toList());
    }

    private java.util.List<String> getDefaultSortPriority() {
        return java.util.Arrays.asList(
                "daysToExpiration",
                "costSavingsPercent",
                "optionPricePercent",
                "breakevenCAGR");
    }

    private Comparator<LongCallLeap> buildSortComparator(java.util.List<String> sortOrder) {
        Comparator<LongCallLeap> comparator = null;

        for (String field : sortOrder) {
            Comparator<LongCallLeap> fieldComparator = getComparatorForField(field);
            comparator = (comparator == null) ? fieldComparator : comparator.thenComparing(fieldComparator);
        }

        return comparator != null ? comparator : Comparator.comparingInt(LongCallLeap::getDaysToExpiration).reversed();
    }

    private Comparator<LongCallLeap> getComparatorForField(String field) {
        return switch (field) {
            case "daysToExpiration" -> Comparator.comparingInt(LongCallLeap::getDaysToExpiration).reversed();
            case "costSavingsPercent" -> Comparator.comparingDouble(LongCallLeap::getCostSavingsPercent).reversed();
            case "optionPricePercent" -> Comparator.comparingDouble(LongCallLeap::getOptionPricePercent);
            case "breakevenCAGR" -> Comparator.comparingDouble(LongCallLeap::getBreakevenCAGR);
            default -> {
                log.warn("Unknown sort field: {}. Using daysToExpiration.", field);
                yield Comparator.comparingInt(LongCallLeap::getDaysToExpiration).reversed();
            }
        };
    }
}
