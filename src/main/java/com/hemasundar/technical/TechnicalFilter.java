package com.hemasundar.technical;

import java.util.List;
import org.ta4j.core.BarSeries;

/**
 * Base interface for all technical analysis filters.
 * Implementations can be combined using TechnicalFilterChain for complex
 * strategies.
 */
public interface TechnicalFilter {

    /**
     * Evaluates the filter against the given bar series.
     *
     * @param series The price data series to evaluate
     * @return true if filter conditions are met, false otherwise
     */
    boolean evaluate(BarSeries series);

    /**
     * @return The name of this filter for logging/display purposes
     */
    String getFilterName();
}
