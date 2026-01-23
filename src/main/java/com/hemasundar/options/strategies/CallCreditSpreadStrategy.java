package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CallCreditSpread;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.OptionType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CallCreditSpreadStrategy extends AbstractTradingStrategy {

    public CallCreditSpreadStrategy() {
        super(StrategyType.CALL_CREDIT_SPREAD);
    }

    public CallCreditSpreadStrategy(StrategyType strategyType) {
        super(strategyType);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionChainResponse.OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL,
                expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        return findValidCallCreditSpreads(callMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidCallCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> callMap,
            double currentPrice, OptionsStrategyFilter filter) {
        List<TradeSetup> spreads = new ArrayList<>();

        // Get leg filters if available
        LegFilter shortLegFilter = null;
        LegFilter longLegFilter = null;
        if (filter instanceof CreditSpreadFilter) {
            CreditSpreadFilter csFilter = (CreditSpreadFilter) filter;
            shortLegFilter = csFilter.getShortLeg();
            longLegFilter = csFilter.getLongLeg();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        // For Call Spreads, Short Call lower strike, Long Call higher strike
        // Iterate for Short Call
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> options = callMap.get(String.valueOf(shortStrikePrice));
            if (CollectionUtils.isEmpty(options))
                continue;
            OptionChainResponse.OptionData shortCall = options.get(0);

            // Short Call should be OTM (Strike > Current Price)
            if (shortStrikePrice <= currentPrice)
                continue;

            // Short leg delta filter (null-safe)
            if (shortLegFilter != null && !shortLegFilter.passesMaxDelta(shortCall.getAbsDelta()))
                continue;

            // Iterate for Long Call (Higher Strike)
            for (int j = i + 1; j < sortedStrikes.size(); j++) {
                double longStrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> longOptions = callMap.get(String.valueOf(longStrikePrice));
                if (CollectionUtils.isEmpty(longOptions))
                    continue;
                OptionChainResponse.OptionData longCall = longOptions.get(0);

                // Long leg delta filter (null-safe)
                if (longLegFilter != null && !longLegFilter.passesDeltaFilter(longCall.getAbsDelta()))
                    continue;

                double netCredit = (shortCall.getBid() - longCall.getAsk()) * 100;

                if (netCredit <= 0)
                    continue;

                double strikeWidth = (longStrikePrice - shortStrikePrice) * 100;
                double maxLoss = strikeWidth - netCredit;

                if (maxLoss > filter.getMaxLossLimit())
                    continue;

                double requiredProfit = maxLoss * ((double) filter.getMinReturnOnRisk() / 100);

                if (netCredit >= requiredProfit) {
                    double breakEvenPrice = shortCall.getStrikePrice() + (netCredit / 100);
                    double breakEvenPercentage = ((breakEvenPrice - currentPrice) / currentPrice) * 100;
                    double returnOnRisk = (netCredit / maxLoss) * 100;

                    spreads.add(CallCreditSpread.builder()
                            .shortCall(shortCall)
                            .longCall(longCall)
                            .netCredit(netCredit)
                            .maxLoss(maxLoss)
                            .breakEvenPrice(breakEvenPrice)
                            .breakEvenPercentage(breakEvenPercentage)
                            .returnOnRisk(returnOnRisk)
                            .currentPrice(currentPrice)
                            .build());
                }
            }
        }
        return spreads;
    }
}
