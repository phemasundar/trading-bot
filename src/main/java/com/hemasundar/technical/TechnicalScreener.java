package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * Technical stock screener that filters stocks based on technical criteria
 * and prints all indicator values for matching stocks.
 */
@Log4j2
public class TechnicalScreener {

    /**
     * Result object containing stock symbol and all technical values.
     */
    @Data
    @Builder
    public static class ScreeningResult {
        private String symbol;
        private double currentPrice;
        private double rsi;
        private double previousRsi;
        private double bollingerLower;
        private double bollingerMiddle;
        private double bollingerUpper;
        private double ma20;
        private double ma50;
        private boolean priceBelowMA20;
        private boolean priceBelowMA50;
        private boolean priceTouchingLowerBand;
        private boolean rsiOversold;
        private boolean rsiBullishCrossover;

        @Override
        public String toString() {
            String rsiStatus = rsiBullishCrossover ? "(BULLISH CROSSOVER ↑)"
                    : rsiOversold ? "(OVERSOLD)"
                            : "";
            return String.format(
                    "╔══════════════════════════════════════════════════════════╗\n" +
                            "║ %-56s ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║ Current Price:        $%-33.2f ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║ RSI (14):                                                ║\n" +
                            "║   Previous:           %-36.2f ║\n" +
                            "║   Current:            %-8.2f %-27s ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║ Bollinger Bands:                                         ║\n" +
                            "║   Upper:              $%-33.2f ║\n" +
                            "║   Middle (SMA20):     $%-33.2f ║\n" +
                            "║   Lower:              $%-33.2f ║\n" +
                            "║   Price @ Lower:      %-35s ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║ Moving Averages:                                         ║\n" +
                            "║   MA(20):             $%-10.2f  Price < MA: %-11s ║\n" +
                            "║   MA(50):             $%-10.2f  Price < MA: %-11s ║\n" +
                            "╚══════════════════════════════════════════════════════════╝",
                    symbol,
                    currentPrice,
                    previousRsi,
                    rsi, rsiStatus,
                    bollingerUpper,
                    bollingerMiddle,
                    bollingerLower,
                    priceTouchingLowerBand ? "YES ✓" : "NO",
                    ma20, priceBelowMA20 ? "YES ✓" : "NO",
                    ma50, priceBelowMA50 ? "YES ✓" : "NO");
        }
    }

    /**
     * Screens stocks against the given filter chain.
     * 
     * @param symbols     List of stock symbols to screen
     * @param filterChain Technical filter chain containing indicators and
     *                    conditions
     * @return List of screening results for stocks matching all criteria
     */
    public static List<ScreeningResult> screenStocks(List<String> symbols, TechnicalFilterChain filterChain) {
        List<ScreeningResult> results = new ArrayList<>();

        log.info("\n{}", filterChain.getFiltersSummary());

        for (String symbol : symbols) {
            try {
                ScreeningResult result = analyzeStock(symbol, filterChain.getIndicators());
                if (result != null && meetsAllCriteria(result, filterChain.getConditions())) {
                    results.add(result);
                    log.info("\n{}", result);
                }
            } catch (Exception e) {
                log.warn("[{}] Error analyzing stock: {}", symbol, e.getMessage());
            }
        }

        log.info("Screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Analyzes a single stock and calculates all technical values.
     */
    public static ScreeningResult analyzeStock(String symbol, TechnicalIndicators indicators) {
        PriceHistoryResponse priceHistory = ThinkOrSwinAPIs.getYearlyPriceHistory(symbol, 1);
        if (priceHistory == null) {
            return null;
        }

        BarSeries series = TechnicalIndicatorUtils.buildBarSeries(symbol, priceHistory);
        if (series.getBarCount() == 0) {
            log.warn("[{}] No price history available", symbol);
            return null;
        }

        double currentPrice = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();

        ScreeningResult.ScreeningResultBuilder builder = ScreeningResult.builder()
                .symbol(symbol)
                .currentPrice(currentPrice);

        // RSI
        if (indicators.getRsiFilter() != null) {
            RSIFilter rsi = indicators.getRsiFilter();
            builder.rsi(rsi.getCurrentRSI(series))
                    .previousRsi(rsi.getPreviousRSI(series))
                    .rsiOversold(rsi.isOversold(series))
                    .rsiBullishCrossover(rsi.isBullishCrossover(series));
        }

        // Bollinger Bands
        if (indicators.getBollingerFilter() != null) {
            BollingerBandsFilter bb = indicators.getBollingerFilter();
            builder.bollingerLower(bb.getLowerBand(series))
                    .bollingerMiddle(bb.getMiddleBand(series))
                    .bollingerUpper(bb.getUpperBand(series))
                    .priceTouchingLowerBand(bb.isPriceTouchingLowerBand(series));
        }

        // MA20
        if (indicators.getMa20Filter() != null) {
            MovingAverageFilter ma20 = indicators.getMa20Filter();
            builder.ma20(ma20.getCurrentMA(series))
                    .priceBelowMA20(ma20.isPriceBelowMA(series));
        }

        // MA50
        if (indicators.getMa50Filter() != null) {
            MovingAverageFilter ma50 = indicators.getMa50Filter();
            builder.ma50(ma50.getCurrentMA(series))
                    .priceBelowMA50(ma50.isPriceBelowMA(series));
        }

        return builder.build();
    }

    /**
     * Checks if the screening result meets all filter conditions.
     */
    private static boolean meetsAllCriteria(ScreeningResult result, FilterConditions conditions) {
        // RSI condition (via enum)
        if (conditions.getRsiCondition() != null) {
            boolean rsiMet = switch (conditions.getRsiCondition()) {
                case OVERSOLD -> result.isRsiOversold();
                case OVERBOUGHT -> !result.isRsiOversold(); // Simplified: overbought = not oversold context
                case BULLISH_CROSSOVER -> result.isRsiBullishCrossover();
                case BEARISH_CROSSOVER -> !result.isRsiBullishCrossover(); // Opposite
            };
            if (!rsiMet)
                return false;
        }

        // Bollinger Band condition (via enum)
        if (conditions.getBollingerCondition() != null) {
            boolean bbMet = switch (conditions.getBollingerCondition()) {
                case LOWER_BAND -> result.isPriceTouchingLowerBand();
                case UPPER_BAND -> !result.isPriceTouchingLowerBand(); // Simplified
            };
            if (!bbMet)
                return false;
        }

        // MA conditions
        if (conditions.isRequirePriceBelowMA20() && !result.isPriceBelowMA20()) {
            return false;
        }
        if (conditions.isRequirePriceBelowMA50() && !result.isPriceBelowMA50()) {
            return false;
        }
        if (conditions.isRequirePriceAboveMA20() && result.isPriceBelowMA20()) {
            return false;
        }
        if (conditions.isRequirePriceAboveMA50() && result.isPriceBelowMA50()) {
            return false;
        }

        return true;
    }
}
