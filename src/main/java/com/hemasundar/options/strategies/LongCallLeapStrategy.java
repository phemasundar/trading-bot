package com.hemasundar.options.strategies;

import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.LongCallLeap;
import com.hemasundar.options.models.LongCallLeapFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

public class LongCallLeapStrategy extends AbstractTradingStrategy {

    public LongCallLeapStrategy() {
        super(StrategyType.LONG_CALL_LEAP);
    }

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        // LEAPS usually span multiple expiries, and we filter by minDTE.
        // We override findTrades to handle iterating through multiple expiries.

        List<TradeSetup> leaps = new ArrayList<>();

        if (chain.getCallExpDateMap() == null)
            return leaps;

        chain.getCallExpDateMap().forEach((key, strikeMap) -> {
            if (key.getDaysToExpiry() > filter.getMinDTE()) {
                List<TradeSetup> trades = findValidLeapsForExpiry(strikeMap, chain.getUnderlyingPrice(),
                        key.getDaysToExpiry(), chain.getDividendYield(), filter);
                leaps.addAll(trades);
            }
        });

        return leaps;
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        // Not used directly as we override findTrades, but required by abstract class.
        return new ArrayList<>();
    }

    private List<TradeSetup> findValidLeapsForExpiry(Map<String, List<OptionChainResponse.OptionData>> strikeMap,
            double currentPrice, int dte, double dividendYield, OptionsStrategyFilter filter) {
        List<TradeSetup> setups = new ArrayList<>();

        // Get leg filter if available
        LegFilter finalLongCallFilter = (filter instanceof LongCallLeapFilter)
                ? ((LongCallLeapFilter) filter).getLongCall()
                : null;

        // Filter calls based on comprehensive LegFilter criteria
        List<OptionChainResponse.OptionData> filteredCalls = strikeMap.values().stream()
                .flatMap(List::stream)
                .filter(call -> {
                    // DTE filter (if targetDTE is set/positive)
                    if (filter.getTargetDTE() > 0 && call.getDaysToExpiration() < filter.getTargetDTE()) {
                        return false;
                    }

                    // Comprehensive filter - validates ALL fields (delta, premium, volume, open
                    // interest)
                    if (!LegFilter.passes(finalLongCallFilter, call)) {
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());

        filteredCalls.forEach(call -> {
            double callPremium = call.getAsk();
            double strikePrice = call.getStrikePrice();

            // Extrinsic Value = Ask - (Underlying - Strike)
            // Only for ITM calls. If OTM, Extrinsic = Ask.
            double intrinsic = Math.max(0, currentPrice - strikePrice);
            double extrinsic = callPremium - intrinsic;

            // Option Premium Limit Check
            if (callPremium > (currentPrice * (filter.getMaxOptionPricePercent() / 100.0))) {
                return;
            }

            double marginInterestAmountPerStock = 0.5 * currentPrice * (filter.getMarginInterestRate() / 100.0)
                    * (dte / 365.0);
            double dividendAmountPerStock = currentPrice * (dividendYield / 100.0) * (dte / 365.0);
            double actualMoneySpentFromPocketPerStock = 0.5 * currentPrice;

            double costOfOptionBuyingPerStock = extrinsic + dividendAmountPerStock;

            double moneySpentExtraFromPocketPerStockForBuyingStock = actualMoneySpentFromPocketPerStock
                    - callPremium;
            double interestEarningOnExtraMoneySpentForBuyingStock = moneySpentExtraFromPocketPerStockForBuyingStock
                    * (filter.getSavingsInterestRate() / 100) * (dte / 365.0);
            double costOfBuyingPerStock = marginInterestAmountPerStock
                    + interestEarningOnExtraMoneySpentForBuyingStock;

            if (costOfOptionBuyingPerStock <= costOfBuyingPerStock * (90 / 100)) {
                double netCredit = -callPremium * 100; // Debit
                double breakEven = strikePrice + callPremium;
                double breakEvenPct = ((breakEven - currentPrice) / currentPrice) * 100;

                setups.add(LongCallLeap.builder()
                        .longCall(call)
                        .breakEvenPrice(breakEven)
                        .breakEvenPercentage(breakEvenPct)
                        .extrinsicValue(extrinsic)
                        .finalCostOfOption(costOfOptionBuyingPerStock)
                        .finalCostOfBuying(costOfBuyingPerStock)
                        .dividendYield(dividendYield)
                        .interestRatePaidForMargin(filter.getMarginInterestRate())
                        .netCredit(netCredit)
                        .maxLoss(callPremium * 100)
                        .currentPrice(currentPrice)
                        .build());
            }
        });

        return setups;
    }
}
