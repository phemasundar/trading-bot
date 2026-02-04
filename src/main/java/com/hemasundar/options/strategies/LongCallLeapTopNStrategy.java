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

        // Step 2: Progressive relaxation of QUALITY filters only
        // Hard requirements (minDTE, ignoreEarnings, delta, etc.) are NEVER relaxed
        List<TradeSetup> allTrades = new ArrayList<>(trades);

        // Level 1: Relax CAGR requirement (lowest sort priority)
        if (allTrades.size() < topN) {
            log.info("[{}] Applying Level 1 relaxation (maxCAGRForBreakEven)", chain.getSymbol());
            List<TradeSetup> level1Trades = findTradesWithRelaxation(chain, filter, RelaxationLevel.LEVEL_1);
            allTrades = combineAndDeduplicate(allTrades, level1Trades);
            log.info("[{}] Total trades after Level 1: {}", chain.getSymbol(), allTrades.size());
        }

        // Level 2: Also relax option price percentage
        if (allTrades.size() < topN) {
            log.info("[{}] Applying Level 2 relaxation (+ maxOptionPricePercent)", chain.getSymbol());
            List<TradeSetup> level2Trades = findTradesWithRelaxation(chain, filter, RelaxationLevel.LEVEL_2);
            allTrades = combineAndDeduplicate(allTrades, level2Trades);
            log.info("[{}] Total trades after Level 2: {}", chain.getSymbol(), allTrades.size());
        }

        // Level 3: Final relaxation - also relax cost savings requirement
        if (allTrades.size() < topN) {
            log.info("[{}] Applying Level 3 relaxation (+ minCostSavingsPercent)", chain.getSymbol());
            List<TradeSetup> level3Trades = findTradesWithRelaxation(chain, filter, RelaxationLevel.LEVEL_3);
            allTrades = combineAndDeduplicate(allTrades, level3Trades);
            log.info("[{}] Total trades after Level 3: {}", chain.getSymbol(), allTrades.size());
        }

        // Sort and return top N from combined list
        return getTopNTrades(allTrades, topN);
    }

    /**
     * Relaxation levels for progressive quality filter loosening.
     * Hard requirements (minDTE, ignoreEarnings, delta) are NEVER relaxed.
     */
    private enum RelaxationLevel {
        LEVEL_1, // Relax maxCAGRForBreakEven only
        LEVEL_2, // + Relax maxOptionPricePercent
        LEVEL_3 // + Relax minCostSavingsPercent
    }

    /**
     * Finds trades with relaxed quality filters based on the specified relaxation
     * level.
     * Hard requirements (minDTE, maxDTE, ignoreEarnings, delta) are preserved at
     * all levels.
     */
    private List<TradeSetup> findTradesWithRelaxation(OptionChainResponse chain,
            OptionsStrategyFilter filter,
            RelaxationLevel level) {
        if (!(filter instanceof LongCallLeapFilter originalFilter)) {
            return new ArrayList<>();
        }

        // Start with all base filters preserved
        LongCallLeapFilter.LongCallLeapFilterBuilder builder = LongCallLeapFilter.builder()
                .minDTE(filter.getMinDTE()) // NEVER relaxed
                .maxDTE(filter.getMaxDTE()) // NEVER relaxed
                .targetDTE(filter.getTargetDTE()) // NEVER relaxed
                .ignoreEarnings(filter.isIgnoreEarnings()) // NEVER relaxed
                .marginInterestRate(filter.getMarginInterestRate())
                .savingsInterestRate(filter.getSavingsInterestRate())
                .minHistoricalVolatility(filter.getMinHistoricalVolatility())
                .longCall(originalFilter.getLongCall()); // NEVER relaxed (delta constraints)

        // Progressive quality filter relaxation based on level
        switch (level) {
            case LEVEL_1:
                // Relax only CAGR (lowest sort priority)
                builder.minCostSavingsPercent(originalFilter.getMinCostSavingsPercent())
                        .maxOptionPricePercent(originalFilter.getMaxOptionPricePercent())
                        .maxCAGRForBreakEven(null); // Relaxed
                break;
            case LEVEL_2:
                // Also relax option price percentage
                builder.minCostSavingsPercent(originalFilter.getMinCostSavingsPercent())
                        .maxOptionPricePercent(null) // Relaxed
                        .maxCAGRForBreakEven(null); // Relaxed
                break;
            case LEVEL_3:
                // Relax all quality filters (last resort)
                builder.minCostSavingsPercent(null) // Relaxed
                        .maxOptionPricePercent(null) // Relaxed
                        .maxCAGRForBreakEven(null); // Relaxed
                break;
        }

        LongCallLeapFilter relaxedFilter = builder.build();
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
