package com.hemasundar.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads strategy configurations from a JSON file.
 * Maps JSON configurations to OptionsConfig objects with proper strategy
 * instances.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StrategiesConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            JsonNode root = MAPPER.readTree(json);

            // Parse technical indicators (for filter chains)
            TechnicalIndicators indicators = parseIndicators(root.get("technicalIndicators"));
            Map<String, TechnicalFilterChain> filterChains = parseFilterChains(root.get("technicalFilters"),
                    indicators);

            // Parse each strategy
            JsonNode strategiesNode = root.get("optionsStrategies");
            if (strategiesNode != null && strategiesNode.isArray()) {
                for (JsonNode strategyNode : strategiesNode) {
                    try {
                        OptionsConfig config = parseStrategyConfig(strategyNode, securitiesMap, filterChains);
                        if (config != null) {
                            configs.add(config);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing strategy config: {}", e.getMessage());
                    }
                }
            }

            log.info("Loaded {} strategy configurations from {}", configs.size(), configPath);

        } catch (IOException e) {
            log.error("Error loading strategies config from {}: {}", configPath, e.getMessage());
        }

        return configs;
    }

    private static OptionsConfig parseStrategyConfig(JsonNode node,
            Map<String, List<String>> securitiesMap,
            Map<String, TechnicalFilterChain> filterChains) throws Exception {

        String strategyTypeName = node.get("strategyType").asText();
        String filterTypeName = node.get("filterType").asText();
        String securitiesFileKey = node.get("securitiesFile").asText();
        JsonNode filterNode = node.get("filter");

        // Get strategy type and create strategy instance using enum factory
        StrategyType strategyType = StrategyType.valueOf(strategyTypeName);
        AbstractTradingStrategy strategy = strategyType.createStrategy();

        // Parse filter using FilterType enum
        FilterType filterType = FilterType.fromJsonName(filterTypeName);
        OptionsStrategyFilter filter = filterType.parseFilter(filterNode);

        // Get securities list
        List<String> securities = securitiesMap.getOrDefault(securitiesFileKey, List.of());

        // Get optional technical filter
        TechnicalFilterChain technicalFilterChain = null;
        if (node.has("technicalFilter")) {
            String techFilterKey = node.get("technicalFilter").asText();
            technicalFilterChain = filterChains.get(techFilterKey);
        }

        return OptionsConfig.builder()
                .strategy(strategy)
                .filter(filter)
                .securities(securities)
                .technicalFilterChain(technicalFilterChain)
                .build();
    }

    // createStrategy and parseFilter logic moved to StrategyType.createStrategy()
    // and FilterType.parseFilter()

    private static TechnicalIndicators parseIndicators(JsonNode node) {
        if (node == null) {
            return TechnicalIndicators.builder().build();
        }

        return TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder()
                        .period(node.path("rsiPeriod").asInt(14))
                        .oversoldThreshold(node.path("oversoldThreshold").asDouble(30.0))
                        .overboughtThreshold(node.path("overboughtThreshold").asDouble(70.0))
                        .build())
                .bollingerFilter(BollingerBandsFilter.builder()
                        .period(node.path("bollingerPeriod").asInt(20))
                        .standardDeviations(node.path("bollingerStdDev").asDouble(2.0))
                        .build())
                .build();
    }

    private static Map<String, TechnicalFilterChain> parseFilterChains(JsonNode node, TechnicalIndicators indicators) {
        Map<String, TechnicalFilterChain> chains = new HashMap<>();

        if (node == null) {
            return chains;
        }

        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode condNode = entry.getValue();

            TechFilterConditions conditions = TechFilterConditions.builder()
                    .rsiCondition(parseRsiCondition(condNode.path("rsiCondition").asText()))
                    .bollingerCondition(parseBollingerCondition(condNode.path("bollingerCondition").asText()))
                    .minVolume(condNode.path("minVolume").asLong(0))
                    .build();

            chains.put(key, TechnicalFilterChain.of(indicators, conditions));
        });

        return chains;
    }

    private static RSICondition parseRsiCondition(String value) {
        if (value == null || value.isEmpty())
            return null;
        return RSICondition.valueOf(value);
    }

    private static BollingerCondition parseBollingerCondition(String value) {
        if (value == null || value.isEmpty())
            return null;
        return BollingerCondition.valueOf(value);
    }
}
