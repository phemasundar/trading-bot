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
public class OptionChainCache {

    private final Map<String, OptionChainResponse> cache = new ConcurrentHashMap<>();
    private final ThinkOrSwinAPIs schwabApi;

    @Getter
    private final AtomicInteger apiCallCounter = new AtomicInteger(0);

    /** @deprecated Use {@link #getApiCallCount()} */
    public int getApiCallCount() {
        return apiCallCounter.get();
    }

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
     * <p>Only symbols not already in the cache are fetched. This method
     * should be called <em>once</em> per execution run, before the
     * per-strategy loops begin, so that all subsequent {@link #get} calls
     * are instant cache hits.
     *
     * @param symbols  union of all securities across selected strategies
     * @param executor shared parallel executor
     */
    public void prewarm(List<String> symbols, SchwabApiExecutor executor) {
        // Only queue symbols that aren't already cached
        List<String> uncached = symbols.stream()
                .distinct()
                .filter(s -> !cache.containsKey(s))
                .toList();

        if (uncached.isEmpty()) {
            log.info("Cache pre-warm: all {} symbols already cached", symbols.size());
            return;
        }

        log.info("Cache pre-warm: fetching {} symbols in parallel (skipping {} already cached)",
                uncached.size(), symbols.size() - uncached.size());

        long t0 = System.currentTimeMillis();
        List<OptionChainResponse> responses = executor.executeParallel(uncached,
                symbol -> {
                    apiCallCounter.incrementAndGet();
                    return schwabApi.getOptionChain(symbol);
                });

        // Store results — nulls from failed calls are skipped
        for (int i = 0; i < uncached.size(); i++) {
            OptionChainResponse resp = responses.get(i);
            if (resp != null) {
                cache.put(uncached.get(i), resp);
            } else {
                log.warn("Pre-warm failed for symbol: {}", uncached.get(i));
            }
        }

        log.info("Cache pre-warm complete: {}/{} symbols fetched in {}ms",
                uncached.size(), symbols.size(), System.currentTimeMillis() - t0);
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
     * Checks if a symbol is already cached.
     *
     * @param symbol The stock symbol
     * @return true if cached, false otherwise
     */
    public boolean isCached(String symbol) {
        return cache.containsKey(symbol);
    }

    /**
     * Returns the number of cached symbols.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all cached data.
     */
    public void clear() {
        cache.clear();
        apiCallCounter.set(0);
    }

    /**
     * Prints cache statistics.
     */
    public void printStats() {
        log.info("Cache Stats - Total API calls: {} | Cached symbols: {}", apiCallCounter.get(), cache.size());
    }
}
