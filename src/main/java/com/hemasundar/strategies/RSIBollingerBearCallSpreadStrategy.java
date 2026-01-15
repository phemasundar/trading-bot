package com.hemasundar.strategies;

import com.hemasundar.pojos.CallCreditSpread;
import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.OptionType;
import com.hemasundar.pojos.OptionsStrategyFilter;
import com.hemasundar.pojos.TradeSetup;
import com.hemasundar.pojos.technicalfilters.TechnicalFilterChain;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RSI & Bollinger Bands Bear Call Spread Strategy.
 * 
 * Entry Conditions (BEARISH signal - Overbought):
 * - RSI (14-day) > 70 (Overbought)
 * - Price is touching or piercing the Upper Bollinger Band (2 SD)
 * 
 * Trade Setup:
 * - Sell: Call option at ~30 Delta (above current price)
 * - Buy: Call option at ~15-20 Delta (further above current price)
 * - DTE: 30 days
 */
@Log4j2
public class RSIBollingerBearCallSpreadStrategy extends AbstractTechnicalStrategy {

    public RSIBollingerBearCallSpreadStrategy(TechnicalFilterChain filterChain) {
        super(filterChain);
    }

    @Override
    protected List<TradeSetup> findStrategySpecificTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        // Find Bear Call Spread trades
        Map<String, List<OptionChainResponse.OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty()) {
            return new ArrayList<>();
        }

        return findBearCallSpreads(callMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findBearCallSpreads(Map<String, List<OptionChainResponse.OptionData>> callMap,
            double currentPrice, OptionsStrategyFilter filter) {
        List<TradeSetup> spreads = new ArrayList<>();

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        // Find short call at ~30 delta
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            List<OptionChainResponse.OptionData> options = callMap.get(String.valueOf(shortStrikePrice));
            if (CollectionUtils.isEmpty(options))
                continue;

            OptionChainResponse.OptionData shortCall = options.get(0);

            // Short call should be OTM (strike > current price) and around 30 delta
            if (shortStrikePrice <= currentPrice)
                continue;
            double shortDelta = shortCall.getAbsDelta();
            if (shortDelta > filter.getMaxDelta() || shortDelta < 0.25)
                continue; // ~30 delta range

            // Find long call at ~15-20 delta (further OTM)
            for (int j = i + 1; j < sortedStrikes.size(); j++) {
                double longStrikePrice = sortedStrikes.get(j);
                List<OptionChainResponse.OptionData> longOptions = callMap.get(String.valueOf(longStrikePrice));
                if (CollectionUtils.isEmpty(longOptions))
                    continue;

                OptionChainResponse.OptionData longCall = longOptions.get(0);

                double longDelta = longCall.getAbsDelta();
                if (longDelta > 0.22 || longDelta < 0.12)
                    continue; // ~15-20 delta range

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
                            .build());
                }
            }
        }

        return spreads;
    }

    @Override
    public String getStrategyName() {
        return "RSI Bollinger Bear Call Spread Strategy";
    }
}
