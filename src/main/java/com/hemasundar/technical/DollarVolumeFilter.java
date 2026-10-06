package com.hemasundar.technical;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;

/**
 * Reusable Dollar Volume technical liquidity filter.
 * Filters stocks based on dollar trading volume threshold (Current stock price * Volume).
 * Default threshold is $5,000,000 (100,000 shares * $50).
 */
@Data
@Builder
public class DollarVolumeFilter implements TechnicalFilter {

    @Builder.Default
    private double minDollarVolume = 5_000_000.0;

    /**
     * Gets the current (most recent) dollar volume from the series.
     * DOLLAR_VOLUME = Current stock price * Volume
     *
     * @param series The price data series
     * @return Current dollar volume as a double value
     */
    public double getCurrentDollarVolume(BarSeries series) {
        if (series == null || series.getBarCount() == 0) {
            return 0.0;
        }
        double price = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        long volume = series.getBar(series.getEndIndex()).getVolume().longValue();
        return price * volume;
    }

    /**
     * Checks if the current dollar volume meets the minimum threshold.
     *
     * @param series The price data series
     * @return true if current dollar volume >= minDollarVolume
     */
    public boolean isDollarVolumeAboveThreshold(BarSeries series) {
        return getCurrentDollarVolume(series) >= minDollarVolume;
    }

    @Override
    public boolean evaluate(BarSeries series) {
        return isDollarVolumeAboveThreshold(series);
    }

    @Override
    public String getFilterName() {
        return String.format("Dollar Volume (>= $%,.0f)", minDollarVolume);
    }
}
