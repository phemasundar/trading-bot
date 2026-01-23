package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.OptionType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PutCreditSpreadStrategy extends AbstractTradingStrategy {

    public PutCreditSpreadStrategy() {
        super(StrategyType.PUT_CREDIT_SPREAD);
    }

    public PutCreditSpreadStrategy(StrategyType strategyType) {
        super(strategyType);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionChainResponse.OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT,
                expiryDate);

        if (putMap == null || putMap.isEmpty())
            return new ArrayList<>();

        return findValidPutCreditSpreads(putMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidPutCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap,
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

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted().toList();

        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> options = putMap.get(String.valueOf(shortStrikePrice));
            if (CollectionUtils.isEmpty(options))
                continue;
            OptionChainResponse.OptionData shortPut = options.get(0);

            // Short leg delta filter (null-safe)
            if (shortLegFilter != null && !shortLegFilter.passesMaxDelta(shortPut.getAbsDelta()))
                continue;

            for (int j = 0; j < i; j++) {
                double longStrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> longOptions = putMap.get(String.valueOf(longStrikePrice));
                if (CollectionUtils.isEmpty(longOptions))
                    continue;
                OptionChainResponse.OptionData longPut = longOptions.get(0);

                // Long leg delta filter (null-safe)
                if (longLegFilter != null && !longLegFilter.passesDeltaFilter(longPut.getAbsDelta()))
                    continue;

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
                            .currentPrice(currentPrice)
                            .build());
                }
            }
        }
        return spreads;
    }
}
