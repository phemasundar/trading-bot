package com.hemasundar.technical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RsiConfig {
        private boolean enabled;
        private int period;
        private double oversold;
        private double overbought;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BollingerConfig {
        private boolean enabled;
        private int period;
        private double stdDev;
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

        @JsonProperty("hv_period")
        private int hvPeriod;
    }
}
