package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.ta4j.core.BarSeries;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreeningResult {
        private String symbol;
        private double currentPrice;
        private long volume;
        private double rsi;
        private double previousRsi;
        private double bollingerLower;
        private double bollingerMiddle;
        private double bollingerUpper;
        private double ma20;
        private double ma50;
        private double ma100;
        private double ma200;
        private boolean priceBelowMA20;
        private boolean priceBelowMA50;
        private boolean priceBelowMA100;
        private boolean priceBelowMA200;
        private boolean priceTouchingLowerBand;
        private boolean priceTouchingUpperBand;
        private boolean rsiOversold;
        private boolean rsiOverbought;
        private boolean rsiBullishCrossover;
        private boolean rsiBearishCrossover;

        /**
         * Returns a concise plain-text summary of the screening result.
         * Used by both the Web UI (click-to-expand) and Telegram alerts.
         * This is the single source of truth for screener result formatting.
         */
        @JsonIgnore
        public String getFormattedSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("  💰 Price: $").append(String.format("%.2f", currentPrice)).append("\n");

            // Volume
            sb.append("  📊 Volume: ").append(formatVolume(volume)).append("\n");

            // RSI Section
            sb.append("  📈 RSI: ").append(String.format("%.2f", rsi));
            sb.append(" (prev: ").append(String.format("%.2f", previousRsi)).append(")");
            if (rsiBullishCrossover) {
                sb.append(" ⬆️ CROSSOVER");
            } else if (rsiBearishCrossover) {
                sb.append(" ⬇️ CROSSOVER");
            } else if (rsiOversold) {
                sb.append(" 🔴 OVERSOLD");
            } else if (rsiOverbought) {
                sb.append(" 🟢 OVERBOUGHT");
            }
            sb.append("\n");

            // Bollinger Bands Section - Condensed
            sb.append("  📉 BB: ");
            if (priceTouchingLowerBand) {
                sb.append("Touching Lower ($").append(String.format("%.2f", bollingerLower)).append(")");
            } else if (priceTouchingUpperBand) {
                sb.append("Touching Upper ($").append(String.format("%.2f", bollingerUpper)).append(")");
            } else {
                sb.append("Within bands ($").append(String.format("%.2f", bollingerLower))
                        .append(" - $").append(String.format("%.2f", bollingerUpper)).append(")");
            }
            sb.append("\n");

            // Moving Averages Section - Condensed summary
            List<String> belowMAs = new ArrayList<>();
            List<String> aboveMAs = new ArrayList<>();

            if (priceBelowMA20) belowMAs.add("MA20"); else aboveMAs.add("MA20");
            if (priceBelowMA50) belowMAs.add("MA50"); else aboveMAs.add("MA50");
            if (priceBelowMA100) belowMAs.add("MA100"); else aboveMAs.add("MA100");
            if (priceBelowMA200) belowMAs.add("MA200"); else aboveMAs.add("MA200");

            sb.append("  📊 MAs: ");
            if (!belowMAs.isEmpty()) {
                sb.append("Below ").append(String.join(", ", belowMAs));
            }
            if (!belowMAs.isEmpty() && !aboveMAs.isEmpty()) {
                sb.append(" | ");
            }
            if (!aboveMAs.isEmpty()) {
                sb.append("Above ").append(String.join(", ", aboveMAs));
            }
            sb.append("\n");
            return sb.toString();
        }

        private static String formatVolume(long volume) {
            if (volume >= 1_000_000) {
                return String.format("%.2fM", volume / 1_000_000.0);
            } else if (volume >= 1_000) {
                return String.format("%.2fK", volume / 1_000.0);
            }
            return String.valueOf(volume);
        }

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
                            "║   MA(100):            $%-10.2f  Price < MA: %-11s ║\n" +
                            "║   MA(200):            $%-10.2f  Price < MA: %-11s ║\n" +
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
                    ma50, priceBelowMA50 ? "YES ✓" : "NO",
                    ma100, priceBelowMA100 ? "YES ✓" : "NO",
                    ma200, priceBelowMA200 ? "YES ✓" : "NO");
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
                    .rsiOverbought(rsi.isOverbought(series))
                    .rsiBullishCrossover(rsi.isBullishCrossover(series))
                    .rsiBearishCrossover(rsi.isBearishCrossover(series));
        }

        // Bollinger Bands
        if (indicators.getBollingerFilter() != null) {
            BollingerBandsFilter bb = indicators.getBollingerFilter();
            builder.bollingerLower(bb.getLowerBand(series))
                    .bollingerMiddle(bb.getMiddleBand(series))
                    .bollingerUpper(bb.getUpperBand(series))
                    .priceTouchingLowerBand(bb.isPriceTouchingLowerBand(series))
                    .priceTouchingUpperBand(bb.isPriceTouchingUpperBand(series));
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

        // MA100
        if (indicators.getMa100Filter() != null) {
            MovingAverageFilter ma100 = indicators.getMa100Filter();
            builder.ma100(ma100.getCurrentMA(series))
                    .priceBelowMA100(ma100.isPriceBelowMA(series));
        }

        // MA200
        if (indicators.getMa200Filter() != null) {
            MovingAverageFilter ma200 = indicators.getMa200Filter();
            builder.ma200(ma200.getCurrentMA(series))
                    .priceBelowMA200(ma200.isPriceBelowMA(series));
        }

        // Volume
        if (indicators.getVolumeFilter() != null) {
            VolumeFilter volumeFilter = indicators.getVolumeFilter();
            builder.volume(volumeFilter.getCurrentVolume(series));
        } else {
            // Default: get volume directly from series
            builder.volume(series.getBar(series.getEndIndex()).getVolume().longValue());
        }

        return builder.build();
    }

    /**
     * Checks if the screening result meets all filter conditions.
     */
    private static boolean meetsAllCriteria(ScreeningResult result, TechFilterConditions conditions) {
        // RSI condition (via enum)
        if (conditions.getRsiCondition() != null) {
            boolean rsiMet = switch (conditions.getRsiCondition()) {
                case OVERSOLD -> result.isRsiOversold();
                case OVERBOUGHT -> result.isRsiOverbought();
                case BULLISH_CROSSOVER -> result.isRsiBullishCrossover();
                case BEARISH_CROSSOVER -> result.isRsiBearishCrossover();
            };
            if (!rsiMet)
                return false;
        }

        // Bollinger Band condition (via enum)
        if (conditions.getBollingerCondition() != null) {
            boolean bbMet = switch (conditions.getBollingerCondition()) {
                case LOWER_BAND -> result.isPriceTouchingLowerBand();
                case UPPER_BAND -> result.isPriceTouchingUpperBand();
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
        // MA100 conditions
        if (conditions.isRequirePriceBelowMA100() && !result.isPriceBelowMA100()) {
            return false;
        }
        if (conditions.isRequirePriceAboveMA100() && result.isPriceBelowMA100()) {
            return false;
        }
        // MA200 conditions
        if (conditions.isRequirePriceBelowMA200() && !result.isPriceBelowMA200()) {
            return false;
        }
        if (conditions.isRequirePriceAboveMA200() && result.isPriceBelowMA200()) {
            return false;
        }

        // Volume condition
        if (conditions.getMinVolume() != null && conditions.getMinVolume() > 0) {
            if (result.getVolume() < conditions.getMinVolume()) {
                return false;
            }
        }

        return true;
    }
}
