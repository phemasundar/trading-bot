package com.hemasundar.technical;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Moving Average technical filter.
 * Can be configured for any period (e.g., 20-day, 50-day MA).
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
    public double getCurrentMA(BarSeries series) {
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
     * @return true if price < MA
     */
    public boolean isPriceBelowMA(BarSeries series) {
        return getCurrentPrice(series) < getCurrentMA(series);
    }

    /**
     * Checks if current price is above the moving average.
     *
     * @param series The price data series
     * @return true if price > MA
     */
    public boolean isPriceAboveMA(BarSeries series) {
        return getCurrentPrice(series) > getCurrentMA(series);
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // Default evaluation: returns true if price is below MA (bearish signal)
        return isPriceBelowMA(series);
    }

    @Override
    public String getFilterName() {
        return String.format("MA(%d)", period);
    }
}
