package com.hemasundar.cache;

import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.utils.VolatilityCalculator;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-safe singleton cache for price history and calculated historical
 * volatility.
 * Caches both raw price data and the calculated annualized volatility to
 * minimize
 * API calls and avoid redundant calculations.
 */
@Log4j2
public class PriceHistoryCache extends AbstractApiCache<PriceHistoryCache.HistoricalData> {

    private static final PriceHistoryCache INSTANCE = new PriceHistoryCache();

    private PriceHistoryCache() {
    }

    public static PriceHistoryCache getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieves cached historical data for a symbol.
     *
     * @param symbol Stock symbol
     * @return HistoricalData if cached, null otherwise
     */
    @Override
    public HistoricalData get(String symbol) {
        HistoricalData data = cache.get(symbol);
        if (data != null) {
            hits.incrementAndGet();
            log.debug("[PriceHistoryCache] HIT for {}", symbol);
        } else {
            misses.incrementAndGet();
            log.debug("[PriceHistoryCache] MISS for {}", symbol);
        }
        return data;
    }

    /**
     * Gets the HistoricalData for a symbol. If not cached, fetches 1-year daily history
     * and calculates historical volatility.
     */
    public HistoricalData getHistoricalData(String symbol, ThinkOrSwinAPIs schwabApi, VolatilityCalculator volatilityCalculator) {
        HistoricalData data = get(symbol);
        if (data != null) {
            return data;
        }

        apiCallCounter.incrementAndGet();
        log.debug("[PriceHistoryCache] Fetching price history for {}", symbol);
        PriceHistoryResponse priceHistory = schwabApi.getYearlyPriceHistory(symbol, 1);
        
        Double historicalVolatility = null;
        if (volatilityCalculator != null && priceHistory != null) {
            historicalVolatility = volatilityCalculator.calculateAnnualizedVolatility(priceHistory);
        }

        data = new HistoricalData(priceHistory, historicalVolatility);
        put(symbol, data);
        return data;
    }

    /**
     * Caches historical data for a symbol.
     *
     * @param symbol Stock symbol
     * @param data   Historical data to cache
     */
    public void put(String symbol, HistoricalData data) {
        cache.put(symbol, data);
        log.debug("[PriceHistoryCache] Cached data for {}", symbol);
    }



    /**
     * POJO containing price history and calculated volatility.
     */
    public static class HistoricalData {
        private final PriceHistoryResponse priceHistory;
        private final Double annualizedHistoricalVolatility;

        public HistoricalData(PriceHistoryResponse priceHistory, Double annualizedHistoricalVolatility) {
            this.priceHistory = priceHistory;
            this.annualizedHistoricalVolatility = annualizedHistoricalVolatility;
        }

        public PriceHistoryResponse getPriceHistory() {
            return priceHistory;
        }

        public Double getAnnualizedHistoricalVolatility() {
            return annualizedHistoricalVolatility;
        }
    }
}
