package com.hemasundar.cache;

import com.hemasundar.pojos.PriceHistoryResponse;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe singleton cache for price history and calculated historical
 * volatility.
 * Caches both raw price data and the calculated annualized volatility to
 * minimize
 * API calls and avoid redundant calculations.
 */
@Log4j2
public class PriceHistoryCache {

    private static final PriceHistoryCache INSTANCE = new PriceHistoryCache();

    private final Map<String, HistoricalData> cache = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);

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
     * Returns cache statistics.
     *
     * @return String with cache hit/miss stats
     */
    public String getStats() {
        int totalCalls = hits.get() + misses.get();
        double hitRate = totalCalls > 0 ? (hits.get() * 100.0 / totalCalls) : 0;
        return String.format("Price History Cache - Hits: %d | Misses: %d | Hit Rate: %.1f%% | Cached Symbols: %d",
                hits.get(), misses.get(), hitRate, cache.size());
    }

    /**
     * Clears all cached data and resets statistics.
     */
    public void clear() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        log.debug("[PriceHistoryCache] Cache cleared");
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
