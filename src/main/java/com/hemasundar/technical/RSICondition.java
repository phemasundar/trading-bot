package com.hemasundar.technical;

import org.ta4j.core.BarSeries;

/**
 * RSI condition to check for.
 * Each condition knows how to evaluate itself against an RSIFilter.
 */
public enum RSICondition {
    /**
     * Check if RSI is below the oversold threshold (e.g., RSI < 30).
     * Indicates potential bullish reversal.
     */
    OVERSOLD {
        @Override
        public boolean evaluate(RSIFilter filter, BarSeries series) {
            return filter.isOversold(series);
        }
    },

    /**
     * Check if RSI is above the overbought threshold (e.g., RSI > 70).
     * Indicates potential bearish reversal.
     */
    OVERBOUGHT {
        @Override
        public boolean evaluate(RSIFilter filter, BarSeries series) {
            return filter.isOverbought(series);
        }
    },

    /**
     * Bullish Crossover: RSI was below oversold threshold and crossed above.
     * (Previous RSI < 30 AND Current RSI >= 30)
     */
    BULLISH_CROSSOVER {
        @Override
        public boolean evaluate(RSIFilter filter, BarSeries series) {
            return filter.isBullishCrossover(series);
        }
    },

    /**
     * Bearish Crossover: RSI was above overbought threshold and crossed below.
     * (Previous RSI > 70 AND Current RSI <= 70)
     */
    BEARISH_CROSSOVER {
        @Override
        public boolean evaluate(RSIFilter filter, BarSeries series) {
            return filter.isBearishCrossover(series);
        }
    };

    /**
     * Evaluates this condition against the given RSI filter and price series.
     *
     * @param filter The RSI filter with threshold settings
     * @param series The price data series
     * @return true if the condition is met
     */
    public abstract boolean evaluate(RSIFilter filter, BarSeries series);
}
