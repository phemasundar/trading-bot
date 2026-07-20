package com.hemasundar.cache;

import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-safe singleton cache for pre-calculated technical indicators.
 * Stores a fully populated ScreeningResult per symbol.
 */
@Log4j2
public class TechnicalIndicatorCache extends AbstractApiCache<ScreeningResult> {

    private static final TechnicalIndicatorCache INSTANCE = new TechnicalIndicatorCache();

    private TechnicalIndicatorCache() {
    }

    public static TechnicalIndicatorCache getInstance() {
        return INSTANCE;
    }

    /**
     * Caches indicator data for a symbol.
     *
     * @param symbol Stock symbol
     * @param data   ScreeningResult to cache
     */
    public void put(String symbol, ScreeningResult data) {
        cache.put(symbol, data);
        log.debug("[TechnicalIndicatorCache] Cached data for {}", symbol);
    }
}
