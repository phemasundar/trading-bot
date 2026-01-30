package com.hemasundar.config;

import com.hemasundar.config.StrategiesConfig.ScreenerConditionsConfig;
import com.hemasundar.config.StrategiesConfig.ScreenerEntry;
import com.hemasundar.config.StrategiesConfig.StrategyEntry;
import com.hemasundar.config.StrategiesConfig.TechnicalFilterConfig;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.technical.*;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads strategy and screener configurations from strategies-config.json.
 * Provides POJO-based parsing for both options strategies and technical
 * screeners.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StrategiesConfigLoader {

    // ==================== OPTIONS STRATEGIES ====================

    /**
     * Loads all strategy configurations from the JSON file.
     *
     * @param configPath    Path to strategies-config.json
     * @param securitiesMap Map of securities file key -> actual securities list
     * @return List of OptionsConfig ready to execute
     */
    public static List<OptionsConfig> load(Path configPath, Map<String, List<String>> securitiesMap) {
        List<OptionsConfig> configs = new ArrayList<>();

        try {
            String json = Files.readString(configPath);

            // Parse root config as POJO
            StrategiesConfig rootConfig = JavaUtils.convertJsonToPojo(json, StrategiesConfig.class);

            // Build named filter chains from presets
            Map<String, TechnicalFilterChain> filterChains = buildFilterChains(
                    rootConfig.getTechnicalFilters());

            // Convert each enabled strategy entry to OptionsConfig
            for (StrategyEntry entry : rootConfig.getEnabledStrategies()) {
                try {
                    OptionsConfig config = convertToOptionsConfig(entry, securitiesMap, filterChains);
                    if (config != null) {
                        configs.add(config);
                    }
                } catch (Exception e) {
                    log.error("Error parsing strategy {}: {}", entry.getStrategyType(), e.getMessage());
                }
            }

            log.info("Loaded {} strategy configurations from {}", configs.size(), configPath);

        } catch (IOException e) {
            log.error("Error loading strategies config from {}: {}", configPath, e.getMessage());
        }

        return configs;
    }

    // ==================== TECHNICAL SCREENERS ====================

    /**
     * Loads screener configurations from the default strategies-config.json path.
     * Only returns enabled screeners.
     *
     * @return list of enabled ScreenerConfig objects
     */
    public static List<ScreenerConfig> loadScreeners() {
        return loadScreeners(FilePaths.strategiesConfig);
    }

    /**
     * Loads screener configurations from the specified config file.
     * Only returns enabled screeners.
     *
     * @param configPath path to the strategies-config.json file
     * @return list of enabled ScreenerConfig objects
     */
    public static List<ScreenerConfig> loadScreeners(Path configPath) {
        List<ScreenerConfig> configs = new ArrayList<>();

        try {
            String json = Files.readString(configPath);

            // Parse root config as POJO
            StrategiesConfig rootConfig = JavaUtils.convertJsonToPojo(json, StrategiesConfig.class);

            // Convert each enabled screener entry to ScreenerConfig
            for (ScreenerEntry entry : rootConfig.getEnabledScreeners()) {
                try {
                    ScreenerConfig config = convertToScreenerConfig(entry);
                    if (config != null) {
                        configs.add(config);
                    }
                } catch (Exception e) {
                    log.error("Error parsing screener {}: {}", entry.getScreenerType(), e.getMessage());
                }
            }

            log.info("Loaded {} screener configurations from {}", configs.size(), configPath);

        } catch (IOException e) {
            log.error("Error loading screeners config from {}: {}", configPath, e.getMessage());
        }

        return configs;
    }

    /**
     * Converts a ScreenerEntry POJO to a ScreenerConfig.
     */
    private static ScreenerConfig convertToScreenerConfig(ScreenerEntry entry) {
        ScreenerConditionsConfig condConfig = entry.getConditions();

        // Build TechFilterConditions from the POJO
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(condConfig.getRsiCondition())
                .bollingerCondition(condConfig.getBollingerCondition())
                .minVolume(condConfig.getMinVolume())
                .requirePriceBelowMA20(condConfig.isRequirePriceBelowMA20())
                .requirePriceAboveMA20(condConfig.isRequirePriceAboveMA20())
                .requirePriceBelowMA50(condConfig.isRequirePriceBelowMA50())
                .requirePriceAboveMA50(condConfig.isRequirePriceAboveMA50())
                .requirePriceBelowMA100(condConfig.isRequirePriceBelowMA100())
                .requirePriceAboveMA100(condConfig.isRequirePriceAboveMA100())
                .requirePriceBelowMA200(condConfig.isRequirePriceBelowMA200())
                .requirePriceAboveMA200(condConfig.isRequirePriceAboveMA200())
                .build();

        return ScreenerConfig.builder()
                .screenerType(entry.getScreenerType())
                .conditions(conditions)
                .build();
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Converts a StrategyEntry POJO to an OptionsConfig.
     */
    private static OptionsConfig convertToOptionsConfig(
            StrategyEntry entry,
            Map<String, List<String>> securitiesMap,
            Map<String, TechnicalFilterChain> filterChains) throws Exception {

        // Create strategy instance using enum factory
        AbstractTradingStrategy strategy = entry.getStrategyType().createStrategy();

        // Parse filter using FilterType enum
        FilterType filterType = FilterType.fromJsonName(entry.getFilterType());
        OptionsStrategyFilter filter = filterType.parseFilter(entry.getFilter());

        // Get securities list (supports comma-separated file names)
        List<String> securities = parseSecuritiesFromFiles(entry.getSecuritiesFile(), securitiesMap);

        // Get optional technical filter
        TechnicalFilterChain technicalFilterChain = null;
        if (entry.hasTechnicalFilter()) {
            technicalFilterChain = resolveTechnicalFilter(
                    entry.getTechnicalFilter(), filterChains);
        }

        return OptionsConfig.builder()
                .strategy(strategy)
                .filter(filter)
                .securities(securities)
                .technicalFilterChain(technicalFilterChain)
                .maxTradesToSend(entry.getMaxTradesToSend())
                .build();
    }

    /**
     * Resolves technical filter - handles both string reference and inline config.
     */
    private static TechnicalFilterChain resolveTechnicalFilter(
            Object techFilter,
            Map<String, TechnicalFilterChain> filterChains) {

        if (techFilter instanceof String filterName) {
            // String reference to predefined filter
            return filterChains.get(filterName);
        } else {
            // Inline object - convert to TechnicalFilterConfig
            TechnicalFilterConfig config = JavaUtils.convertValue(techFilter, TechnicalFilterConfig.class);
            return buildFilterChainFromConfig(config);
        }
    }

    /**
     * Builds named filter chains from preset configurations.
     */
    private static Map<String, TechnicalFilterChain> buildFilterChains(
            Map<String, TechnicalFilterConfig> presets) {

        Map<String, TechnicalFilterChain> chains = new HashMap<>();

        if (presets == null) {
            return chains;
        }

        for (Map.Entry<String, TechnicalFilterConfig> entry : presets.entrySet()) {
            TechnicalFilterChain chain = buildFilterChainFromConfig(entry.getValue());
            chains.put(entry.getKey(), chain);
        }

        return chains;
    }

    /**
     * Builds a TechnicalFilterChain from config.
     */
    private static TechnicalFilterChain buildFilterChainFromConfig(TechnicalFilterConfig config) {
        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder()
                        .period(config.getRsiPeriod())
                        .oversoldThreshold(config.getOversoldThreshold())
                        .overboughtThreshold(config.getOverboughtThreshold())
                        .build())
                .bollingerFilter(BollingerBandsFilter.builder()
                        .period(config.getBollingerPeriod())
                        .standardDeviations(config.getBollingerStdDev())
                        .build())
                .build();

        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(config.getRsiCondition())
                .bollingerCondition(config.getBollingerCondition())
                .minVolume(config.getMinVolume())
                .requirePriceBelowMA20(config.isRequirePriceBelowMA20())
                .requirePriceBelowMA50(config.isRequirePriceBelowMA50())
                .requirePriceBelowMA100(config.isRequirePriceBelowMA100())
                .requirePriceBelowMA200(config.isRequirePriceBelowMA200())
                .build();

        return TechnicalFilterChain.of(indicators, conditions);
    }

    /**
     * Parses comma-separated securities file names and combines all securities into
     * a unique list.
     * 
     * @param securitiesFile comma-separated file names (e.g.,
     *                       "portfolio,tracking,2026")
     * @param securitiesMap  map of file names to securities lists
     * @return combined unique list of securities from all specified files
     */
    private static List<String> parseSecuritiesFromFiles(
            String securitiesFile,
            Map<String, List<String>> securitiesMap) {

        if (securitiesFile == null || securitiesFile.trim().isEmpty()) {
            return List.of();
        }

        // Split by comma and trim whitespace
        String[] fileNames = securitiesFile.split(",");

        // Use LinkedHashSet to maintain order and uniqueness
        Set<String> uniqueSecurities = new LinkedHashSet<>();

        for (String fileName : fileNames) {
            String trimmedFileName = fileName.trim();
            List<String> securities = securitiesMap.getOrDefault(trimmedFileName, List.of());
            uniqueSecurities.addAll(securities);

            if (securities.isEmpty()) {
                log.warn("Securities file '{}' not found in map or is empty", trimmedFileName);
            }
        }

        log.debug("Loaded {} unique securities from files: {}", uniqueSecurities.size(), securitiesFile);
        return new ArrayList<>(uniqueSecurities);
    }
}
