package com.hemasundar.technical;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Exponential Moving Average technical filter.
 * Can be configured for any period (e.g., 20-day, 50-day EMA).
 */
@Data
@Builder
public class ExponentialMovingAverageFilter implements TechnicalFilter {

    private final int period;

    /**
     * Calculates the current Exponential Moving Average value.
     *
     * @param series The price data series
     * @return Current EMA value
     */
    public double getCurrentEMA(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(closePrice, period);
        return ema.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Gets the current close price.
     *
     * @param series The price data series
     * @return Current close price
     */
    public double getCurrentPrice(BarSeries series) {
        return series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
    }

    /**
     * Checks if current price is below the moving average.
     *
     * @param series The price data series
     * @return true if price < EMA
     */
    public boolean isPriceBelowEMA(BarSeries series) {
        return getCurrentPrice(series) < getCurrentEMA(series);
    }

    /**
     * Checks if current price is above the moving average.
     *
     * @param series The price data series
     * @return true if price > EMA
     */
    public boolean isPriceAboveEMA(BarSeries series) {
        return getCurrentPrice(series) > getCurrentEMA(series);
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // Default evaluation: returns true if price is below EMA (bearish signal)
        return isPriceBelowEMA(series);
    }

    @Override
    public String getFilterName() {
        return String.format("EMA(%d)", period);
    }
}
