package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import lombok.Data;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

/**
 * Reusable Bollinger Bands technical filter.
 * Detects when price is touching or piercing the upper or lower bands.
 */
@Data
@Builder
public class BollingerBandsFilter implements TechnicalFilter {

    @Builder.Default
    private int period = 20;

    @Builder.Default
    private double standardDeviations = 2.0;

    /**
     * Calculates the upper Bollinger Band value.
     *
     * @param series The price data series
     * @return Upper band value
     */
    public double getUpperBand(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, stdDev,
                DecimalNum.valueOf(standardDeviations));
        return upper.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Calculates the lower Bollinger Band value.
     *
     * @param series The price data series
     * @return Lower band value
     */
    public double getLowerBand(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, stdDev,
                DecimalNum.valueOf(standardDeviations));
        return lower.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Calculates the middle Bollinger Band (SMA) value.
     *
     * @param series The price data series
     * @return Middle band (SMA) value
     */
    public double getMiddleBand(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);
        return middle.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Checks if the current price is touching or piercing the upper band.
     *
     * @param series The price data series
     * @return true if price >= upper band
     */
    public boolean isPriceTouchingUpperBand(BarSeries series) {
        double currentPrice = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        return currentPrice >= getUpperBand(series);
    }

    /**
     * Checks if the current price is touching or piercing the lower band.
     *
     * @param series The price data series
     * @return true if price <= lower band
     */
    public boolean isPriceTouchingLowerBand(BarSeries series) {
        double currentPrice = series.getBar(series.getEndIndex()).getClosePrice().doubleValue();
        return currentPrice <= getLowerBand(series);
    }

    @Override
    public boolean evaluate(BarSeries series) {
        // Returns true if price is touching either band
        return isPriceTouchingUpperBand(series) || isPriceTouchingLowerBand(series);
    }

    @Override
    public String getFilterName() {
        return String.format("Bollinger Bands(%d, %.1f SD)", period, standardDeviations);
    }
}
