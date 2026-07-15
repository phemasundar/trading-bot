package com.hemasundar.technical;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

/**
 * Average True Range (ATR) technical filter.
 * Measures market volatility by decomposing the entire range of an asset price for that period.
 */
@Data
@Builder
public class AverageTrueRangeFilter implements TechnicalFilter {

    private final int period;

    /**
     * Calculates the current Average True Range value.
     *
     * @param series The price data series
     * @return Current ATR value
     */
    public double getCurrentATR(BarSeries series) {
        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getEndIndex()).doubleValue();
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // ATR by itself is not typically a boolean filter (it's used as a variable in formulas),
        // but we return true as a default implementation for the TechnicalFilter interface.
        return true;
    }

    @Override
    public String getFilterName() {
        return String.format("ATR(%d)", period);
    }
}
