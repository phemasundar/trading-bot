package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Reusable RSI (Relative Strength Index) technical filter.
 * Detects oversold (RSI < threshold), overbought (RSI > threshold),
 * and crossover conditions (bullish/bearish divergence).
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
     * Gets the RSI indicator for calculations.
     */
    private RSIIndicator getRSIIndicator(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        return new RSIIndicator(closePrice, period);
    }

    /**
     * Calculates the current RSI value for the series.
     *
     * @param series The price data series
     * @return Current RSI value (0-100)
     */
    public double getCurrentRSI(BarSeries series) {
        RSIIndicator rsi = getRSIIndicator(series);
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Gets the previous day's RSI value.
     *
     * @param series The price data series
     * @return Previous RSI value (0-100)
     */
    public double getPreviousRSI(BarSeries series) {
        RSIIndicator rsi = getRSIIndicator(series);
        int lastIndex = series.getEndIndex();
        if (lastIndex < 1) {
            return getCurrentRSI(series);
        }
        return rsi.getValue(lastIndex - 1).doubleValue();
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

    /**
     * Detects a BULLISH CROSSOVER (Bullish Divergence signal):
     * RSI was below the oversold threshold and has now crossed above it.
     * This indicates a potential reversal from oversold to bullish momentum.
     *
     * @param series The price data series
     * @return true if previous RSI < threshold AND current RSI >= threshold
     */
    public boolean isBullishCrossover(BarSeries series) {
        double previousRSI = getPreviousRSI(series);
        double currentRSI = getCurrentRSI(series);
        return previousRSI < oversoldThreshold && currentRSI >= oversoldThreshold;
    }

    /**
     * Detects a BEARISH CROSSOVER (Bearish Divergence signal):
     * RSI was above the overbought threshold and has now crossed below it.
     * This indicates a potential reversal from overbought to bearish momentum.
     *
     * @param series The price data series
     * @return true if previous RSI > threshold AND current RSI <= threshold
     */
    public boolean isBearishCrossover(BarSeries series) {
        double previousRSI = getPreviousRSI(series);
        double currentRSI = getCurrentRSI(series);
        return previousRSI > overboughtThreshold && currentRSI <= overboughtThreshold;
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
