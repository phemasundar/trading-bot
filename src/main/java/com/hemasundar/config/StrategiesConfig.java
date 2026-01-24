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
 * Contains all options strategies, technical screeners, and global technical
 * indicator settings.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StrategiesConfig {

    /**
     * List of all strategy configurations.
     */
    private List<StrategyEntry> optionsStrategies = new ArrayList<>();

    /**
     * List of all technical screener configurations.
     */
    private List<ScreenerEntry> technicalScreeners = new ArrayList<>();

    /**
     * Global technical indicator settings (used by string-referenced filters).
     */
    private TechnicalIndicatorsConfig technicalIndicators;

    /**
     * Named technical filter presets (e.g., "oversold", "overbought").
     */
    private Map<String, TechnicalFilterConfig> technicalFilters = new HashMap<>();

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
     * POJO representing a single strategy entry in strategies-config.json.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StrategyEntry {

        private boolean enabled = true;
        private StrategyType strategyType;
        private String filterType;
        private Object filter;
        private String securitiesFile;
        private Object technicalFilter;

        public boolean hasTechnicalFilter() {
            return technicalFilter != null;
        }
    }

    /**
     * POJO representing a single screener entry in strategies-config.json.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreenerEntry {

        private boolean enabled = true;
        private ScreenerType screenerType;
        private ScreenerConditionsConfig conditions;
    }

    /**
     * POJO for screener filter conditions.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreenerConditionsConfig {

        private RSICondition rsiCondition;
        private BollingerCondition bollingerCondition;
        private Long minVolume;

        // MA price filters
        private boolean requirePriceBelowMA20 = false;
        private boolean requirePriceAboveMA20 = false;
        private boolean requirePriceBelowMA50 = false;
        private boolean requirePriceAboveMA50 = false;
        private boolean requirePriceBelowMA100 = false;
        private boolean requirePriceAboveMA100 = false;
        private boolean requirePriceBelowMA200 = false;
        private boolean requirePriceAboveMA200 = false;
    }

    /**
     * POJO for global technical indicator settings.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TechnicalIndicatorsConfig {

        private int rsiPeriod = 14;
        private double oversoldThreshold = 30.0;
        private double overboughtThreshold = 70.0;
        private int bollingerPeriod = 20;
        private double bollingerStdDev = 2.0;
    }

    /**
     * POJO for inline technical filter configuration.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TechnicalFilterConfig {

        // Indicator settings
        private int rsiPeriod = 14;
        private double oversoldThreshold = 30.0;
        private double overboughtThreshold = 70.0;
        private int bollingerPeriod = 20;
        private double bollingerStdDev = 2.0;

        // Filter conditions
        private RSICondition rsiCondition;
        private BollingerCondition bollingerCondition;
        private long minVolume = 0;

        // MA price filters
        private boolean requirePriceBelowMA20 = false;
        private boolean requirePriceBelowMA50 = false;
        private boolean requirePriceBelowMA100 = false;
        private boolean requirePriceBelowMA200 = false;
    }
}
