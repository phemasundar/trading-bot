package com.hemasundar.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.pojos.EarningsCache;
import com.hemasundar.pojos.EarningsCalendarResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Manages caching of earnings calendar data to minimize API calls.
 * Uses a runtime JSON file to persist data safely without modifying the compiled classpath resource.
 */
@Log4j2
@UtilityClass
public class EarningsCacheManager {

    private static final int CACHE_VALIDITY_DAYS = 30;
    private static final Path CACHE_FILE_PATH = resolveCacheFilePath();
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private static EarningsCache earningsCache;

    static {
        loadCache();
    }

    private static Path resolveCacheFilePath() {
        String customDir = System.getProperty("tradingbot.cache.dir");
        if (customDir != null && !customDir.isBlank()) {
            return Paths.get(customDir, "earnings_cache.json");
        }
        return Paths.get(System.getProperty("user.home"), ".trading-bot", "earnings_cache.json");
    }

    /**
     * Retrieves valid cached earnings for a symbol if available.
     * Checks if:
     * 1. Cache exists for symbol
     * 2. Cache is fresh (fetched within last 30 days)
     *
     * @param symbol     The stock symbol
     * @param targetDate The max date we are interested in checking
     * @return List of earnings if cache is valid, null otherwise
     */
    public static List<EarningsCalendarResponse.EarningCalendar> getEarningsFromCache(String symbol,
            LocalDate targetDate) {
        if (earningsCache == null || earningsCache.getCache() == null || !earningsCache.getCache().containsKey(symbol)) {
            return null;
        }

        EarningsCache.CacheEntry entry = earningsCache.getCache().get(symbol);
        if (entry == null || entry.getLastFetched() == null) {
            return null;
        }

        LocalDate lastFetched = LocalDate.parse(entry.getLastFetched());
        long daysSinceFetch = ChronoUnit.DAYS.between(lastFetched, LocalDate.now());

        if (daysSinceFetch > CACHE_VALIDITY_DAYS) {
            log.info("Cache stale for {} (fetched {} days ago). Requesting fresh data.", symbol, daysSinceFetch);
            return null;
        }

        log.debug("Using cached earnings for {}", symbol);
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
        if (Files.exists(CACHE_FILE_PATH)) {
            try (InputStream is = Files.newInputStream(CACHE_FILE_PATH)) {
                earningsCache = objectMapper.readValue(is, EarningsCache.class);
                return;
            } catch (Exception e) {
                log.warn("Failed to load earnings cache from {}: {}", CACHE_FILE_PATH, e.getMessage());
            }
        }

        // Fallback: seed from classpath if present
        try (InputStream is = EarningsCacheManager.class.getClassLoader().getResourceAsStream("earnings_cache.json")) {
            if (is != null) {
                earningsCache = objectMapper.readValue(is, EarningsCache.class);
                log.info("Seeded initial earnings cache from classpath");
                return;
            }
        } catch (Exception e) {
            log.warn("Could not seed earnings cache from classpath: {}", e.getMessage());
        }

        earningsCache = EarningsCache.builder().build();
    }

    private static void saveCache() {
        try {
            if (CACHE_FILE_PATH.getParent() != null && !Files.exists(CACHE_FILE_PATH.getParent())) {
                Files.createDirectories(CACHE_FILE_PATH.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CACHE_FILE_PATH.toFile(), earningsCache);
        } catch (Exception e) {
            log.error("Failed to save earnings cache to {}: {}", CACHE_FILE_PATH, e.getMessage());
        }
    }
}
