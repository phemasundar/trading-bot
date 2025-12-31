package com.hemasundar.strategies;

import com.hemasundar.pojos.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PutCreditSpreadStrategy extends AbstractTradingStrategy {
    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate, StrategyFilter filter) {
        Map<String, List<OptionChainResponse.OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT,
                expiryDate);

        if (putMap == null || putMap.isEmpty())
            return new ArrayList<>();

        return findValidPutCreditSpreads(putMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidPutCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap,
            double currentPrice, StrategyFilter filter) {

        List<TradeSetup> spreads = new ArrayList<>();

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted().toList();

        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> options = putMap.get(String.valueOf(shortStrikePrice));
            if (CollectionUtils.isEmpty(options))
                continue;
            OptionChainResponse.OptionData shortPut = options.get(0);

            if (Math.abs(shortPut.getDelta()) > filter.getMaxDelta()) {
                continue;
            }

            for (int j = 0; j < i; j++) {
                double longStrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> longOptions = putMap.get(String.valueOf(longStrikePrice));
                if (CollectionUtils.isEmpty(longOptions))
                    continue;
                OptionChainResponse.OptionData longPut = longOptions.get(0);

                double netCredit = (shortPut.getBid() - longPut.getAsk()) * 100;

                if (netCredit <= 0)
                    continue;

                double strikeWidth = (shortStrikePrice - longStrikePrice) * 100;
                double maxLoss = strikeWidth - netCredit;

                if (maxLoss > filter.getMaxLossLimit())
                    continue;

                double requiredProfit = maxLoss * ((double) filter.getMinReturnOnRisk() / 100);

                if (netCredit >= requiredProfit) {
                    double breakEvenPrice = shortPut.getStrikePrice() - (netCredit / 100);
                    double breakEvenPercentage = ((currentPrice - breakEvenPrice) / currentPrice) * 100;
                    double returnOnRisk = (netCredit / maxLoss) * 100;

                    spreads.add(PutCreditSpread.builder()
                            .shortPut(shortPut)
                            .longPut(longPut)
                            .netCredit(netCredit)
                            .maxLoss(maxLoss)
                            .breakEvenPrice(breakEvenPrice)
                            .breakEvenPercentage(breakEvenPercentage)
                            .returnOnRisk(returnOnRisk)
                            .build());
                }
            }
        }
        return spreads;
    }
}
