package com.hemasundar.options.strategies;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.technical.BollingerBandsFilter;
import org.apache.commons.collections4.CollectionUtils;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RSI & Bollinger Bands Bull Put Spread Strategy.
 * 
 * Entry Conditions (BULLISH signal - Oversold):
 * - RSI (14-day) < 30 (Oversold)
 * - Price is touching or piercing the Lower Bollinger Band (2 SD)
 * 
 * Trade Setup:
 * - Sell: Put option at ~30 Delta (below current price)
 * - Buy: Put option at ~15-20 Delta (further below current price)
 * - DTE: 30 days
 */
@Log4j2
public class RSIBollingerBullPutSpreadStrategy extends AbstractTradingStrategy {

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        // Find Bull Put Spread trades
        Map<String, List<OptionChainResponse.OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);

        if (putMap == null || putMap.isEmpty()) {
            return new ArrayList<>();
        }

        return findBullPutSpreads(putMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findBullPutSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap,
            double currentPrice, OptionsStrategyFilter filter) {
        List<TradeSetup> spreads = new ArrayList<>();

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        // Find short put at ~30 delta
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> options = putMap.get(String.valueOf(shortStrikePrice));
            if (CollectionUtils.isEmpty(options))
                continue;

            OptionChainResponse.OptionData shortPut = options.get(0);

            // Short put should be OTM and around 30 delta
            if (shortStrikePrice >= currentPrice)
                continue;
            double shortDelta = shortPut.getAbsDelta();
            if (shortDelta > filter.getMaxDelta() || shortDelta < 0.25)
                continue; // ~30 delta range

            // Find long put at ~15-20 delta (further OTM)
            for (int j = 0; j < i; j++) {
                double longStrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> longOptions = putMap.get(String.valueOf(longStrikePrice));
                if (CollectionUtils.isEmpty(longOptions))
                    continue;

                OptionChainResponse.OptionData longPut = longOptions.get(0);

                double longDelta = longPut.getAbsDelta();
                if (longDelta > 0.22 || longDelta < 0.12)
                    continue; // ~15-20 delta range

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

    @Override
    public String getStrategyName() {
        return "RSI Bollinger Bull Put Spread Strategy";
    }
}
