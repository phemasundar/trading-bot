package com.hemasundar.options.strategies;

import com.hemasundar.options.models.BrokenWingButterfly;
import com.hemasundar.options.models.BrokenWingButterflyFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BrokenWingButterflyStrategy extends AbstractTradingStrategy {

    public BrokenWingButterflyStrategy() {
        super(StrategyType.BULLISH_BROKEN_WING_BUTTERFLY);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionChainResponse.OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL,
                expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        return findValidBrokenWingButterflies(callMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidBrokenWingButterflies(
            Map<String, List<OptionChainResponse.OptionData>> callMap,
            double currentPrice, OptionsStrategyFilter filter) {
        List<TradeSetup> trades = new ArrayList<>();

        // Get leg filters if available
        LegFilter leg1Filter = null;
        LegFilter leg2Filter = null;
        LegFilter leg3Filter = null;
        if (filter instanceof BrokenWingButterflyFilter) {
            BrokenWingButterflyFilter bwbFilter = (BrokenWingButterflyFilter) filter;
            leg1Filter = bwbFilter.getLeg1Long();
            leg2Filter = bwbFilter.getLeg2Short();
            leg3Filter = bwbFilter.getLeg3Long();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        // Leg 1: Buy 1 Call (Lower Strike)
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double leg1StrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> leg1Options = callMap.get(String.valueOf(leg1StrikePrice));
            if (CollectionUtils.isEmpty(leg1Options))
                continue;
            OptionChainResponse.OptionData leg1 = leg1Options.get(0);

            // Leg 1 Delta Check (null-safe)
            if (leg1Filter != null && !leg1Filter.passesMinDelta(leg1.getAbsDelta()))
                continue;

            // Leg 2: Sell 2 Calls (Middle Strike)
            for (int j = i + 1; j < sortedStrikes.size(); j++) {
                double leg2StrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> leg2Options = callMap.get(String.valueOf(leg2StrikePrice));
                if (CollectionUtils.isEmpty(leg2Options))
                    continue;
                OptionChainResponse.OptionData leg2 = leg2Options.get(0);

                // Leg 2 Delta Check (null-safe)
                if (leg2Filter != null && !leg2Filter.passesMaxDelta(leg2.getAbsDelta()))
                    continue;

                // Leg 3: Buy 1 Call (Higher Strike, Protection)
                for (int k = j + 1; k < sortedStrikes.size(); k++) {
                    double leg3StrikePrice = sortedStrikes.get(k);
                    List<OptionChainResponse.OptionData> leg3Options = callMap.get(String.valueOf(leg3StrikePrice));
                    if (CollectionUtils.isEmpty(leg3Options))
                        continue;
                    OptionChainResponse.OptionData leg3 = leg3Options.get(0);

                    // Leg 3 Delta Check (null-safe)
                    if (leg3Filter != null && !leg3Filter.passesDeltaFilter(leg3.getAbsDelta()))
                        continue;

                    // Calculate wing widths
                    double lowerWingWidth = (leg2StrikePrice - leg1StrikePrice) * 100;
                    double upperWingWidth = (leg3StrikePrice - leg2StrikePrice) * 100;

                    // Calculate Debit: Buy 1 Leg1 @ Ask, Sell 2 Leg2 @ Bid, Buy 1 Leg3 @ Ask
                    double totalDebit = (leg1.getAsk() + leg3.getAsk() - (leg2.getBid() * 2)) * 100;

                    // Check max total debit filter
                    if (filter.getMaxTotalDebit() > 0 && totalDebit > filter.getMaxTotalDebit())
                        continue;

                    // Max Loss Calculations
                    // Max Loss (Upside): (Upper Wing Width − Lower Wing Width) + Debit Paid
                    double maxLossUpside = (upperWingWidth - lowerWingWidth) + totalDebit;

                    // Max Loss (Downside): Debit Paid
                    double maxLossDownside = totalDebit;

                    // Actual Max Loss: Max of Upside & Downside
                    double maxLoss = Math.max(maxLossUpside, maxLossDownside);

                    // Check max loss limit from filter
                    if (filter.getMaxLossLimit() > 0 && maxLoss > filter.getMaxLossLimit())
                        continue;

                    // Calculate return on risk (max profit / max risk)
                    double maxProfit = lowerWingWidth - totalDebit;
                    double returnOnRisk = (maxProfit > 0 && maxLoss > 0) ? (maxProfit / maxLoss) * 100 : 0;

                    trades.add(BrokenWingButterfly.builder()
                            .leg1LongCall(leg1)
                            .leg2ShortCalls(leg2)
                            .leg3LongCall(leg3)
                            .lowerWingWidth(lowerWingWidth)
                            .upperWingWidth(upperWingWidth)
                            .totalDebit(totalDebit)
                            .maxLossUpside(maxLossUpside)
                            .maxLossDownside(maxLossDownside)
                            .maxLoss(maxLoss)
                            .returnOnRisk(returnOnRisk)
                            .build());
                }
            }
        }
        return trades;
    }
}
