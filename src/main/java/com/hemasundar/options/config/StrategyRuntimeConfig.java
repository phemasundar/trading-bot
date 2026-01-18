package com.hemasundar.options.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.options.strategies.StrategyType;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for all strategy runtime configurations.
 * Loaded from JSON config file at startup.
 * 
 * Provides a simple API to check if a strategy is enabled:
 * 
 * <pre>
 * StrategyRuntimeConfig config = StrategyRuntimeConfig.load(FilePaths.strategiesConfig);
 * if (config.isEnabled(StrategyType.PUT_CREDIT_SPREAD)) {
 *     // Execute strategy
 * }
 * </pre>
 */
@Data
@Log4j2
public class StrategyRuntimeConfig {

    /**
     * Map of strategy name (StrategyType enum name) to its runtime settings.
     */
    private Map<String, StrategyRuntimeSettings> strategies = new HashMap<>();

    /**
     * Checks if a strategy is enabled.
     * Returns true if strategy is not in config (default: enabled).
     *
     * @param type The strategy type to check
     * @return true if strategy should be executed
     */
    public boolean isEnabled(StrategyType type) {
        StrategyRuntimeSettings settings = strategies.get(type.name());
        return settings == null || settings.isEnabled();
    }

    /**
     * Gets the runtime settings for a specific strategy.
     * Returns null if strategy is not in config.
     *
     * @param type The strategy type
     * @return Settings for the strategy, or null if not configured
     */
    public StrategyRuntimeSettings getSettings(StrategyType type) {
        return strategies.get(type.name());
    }

    /**
     * Loads strategy runtime config from a JSON file.
     *
     * @param path Path to the JSON config file
     * @return Loaded config, or empty config if file doesn't exist
     */
    public static StrategyRuntimeConfig load(Path path) {
        if (!Files.exists(path)) {
            log.warn("Strategy config file not found: {}. All strategies enabled by default.", path);
            return new StrategyRuntimeConfig();
        }

        try {
            String json = Files.readString(path);
            ObjectMapper mapper = new ObjectMapper();
            StrategyRuntimeConfig config = mapper.readValue(json, StrategyRuntimeConfig.class);
            log.info("Loaded strategy runtime config from: {}", path);
            return config;
        } catch (IOException e) {
            log.error("Error loading strategy config: {}. All strategies enabled by default.", e.getMessage());
            return new StrategyRuntimeConfig();
        }
    }
}
