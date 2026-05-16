package com.hemasundar.cache;

import lombok.extern.log4j.Log4j2;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe singleton cache for IV Rank values calculated during a single execution run.
 *
 * <p>IV Rank is expensive to compute (requires a Supabase query per symbol). This cache
 * ensures each symbol is only queried once per execution, regardless of how many strategies
 * are running concurrently.
 *
 * <p>The cache must be cleared at the start of each execution via {@link #clear()}.
 */
@Log4j2
public class IVRankCache {

    private static final IVRankCache INSTANCE = new IVRankCache();

    /**
     * Maps symbol → computed IV Rank (0–100), or {@code null} if data was insufficient.
     * Storing {@code null} explicitly distinguishes "we checked, no data" from "not yet queried".
     */
    private final ConcurrentHashMap<String, Optional<Double>> cache = new ConcurrentHashMap<>();

    private IVRankCache() {}

    public static IVRankCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the cached IV Rank for a symbol.
     *
     * @param symbol stock ticker
     * @return {@code Optional.empty()} if the symbol has not been cached yet;
     *         {@code Optional.of(null)} (wrapped as empty Optional) is avoided — instead
     *         we use {@code Optional.ofNullable(rank)} so a cached {@code null} rank still
     *         returns {@code Optional.empty()}. To distinguish "cached null" from "not cached"
     *         use {@link #isCached(String)}.
     */
    public Optional<Double> get(String symbol) {
        return cache.getOrDefault(symbol, null);
    }

    /**
     * Returns true if this symbol has already been looked up (even if the result was null).
     */
    public boolean isCached(String symbol) {
        return cache.containsKey(symbol);
    }

    /**
     * Caches the IV Rank for a symbol. Pass {@code null} to indicate that data was
     * insufficient to compute a rank.
     *
     * @param symbol  stock ticker
     * @param ivRank  computed IV Rank (0–100), or {@code null} if unavailable
     */
    public void put(String symbol, Double ivRank) {
        cache.put(symbol, Optional.ofNullable(ivRank));
        log.debug("[{}] IV Rank cached: {}", symbol, ivRank != null ? String.format("%.1f", ivRank) : "N/A");
    }

    /**
     * Clears all cached IV Rank values. Should be called at the start of each execution run.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        if (size > 0) {
            log.debug("IV Rank cache cleared ({} entries)", size);
        }
    }

    /** Returns the number of symbols currently in the cache. */
    public int size() {
        return cache.size();
    }
}
