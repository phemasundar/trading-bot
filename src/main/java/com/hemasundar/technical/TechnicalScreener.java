package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.cache.QuotesCache;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
        private Double volumeSmaShort;
        private Double volumeSmaLong;
        @Builder.Default
        private Map<Integer, Double> maValues = new HashMap<>();
        @Builder.Default
        private Map<Integer, Double> emaValues = new HashMap<>();

        /**
         * Volume SMA values keyed by period. Populated when volume SMA comparison
         * conditions are configured.
         */
        @Builder.Default
        private Map<Integer, Double> volumeMaValues = new HashMap<>();

        private boolean priceTouchingLowerBand;
        private boolean priceTouchingUpperBand;
        private boolean rsiOversold;
        private boolean rsiOverbought;
        private boolean rsiBullishCrossover;
        private boolean rsiBearishCrossover;
        private Double historicalVolatilityRank;

        // Price drop screener fields
        private double dropPercent;
        private double referencePrice;
        private String dropType; // INTRADAY, <N>D (multi-day), 52W_HIGH

        /** Market capitalisation in Billions (sharesOutstanding * lastPrice / 1e9). */
        private Double marketCapB;

        /**
         * Returns a concise plain-text summary of the screening result.
         * Used by both the Web UI (click-to-expand) and Telegram alerts.
         * This is the single source of truth for screener result formatting.
         */
        @JsonProperty("formattedSummary")
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
                sb.append("TOUCHING LOWER");
            } else if (priceTouchingUpperBand) {
                sb.append("TOUCHING UPPER");
            } else {
                sb.append("INSIDE");
            }
            sb.append(" ($").append(String.format("%.2f", bollingerLower)).append(" - $")
                    .append(String.format("%.2f", bollingerUpper)).append(")\n");

            // Moving Averages Section - Condensed summary
            sb.append("  📊 SMAs: ");
            if (maValues != null && !maValues.isEmpty()) {
                List<String> maStrings = new ArrayList<>();
                maValues.forEach((period, value) -> {
                    maStrings.add(String.format("SMA%d($%.2f)", period, value));
                });
                sb.append(String.join(", ", maStrings));
            } else {
                sb.append("None");
            }
            sb.append("\n");

            // Exponential Moving Averages Section
            if (emaValues != null && !emaValues.isEmpty()) {
                sb.append("  📈 EMAs: ");
                List<String> emaStrings = new ArrayList<>();
                emaValues.forEach((period, value) -> {
                    emaStrings.add(String.format("EMA%d($%.2f)", period, value));
                });
                sb.append(String.join(", ", emaStrings));
                sb.append("\n");
            }

            // Historical Volatility Rank
            if (historicalVolatilityRank != null) {
                sb.append("  📈 HV Rank: ").append(String.format("%.1f", historicalVolatilityRank)).append("\n");
            }

            // Market Cap
            if (marketCapB != null) {
                sb.append("  🏢 Mkt Cap: ").append(formatMarketCap(marketCapB)).append("\n");
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

        private String formatMarketCap(double capB) {
            if (capB >= 1_000.0) {
                return String.format("$%.2fT", capB / 1_000.0);
            } else if (capB >= 1.0) {
                return String.format("$%.2fB", capB);
            }
            return String.format("$%.0fM", capB * 1_000.0);
        }

        /**
         * Resolves a standardized indicator variable name to its numeric value.
         *
         * <p>
         * Supported variable names:
         * <ul>
         *   <li>{@code PRICE}, {@code CURRENT_PRICE} — current price</li>
         *   <li>{@code VOLUME} — current volume</li>
         *   <li>{@code RSI} — current RSI</li>
         *   <li>{@code PREVIOUS_RSI} — previous bar RSI</li>
         *   <li>{@code BB_LOWER}, {@code BB_MIDDLE}, {@code BB_UPPER} —
         *       Bollinger Bands</li>
         *   <li>{@code SMA<N>} — simple moving average for period N</li>
         *   <li>{@code VOLUME_SMA<N>} — volume SMA for period N</li>
         *   <li>{@code HV_RANK} — historical volatility rank</li>
         *   <li>{@code DROP_PCT} — price drop percentage</li>
         * </ul>
         *
         * @param variable variable name (case-insensitive)
         * @return resolved value, or null if unavailable
         */
        public Double getIndicatorValue(String variable) {
            if (variable == null) {
                return null;
            }
            String key = variable.trim().toUpperCase();
            return switch (key) {
                case "PRICE", "CURRENT_PRICE" -> currentPrice;
                case "VOLUME" -> (double) volume;
                case "RSI" -> rsi;
                case "PREVIOUS_RSI" -> previousRsi;
                case "BB_LOWER" -> bollingerLower;
                case "BB_MIDDLE" -> bollingerMiddle;
                case "BB_UPPER" -> bollingerUpper;
                case "HV_RANK" -> historicalVolatilityRank;
                case "DROP_PCT" -> dropPercent;
                case "MARKET_CAP_B" -> marketCapB;
                default -> resolveMappedValue(key);
            };
        }

        private Double resolveMappedValue(String key) {
            if (key.startsWith("SMA")) {
                Integer period = parsePeriod(key.substring(3));
                if (period != null && maValues != null) {
                    return maValues.get(period);
                }
            }
            if (key.startsWith("VOLUME_SMA")) {
                Integer period = parsePeriod(key.substring(10));
                if (period != null && volumeMaValues != null) {
                    return volumeMaValues.get(period);
                }
            }
            return null;
        }

        private Integer parsePeriod(String suffix) {
            try {
                return Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            String rsiStatus = rsiBullishCrossover ? "(BULLISH CROSSOVER ↑)"
                    : rsiOversold ? "(OVERSOLD)"
                            : "";
            StringBuilder maOutput = new StringBuilder();
            if (maValues != null && !maValues.isEmpty()) {
                maValues.forEach((period, value) -> {
                    maOutput.append(String.format("║   SMA(%d): %-13.2f %32s ║\n", period, value, ""));
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
                            "║ HV Rank:              %-33s ║\n" +
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
                    historicalVolatilityRank != null ? String.format("%.1f", historicalVolatilityRank) : "N/A",
                    maOutput.toString().endsWith("\n") ? maOutput.substring(0, maOutput.length() - 1) : maOutput.toString());
        }
    }

    /**
     * Screens stocks against the given filter chain and optional fundamental conditions.
     *
     * @param symbols                List of stock symbols to screen
     * @param filterChain            Technical filter chain containing indicators and conditions
     * @param fundamentalConditions  Optional fundamental conditions (may be null)
     * @return List of screening results for stocks matching all criteria
     */
    public List<ScreeningResult> screenStocks(
            List<String> symbols,
            TechnicalFilterChain filterChain,
            FundamentalFilterConditions fundamentalConditions,
            java.util.function.BiConsumer<String, String> alertCallback) {
        log.info("\n{}", filterChain.getFiltersSummary());

        log.info("Screening {} symbols in parallel", symbols.size());
        long screenT0 = System.currentTimeMillis();

        List<ScreeningResult> parallelResults = schwabApiExecutor.executeParallel(symbols, symbol -> {
            return analyzeStock(symbol, filterChain.getIndicators(), filterChain.getConditions());
        }, alertCallback);

        // Filter out nulls (errors) and apply all conditions on the calling thread
        List<ScreeningResult> results = new ArrayList<>();
        for (ScreeningResult result : parallelResults) {
            if (result != null
                    && meetsAllCriteria(result, filterChain.getConditions())
                    && meetsFundamentalCriteria(result, fundamentalConditions)) {
                results.add(result);
                log.info("\n{}", result);
            }
        }

        log.info("Screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Backward-compatible overload without fundamental conditions.
     */
    public List<ScreeningResult> screenStocks(List<String> symbols, TechnicalFilterChain filterChain, java.util.function.BiConsumer<String, String> alertCallback) {
        return screenStocks(symbols, filterChain, null, alertCallback);
    }

    /**
     * Analyzes a single stock and calculates all technical values.
     */
    public ScreeningResult analyzeStock(String symbol, TechnicalIndicators indicators, TechFilterConditions conditions) {
        Integer hvPeriod = conditions != null ? conditions.getHvPeriod() : 20;
        PriceHistoryCache.HistoricalData cachedData = PriceHistoryCache.getInstance().getHistoricalData(symbol, thinkOrSwinAPIs);
        PriceHistoryResponse priceHistory = cachedData != null ? cachedData.getPriceHistory() : null;
        
        if (priceHistory == null) {
            return null;
        }

        Double hvRank = null;
        if (hvPeriod != null && hvPeriod > 0) {
            hvRank = volatilityCalculator.calculateHvRank(priceHistory, hvPeriod);
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
                .historicalVolatilityRank(hvRank);

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
                maValues.put(entry.getKey(), entry.getValue().getCurrentSMA(series));
            }
            builder.maValues(maValues);
        }

        // Exponential Moving Averages
        if (indicators.getEmaFilters() != null) {
            Map<Integer, Double> emaValues = new HashMap<>();
            for (Map.Entry<Integer, ExponentialMovingAverageFilter> entry : indicators.getEmaFilters().entrySet()) {
                emaValues.put(entry.getKey(), entry.getValue().getCurrentEMA(series));
            }
            builder.emaValues(emaValues);
        }

        // Volume
        if (indicators.getVolumeFilter() != null) {
            VolumeFilter volumeFilter = indicators.getVolumeFilter();
            builder.volume(volumeFilter.getCurrentVolume(series));
        } else {
            // Default: get volume directly from series
            builder.volume(series.getBar(series.getEndIndex()).getVolume().longValue());
        }

        // Volume SMA calculation when any VOLUME_SMA<N> expression is configured
        if (conditions != null && conditions.getFilterExpressions() != null) {
            java.util.Set<Integer> volumeSmaPeriods = new java.util.LinkedHashSet<>();
            for (MathExpression expr : conditions.getFilterExpressions()) {
                Integer p = extractVolumeSmaPeriod(expr.getLeftVariable());
                if (p != null) {
                    volumeSmaPeriods.add(p);
                }
                p = extractVolumeSmaPeriod(expr.getRightVariable());
                if (p != null) {
                    volumeSmaPeriods.add(p);
                }
            }
            if (!volumeSmaPeriods.isEmpty()) {
                org.ta4j.core.indicators.helpers.VolumeIndicator volumeInd = new org.ta4j.core.indicators.helpers.VolumeIndicator(series);
                Map<Integer, Double> volumeMaValues = new HashMap<>();
                for (Integer period : volumeSmaPeriods) {
                    org.ta4j.core.indicators.SMAIndicator sma = new org.ta4j.core.indicators.SMAIndicator(volumeInd, period);
                    double value = sma.getValue(series.getEndIndex()).doubleValue();
                    volumeMaValues.put(period, value);
                    if (period <= 20) {
                        builder.volumeSmaShort(value);
                    }
                    if (period >= 50) {
                        builder.volumeSmaLong(value);
                    }
                }
                builder.volumeMaValues(volumeMaValues);
            }
        }

        // Market Cap
        QuotesResponse.QuoteData quoteData = QuotesCache.getInstance().get(symbol);
        if (quoteData != null) {
            Double mcap = quoteData.getMarketCapB();
            if (mcap != null) {
                builder.marketCapB(mcap);
                log.info("Symbol {}: Calculated Market Cap B: {}", symbol, mcap);
            } else {
                log.info("Symbol {}: Market Cap data missing in quote", symbol);
            }
        } else {
            log.info("Symbol {}: quoteData is null", symbol);
        }


        return builder.build();
    }

    private static Integer extractVolumeSmaPeriod(String variable) {
        if (variable == null) {
            return null;
        }
        String upper = variable.trim().toUpperCase();
        if (upper.startsWith("VOLUME_SMA")) {
            try {
                return Integer.parseInt(upper.substring(10));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if the screening result meets all technical filter conditions.
     */
    private boolean meetsAllCriteria(ScreeningResult result, TechFilterConditions conditions) {
        return MathExpressionEvaluator.evaluateAll(conditions.getFilterExpressions(), result::getIndicatorValue);
    }

    /**
     * Checks if the screening result meets all fundamental filter conditions
     * (e.g. Market Cap). Returns true if {@code fundamentalConditions} is null
     * or has no expressions.
     */
    private boolean meetsFundamentalCriteria(ScreeningResult result, FundamentalFilterConditions fundamentalConditions) {
        if (fundamentalConditions == null) {
            return true;
        }
        return MathExpressionEvaluator.evaluateAll(
                fundamentalConditions.getFilterExpressions(), result::getIndicatorValue);
    }
}
