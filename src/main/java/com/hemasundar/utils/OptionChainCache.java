package com.hemasundar.utils;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.cache.AbstractApiCache;
import com.hemasundar.options.models.OptionChainResponse;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Lazy-loading, bounded cache for {@link OptionChainResponse} data.
 * Fetches from API only when a symbol is requested for the first time in a micro-batch.
 * Subsequent requests for the same symbol within the batch return the cached response.
 * <p>
 * Note: This is NOT a Spring bean — it is created per-execution by
 * {@code StrategyExecutionService}, which streams symbols in micro-batches and evicts
 * option chains immediately after all strategies subscribed to a batch finish evaluating.
 */
@Log4j2
public class OptionChainCache extends AbstractApiCache<OptionChainResponse> {

    public static final int DEFAULT_MAX_SIZE = 100;

    private final ThinkOrSwimAPIs schwabApi;

    public OptionChainCache(ThinkOrSwimAPIs schwabApi) {
        this(schwabApi, DEFAULT_MAX_SIZE);
    }

    public OptionChainCache(ThinkOrSwimAPIs schwabApi, int maxSize) {
        super(maxSize);
        this.schwabApi = schwabApi;
    }

    /**
     * Gets the OptionChainResponse for a symbol.
     * If not in cache, fetches from API and caches the result.
     *
     * @param symbol The stock symbol
     * @return OptionChainResponse for the symbol
     */
    @Override
    public OptionChainResponse get(String symbol) {
        OptionChainResponse cached = cache.get(symbol);
        if (cached != null) {
            hits.incrementAndGet();
            return cached;
        }

        misses.incrementAndGet();
        apiCallCounter.incrementAndGet();
        log.debug("Fetching from API: {} (API call #{})", symbol, apiCallCounter.get());
        OptionChainResponse response = schwabApi.getOptionChain(symbol);
        if (response != null) {
            put(symbol, response);
        }
        return response;
    }

    /**
     * Pre-warms the cache by fetching all {@code symbols} in parallel via
     * the supplied {@link SchwabApiExecutor}.
     *
     * @param symbols  union of securities in the active micro-batch
     * @param executor shared parallel executor
     */
    public void prewarm(List<String> symbols, SchwabApiExecutor executor) {
        super.prewarm(symbols, executor, schwabApi::getOptionChain, null);
    }

    /**
     * Gets OptionChainResponses for a list of symbols.
     * Uses lazy loading - only fetches from API if not already cached.
     *
     * @param symbols List of stock symbols
     * @return List of OptionChainResponse objects
     */
    public List<OptionChainResponse> getAll(List<String> symbols) {
        List<OptionChainResponse> responses = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                responses.add(get(symbol));
            } catch (Exception e) {
                log.error("Failed to fetch {}: {}", symbol, e.getMessage());
            }
        }
        return responses;
    }

    /**
     * Prints cache statistics.
     */
    @Override
    public void printStats() {
        log.debug("Cache Stats - Total API calls: {} | Cached symbols: {}", apiCallCounter.get(), cache.size());
    }
}
