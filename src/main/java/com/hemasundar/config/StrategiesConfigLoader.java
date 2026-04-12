package com.hemasundar.config;

import com.hemasundar.config.StrategiesConfig.ScreenerConditionsConfig;
import com.hemasundar.config.StrategiesConfig.ScreenerEntry;
import com.hemasundar.config.StrategiesConfig.StrategyEntry;
import com.hemasundar.config.StrategiesConfig.TechnicalFilterConfig;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.*;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.*;

/**
 * Loads strategy and screener configurations from strategies-config.json.
 * Provides POJO-based parsing for both options strategies and technical
 * screeners.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StrategiesConfigLoader {

    private final List<AbstractTradingStrategy> availableStrategies;
    private final Map<StrategyType, AbstractTradingStrategy> strategyMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AbstractTradingStrategy strategy : availableStrategies) {
            strategyMap.put(strategy.getStrategyType(), strategy);
        }
        log.info("Initialized StrategiesConfigLoader with {} strategies", strategyMap.size());
    }

    /**
     * Retrieves a strategy instance by its type.
     *
     * @param type StrategyType to look up
     * @return AbstractTradingStrategy instance
     * @throws IllegalArgumentException if no strategy is found for the type
     */
    public AbstractTradingStrategy getStrategy(StrategyType type) {
        AbstractTradingStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for type: " + type);
        }
        return strategy;
    }

    // ==================== OPTIONS STRATEGIES ====================

    /**
     * Loads all strategy configurations from the JSON file.
     *
     * @param configResource Path to strategies-config.json
     * @param securitiesMap  Map of securities file key -> actual securities list
     * @return List of OptionsConfig ready to execute
     */
    public List<OptionsConfig> load(String configResource, Map<String, List<String>> securitiesMap) {
        List<OptionsConfig> configs = new ArrayList<>();

        try {
            String json = FilePaths.readResource(configResource);

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

            log.info("Loaded {} strategy configurations from {}", configs.size(), configResource);

        } catch (IOException e) {
            log.error("Error loading strategies config from {}: {}", configResource, e.getMessage());
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
    public List<ScreenerConfig> loadScreeners(Map<String, List<String>> securitiesMap) {
        return loadScreeners(FilePaths.strategiesConfig, securitiesMap);
    }

    /**
     * Loads screener configurations from the specified config file.
     * Only returns enabled screeners.
     *
     * @param configResource path to the strategies-config.json file
     * @param securitiesMap map of securities
     * @return list of enabled ScreenerConfig objects
     */
    public List<ScreenerConfig> loadScreeners(String configResource, Map<String, List<String>> securitiesMap) {
        List<ScreenerConfig> configs = new ArrayList<>();

        try {
            String json = FilePaths.readResource(configResource);

            // Parse root config as POJO
            StrategiesConfig rootConfig = JavaUtils.convertJsonToPojo(json, StrategiesConfig.class);

            // Convert each enabled screener entry to ScreenerConfig
            for (ScreenerEntry entry : rootConfig.getEnabledScreeners()) {
                try {
                    ScreenerConfig config = convertToScreenerConfig(entry, securitiesMap);
                    if (config != null) {
                        configs.add(config);
                    }
                } catch (Exception e) {
                    log.error("Error parsing screener {}: {}", entry.getScreenerType(), e.getMessage());
                }
            }

            log.info("Loaded {} screener configurations from {}", configs.size(), configResource);

        } catch (IOException e) {
            log.error("Error loading screeners config from {}: {}", configResource, e.getMessage());
        }

        return configs;
    }

    private ScreenerConfig convertToScreenerConfig(ScreenerEntry entry, Map<String, List<String>> securitiesMap) {
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
                .minDropPercent(condConfig.getMinDropPercent())
                .lookbackDays(condConfig.getLookbackDays())
                .build();

        // Get securities list from files (supports comma-separated file names)
        List<String> securities = parseSecuritiesFromFiles(entry.getSecuritiesFile(), securitiesMap);

        // Combine with inline securities if specified (supports comma-separated symbols)
        if (entry.getSecurities() != null && !entry.getSecurities().trim().isEmpty()) {
            java.util.Set<String> combined = new java.util.LinkedHashSet<>(securities);
            for (String symbol : entry.getSecurities().split(",")) {
                String trimmed = symbol.trim();
                if (!trimmed.isEmpty()) {
                    combined.add(trimmed);
                }
            }
            securities = new java.util.ArrayList<>(combined);
            log.debug("Combined {} unique securities from files + inline for screener", securities.size());
        }

        return ScreenerConfig.builder()
                .screenerType(entry.getScreenerType())
                .alias(entry.getAlias())
                .securities(securities)
                .conditions(conditions)
                .build();
    }

    // ==================== PRIVATE HELPERS ====================

    private OptionsConfig convertToOptionsConfig(
            StrategyEntry entry,
            Map<String, List<String>> securitiesMap,
            Map<String, TechnicalFilterChain> filterChains) throws Exception {

        // Fetch strategy instance from map
        AbstractTradingStrategy strategy = strategyMap.get(entry.getStrategyType());
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy not found for type: " + entry.getStrategyType());
        }

        // Parse filter using FilterType enum
        FilterType filterType = FilterType.fromJsonName(entry.getFilterType());
        OptionsStrategyFilter filter = filterType.parseFilter(entry.getFilter());

        // Get securities list from files (supports comma-separated file names)
        List<String> securities = parseSecuritiesFromFiles(entry.getSecuritiesFile(), securitiesMap);

        // Combine with inline securities if specified (supports comma-separated
        // symbols)
        if (entry.getSecurities() != null && !entry.getSecurities().trim().isEmpty()) {
            java.util.Set<String> combined = new java.util.LinkedHashSet<>(securities);
            for (String symbol : entry.getSecurities().split(",")) {
                String trimmed = symbol.trim();
                if (!trimmed.isEmpty()) {
                    combined.add(trimmed);
                }
            }
            securities = new java.util.ArrayList<>(combined);
            log.debug("Combined {} unique securities from files + inline", securities.size());
        }

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
                .alias(entry.getAlias())
                .descriptionFile(entry.getDescriptionFile())
                .maxTradesToSend(entry.getMaxTradesToSend())
                .build();
    }

    private TechnicalFilterChain resolveTechnicalFilter(
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

    private Map<String, TechnicalFilterChain> buildFilterChains(
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

    private TechnicalFilterChain buildFilterChainFromConfig(TechnicalFilterConfig config) {
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

    private List<String> parseSecuritiesFromFiles(
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
