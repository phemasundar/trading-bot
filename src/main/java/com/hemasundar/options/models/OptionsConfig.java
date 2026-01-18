package com.hemasundar.options.models;

import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.technical.TechnicalFilterChain;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Configuration for an options trading strategy.
 * Encapsulates the strategy implementation, filter parameters, and securities
 * list.
 * Strategy name is derived from strategy.getStrategyType().
 * 
 * Example usage:
 * 
 * <pre>
 * OptionsConfig.builder()
 *         .strategy(new PutCreditSpreadStrategy())
 *         .filter(OptionsStrategyFilter.builder().targetDTE(30).maxDelta(0.20).build())
 *         .securities(portfolioSecurities)
 *         .build()
 * 
 * // With technical filter
 * OptionsConfig.builder()
 *         .strategy(new PutCreditSpreadStrategy(StrategyType.RSI_BOLLINGER_BULL_PUT_SPREAD))
 *         .filter(OptionsStrategyFilter.builder().targetDTE(30).maxDelta(0.35).build())
 *         .securities(top100Securities)
 *         .technicalFilterChain(TechnicalFilterChain.of(indicators, oversoldConditions))
 *         .build()
 * </pre>
 */
@Getter
@Builder
public class OptionsConfig {

    /**
     * The trading strategy implementation to use.
     */
    private final AbstractTradingStrategy strategy;

    /**
     * Filter parameters for the strategy (DTE, delta, loss limits, etc.).
     */
    private final OptionsStrategyFilter filter;

    /**
     * List of securities (stock symbols) to run this strategy against.
     */
    private final List<String> securities;

    /**
     * Optional technical filter chain for pre-filtering stocks.
     * Used for RSI Bollinger and similar technical-based strategies.
     * If null, no technical pre-filtering is applied.
     */
    private final TechnicalFilterChain technicalFilterChain;

    /**
     * Gets the display name from the strategy type.
     */
    public String getName() {
        return strategy.getStrategyType().toString();
    }

    /**
     * Returns true if this config has a technical filter to apply before the
     * options strategy.
     */
    public boolean hasTechnicalFilter() {
        return technicalFilterChain != null;
    }
}
