package com.hemasundar.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.pojos.EarningsCache;
import com.hemasundar.pojos.EarningsCalendarResponse;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages caching of earnings calendar data to minimize API calls.
 * Uses a runtime JSON file to persist data safely without modifying the compiled classpath resource.
 */
@Log4j2
@UtilityClass
public class EarningsCacheManager {

    private static final int CACHE_VALIDITY_DAYS = 5;
    private static final Path CACHE_FILE_PATH = resolveCacheFilePath();
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Getter
    private static EarningsCache earningsCache;

    static {
        loadCache();
    }

    private static Path resolveCacheFilePath() {
        String customDir = System.getProperty("tradingbot.cache.dir");
        if (StringUtils.isNotBlank(customDir)) {
            return Paths.get(customDir, "earnings_cache.json");
        }
        return Paths.get(System.getProperty("user.home"), ".trading-bot", "earnings_cache.json");
    }

    /**
     * Retrieves valid cached earnings for a symbol if available.
     * Checks if:
     * 1. Cache exists for symbol
     * 2. Cache is fresh (fetched within last 5 days)
     *
     * @param symbol     The stock symbol
     * @param targetDate The max date we are interested in checking
     * @return List of earnings if cache is valid, null otherwise
     */
    public static synchronized List<EarningsCalendarResponse.EarningCalendar> getEarningsFromCache(String symbol,
            LocalDate targetDate) {
        if (earningsCache == null || earningsCache.getCache() == null || !earningsCache.getCache().containsKey(symbol)) {
            return null;
        }

        EarningsCache.CacheEntry entry = earningsCache.getCache().get(symbol);
        if (entry == null || entry.getLastFetched() == null) {
            return null;
        }

        LocalDate lastFetched = LocalDate.parse(entry.getLastFetched().substring(0, 10));
        long daysSinceFetch = ChronoUnit.DAYS.between(lastFetched, LocalDate.now());

        if (daysSinceFetch > CACHE_VALIDITY_DAYS) {
            log.debug("Cache stale for {} (fetched {} days ago). Requesting fresh data.", symbol, daysSinceFetch);
            return null;
        }

        log.debug("Using cached earnings for {}", symbol);
        return entry.getEarnings();
    }

    /**
     * Updates the cache with fresh forward-only data and saves to disk.
     * Retains historical past data (before today) and replaces all future data
     * (today and onward) by removing any stale future entries.
     */
    public static synchronized void updateCache(String symbol, List<EarningsCalendarResponse.EarningCalendar> earnings) {
        if (earningsCache == null) {
            earningsCache = EarningsCache.builder().build();
        }

        LocalDate today = LocalDate.now();
        List<EarningsCalendarResponse.EarningCalendar> updatedEarnings = new ArrayList<>();

        EarningsCache.CacheEntry existingEntry = earningsCache.getCache().get(symbol);
        if (existingEntry != null && CollectionUtils.isNotEmpty(existingEntry.getEarnings())) {
            existingEntry.getEarnings().stream()
                    .filter(e -> e.getDate() != null && e.getDate().isBefore(today))
                    .forEach(updatedEarnings::add);
        }

        if (CollectionUtils.isNotEmpty(earnings)) {
            earnings.stream()
                    .filter(e -> e.getDate() == null || !e.getDate().isBefore(today))
                    .forEach(updatedEarnings::add);
        }

        updatedEarnings.sort(Comparator.comparing(
                EarningsCalendarResponse.EarningCalendar::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        String nowIso = ZonedDateTime.now(ZoneId.of("America/Chicago")).toOffsetDateTime().toString();
        EarningsCache.CacheEntry entry = EarningsCache.CacheEntry.builder()
                .lastFetched(nowIso)
                .earnings(updatedEarnings)
                .build();

        earningsCache.setLastUpdated(nowIso);
        earningsCache.getCache().put(symbol, entry);
        saveCache();
    }

    private static void loadCache() {
        if (Files.exists(CACHE_FILE_PATH)) {
            try (InputStream is = Files.newInputStream(CACHE_FILE_PATH)) {
                earningsCache = objectMapper.readValue(is, EarningsCache.class);
                if (earningsCache != null) {
                    if (StringUtils.isBlank(earningsCache.getLastUpdated())) {
                        earningsCache.setLastUpdated(Files.getLastModifiedTime(CACHE_FILE_PATH)
                                .toInstant()
                                .atZone(ZoneId.of("America/Chicago"))
                                .toOffsetDateTime()
                                .toString());
                    }
                    String lastUpdated = earningsCache.getLastUpdated();
                    if (StringUtils.isNotBlank(lastUpdated) && lastUpdated.length() >= 10 && earningsCache.getCache() != null) {
                        String updatedDatePrefix = lastUpdated.substring(0, 10);
                        earningsCache.getCache().values().forEach(entry -> {
                            if (entry != null && updatedDatePrefix.equals(entry.getLastFetched())) {
                                entry.setLastFetched(lastUpdated);
                            }
                        });
                    }
                }
                return;
            } catch (Exception e) {
                log.warn("Failed to load earnings cache from {}: {}", CACHE_FILE_PATH, e.getMessage());
            }
        }

        // Fallback: seed from classpath if present
        try (InputStream is = EarningsCacheManager.class.getClassLoader().getResourceAsStream("earnings_cache.json")) {
            if (is != null) {
                earningsCache = objectMapper.readValue(is, EarningsCache.class);
                log.debug("Seeded initial earnings cache from classpath");
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
