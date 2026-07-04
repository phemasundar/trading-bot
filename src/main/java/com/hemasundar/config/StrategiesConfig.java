package com.hemasundar.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.BollingerCondition;
import com.hemasundar.technical.RSICondition;
import com.hemasundar.technical.ScreenerType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Root POJO for strategies-config.json with all nested configuration classes.
 * Contains all options strategies, technical screeners, and reusable technical
 * indicator config definitions.
 *
 * <h2>Technical Filter Format</h2>
 * Both options strategies and technical screeners use the same {@code technicalFilters}
 * map structure:
 * <pre>
 * "technicalFilters": {
 *     "RSI": {
 *         "config": "default",            // string ref to technicalIndicatorConfigs
 *         "condition": "BULLISH_CROSSOVER"
 *     },
 *     "BOLLINGER_BAND": {
 *         "config": { "period": 20, "stdDev": 2.0 },  // inline config object
 *         "condition": "LOWER_BAND"
 *     },
 *     "VOLUME": { "config": { "min": 1000000 } },
 *     "MOVING_AVERAGE": { "config": { "requirePriceBelowMA200": true } },
 *     "PRICE_DROP": { "config": { "minDropPercent": 5.0, "lookbackDays": 5 } }
 * }
 * </pre>
 * Alternatively, {@code technicalFilters} on a strategy entry may be a string
 * reference to a named preset in the root {@code technicalFilters} map
 * (e.g. {@code "technicalFilters": "oversold"}).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategiesConfig {

    /**
     * List of all option strategy configurations.
     */
    private List<StrategyEntry> optionsStrategies = new ArrayList<>();

    /**
     * List of all technical screener configurations.
     */
    private List<ScreenerEntry> technicalScreeners = new ArrayList<>();

    /**
     * Named reusable indicator parameter configs.
     * Each entry is filter-type-specific — RSI configs carry RSI fields,
     * Bollinger configs carry Bollinger fields, etc. Referenced by name
     * from a filter entry's {@code "config"} field.
     *
     * <p>Example JSON:
     * <pre>
     * "technicalIndicatorConfigs": {
     *     "defaultRSI": { "period": 14, "oversoldThreshold": 30.0, "overboughtThreshold": 70.0 },
     *     "defaultBollingerBand": { "bollingerPeriod": 20, "bollingerStdDev": 2.0 }
     * }
     * </pre>
     */
    private Map<String, Object> technicalIndicatorConfigs = new HashMap<>();

    /**
     * Named technical filter presets (e.g., "oversold", "overbought").
     * Each preset is a {@code Map<String, Object>} using the same structure
     * as an inline {@code technicalFilters} block on a strategy/screener entry.
     * A strategy entry may reference a preset by name:
     * {@code "technicalFilters": "oversold"}.
     */
    private Map<String, Map<String, Object>> technicalFilters = new HashMap<>();

    /**
     * Returns only enabled strategies.
     */
    public List<StrategyEntry> getEnabledStrategies() {
        return optionsStrategies.stream()
                .filter(StrategyEntry::isEnabled)
                .toList();
    }

    /**
     * Returns only enabled screeners.
     */
    public List<ScreenerEntry> getEnabledScreeners() {
        return technicalScreeners.stream()
                .filter(ScreenerEntry::isEnabled)
                .toList();
    }

    // ==================== INNER CLASSES ====================

    /**
     * POJO representing a single option strategy entry in strategies-config.json.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StrategyEntry {

        private boolean enabled = true;
        private StrategyType strategyType;
        private String alias;
        private String filterType;
        private Object filter;
        private String securitiesFile;
        private String descriptionFile;
        /**
         * Optional comma-separated inline stock symbols (e.g., "GOOG, AMZN, META").
         * Combined with securitiesFile symbols into a single unique list.
         */
        private String securities;

        /**
         * Technical filters for this strategy.
         * May be:
         * <ul>
         *   <li>A {@code String} — name of a preset in the root {@code technicalFilters} map</li>
         *   <li>A {@code Map<String, Object>} — inline filter definition</li>
         *   <li>{@code null} — no technical filter applied</li>
         * </ul>
         */
        private Object technicalFilters;

        /**
         * Optional Greek exposure map for this strategy.
         * Keys: "delta", "gamma", "theta", "vega".
         * Values: "positive", "negative", or "neutral".
         */
        private Map<String, String> greeks;

        public boolean hasTechnicalFilter() {
            return technicalFilters != null;
        }

        private int maxTradesToSend = 30;
    }

    /**
     * POJO representing a single screener entry in strategies-config.json.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreenerEntry {

        private boolean enabled = true;
        private String alias;
        private ScreenerType screenerType;
        private String securitiesFile;
        private String securities;

        /**
         * Technical filters for this screener. Uses the same format as
         * {@link StrategyEntry#technicalFilters} — a {@code Map<String, Object>}
         * with keys like "RSI", "BOLLINGER_BAND", "VOLUME", "MOVING_AVERAGE", "PRICE_DROP".
         */
        private Map<String, Object> technicalFilters;
    }

    // TechnicalIndicatorConfigEntry removed — configs are now filter-type-specific.
    // RSI configs → deserialize to RSIConfigParams
    // Bollinger configs → deserialize to BollingerConfigParams
    // Both are stored as Object in the technicalIndicatorConfigs map.
    /**
     * POJO for an RSI filter entry within a {@code technicalFilters} map.
     * Key: {@code "RSI"}.
     *
     * <p>Example JSON:
     * <pre>
     * "RSI": {
     *     "config": "defaultRSI",
     *     "condition": "BULLISH_CROSSOVER"
     * }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RSIFilterEntry {
        /**
         * Either a string reference (e.g. "default") to a named {@code TechnicalIndicatorConfigEntry},
         * or an inline {@code RSIConfigParams} object.
         */
        private Object config;
        private RSICondition condition;
    }

    /**
     * POJO for inline RSI indicator params (used when {@code "config"} is an object, not a string ref).
     * Also used when deserializing a named RSI config entry from {@code technicalIndicatorConfigs}.
     *
     * <p>Named config example:
     * <pre>
     * "defaultRSI": { "period": 14, "oversoldThreshold": 30.0, "overboughtThreshold": 70.0 }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RSIConfigParams {
        private int period = 14;
        private double oversoldThreshold = 30.0;
        private double overboughtThreshold = 70.0;
    }

    /**
     * POJO for a Bollinger Band filter entry within a {@code technicalFilters} map.
     * Key: {@code "BOLLINGER_BAND"}.
     *
     * <p>Example JSON:
     * <pre>
     * "BOLLINGER_BAND": {
     *     "config": "defaultBollingerBand",
     *     "condition": "LOWER_BAND"
     * }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BollingerFilterEntry {
        /**
         * Either a string reference or an inline {@code BollingerConfigParams} object.
         */
        private Object config;
        private BollingerCondition condition;
    }

    /**
     * POJO for inline or named Bollinger Band indicator params.
     * Field names match the expected JSON for a named Bollinger config entry:
     *
     * <p>Named config example:
     * <pre>
     * "defaultBollingerBand": { "bollingerPeriod": 20, "bollingerStdDev": 2.0 }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BollingerConfigParams {
        private int bollingerPeriod = 20;
        private double bollingerStdDev = 2.0;
    }

    /**
     * POJO for a Volume filter entry within a {@code technicalFilters} map.
     * Key: {@code "VOLUME"}.
     *
     * <p>Example JSON:
     * <pre>
     * "VOLUME": { "config": { "min": 1000000 } }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VolumeFilterEntry {
        private VolumeConfigParams config;
    }

    /**
     * POJO for inline Volume filter params.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VolumeConfigParams {
        private long min = 0;
    }

    /**
     * POJO for a Moving Average filter entry within a {@code technicalFilters} map.
     * Key: {@code "MOVING_AVERAGE"}.
     *
     * Example:
     * <pre>
     * "MOVING_AVERAGE": [
     *     "PRICE_ABOVE_SMA50",
     *     "SMA50_ABOVE_SMA200"
     * ]
     * </pre>
     */


    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceCondition {
        private int period;
        private Position position;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmaCondition {
        private int period1;
        private int period2;
        private Position position;
    }

    public enum Position {
        ABOVE,
        BELOW
    }

    /**
     * POJO for a Price Drop filter entry within a {@code technicalFilters} map.
     * Key: {@code "PRICE_DROP"}.
     *
     * <p>Example JSON:
     * <pre>
     * "PRICE_DROP": { "config": { "minDropPercent": 5.0, "lookbackDays": 5 } }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDropFilterEntry {
        private PriceDropConfigParams config;
    }

    /**
     * POJO for inline Price Drop filter params.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDropConfigParams {
        private Double minDropPercent;
        private Integer lookbackDays;
    }

    /**
     * POJO for a Historical Volatility filter entry within a {@code technicalFilters} map.
     * Key: {@code "HISTORICAL_VOLATILITY"}.
     *
     * <p>Example JSON:
     * <pre>
     * "HISTORICAL_VOLATILITY": { "condition": { "min": 25.0 } }
     * </pre>
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoricalVolatilityFilterEntry {
        private HistoricalVolatilityCondition condition;
    }

    /**
     * POJO for inline Historical Volatility filter condition parameters.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoricalVolatilityCondition {
        private Double min;
        private Double max;
    }
}
