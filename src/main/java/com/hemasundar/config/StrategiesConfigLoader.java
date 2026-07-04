package com.hemasundar.config;

import com.hemasundar.config.StrategiesConfig.*;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.*;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.WikipediaSecuritiesFetcher;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.*;

/**
 * Loads strategy and screener configurations from strategies-config.json.
 * Provides POJO-based parsing for both options strategies and technical screeners.
 *
 * <h2>technicalFilters resolution</h2>
 * Both strategies and screeners use the same {@code technicalFilters} format.
 * On a strategy entry {@code technicalFilters} may be:
 * <ul>
 *   <li>A {@code String} — name of a preset in the root {@code technicalFilters} map</li>
 *   <li>A {@code Map<String, Object>} — inline filter definition</li>
 * </ul>
 * On a screener entry {@code technicalFilters} is always a {@code Map<String, Object>}.
 *
 * <p>Each filter entry's {@code "config"} field may be:
 * <ul>
 *   <li>A {@code String} — name of a named indicator config in {@code technicalIndicatorConfigs}</li>
 *   <li>An inline object — deserialized directly to the appropriate config POJO</li>
 * </ul>
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StrategiesConfigLoader {

    private final List<AbstractTradingStrategy> availableStrategies;

    /**
     * Fetcher for dynamic Wikipedia index constituents (SPY, QQQ).
     * Called lazily — only when a strategy references SPY or QQQ and is about to run.
     */
    private final WikipediaSecuritiesFetcher wikipediaFetcher;

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

            // Build named preset filter-map lookup from root technicalFilters
            Map<String, Map<String, Object>> presetFilters = rootConfig.getTechnicalFilters();
            Map<String, Object> indicatorConfigs = rootConfig.getTechnicalIndicatorConfigs();

            // Convert each enabled strategy entry to OptionsConfig
            for (StrategyEntry entry : rootConfig.getEnabledStrategies()) {
                try {
                    OptionsConfig config = convertToOptionsConfig(entry, securitiesMap, presetFilters, indicatorConfigs);
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
     * @param securitiesMap  map of securities
     * @return list of enabled ScreenerConfig objects
     */
    public List<ScreenerConfig> loadScreeners(String configResource, Map<String, List<String>> securitiesMap) {
        List<ScreenerConfig> configs = new ArrayList<>();

        try {
            String json = FilePaths.readResource(configResource);

            // Parse root config as POJO
            StrategiesConfig rootConfig = JavaUtils.convertJsonToPojo(json, StrategiesConfig.class);
            Map<String, Object> indicatorConfigs = rootConfig.getTechnicalIndicatorConfigs();

            // Convert each enabled screener entry to ScreenerConfig
            for (ScreenerEntry entry : rootConfig.getEnabledScreeners()) {
                try {
                    ScreenerConfig config = convertToScreenerConfig(entry, securitiesMap, indicatorConfigs);
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

    private ScreenerConfig convertToScreenerConfig(
            ScreenerEntry entry,
            Map<String, List<String>> securitiesMap,
            Map<String, Object> indicatorConfigs) {

        // Build TechnicalFilterChain from the technicalFilters map
        TechnicalFilterChain filterChain = buildFilterChainFromMap(
                entry.getTechnicalFilters(), indicatorConfigs);

        // Get securities list from files (supports comma-separated file names)
        List<String> securities = parseSecuritiesFromFiles(entry.getSecuritiesFile(), securitiesMap);

        // Combine with inline securities if specified
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
                .filterChain(filterChain)
                .build();
    }

    // ==================== PRIVATE HELPERS ====================

    private OptionsConfig convertToOptionsConfig(
            StrategyEntry entry,
            Map<String, List<String>> securitiesMap,
            Map<String, Map<String, Object>> presetFilters,
            Map<String, Object> indicatorConfigs) throws Exception {

        // Fetch strategy instance from map
        AbstractTradingStrategy strategy = strategyMap.get(entry.getStrategyType());
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy not found for type: " + entry.getStrategyType());
        }

        // Parse filter using FilterType enum
        FilterType filterType = FilterType.fromJsonName(entry.getFilterType());
        OptionsStrategyFilter filter = filterType.parseFilter(entry.getFilter());
        if (filter != null) {
            filter.setSecuritiesFile(entry.getSecuritiesFile());
            if (entry.getSecurities() != null && !entry.getSecurities().trim().isEmpty()) {
                filter.setSecurities(entry.getSecurities());
            }
            if (entry.getGreeks() != null && !entry.getGreeks().isEmpty()) {
                filter.setGreeks(entry.getGreeks());
            }
        }

        // Get securities list from files (supports comma-separated file names)
        List<String> securities = parseSecuritiesFromFiles(entry.getSecuritiesFile(), securitiesMap);

        // Combine with inline securities if specified
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

        // Get optional technical filter chain
        TechnicalFilterChain technicalFilterChain = null;
        if (entry.hasTechnicalFilter()) {
            Map<String, Object> filtersMap = resolveTechnicalFiltersMap(entry.getTechnicalFilters(), presetFilters);
            if (filtersMap != null) {
                technicalFilterChain = buildFilterChainFromMap(filtersMap, indicatorConfigs);
                if (filter != null) {
                    filter.setTechnicalFilters(filtersMap);
                }
            }
        }

        return OptionsConfig.builder()
                .strategy(strategy)
                .filter(filter)
                .securities(securities)
                .technicalFilterChain(technicalFilterChain)
                .alias(entry.getAlias())
                .descriptionFile(entry.getDescriptionFile())
                .maxTradesToSend(entry.getMaxTradesToSend())
                .greeks(entry.getGreeks())
                .build();
    }

    /**
     * Resolves {@code technicalFilters} on a strategy entry into a map.
     * The value may be:
     * <ul>
     *   <li>A {@code String} — preset name; looked up in {@code presetFilters}</li>
     *   <li>A {@code Map<String, Object>} — inline filter definition</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTechnicalFiltersMap(
            Object technicalFilters,
            Map<String, Map<String, Object>> presetFilters) {

        if (technicalFilters instanceof String presetName) {
            // String reference to a root-level named preset
            Map<String, Object> filtersMap = presetFilters.get(presetName);
            if (filtersMap == null) {
                log.warn("technicalFilters preset '{}' not found in root technicalFilters map", presetName);
                return null;
            }
            return filtersMap;
        } else {
            // Inline map
            return (Map<String, Object>) technicalFilters;
        }
    }

    /**
     * Builds a {@link TechnicalFilterChain} from a {@code technicalFilters} map.
     * Keys: "RSI", "BOLLINGER_BAND", "VOLUME", "MOVING_AVERAGE", "PRICE_DROP".
     */
    public TechnicalFilterChain parseTechnicalFilters(Map<String, Object> filtersMap) {
        return buildFilterChainFromMap(filtersMap, java.util.Collections.emptyMap());
    }

    private TechnicalFilterChain buildFilterChainFromMap(
            Map<String, Object> filtersMap,
            Map<String, Object> indicatorConfigs) {

        // Pre-populate with default indicators so UI/Telegram receive full context for all screeners,
        // even if not explicitly defined in the strategy-config. Explicitly defined ones will overwrite these.
        TechnicalIndicators.TechnicalIndicatorsBuilder indicatorsBuilder = TechnicalIndicators.createDefaults().toBuilder();
                
        TechFilterConditions.TechFilterConditionsBuilder conditionsBuilder = TechFilterConditions.builder();

        if (filtersMap != null) {
            for (Map.Entry<String, Object> entry : filtersMap.entrySet()) {
                String key = entry.getKey().toUpperCase();
                Object rawEntry = entry.getValue();

                switch (key) {
                    case "RSI" -> applyRsiFilter(rawEntry, indicatorConfigs, indicatorsBuilder, conditionsBuilder);
                    case "BOLLINGER_BAND" -> applyBollingerFilter(rawEntry, indicatorConfigs, indicatorsBuilder, conditionsBuilder);
                    case "VOLUME" -> applyVolumeFilter(rawEntry, conditionsBuilder);
                    case "MOVING_AVERAGE" -> applyMovingAverageFilters(rawEntry, indicatorsBuilder, conditionsBuilder);
                    case "PRICE_DROP" -> applyPriceDropFilter(rawEntry, conditionsBuilder);
                    case "HISTORICAL_VOLATILITY" -> applyHistoricalVolatilityFilter(rawEntry, conditionsBuilder);
                    default -> log.warn("Unknown technicalFilters key '{}' — skipping", key);
                }
            }
        }

        return TechnicalFilterChain.of(indicatorsBuilder.build(), conditionsBuilder.build());
    }

    // ─────────────────────────────────────────────────────────
    //  Per-filter-type application helpers
    // ─────────────────────────────────────────────────────────

    private void applyRsiFilter(
            Object rawEntry,
            Map<String, Object> indicatorConfigs,
            TechnicalIndicators.TechnicalIndicatorsBuilder indicators,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        RSIFilterEntry entry = JavaUtils.convertValue(rawEntry, RSIFilterEntry.class);
        RSIConfigParams cfg = resolveRSIConfig(entry.getConfig(), indicatorConfigs);

        indicators.rsiFilter(RSIFilter.builder()
                .period(cfg.getPeriod())
                .oversoldThreshold(cfg.getOversoldThreshold())
                .overboughtThreshold(cfg.getOverboughtThreshold())
                .build());

        if (entry.getCondition() != null) {
            conditions.rsiCondition(entry.getCondition());
        }
    }

    private void applyBollingerFilter(
            Object rawEntry,
            Map<String, Object> indicatorConfigs,
            TechnicalIndicators.TechnicalIndicatorsBuilder indicators,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        BollingerFilterEntry entry = JavaUtils.convertValue(rawEntry, BollingerFilterEntry.class);
        BollingerConfigParams cfg = resolveBollingerConfig(entry.getConfig(), indicatorConfigs);

        indicators.bollingerFilter(BollingerBandsFilter.builder()
                .period(cfg.getBollingerPeriod())
                .standardDeviations(cfg.getBollingerStdDev())
                .build());

        if (entry.getCondition() != null) {
            conditions.bollingerCondition(entry.getCondition());
        }
    }

    private void applyVolumeFilter(
            Object rawEntry,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        VolumeFilterEntry entry = JavaUtils.convertValue(rawEntry, VolumeFilterEntry.class);
        if (entry.getConfig() != null && entry.getConfig().getMin() > 0) {
            conditions.minVolume(entry.getConfig().getMin());
        }
    }

    public void applyMovingAverageFilters(
            Object rawEntry,
            TechnicalIndicators.TechnicalIndicatorsBuilder indicators,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        List<String> rules = new ArrayList<>();
        if (rawEntry instanceof List) {
            for (Object obj : (List<?>) rawEntry) {
                rules.add(String.valueOf(obj));
            }
        } else if (rawEntry instanceof String) {
            rules.add((String) rawEntry);
        } else {
            log.warn("Invalid MOVING_AVERAGE config format. Expected string or list of strings.");
            return;
        }

        java.util.Map<Integer, MovingAverageFilter> existingMaFilters = indicators.build().getMaFilters();
        java.util.Map<Integer, MovingAverageFilter> maFilters = new java.util.HashMap<>(existingMaFilters != null ? existingMaFilters : java.util.Map.of());

        List<PriceCondition> priceConds = new ArrayList<>();
        List<SmaCondition> smaConds = new ArrayList<>();

        java.util.regex.Pattern pricePattern = java.util.regex.Pattern.compile("^PRICE_(ABOVE|BELOW)_SMA(\\d+)$");
        java.util.regex.Pattern smaPattern = java.util.regex.Pattern.compile("^SMA(\\d+)_(ABOVE|BELOW)_SMA(\\d+)$");

        for (String rule : rules) {
            rule = rule.toUpperCase().trim();
            java.util.regex.Matcher pMatcher = pricePattern.matcher(rule);
            if (pMatcher.matches()) {
                StrategiesConfig.Position pos = StrategiesConfig.Position.valueOf(pMatcher.group(1));
                int period = Integer.parseInt(pMatcher.group(2));
                maFilters.putIfAbsent(period, MovingAverageFilter.builder().period(period).build());
                
                PriceCondition pc = new PriceCondition();
                pc.setPeriod(period);
                pc.setPosition(pos);
                priceConds.add(pc);
                continue;
            }

            java.util.regex.Matcher sMatcher = smaPattern.matcher(rule);
            if (sMatcher.matches()) {
                int p1 = Integer.parseInt(sMatcher.group(1));
                StrategiesConfig.Position pos = StrategiesConfig.Position.valueOf(sMatcher.group(2));
                int p2 = Integer.parseInt(sMatcher.group(3));
                
                maFilters.putIfAbsent(p1, MovingAverageFilter.builder().period(p1).build());
                maFilters.putIfAbsent(p2, MovingAverageFilter.builder().period(p2).build());

                SmaCondition sc = new SmaCondition();
                sc.setPeriod1(p1);
                sc.setPeriod2(p2);
                sc.setPosition(pos);
                smaConds.add(sc);
                continue;
            }
            log.warn("Unrecognized MOVING_AVERAGE rule: {}", rule);
        }

        TechFilterConditions builtConds = conditions.build();
        if (!priceConds.isEmpty()) {
            List<PriceCondition> existingPriceConds = builtConds.getPriceConditions();
            if (existingPriceConds != null) priceConds.addAll(0, existingPriceConds);
            conditions.priceConditions(priceConds);
        }

        if (!smaConds.isEmpty()) {
            List<SmaCondition> existingSmaConds = builtConds.getSmaConditions();
            if (existingSmaConds != null) smaConds.addAll(0, existingSmaConds);
            conditions.smaConditions(smaConds);
        }

        indicators.maFilters(maFilters);
    }

    private void applyPriceDropFilter(
            Object rawEntry,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        PriceDropFilterEntry entry = JavaUtils.convertValue(rawEntry, PriceDropFilterEntry.class);
        if (entry.getConfig() != null) {
            conditions.minDropPercent(entry.getConfig().getMinDropPercent());
            conditions.lookbackDays(entry.getConfig().getLookbackDays());
        }
    }

    private void applyHistoricalVolatilityFilter(
            Object rawEntry,
            TechFilterConditions.TechFilterConditionsBuilder conditions) {

        StrategiesConfig.HistoricalVolatilityFilterEntry entry = JavaUtils.convertValue(rawEntry, StrategiesConfig.HistoricalVolatilityFilterEntry.class);
        if (entry != null) {
            if (entry.getConfig() != null && entry.getConfig().getPeriod() != null) {
                conditions.hvPeriod(entry.getConfig().getPeriod());
            }
            if (entry.getCondition() != null) {
                if (entry.getCondition().getMinRank() != null) {
                    conditions.minHvRank(entry.getCondition().getMinRank());
                }
                if (entry.getCondition().getMaxRank() != null) {
                    conditions.maxHvRank(entry.getCondition().getMaxRank());
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Type-specific indicator config resolution
    // ─────────────────────────────────────────────────────────

    /**
     * Resolves an RSI filter entry's {@code "config"} field to {@link RSIConfigParams}.
     * <ul>
     *   <li>String → look up by name in {@code indicatorConfigs}, deserialize as {@link RSIConfigParams}</li>
     *   <li>Object (inline) → deserialize directly as {@link RSIConfigParams}</li>
     *   <li>null → return defaults (period=14, oversold=30, overbought=70)</li>
     * </ul>
     */
    private RSIConfigParams resolveRSIConfig(Object configValue, Map<String, Object> indicatorConfigs) {
        RSIConfigParams defaults = new RSIConfigParams();
        if (configValue == null) return defaults;
        if (configValue instanceof String refName) {
            Object named = indicatorConfigs.get(refName);
            if (named == null) {
                log.warn("technicalIndicatorConfigs reference '{}' not found for RSI — using defaults", refName);
                return defaults;
            }
            // If the named config is a map, it MUST contain the expected "RSI" key wrapper
            if (named instanceof Map namedMap) {
                if (namedMap.containsKey("RSI")) {
                    named = namedMap.get("RSI");
                } else {
                    throw new IllegalArgumentException("Named config '" + refName + "' does not contain an 'RSI' configuration block.");
                }
            } else {
                throw new IllegalArgumentException("Named config '" + refName + "' is invalid. It must be a JSON object containing configuration blocks.");
            }
            RSIConfigParams resolved = JavaUtils.convertValue(named, RSIConfigParams.class);
            return resolved != null ? resolved : defaults;
        }
        RSIConfigParams inline = JavaUtils.convertValue(configValue, RSIConfigParams.class);
        return inline != null ? inline : defaults;
    }

    /**
     * Resolves a Bollinger filter entry's {@code "config"} field to {@link BollingerConfigParams}.
     * <ul>
     *   <li>String → look up by name in {@code indicatorConfigs}, deserialize as {@link BollingerConfigParams}</li>
     *   <li>Object (inline) → deserialize directly as {@link BollingerConfigParams}</li>
     *   <li>null → return defaults (bollingerPeriod=20, bollingerStdDev=2.0)</li>
     * </ul>
     */
    private BollingerConfigParams resolveBollingerConfig(Object configValue, Map<String, Object> indicatorConfigs) {
        BollingerConfigParams defaults = new BollingerConfigParams();
        if (configValue == null) return defaults;
        if (configValue instanceof String refName) {
            Object named = indicatorConfigs.get(refName);
            if (named == null) {
                log.warn("technicalIndicatorConfigs reference '{}' not found for Bollinger — using defaults", refName);
                return defaults;
            }
            // If the named config is a map, it MUST contain the expected "BOLLINGER_BAND" key wrapper
            if (named instanceof Map namedMap) {
                if (namedMap.containsKey("BOLLINGER_BAND")) {
                    named = namedMap.get("BOLLINGER_BAND");
                } else {
                    throw new IllegalArgumentException("Named config '" + refName + "' does not contain a 'BOLLINGER_BAND' configuration block.");
                }
            } else {
                throw new IllegalArgumentException("Named config '" + refName + "' is invalid. It must be a JSON object containing configuration blocks.");
            }
            BollingerConfigParams resolved = JavaUtils.convertValue(named, BollingerConfigParams.class);
            return resolved != null ? resolved : defaults;
        }
        BollingerConfigParams inline = JavaUtils.convertValue(configValue, BollingerConfigParams.class);
        return inline != null ? inline : defaults;
    }

    // ─────────────────────────────────────────────────────────
    //  Securities helpers
    // ─────────────────────────────────────────────────────────

    private List<String> parseSecuritiesFromFiles(
            String securitiesFile,
            Map<String, List<String>> securitiesMap) {

        if (securitiesFile == null || securitiesFile.trim().isEmpty()) {
            return List.of();
        }

        String[] fileNames = securitiesFile.split(",");
        Set<String> uniqueSecurities = new LinkedHashSet<>();

        for (String fileName : fileNames) {
            String key = fileName.trim();

            // Fast path: key is in the pre-loaded static map
            if (securitiesMap.containsKey(key)) {
                List<String> securities = securitiesMap.get(key);
                uniqueSecurities.addAll(securities);
                if (securities.isEmpty()) {
                    log.warn("Securities key '{}' is present in the map but empty", key);
                }
                continue;
            }

            // Lazy path: dynamic keyword — fetch from Wikipedia on demand.
            if (key.equalsIgnoreCase("SPY") || key.equalsIgnoreCase("QQQ")) {
                log.info("Lazily fetching dynamic securities for keyword '{}' from Wikipedia", key);
                List<String> tickers = wikipediaFetcher.fetch(key);
                log.info("Fetched {} tickers for '{}' from Wikipedia", tickers.size(), key);
                uniqueSecurities.addAll(tickers);
                continue;
            }

            log.warn("Securities key '{}' not found in static map and is not a recognised dynamic keyword (SPY/QQQ)", key);
        }

        log.debug("Resolved {} unique securities from '{}'", uniqueSecurities.size(), securitiesFile);
        return new ArrayList<>(uniqueSecurities);
    }
}
