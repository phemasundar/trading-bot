package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Reusable RSI (Relative Strength Index) technical filter.
 * Detects oversold (RSI < threshold) and overbought (RSI > threshold)
 * conditions.
 */
@Data
@Builder
public class RSIFilter implements TechnicalFilter {

    @Builder.Default
    private int period = 14;

    @Builder.Default
    private double oversoldThreshold = 30.0;

    @Builder.Default
    private double overboughtThreshold = 70.0;

    /**
     * Calculates the current RSI value for the series.
     *
     * @param series The price data series
     * @return Current RSI value (0-100)
     */
    public double getCurrentRSI(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        int lastIndex = series.getEndIndex();
        return rsi.getValue(lastIndex).doubleValue();
    }

    /**
     * Checks if the current RSI indicates an oversold condition.
     *
     * @param series The price data series
     * @return true if RSI < oversoldThreshold
     */
    public boolean isOversold(BarSeries series) {
        return getCurrentRSI(series) < oversoldThreshold;
    }

    /**
     * Checks if the current RSI indicates an overbought condition.
     *
     * @param series The price data series
     * @return true if RSI > overboughtThreshold
     */
    public boolean isOverbought(BarSeries series) {
        return getCurrentRSI(series) > overboughtThreshold;
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // Returns true if either oversold or overbought
        return isOversold(series) || isOverbought(series);
    }

    @Override
    public String getFilterName() {
        return String.format("RSI(%d) [Oversold: <%.1f, Overbought: >%.1f]",
                period, oversoldThreshold, overboughtThreshold);
    }
}
