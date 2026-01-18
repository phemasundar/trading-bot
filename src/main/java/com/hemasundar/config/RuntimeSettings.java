package com.hemasundar.config;

import lombok.Data;

/**
 * Runtime settings for an individual strategy or screener.
 * Loaded from JSON config file to control execution behavior.
 * 
 * Currently supports enabling/disabling.
 * Future: Can be extended with filter overrides (minDelta, targetDTE, etc.)
 */
@Data
public class RuntimeSettings {

    /**
     * Whether this strategy/screener should be executed.
     * Default is true (enabled).
     */
    private boolean enabled = true;

    // Future fields for config overrides:
    // private Double minDelta;
    // private Double maxDelta;
    // private Integer targetDTE;
    // private Double minReturnOnRisk;
}
