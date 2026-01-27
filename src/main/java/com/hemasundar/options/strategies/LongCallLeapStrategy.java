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
                .filter(costEfficiencyFilter(filter))
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
                filter.getMarginInterestRate()));
    }

    private TradeSetup buildTradeSetup(LeapCandidate c) {
        double netCredit = -c.callPremium() * 100; // Debit
        double breakEven = c.strikePrice() + c.callPremium();
        double breakEvenPct = ((breakEven - c.currentPrice()) / c.currentPrice()) * 100;

        return LongCallLeap.builder()
                .longCall(c.call())
                .breakEvenPrice(breakEven)
                .breakEvenPercentage(breakEvenPct)
                .extrinsicValue(c.extrinsic())
                .finalCostOfOption(c.costOfOptionBuyingPerStock())
                .finalCostOfBuying(c.costOfBuyingPerStock())
                .dividendYield(c.dividendYield())
                .interestRatePaidForMargin(c.marginInterestRate())
                .netCredit(netCredit)
                .maxLoss(c.callPremium() * 100)
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== FILTER PREDICATES ==========

    private java.util.function.Predicate<LeapCandidate> deltaFilter(LegFilter filter) {
        return c -> LegFilter.passes(filter, c.call());
    }

    private java.util.function.Predicate<LeapCandidate> premiumLimitFilter(OptionsStrategyFilter filter) {
        return c -> {
            double maxPrice = c.currentPrice() * (filter.getMaxOptionPricePercent() / 100.0);
            return c.callPremium() <= maxPrice;
        };
    }

    private java.util.function.Predicate<LeapCandidate> costEfficiencyFilter(OptionsStrategyFilter filter) {
        return c -> {
            // Check if Buying Option is significantly cheaper (90%) than Buying Stock on
            // Margin
            return c.costOfOptionBuyingPerStock() <= c.costOfBuyingPerStock() * (90.0 / 100.0);
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
            double marginInterestRate) {
    }
}
