package com.hemasundar.utils;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.hemasundar.cache.AbstractApiCache;

/**
 * Lazy-loading cache for OptionChainResponse data.
 * Fetches from API only when a symbol is requested for the first time.
 * Subsequent requests for the same symbol return the cached response.
 * This minimizes API calls when multiple strategies use overlapping symbols.
 * <p>
 * Note: This is NOT a Spring bean — it is created per-execution by
 * StrategyExecutionService.
 */
@Log4j2
@lombok.RequiredArgsConstructor
public class OptionChainCache extends AbstractApiCache<OptionChainResponse> {

    private final ThinkOrSwinAPIs schwabApi;

    /**
     * Gets the OptionChainResponse for a symbol.
     * If not in cache, fetches from API and caches the result.
     *
     * @param symbol The stock symbol
     * @return OptionChainResponse for the symbol
     */
    public OptionChainResponse get(String symbol) {
        if (cache.containsKey(symbol)) {
            return cache.get(symbol);
        }

        // cache miss — time the actual Schwab API call
        apiCallCounter.incrementAndGet();
        log.debug("Fetching from API: {} (API call #{})", symbol, apiCallCounter.get());
        OptionChainResponse response = schwabApi.getOptionChain(symbol);
        cache.put(symbol, response);
        return response;
    }

    /**
     * Pre-warms the cache by fetching all {@code symbols} in parallel via
     * the supplied {@link SchwabApiExecutor}.
     *
     * @param symbols  union of all securities across selected strategies
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
    public void printStats() {
        log.info("Cache Stats - Total API calls: {} | Cached symbols: {}", apiCallCounter.get(), cache.size());
    }
}
