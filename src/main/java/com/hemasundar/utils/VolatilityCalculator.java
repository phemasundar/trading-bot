package com.hemasundar.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Utility class for calculating historical volatility from price data.
 * Uses log returns method for better statistical properties.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VolatilityCalculator {

    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * Calculates annualized historical volatility from price history data.
     * 
     * Formula:
     * 1. Calculate daily log returns: ln(price[i] / price[i-1])
     * 2. Calculate standard deviation of returns
     * 3. Annualize: stdDev × √252
     * 4. Convert to percentage
     *
     * @param priceHistory Price history response with candle data
     * @return Annualized volatility as a percentage (e.g., 25.0 for 25%), or null
     *         if calculation fails
     */
    public static Double calculateAnnualizedVolatility(PriceHistoryResponse priceHistory) {
        if (priceHistory == null || priceHistory.getCandles() == null || priceHistory.getCandles().isEmpty()) {
            log.warn("Cannot calculate volatility: price history is null or empty");
            return null;
        }

        List<PriceHistoryResponse.CandleData> candles = priceHistory.getCandles();

        // Need at least 2 data points to calculate returns
        if (candles.size() < 2) {
            log.warn("Cannot calculate volatility for {}: insufficient data ({} candles)",
                    priceHistory.getSymbol(), candles.size());
            return null;
        }

        // Calculate log returns
        double[] logReturns = new double[candles.size() - 1];
        for (int i = 1; i < candles.size(); i++) {
            double currentPrice = candles.get(i).getClose();
            double previousPrice = candles.get(i - 1).getClose();

            if (previousPrice <= 0 || currentPrice <= 0) {
                log.warn("Invalid price data for {}: price <= 0", priceHistory.getSymbol());
                return null;
            }

            logReturns[i - 1] = Math.log(currentPrice / previousPrice);
        }

        // Calculate standard deviation of log returns
        double stdDev = calculateStdDev(logReturns);

        // Annualize and convert to percentage
        double annualizedVolatility = stdDev * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;

        log.debug("Calculated volatility for {}: {}% ({} days of data)",
                priceHistory.getSymbol(), annualizedVolatility, candles.size());

        return annualizedVolatility;
    }

    /**
     * Calculates sample standard deviation of an array of values.
     * Uses Bessel's correction (N-1) for unbiased estimation, which is standard in
     * finance.
     *
     * @param values Array of values
     * @return Sample standard deviation
     */
    private static double calculateStdDev(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }

        if (values.length == 1) {
            return 0.0; // Cannot calculate standard deviation with single value
        }

        // Calculate mean
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.length;

        // Calculate variance using Bessel's correction (N-1)
        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        double variance = sumSquaredDiff / (values.length - 1);

        // Return standard deviation
        return Math.sqrt(variance);
    }
}
