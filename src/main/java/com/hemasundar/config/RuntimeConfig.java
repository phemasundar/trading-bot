package com.hemasundar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.ScreenerType;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified runtime configuration for both strategies and screeners.
 * Loaded from a single JSON config file at startup.
 * 
 * Example JSON structure:
 * 
 * <pre>
 * {
 *     "strategies": {
 *         "PUT_CREDIT_SPREAD": { "enabled": true }
 *     },
 *     "screeners": {
 *         "RSI_BB_BULLISH_CROSSOVER": { "enabled": true }
 *     }
 * }
 * </pre>
 * 
 * Usage:
 * 
 * <pre>
 * RuntimeConfig config = RuntimeConfig.load(FilePaths.runtimeConfig);
 * if (config.isStrategyEnabled(StrategyType.PUT_CREDIT_SPREAD)) {
 *     // Execute strategy
 * }
 * </pre>
 */
@Data
@Log4j2
public class RuntimeConfig {

    /**
     * Map of strategy name (StrategyType enum name) to its runtime settings.
     */
    private Map<String, RuntimeSettings> strategies = new HashMap<>();

    /**
     * Map of screener name (ScreenerType enum name) to its runtime settings.
     */
    private Map<String, RuntimeSettings> screeners = new HashMap<>();

    // ==================== Strategy Methods ====================

    /**
     * Checks if a strategy is enabled.
     * Returns true if strategy is not in config (default: enabled).
     */
    public boolean isStrategyEnabled(StrategyType type) {
        RuntimeSettings settings = strategies.get(type.name());
        return settings == null || settings.isEnabled();
    }

    /**
     * Gets the runtime settings for a specific strategy.
     */
    public RuntimeSettings getStrategySettings(StrategyType type) {
        return strategies.get(type.name());
    }

    // ==================== Screener Methods ====================

    /**
     * Checks if a screener is enabled.
     * Returns true if screener is not in config (default: enabled).
     */
    public boolean isScreenerEnabled(ScreenerType type) {
        RuntimeSettings settings = screeners.get(type.name());
        return settings == null || settings.isEnabled();
    }

    /**
     * Gets the runtime settings for a specific screener.
     */
    public RuntimeSettings getScreenerSettings(ScreenerType type) {
        return screeners.get(type.name());
    }

    // ==================== Static Loader ====================

    /**
     * Loads runtime config from a JSON file.
     *
     * @param path Path to the JSON config file
     * @return Loaded config, or empty config if file doesn't exist
     */
    public static RuntimeConfig load(Path path) {
        if (!Files.exists(path)) {
            log.warn("Runtime config file not found: {}. All strategies/screeners enabled by default.", path);
            return new RuntimeConfig();
        }

        try {
            String json = Files.readString(path);
            ObjectMapper mapper = new ObjectMapper();
            RuntimeConfig config = mapper.readValue(json, RuntimeConfig.class);
            log.info("Loaded runtime config from: {} ({} strategies, {} screeners)",
                    path, config.strategies.size(), config.screeners.size());
            return config;
        } catch (IOException e) {
            log.error("Error loading runtime config: {}. All strategies/screeners enabled by default.", e.getMessage());
            return new RuntimeConfig();
        }
    }
}
