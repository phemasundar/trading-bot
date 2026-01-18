package com.hemasundar.options.config;

import lombok.Data;

/**
 * Runtime settings for an individual options strategy.
 * Loaded from JSON config file to control execution behavior.
 * 
 * Currently supports enabling/disabling strategies.
 * Future: Can be extended with filter overrides (minDelta, targetDTE, etc.)
 */
@Data
public class StrategyRuntimeSettings {

    /**
     * Whether this strategy should be executed.
     * Default is true (strategy enabled).
     */
    private boolean enabled = true;

    // Future fields for config overrides:
    // private Double minDelta;
    // private Double maxDelta;
    // private Integer targetDTE;
    // private Double minReturnOnRisk;
}
