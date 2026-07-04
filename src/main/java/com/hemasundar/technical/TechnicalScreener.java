package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.utils.VolatilityCalculator;
import com.hemasundar.cache.PriceHistoryCache;
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
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.hemasundar.config.StrategiesConfig.PriceCondition;
import com.hemasundar.config.StrategiesConfig.SmaCondition;
import com.hemasundar.config.StrategiesConfig.Position;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * Technical stock screener that filters stocks based on technical criteria
 * and prints all indicator values for matching stocks.
 */
@Log4j2
@Component
@lombok.RequiredArgsConstructor
public class TechnicalScreener {

    private final ThinkOrSwinAPIs thinkOrSwinAPIs;
    private final SchwabApiExecutor schwabApiExecutor;
    private final VolatilityCalculator volatilityCalculator;

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
        @Builder.Default
        private Map<Integer, Double> maValues = new HashMap<>();
        private boolean priceTouchingLowerBand;
        private boolean priceTouchingUpperBand;
        private boolean rsiOversold;
        private boolean rsiOverbought;
        private boolean rsiBullishCrossover;
        private boolean rsiBearishCrossover;
        private Double historicalVolatility;

        // Price drop screener fields
        private double dropPercent;
        private double referencePrice;
        private String dropType; // INTRADAY, <N>D (multi-day), 52W_HIGH

        /**
         * Returns a concise plain-text summary of the screening result.
         * Used by both the Web UI (click-to-expand) and Telegram alerts.
         * This is the single source of truth for screener result formatting.
         */
        @JsonIgnore
        public String getFormattedSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("  💰 Price: $").append(String.format("%.2f", currentPrice)).append("\n");

            // Drop screener fields
            if (dropType != null && !dropType.isEmpty()) {
                sb.append("  📉 Drop: ").append(String.format("%.2f%%", dropPercent));
                sb.append(" (").append(dropType).append(")").append("\n");
                sb.append("  📌 Ref Price: $").append(String.format("%.2f", referencePrice)).append("\n");
            }

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
            sb.append("  📊 MAs: ");
            if (maValues != null && !maValues.isEmpty()) {
                List<String> maStrings = new ArrayList<>();
                maValues.forEach((period, value) -> {
                    maStrings.add(String.format("MA%d($%.2f)", period, value));
                });
                sb.append(String.join(", ", maStrings));
            } else {
                sb.append("None");
            }
            sb.append("\n");

            // Historical Volatility
            if (historicalVolatility != null) {
                sb.append("  📈 HV: ").append(String.format("%.2f%%", historicalVolatility)).append("\n");
            }

            return sb.toString();
        }

        private String formatVolume(long volume) {
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
            StringBuilder maOutput = new StringBuilder();
            if (maValues != null && !maValues.isEmpty()) {
                maValues.forEach((period, value) -> {
                    maOutput.append(String.format("║   MA(%d): %-13.2f %32s ║\n", period, value, ""));
                });
            } else {
                maOutput.append("║   None configured                                        ║\n");
            }

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
                            "║ Historical Volatility: %-33s ║\n" +
                            "╠══════════════════════════════════════════════════════════╣\n" +
                            "║ Moving Averages:                                         ║\n" +
                            "%s" +
                            "╚══════════════════════════════════════════════════════════╝",
                    symbol,
                    currentPrice,
                    previousRsi,
                    rsi, rsiStatus,
                    bollingerUpper,
                    bollingerMiddle,
                    bollingerLower,
                    priceTouchingLowerBand ? "YES ✓" : "NO",
                    historicalVolatility != null ? String.format("%.2f%%", historicalVolatility) : "N/A",
                    maOutput.toString().endsWith("\n") ? maOutput.substring(0, maOutput.length() - 1) : maOutput.toString());
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
    public List<ScreeningResult> screenStocks(List<String> symbols, TechnicalFilterChain filterChain, java.util.function.BiConsumer<String, String> alertCallback) {
        log.info("\n{}", filterChain.getFiltersSummary());

        // ── Parallel execution (Track B) ──
        // analyzeStock() is pure I/O (getYearlyPriceHistory); each symbol is
        // independent, so we fan out across all symbols in the thread pool.
        log.info("Screening {} symbols in parallel", symbols.size());
        long screenT0 = System.currentTimeMillis();

        List<ScreeningResult> parallelResults = schwabApiExecutor.executeParallel(symbols, symbol -> {
            return analyzeStock(symbol, filterChain.getIndicators());
        }, alertCallback);

        // Filter out nulls (errors) and apply conditions on the calling thread
        List<ScreeningResult> results = new ArrayList<>();
        for (ScreeningResult result : parallelResults) {
            if (result != null && meetsAllCriteria(result, filterChain.getConditions())) {
                results.add(result);
                log.info("\n{}", result);
            }
        }

        log.info("Screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Analyzes a single stock and calculates all technical values.
     */
    public ScreeningResult analyzeStock(String symbol, TechnicalIndicators indicators) {
        PriceHistoryCache.HistoricalData cachedData = PriceHistoryCache.getInstance().getHistoricalData(symbol, thinkOrSwinAPIs, volatilityCalculator);
        PriceHistoryResponse priceHistory = cachedData != null ? cachedData.getPriceHistory() : null;
        
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
                .currentPrice(currentPrice)
                .historicalVolatility(cachedData != null ? cachedData.getAnnualizedHistoricalVolatility() : null);

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

        // Moving Averages
        if (indicators.getMaFilters() != null) {
            Map<Integer, Double> maValues = new HashMap<>();
            for (Map.Entry<Integer, MovingAverageFilter> entry : indicators.getMaFilters().entrySet()) {
                maValues.put(entry.getKey(), entry.getValue().getCurrentMA(series));
            }
            builder.maValues(maValues);
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
    private boolean meetsAllCriteria(ScreeningResult result, TechFilterConditions conditions) {
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

        // MA conditions dynamically evaluated
        if (conditions.getPriceConditions() != null) {
            for (PriceCondition condition : conditions.getPriceConditions()) {
                Double maVal = result.getMaValues().get(condition.getPeriod());
                if (maVal == null) return false;
                if (condition.getPosition() == Position.ABOVE && result.getCurrentPrice() <= maVal) {
                    return false;
                }
                if (condition.getPosition() == Position.BELOW && result.getCurrentPrice() >= maVal) {
                    return false;
                }
            }
        }
        
        if (conditions.getSmaConditions() != null) {
            for (SmaCondition condition : conditions.getSmaConditions()) {
                Double maVal1 = result.getMaValues().get(condition.getPeriod1());
                Double maVal2 = result.getMaValues().get(condition.getPeriod2());
                if (maVal1 == null || maVal2 == null) return false;
                if (condition.getPosition() == Position.ABOVE && maVal1 <= maVal2) {
                    return false;
                }
                if (condition.getPosition() == Position.BELOW && maVal1 >= maVal2) {
                    return false;
                }
            }
        }

        // Volume condition
        if (conditions.getMinVolume() != null && conditions.getMinVolume() > 0) {
            if (result.getVolume() < conditions.getMinVolume()) {
                return false;
            }
        }

        // Historical Volatility condition (fail-open)
        if (conditions.getMinHistoricalVolatility() != null) {
            if (result.getHistoricalVolatility() != null && result.getHistoricalVolatility() < conditions.getMinHistoricalVolatility()) {
                return false;
            }
        }
        
        if (conditions.getMaxHistoricalVolatility() != null) {
            if (result.getHistoricalVolatility() != null && result.getHistoricalVolatility() > conditions.getMaxHistoricalVolatility()) {
                return false;
            }
        }

        return true;
    }
}
