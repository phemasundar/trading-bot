package com.hemasundar.pojos.technicalfilters;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Reusable Volume technical filter.
 * Filters stocks based on trading volume threshold.
 * Default threshold is 100,000 shares.
 */
@Data
@Builder
public class VolumeFilter implements TechnicalFilter {

    @Builder.Default
    private long minVolume = 100_000L;

    /**
     * Gets the current (most recent) trading volume from the series.
     *
     * @param series The price data series
     * @return Current volume as a long value
     */
    public long getCurrentVolume(BarSeries series) {
        if (series == null || series.getBarCount() == 0) {
            return 0L;
        }
        Num volume = series.getBar(series.getEndIndex()).getVolume();
        return volume.longValue();
    }

    /**
     * Checks if the current volume meets the minimum threshold.
     *
     * @param series The price data series
     * @return true if current volume >= minVolume
     */
    public boolean isVolumeAboveThreshold(BarSeries series) {
        return getCurrentVolume(series) >= minVolume;
    }

    @Override
    public boolean evaluate(BarSeries series) {
        return isVolumeAboveThreshold(series);
    }

    @Override
    public String getFilterName() {
        return String.format("Volume (>= %,d)", minVolume);
    }
}
