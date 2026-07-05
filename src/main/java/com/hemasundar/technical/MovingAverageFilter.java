package com.hemasundar.technical;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Moving Average technical filter.
 * Can be configured for any period (e.g., 20-day, 50-day SMA).
 */
@Data
@Builder
public class MovingAverageFilter implements TechnicalFilter {

    private final int period;

    /**
     * Calculates the current Simple Moving Average value.
     *
     * @param series The price data series
     * @return Current SMA value
     */
    public double getCurrentSMA(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        return sma.getValue(series.getEndIndex()).doubleValue();
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
     * @return true if price < SMA
     */
    public boolean isPriceBelowSMA(BarSeries series) {
        return getCurrentPrice(series) < getCurrentSMA(series);
    }

    /**
     * Checks if current price is above the moving average.
     *
     * @param series The price data series
     * @return true if price > SMA
     */
    public boolean isPriceAboveSMA(BarSeries series) {
        return getCurrentPrice(series) > getCurrentSMA(series);
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // Default evaluation: returns true if price is below SMA (bearish signal)
        return isPriceBelowSMA(series);
    }

    @Override
    public String getFilterName() {
        return String.format("SMA(%d)", period);
    }
}
