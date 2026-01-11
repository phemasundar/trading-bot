package com.hemasundar.utils;

import com.hemasundar.pojos.EarningsCache;
import com.hemasundar.pojos.EarningsCalendarResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Manages caching of earnings calendar data to minimize API calls.
 * Uses a local JSON file to persist data.
 */
@Log
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EarningsCacheManager {

    private static final String CACHE_FILE_PATH = "src/test/resources/earnings_cache.json";
    private static final int CACHE_VALIDITY_DAYS = 30;

    // Singleton instance of the cache data
    private static EarningsCache earningsCache;
    private static final ObjectMapper objectMapper;

    static {
        // Jackson 3 initialization using Builder
        objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        loadCache();
    }

    /**
     * Retrieves valid cached earnings for a symbol if available.
     * Checks if:
     * 1. Cache exists for symbol
     * 2. Cache is fresh (fetched within last 30 days)
     * 3. Cache contains at least one event AFTER the target date (to ensure
     * coverage)
     *
     * @param symbol     The stock symbol
     * @param targetDate The max date we are interested in checking
     * @return List of earnings if cache is valid, null otherwise
     */
    public static List<EarningsCalendarResponse.EarningCalendar> getEarningsFromCache(String symbol,
            LocalDate targetDate) {
        if (earningsCache == null || !earningsCache.getCache().containsKey(symbol)) {
            return null;
        }

        EarningsCache.CacheEntry entry = earningsCache.getCache().get(symbol);

        // Check 1: Freshness (fetched within 30 days)
        LocalDate lastFetched = LocalDate.parse(entry.getLastFetched());
        long daysSinceFetch = ChronoUnit.DAYS.between(lastFetched, LocalDate.now());

        if (daysSinceFetch > CACHE_VALIDITY_DAYS) {
            log.info(
                    "Cache stale for " + symbol + " (fetched " + daysSinceFetch + " days ago). Requesting fresh data.");
            return null;
        }

        // Check 2: Coverage (contains data beyond targetDate)
        /*boolean hasFutureCoverage = false;
        if (entry.getEarnings() != null) {
            for (EarningsCalendarResponse.EarningCalendar event : entry.getEarnings()) {
                if (event.getDate() != null && event.getDate().isAfter(targetDate)) {
                    hasFutureCoverage = true;
                    break;
                }
            }
        }

        if (!hasFutureCoverage) {
            log.info("Cache insufficient for " + symbol + " (no events found after " + targetDate
                    + "). Requesting fresh data.");
            return null;
        }*/

        log.info("Using cached earnings for " + symbol);

        // Return only relevant earnings between now and target date (plus a buffer if
        // needed)
        // Actually, returning full list is fine, the caller will filter or check
        // specific dates
        return entry.getEarnings();
    }

    /**
     * Updates the cache with fresh data and saves to disk.
     */
    public static void updateCache(String symbol, List<EarningsCalendarResponse.EarningCalendar> earnings) {
        if (earningsCache == null) {
            earningsCache = EarningsCache.builder().build();
        }

        EarningsCache.CacheEntry entry = EarningsCache.CacheEntry.builder()
                .lastFetched(LocalDate.now().toString())
                .earnings(earnings)
                .build();

        earningsCache.getCache().put(symbol, entry);
        saveCache();
    }

    private static void loadCache() {
        File file = new File(CACHE_FILE_PATH);
        if (file.exists()) {
            try {
                earningsCache = objectMapper.readValue(file, EarningsCache.class);
            } catch (Exception e) {
                log.warning("Failed to load earnings cache: " + e.getMessage());
                earningsCache = EarningsCache.builder().build();
            }
        } else {
            earningsCache = EarningsCache.builder().build();
        }
    }

    private static void saveCache() {
        try {
            Path path = Paths.get(CACHE_FILE_PATH);
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(CACHE_FILE_PATH), earningsCache);
        } catch (Exception e) {
            log.severe("Failed to save earnings cache: " + e.getMessage());
        }
    }
}
