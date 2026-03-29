package com.hemasundar.options.strategies;

import com.hemasundar.options.models.*;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Unified Long Call LEAP strategy.
 * Supports both strict filtering and Top N ranked results with progressive
 * relaxation.
 */
@Log4j2
public class LongCallLeapStrategy extends AbstractTradingStrategy {

    public LongCallLeapStrategy() {
        super(StrategyType.LONG_CALL_LEAP);
    }

    // Constructor for backward compatibility if needed, though mostly using the default
    protected LongCallLeapStrategy(StrategyType strategyType) {
        super(strategyType);
    }

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        // Step 1: Find strict trades using the base strategy logic (findValidTrades)
        List<TradeSetup> strictTrades = super.findTrades(chain, filter);
        log.info("[{}] Found {} trades with strict filters", chain.getSymbol(), strictTrades.size());

        // Step 2: Identify target N from filter
        Integer topTradesCount = null;
        if (filter instanceof LongCallLeapFilter leapFilter) {
            topTradesCount = leapFilter.getTopTradesCount();
        }

        // Case A: No limit set - return all strict trades (sorted using strategy preference)
        if (topTradesCount == null) {
            log.info("[{}] No topTradesCount limit set. Returning all {} strict trades.",
                    chain.getSymbol(), strictTrades.size());
            return getTopNTrades(strictTrades, Integer.MAX_VALUE, filter);
        }

        // Case B: Limit set - apply Top N logic with optional relaxation
        int topN = topTradesCount;
        log.info("[{}] Finding Top {} Long LEAP trades", chain.getSymbol(), topN);

        // If we already have enough trades meeting strict criteria, return them
        if (strictTrades.size() >= topN) {
            return getTopNTrades(strictTrades, topN, filter);
        }

        // Step 3: Progressive relaxation
        java.util.List<String> relaxationOrder = getRelaxationPriority(filter);

        if (relaxationOrder == null || relaxationOrder.isEmpty()) {
            // No relaxation configured - return whatever strict results we found
            log.info("[{}] No relaxationPriority configured. Returning {} strict trades (target was {})",
                    chain.getSymbol(), strictTrades.size(), topN);
            return getTopNTrades(strictTrades, topN, filter);
        }

        // Progressive relaxation is enabled
        log.info("[{}] Applying progressive relaxation with order: {}", chain.getSymbol(), relaxationOrder);
        List<TradeSetup> allTrades = new ArrayList<>(strictTrades);

        // Apply relaxation levels based on configured priority until target N is reached
        for (int i = 0; i < relaxationOrder.size() && allTrades.size() < topN; i++) {
            String filterToRelax = relaxationOrder.get(i);
            log.info("[{}] Applying relaxation level {} ({})", chain.getSymbol(), i + 1, filterToRelax);

            List<TradeSetup> relaxedTrades = findTradesWithRelaxation(chain, filter, relaxationOrder.subList(0, i + 1));
            allTrades = combineAndDeduplicate(allTrades, relaxedTrades);
            log.info("[{}] Total trades after level {}: {}", chain.getSymbol(), i + 1, allTrades.size());
        }

        // Sort and return top N from the combined (strict + relaxed) list
        return getTopNTrades(allTrades, topN, filter);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {

        Map<String, List<OptionChainResponse.OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        // Flatten the map to get all calls for this expiry
        List<OptionChainResponse.OptionData> calls = callMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Get leg filter if available
        LegFilter finalLongCallFilter = (filter instanceof LongCallLeapFilter)
                ? ((LongCallLeapFilter) filter).getLongCall()
                : null;

        return calls.stream()
                // 1. Initial Candidate Generation (Map to Candidate)
                .map(call -> createCandidate(call, chain.getUnderlyingPrice(), chain.getDividendYield(), filter))
                .filter(Optional::isPresent)
                .map(Optional::get)
                // 2. Apply Filters
                .filter(deltaFilter(finalLongCallFilter))
                .filter(premiumLimitFilter(filter))
                .filter(maxLossFilter(filter))
                .filter(costEfficiencyFilter(filter))
                .filter(cagrFilter(filter))
                .filter(costSavingsFilter(filter))
                .filter(candidate -> filter.passesDebitLimit(candidate.callPremium() * 100))
                // 3. Build Trade Setup
                .map(this::buildTradeSetup)
                .filter(commonMaxNetExtrinsicValueToPricePercentageFilter(filter))
                .filter(commonMinNetExtrinsicValueToPricePercentageFilter(filter))
                .filter(trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .collect(Collectors.toList());
    }

    private Optional<LeapCandidate> createCandidate(OptionChainResponse.OptionData call,
            double currentPrice, double dividendYield, OptionsStrategyFilter filter) {

        double callPremium = call.getAsk();
        double strikePrice = call.getStrikePrice();
        int dte = call.getDaysToExpiration();

        // Extrinsic Value = Ask - (Underlying - Strike)
        // Only for ITM calls. If OTM, Extrinsic = Ask.
        double intrinsic = Math.max(0, currentPrice - strikePrice);
        double extrinsic = callPremium - intrinsic;

        double marginRate = filter.getMarginInterestRate() != null ? filter.getMarginInterestRate() : 0.0;
        double savingsRate = filter.getSavingsInterestRate() != null ? filter.getSavingsInterestRate() : 0.0;

        double marginInterestAmountPerStock = 0.5 * currentPrice * (marginRate / 100.0)
                * (dte / 365.0);
        double dividendAmountPerStock = currentPrice * (dividendYield / 100.0) * (dte / 365.0);
        double actualMoneySpentFromPocketPerStock = 0.5 * currentPrice;

        double costOfOptionBuyingPerStock = extrinsic + dividendAmountPerStock;

        double moneySpentExtraFromPocketPerStockForBuyingStock = actualMoneySpentFromPocketPerStock - callPremium;
        double interestEarningOnExtraMoneySpentForBuyingStock = moneySpentExtraFromPocketPerStockForBuyingStock
                * (savingsRate / 100) * (dte / 365.0);
        double costOfBuyingPerStock = marginInterestAmountPerStock + interestEarningOnExtraMoneySpentForBuyingStock;

        double breakEven = strikePrice + callPremium;
        double breakEvenPct = ((breakEven - currentPrice) / currentPrice) * 100;

        double yearsToExpiration = dte / 365.0;
        double growthFactor = 1 + (breakEvenPct / 100.0);
        double breakevenCAGR = (Math.pow(growthFactor, 1.0 / yearsToExpiration) - 1) * 100.0;

        // Calculate cost savings percentage: how much cheaper is option vs stock
        double costSavingsPercent = 0.0;
        if (costOfBuyingPerStock > 0) {
            costSavingsPercent = ((costOfBuyingPerStock - costOfOptionBuyingPerStock) / costOfBuyingPerStock) * 100.0;
        }

        return Optional.of(new LeapCandidate(
                call,
                currentPrice,
                dividendYield,
                callPremium,
                strikePrice,
                dte,
                extrinsic,
                costOfOptionBuyingPerStock,
                costOfBuyingPerStock,
                filter.getMarginInterestRate() != null ? filter.getMarginInterestRate() : 0.0,
                breakEvenPct,
                breakevenCAGR,
                costSavingsPercent));
    }

    private TradeSetup buildTradeSetup(LeapCandidate c) {
        double netCredit = -c.callPremium() * 100; // Debit
        double breakEven = c.strikePrice() + c.callPremium();

        return LongCallLeap.builder()
                .longCall(c.call())
                .breakEvenPrice(breakEven)
                .breakEvenPercentage(c.breakEvenPct())
                .extrinsicValue(c.extrinsic() * 100) // per-contract to match maxLoss
                .finalCostOfOption(c.costOfOptionBuyingPerStock())
                .finalCostOfBuying(c.costOfBuyingPerStock())
                .dividendYield(c.dividendYield())
                .interestRatePaidForMargin(c.marginInterestRate())
                .netCredit(netCredit)
                .maxLoss(c.callPremium() * 100)
                .currentPrice(c.currentPrice())
                .costSavingsPercent(c.costSavingsPercent())
                .breakevenCAGR(c.breakevenCAGR())
                .build();
    }

    /**
     * Gets the relaxation priority from filter configuration.
     */
    private java.util.List<String> getRelaxationPriority(OptionsStrategyFilter filter) {
        if (filter instanceof LongCallLeapFilter leapFilter) {
            return leapFilter.getRelaxationPriority();
        }
        return null;
    }

    /**
     * Finds trades with relaxed filters.
     */
    private List<TradeSetup> findTradesWithRelaxation(OptionChainResponse chain,
            OptionsStrategyFilter filter,
            java.util.List<String> filtersToRelax) {
        if (!(filter instanceof LongCallLeapFilter originalFilter)) {
            return new ArrayList<>();
        }

        boolean relaxCAGR = filtersToRelax.contains("maxCAGRForBreakEven");
        boolean relaxOptionPrice = filtersToRelax.contains("maxOptionPricePercent");
        boolean relaxCostSavings = filtersToRelax.contains("minCostSavingsPercent");

        LongCallLeapFilter relaxedFilter = LongCallLeapFilter.builder()
                .minDTE(filter.getMinDTE())
                .maxDTE(filter.getMaxDTE())
                .targetDTE(filter.getTargetDTE())
                .ignoreEarnings(filter.isIgnoreEarnings())
                .marginInterestRate(filter.getMarginInterestRate())
                .savingsInterestRate(filter.getSavingsInterestRate())
                .minHistoricalVolatility(filter.getMinHistoricalVolatility())
                .maxLossLimit(filter.getMaxLossLimit())
                .maxCAGRForBreakEven(relaxCAGR ? null : originalFilter.getMaxCAGRForBreakEven())
                .maxOptionPricePercent(relaxOptionPrice ? null : originalFilter.getMaxOptionPricePercent())
                .longCall(originalFilter.getLongCall())
                .minCostSavingsPercent(relaxCostSavings ? null : originalFilter.getMinCostSavingsPercent())
                .build();

        return super.findTrades(chain, relaxedFilter);
    }

    /**
     * Combines lists and removes duplicates.
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
     */
    private List<TradeSetup> getTopNTrades(List<TradeSetup> trades, int topN, OptionsStrategyFilter filter) {
        // Get sort priority from filter or use default
        java.util.List<String> sortOrder = null;
        if (filter instanceof LongCallLeapFilter leapFilter) {
            sortOrder = leapFilter.getSortPriority();
        }
        
        if (sortOrder == null || sortOrder.isEmpty()) {
            sortOrder = getDefaultSortPriority();
        }

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

    // ========== FILTER PREDICATES ==========

    private java.util.function.Predicate<LeapCandidate> deltaFilter(LegFilter filter) {
        return c -> LegFilter.passes(filter, c.call());
    }

    private java.util.function.Predicate<LeapCandidate> premiumLimitFilter(OptionsStrategyFilter filter) {
        return c -> {
            if (filter.getMaxOptionPricePercent() == null) {
                return true; // No limit
            }
            double maxPrice = c.currentPrice() * (filter.getMaxOptionPricePercent() / 100.0);
            return c.callPremium() <= maxPrice;
        };
    }

    private java.util.function.Predicate<LeapCandidate> maxLossFilter(OptionsStrategyFilter filter) {
        return c -> {
            double maxLoss = c.callPremium() * 100; // Max loss for Long Call LEAP
            return filter.passesMaxLoss(maxLoss);
        };
    }

    private java.util.function.Predicate<LeapCandidate> costEfficiencyFilter(OptionsStrategyFilter filter) {
        return c -> {
            // Optional filter: Check if buying option is cheaper than buying stock on
            // margin
            // Only apply if explicitly configured in LongCallLeapFilter
            if (filter instanceof LongCallLeapFilter leapFilter) {
                Double minEfficiency = leapFilter.getMinCostEfficiencyPercent();
                if (minEfficiency != null) {
                    double efficiencyThreshold = c.costOfBuyingPerStock() * (minEfficiency / 100.0);
                    return c.costOfOptionBuyingPerStock() <= efficiencyThreshold;
                }
            }
            return true; // No efficiency filter set, pass all
        };
    }

    private java.util.function.Predicate<LeapCandidate> cagrFilter(OptionsStrategyFilter filter) {
        return c -> {
            if (filter.getMaxCAGRForBreakEven() == null) {
                return true;
            }
            return c.breakevenCAGR() <= filter.getMaxCAGRForBreakEven();
        };
    }

    private java.util.function.Predicate<LeapCandidate> costSavingsFilter(OptionsStrategyFilter filter) {
        return c -> {
            // Only apply if filter is LongCallLeapFilter and minCostSavingsPercent is set
            if (filter instanceof LongCallLeapFilter leapFilter) {
                Double minSavings = leapFilter.getMinCostSavingsPercent();
                if (minSavings != null) {
                    return c.costSavingsPercent() >= minSavings;
                }
            }
            return true; // No filter set, pass all trades
        };
    }

    // ========== CANDIDATE RECORD ==========

    private record LeapCandidate(
            OptionChainResponse.OptionData call,
            double currentPrice,
            double dividendYield,
            double callPremium,
            double strikePrice,
            int dte,
            double extrinsic,
            double costOfOptionBuyingPerStock,
            double costOfBuyingPerStock,
            double marginInterestRate,
            double breakEvenPct,
            double breakevenCAGR,
            double costSavingsPercent) {
    }
}
