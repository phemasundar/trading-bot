package com.hemasundar.pojos.technicalfilters;

import org.ta4j.core.BarSeries;

/**
 * Bollinger Band condition to check for.
 * Each condition knows how to evaluate itself against a BollingerBandsFilter.
 */
public enum BollingerCondition {
    /**
     * Check if price is touching or below the lower Bollinger Band.
     * Combined with RSI OVERSOLD indicates bullish signal.
     */
    LOWER_BAND {
        @Override
        public boolean evaluate(BollingerBandsFilter filter, BarSeries series) {
            return filter.isPriceTouchingLowerBand(series);
        }
    },

    /**
     * Check if price is touching or above the upper Bollinger Band.
     * Combined with RSI OVERBOUGHT indicates bearish signal.
     */
    UPPER_BAND {
        @Override
        public boolean evaluate(BollingerBandsFilter filter, BarSeries series) {
            return filter.isPriceTouchingUpperBand(series);
        }
    };

    /**
     * Evaluates this condition against the given Bollinger Bands filter and price
     * series.
     *
     * @param filter The Bollinger Bands filter with period/SD settings
     * @param series The price data series
     * @return true if the condition is met
     */
    public abstract boolean evaluate(BollingerBandsFilter filter, BarSeries series);
}
