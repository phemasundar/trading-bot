package com.hemasundar.cache;

import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-safe bounded singleton cache for pre-calculated technical indicators.
 * Stores a fully populated ScreeningResult per symbol.
 */
@Log4j2
public class TechnicalIndicatorCache extends AbstractApiCache<ScreeningResult> {

    public static final int DEFAULT_MAX_SIZE = 750;
    private static final TechnicalIndicatorCache INSTANCE = new TechnicalIndicatorCache();

    private TechnicalIndicatorCache() {
        super(DEFAULT_MAX_SIZE);
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
    @Override
    public void put(String symbol, ScreeningResult data) {
        super.put(symbol, data);
        log.debug("[TechnicalIndicatorCache] Cached data for {}", symbol);
    }
}
