package com.hemasundar.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Component for calculating historical volatility from price data.
 * Uses log returns method for better statistical properties.
 */
@Log4j2
@Component
public class VolatilityCalculator {

    private static final int TRADING_DAYS_PER_YEAR = 252;

    /**
     * Calculates the Historical Volatility Rank.
     * 
     * Formula:
     * 1. Calculate daily log returns for the entire available history.
     * 2. For each day (from index `period` to end), calculate the standard deviation
     *    of the preceding `period` log returns.
     * 3. Annualize each standard deviation: stdDev × √252
     * 4. Calculate the Min-Max rank: (Current - Low) / (High - Low) * 100
     *
     * @param priceHistory Price history response with candle data
     * @param period The rolling window period (e.g., 20)
     * @return HV Rank (0.0 to 100.0), or null if calculation fails
     */
    public Double calculateHvRank(PriceHistoryResponse priceHistory, int period) {
        if (priceHistory == null || priceHistory.getCandles() == null || priceHistory.getCandles().isEmpty()) {
            log.warn("Cannot calculate volatility rank: price history is null or empty");
            return null;
        }

        List<PriceHistoryResponse.CandleData> candles = priceHistory.getCandles();

        // Need at least period + 1 data points to calculate returns and 1 rolling HV
        if (candles.size() <= period) {
            log.warn("Cannot calculate volatility rank for {}: insufficient data ({} candles, period {})",
                    priceHistory.getSymbol(), candles.size(), period);
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

        int numHvs = logReturns.length - period + 1;
        if (numHvs <= 1) {
            return null; // Not enough historical HVs to compute a rank
        }

        double[] rollingHvs = new double[numHvs];
        for (int i = 0; i < numHvs; i++) {
            double stdDev = calculateStdDev(logReturns, i, period);
            rollingHvs[i] = stdDev * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;
        }

        double currentHv = rollingHvs[numHvs - 1];

        // Calculate Min-Max Rank (Industry standard for HV/IV Rank)
        double minHv = Double.MAX_VALUE;
        double maxHv = Double.MIN_VALUE;

        for (double hv : rollingHvs) {
            if (hv < minHv) minHv = hv;
            if (hv > maxHv) maxHv = hv;
        }

        double rank;
        if (maxHv == minHv) {
            rank = 0.0; // Avoid division by zero if volatility is perfectly flat
        } else {
            rank = ((currentHv - minHv) / (maxHv - minHv)) * 100.0;
        }

        log.debug("Calculated HV Rank for {}: {} (current HV: {}%, period: {}, data points: {})",
                priceHistory.getSymbol(), rank, currentHv, period, numHvs);

        return rank;
    }

    /**
     * Calculates sample standard deviation of a subset of an array.
     * Uses Bessel's correction (N-1) for unbiased estimation.
     *
     * @param values Array of values
     * @param offset Start index
     * @param length Number of elements
     * @return Sample standard deviation
     */
    private double calculateStdDev(double[] values, int offset, int length) {
        if (length <= 1) return 0.0;

        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            sum += values[offset + i];
        }
        double mean = sum / length;

        double varianceSum = 0.0;
        for (int i = 0; i < length; i++) {
            double diff = values[offset + i] - mean;
            varianceSum += diff * diff;
        }

        double variance = varianceSum / (length - 1);
        return Math.sqrt(variance);
    }
}
