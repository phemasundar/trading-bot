package com.hemasundar.options.strategies;

import com.hemasundar.options.models.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.stream.Collectors;

public class LongCallLeapStrategy extends AbstractTradingStrategy {

    public LongCallLeapStrategy() {
        super(StrategyType.LONG_CALL_LEAP);
    }

    // Protected constructor for subclasses (e.g., LongCallLeapTopNStrategy)
    protected LongCallLeapStrategy(StrategyType strategyType) {
        super(strategyType);
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
                // 3. Build Trade Setup
                .map(this::buildTradeSetup)
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

        double marginInterestAmountPerStock = 0.5 * currentPrice * (filter.getMarginInterestRate() / 100.0)
                * (dte / 365.0);
        double dividendAmountPerStock = currentPrice * (dividendYield / 100.0) * (dte / 365.0);
        double actualMoneySpentFromPocketPerStock = 0.5 * currentPrice;

        double costOfOptionBuyingPerStock = extrinsic + dividendAmountPerStock;

        double moneySpentExtraFromPocketPerStockForBuyingStock = actualMoneySpentFromPocketPerStock - callPremium;
        double interestEarningOnExtraMoneySpentForBuyingStock = moneySpentExtraFromPocketPerStockForBuyingStock
                * (filter.getSavingsInterestRate() / 100) * (dte / 365.0);
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
                filter.getMarginInterestRate(),
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
                .extrinsicValue(c.extrinsic())
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
