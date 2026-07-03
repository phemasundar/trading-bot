package com.hemasundar.cache;

import com.hemasundar.utils.SchwabApiExecutor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Base generic cache for API responses.
 *
 * @param <T> The type of the cached data
 */
@Log4j2
public abstract class AbstractApiCache<T> {

    protected final Map<String, T> cache = new ConcurrentHashMap<>();

    @Getter
    protected final AtomicInteger apiCallCounter = new AtomicInteger(0);
    @Getter
    protected final AtomicInteger hits = new AtomicInteger(0);
    @Getter
    protected final AtomicInteger misses = new AtomicInteger(0);

    /**
     * Pre-warms the cache by fetching all uncached {@code symbols} in parallel via
     * the supplied {@link SchwabApiExecutor}.
     *
     * @param symbols       Union of all securities to fetch
     * @param executor      Shared parallel executor
     * @param fetchFunction Function to fetch the data from the API
     * @param alertCallback Callback for surfacing errors (can be null)
     */
    public void prewarm(List<String> symbols, SchwabApiExecutor executor, Function<String, T> fetchFunction, BiConsumer<String, String> alertCallback) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }

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
        List<T> responses = executor.executeParallel(uncached,
                symbol -> {
                    apiCallCounter.incrementAndGet();
                    return fetchFunction.apply(symbol);
                }, alertCallback);

        // Store results — nulls from failed calls are skipped
        for (int i = 0; i < uncached.size(); i++) {
            T resp = responses.get(i);
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
     * Checks if a symbol is already cached.
     *
     * @param symbol The stock symbol
     * @return true if cached, false otherwise
     */
    public boolean isCached(String symbol) {
        return cache.containsKey(symbol);
    }
    
    /**
     * Retrieves cached data for a symbol.
     *
     * @param symbol The stock symbol
     * @return T if cached, null otherwise
     */
    public T get(String symbol) {
        T data = cache.get(symbol);
        if (data != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return data;
    }

    /**
     * Returns the number of cached symbols.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all cached data and resets counters.
     */
    public void clear() {
        cache.clear();
        apiCallCounter.set(0);
        hits.set(0);
        misses.set(0);
        log.debug("[Cache] Cleared");
    }

    /**
     * Prints or returns cache statistics.
     */
    public String getStats() {
        int totalCalls = hits.get() + misses.get();
        double hitRate = totalCalls > 0 ? (hits.get() * 100.0 / totalCalls) : 0;
        return String.format("Cache - Hits: %d | Misses: %d | Hit Rate: %.1f%% | Cached Symbols: %d | Total API calls: %d",
                hits.get(), misses.get(), hitRate, cache.size(), apiCallCounter.get());
    }

    public void printStats() {
        log.info(getStats());
    }
}
