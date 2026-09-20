package com.hemasundar.technical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration model for securities screen technical analysis filters.
 * Mapped from {@code securities-filters.yml}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecuritiesFilterConfig {

    private FilterSettings filters;

    public List<Integer> getAllowedSmaPeriods() {
        return filters != null && filters.getMovingAverages() != null && filters.getMovingAverages().getPeriods() != null
                ? filters.getMovingAverages().getPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedEmaPeriods() {
        return filters != null && filters.getExponentialMovingAverages() != null && filters.getExponentialMovingAverages().getPeriods() != null
                ? filters.getExponentialMovingAverages().getPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedVolumeSmaPeriods() {
        return filters != null && filters.getVolume() != null && filters.getVolume().getSmaPeriods() != null
                ? filters.getVolume().getSmaPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedHighPeriods() {
        return filters != null && filters.getHighs() != null && filters.getHighs().getPeriods() != null
                ? filters.getHighs().getPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedRsiPeriods() {
        return filters != null && filters.getRsi() != null
                ? filters.getRsi().getAllowedPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedBollingerPeriods() {
        return filters != null && filters.getBollinger() != null
                ? filters.getBollinger().getAllowedPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedAtrPeriods() {
        return filters != null && filters.getVolatility() != null
                ? filters.getVolatility().getAllowedAtrPeriods() : Collections.emptyList();
    }

    public List<Integer> getAllowedHvPeriods() {
        return filters != null && filters.getVolatility() != null
                ? filters.getVolatility().getAllowedHvPeriods() : Collections.emptyList();
    }

    /**
     * Converts this filter configuration into a universal TechnicalIndicators instance.
     */
    public TechnicalIndicators toUniversalTechnicalIndicators() {
        TechnicalIndicators.TechnicalIndicatorsBuilder builder = TechnicalIndicators.builder();

        if (filters != null) {
            // RSI
            if (filters.getRsi() != null && filters.getRsi().isEnabled()) {
                builder.rsiFilter(RSIFilter.builder()
                        .period(filters.getRsi().getPeriod())
                        .oversoldThreshold(filters.getRsi().getOversold())
                        .overboughtThreshold(filters.getRsi().getOverbought())
                        .build());
            }

            // Bollinger
            if (filters.getBollinger() != null && filters.getBollinger().isEnabled()) {
                builder.bollingerFilter(BollingerBandsFilter.builder()
                        .period(filters.getBollinger().getPeriod())
                        .standardDeviations(filters.getBollinger().getStdDev())
                        .build());
            }

            // SMAs
            if (filters.getMovingAverages() != null && filters.getMovingAverages().isEnabled()
                    && filters.getMovingAverages().getPeriods() != null) {
                Map<Integer, MovingAverageFilter> maMap = new HashMap<>();
                for (Integer p : filters.getMovingAverages().getPeriods()) {
                    maMap.put(p, MovingAverageFilter.builder().period(p).build());
                }
                builder.maFilters(maMap);
            }

            // EMAs
            if (filters.getExponentialMovingAverages() != null && filters.getExponentialMovingAverages().isEnabled()
                    && filters.getExponentialMovingAverages().getPeriods() != null) {
                Map<Integer, ExponentialMovingAverageFilter> emaMap = new HashMap<>();
                for (Integer p : filters.getExponentialMovingAverages().getPeriods()) {
                    emaMap.put(p, ExponentialMovingAverageFilter.builder().period(p).build());
                }
                builder.emaFilters(emaMap);
            }

            // Volume
            if (filters.getVolume() != null && filters.getVolume().isEnabled()) {
                builder.volumeFilter(VolumeFilter.builder().build());
            }

            // ATR
            if (filters.getVolatility() != null && filters.getVolatility().isEnabled()) {
                builder.atrFilter(AverageTrueRangeFilter.builder().period(filters.getVolatility().getAtrPeriod()).build());
            }
        }

        return builder.build();
    }

    /**
     * Converts this filter configuration into a universal TechFilterConditions instance.
     */
    public TechFilterConditions toUniversalTechFilterConditions() {
        List<MathExpression> exprs = new ArrayList<>();

        if (filters != null) {
            // Volume SMAs
            if (filters.getVolume() != null && filters.getVolume().isEnabled()
                    && filters.getVolume().getSmaPeriods() != null) {
                for (Integer p : filters.getVolume().getSmaPeriods()) {
                    exprs.add(MathExpression.builder()
                            .leftVariable("VOLUME_SMA" + p)
                            .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                            .rightVariable("0")
                            .build());
                }
            }

            // Highs
            if (filters.getHighs() != null && filters.getHighs().isEnabled()
                    && filters.getHighs().getPeriods() != null) {
                for (Integer p : filters.getHighs().getPeriods()) {
                    exprs.add(MathExpression.builder()
                            .leftVariable("HIGH_" + p + "D")
                            .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                            .rightVariable("0")
                            .build());
                    exprs.add(MathExpression.builder()
                            .leftVariable("HIGH" + p)
                            .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                            .rightVariable("0")
                            .build());
                }
            }
        }

        int hv = (filters != null && filters.getVolatility() != null) ? filters.getVolatility().getHvPeriod() : 20;

        return TechFilterConditions.builder()
                .filterExpressions(exprs)
                .hvPeriod(hv)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilterSettings {
        private RsiConfig rsi;
        private BollingerConfig bollinger;

        @JsonProperty("moving_averages")
        private MovingAveragesConfig movingAverages;

        @JsonProperty("exponential_moving_averages")
        private EmaConfig exponentialMovingAverages;

        private VolumeConfig volume;
        private VolatilityConfig volatility;
        private HighsConfig highs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RsiConfig {
        private boolean enabled;
        private int period;
        private List<Integer> periods;
        private double oversold;
        private double overbought;

        public List<Integer> getAllowedPeriods() {
            Set<Integer> set = new LinkedHashSet<>();
            if (period > 0) set.add(period);
            if (periods != null) set.addAll(periods);
            return new ArrayList<>(set);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BollingerConfig {
        private boolean enabled;
        private int period;
        private List<Integer> periods;
        private double stdDev;

        public List<Integer> getAllowedPeriods() {
            Set<Integer> set = new LinkedHashSet<>();
            if (period > 0) set.add(period);
            if (periods != null) set.addAll(periods);
            return new ArrayList<>(set);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HighsConfig {
        private boolean enabled;
        private List<Integer> periods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MovingAveragesConfig {
        private boolean enabled;
        private List<Integer> periods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmaConfig {
        private boolean enabled;
        private List<Integer> periods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VolumeConfig {
        private boolean enabled;

        @JsonProperty("sma_periods")
        private List<Integer> smaPeriods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VolatilityConfig {
        private boolean enabled;

        @JsonProperty("atr_period")
        private int atrPeriod;

        @JsonProperty("atr_periods")
        private List<Integer> atrPeriods;

        @JsonProperty("hv_period")
        private int hvPeriod;

        @JsonProperty("hv_periods")
        private List<Integer> hvPeriods;

        public List<Integer> getAllowedAtrPeriods() {
            Set<Integer> set = new LinkedHashSet<>();
            if (atrPeriod > 0) set.add(atrPeriod);
            if (atrPeriods != null) set.addAll(atrPeriods);
            return new ArrayList<>(set);
        }

        public List<Integer> getAllowedHvPeriods() {
            Set<Integer> set = new LinkedHashSet<>();
            if (hvPeriod > 0) set.add(hvPeriod);
            if (hvPeriods != null) set.addAll(hvPeriods);
            return new ArrayList<>(set);
        }
    }
}
