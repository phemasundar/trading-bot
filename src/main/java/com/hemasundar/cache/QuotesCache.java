package com.hemasundar.cache;

import com.hemasundar.pojos.QuotesResponse;
import lombok.extern.log4j.Log4j2;

/**
 * Thread-safe bounded singleton cache for Schwab quote + fundamental data.
 *
 * <p>
 * Stores {@link QuotesResponse.QuoteData} keyed by symbol. Populated in bulk
 * during screener pre-warming via {@link AbstractApiCache#prewarm(java.util.List, com.hemasundar.utils.SchwabApiExecutor, java.util.function.Function, java.util.function.BiConsumer)}
 * before any per-symbol analysis begins, keeping the per-symbol hot-path
 * entirely cache-based.
 */
@Log4j2
public class QuotesCache extends AbstractApiCache<QuotesResponse.QuoteData> {

    public static final int DEFAULT_MAX_SIZE = 750;
    private static final QuotesCache INSTANCE = new QuotesCache();

    private QuotesCache() {
        super(DEFAULT_MAX_SIZE);
    }

    public static QuotesCache getInstance() {
        return INSTANCE;
    }
}
