package com.hemasundar.utils;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.OptionChainResponse;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy-loading cache for OptionChainResponse data.
 * Fetches from API only when a symbol is requested for the first time.
 * Subsequent requests for the same symbol return the cached response.
 * This minimizes API calls when multiple strategies use overlapping symbols.
 */
@Log4j2
public class OptionChainCache {

    private final Map<String, OptionChainResponse> cache = new ConcurrentHashMap<>();

    @Getter
    private int apiCallCount = 0;

    /**
     * Gets the OptionChainResponse for a symbol.
     * If not in cache, fetches from API and caches the result.
     *
     * @param symbol The stock symbol
     * @return OptionChainResponse for the symbol
     */
    public OptionChainResponse get(String symbol) {
        return cache.computeIfAbsent(symbol, s -> {
            apiCallCount++;
            log.debug("Fetching from API: {} (API call #{})", s, apiCallCount);
            return ThinkOrSwinAPIs.getOptionChainResponse(s);
        });
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
        apiCallCount = 0;
    }

    /**
     * Prints cache statistics.
     */
    public void printStats() {
        log.info("Cache Stats - Total API calls: {} | Cached symbols: {}", apiCallCount, cache.size());
    }
}
