package com.hemasundar.cache;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.QuotesResponse;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;

/**
 * Thread-safe singleton cache for Schwab quote + fundamental data.
 *
 * <p>
 * Stores {@link QuotesResponse.QuoteData} keyed by symbol. Populated in bulk
 * during screener pre-warming via {@link AbstractApiCache#prewarm(java.util.List, com.hemasundar.utils.SchwabApiExecutor, java.util.function.Function, java.util.function.BiConsumer)}
 * before any per-symbol analysis begins, keeping the per-symbol hot-path
 * entirely cache-based.
 */
@Log4j2
public class QuotesCache extends AbstractApiCache<QuotesResponse.QuoteData> {

    private static final QuotesCache INSTANCE = new QuotesCache();

    private QuotesCache() {
    }

    public static QuotesCache getInstance() {
        return INSTANCE;
    }
}

