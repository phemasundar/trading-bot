package com.hemasundar.strategies;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.*;
import com.hemasundar.pojos.technicalfilters.BollingerBandsFilter;
import com.hemasundar.pojos.technicalfilters.RSIFilter;
import com.hemasundar.pojos.technicalfilters.TechnicalFilterChain;
import com.hemasundar.pojos.technicalfilters.VolumeFilter;
import com.hemasundar.utils.TechnicalIndicators;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
public class RSIBollingerBullPutSpreadStrategy extends AbstractTradingStrategy {

    private final TechnicalFilterChain filterChain;

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate, StrategyFilter filter) {
        // 1. Fetch price history for technical analysis
        PriceHistoryResponse priceHistory = ThinkOrSwinAPIs.getYearlyPriceHistory(chain.getSymbol(), 1);
        BarSeries series = TechnicalIndicators.buildBarSeries(chain.getSymbol(), priceHistory);

        if (series.getBarCount() == 0) {
            System.out.println("No price history available for " + chain.getSymbol());
            return new ArrayList<>();
        }

        // 2. Check volume condition using Quotes API (real-time volume)
        VolumeFilter volumeFilter = filterChain.getFilter(VolumeFilter.class);
        if (volumeFilter != null) {
            try {
                QuotesResponse.QuoteData quoteData = ThinkOrSwinAPIs.getQuote(chain.getSymbol());
                long currentVolume = quoteData.getQuote().getTotalVolume();
                if (currentVolume < volumeFilter.getMinVolume()) {
                    System.out.printf("  [%s] Volume: %,d - BELOW threshold (%,d). Skipping.%n",
                            chain.getSymbol(), currentVolume, volumeFilter.getMinVolume());
                    return new ArrayList<>();
                }
                System.out.printf("  [%s] Volume: %,d - OK%n", chain.getSymbol(), currentVolume);
            } catch (Exception e) {
                System.out.printf("  [%s] Failed to fetch quote for volume check: %s%n",
                        chain.getSymbol(), e.getMessage());
            }
        }

        // 3. Check for OVERSOLD conditions (Bullish signal)
        RSIFilter rsiFilter = filterChain.getFilter(RSIFilter.class);
        BollingerBandsFilter bbFilter = filterChain.getFilter(BollingerBandsFilter.class);

        if (rsiFilter == null || bbFilter == null) {
            System.out.println("RSI or Bollinger Bands filter not configured in filter chain");
            return new ArrayList<>();
        }

        double currentRSI = rsiFilter.getCurrentRSI(series);
        double lowerBand = bbFilter.getLowerBand(series);
        double currentPrice = chain.getUnderlyingPrice();

        System.out.printf("  [%s] RSI: %.2f | Lower BB: %.2f | Price: %.2f%n",
                chain.getSymbol(), currentRSI, lowerBand, currentPrice);

        // OVERSOLD condition: RSI < threshold AND price touching/piercing lower band
        if (!rsiFilter.isOversold(series) || !bbFilter.isPriceTouchingLowerBand(series)) {
            System.out.println("  -> Conditions NOT met for Bull Put Spread (not oversold)");
            return new ArrayList<>();
        }

        System.out.println("  -> OVERSOLD conditions met! Looking for Bull Put Spread...");

        // 3. Find Bull Put Spread trades
        Map<String, List<OptionChainResponse.OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);

        if (putMap == null || putMap.isEmpty()) {
            return new ArrayList<>();
        }

        return findBullPutSpreads(putMap, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findBullPutSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap,
            double currentPrice, StrategyFilter filter) {
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
