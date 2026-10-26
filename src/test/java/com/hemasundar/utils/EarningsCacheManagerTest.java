package com.hemasundar.utils;

import com.hemasundar.pojos.EarningsCache;
import com.hemasundar.pojos.EarningsCalendarResponse;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import static org.testng.Assert.*;

public class EarningsCacheManagerTest {

    private String originalLastUpdated;

    @BeforeClass
    public void snapshotCacheState() {
        if (EarningsCacheManager.getEarningsCache() != null) {
            originalLastUpdated = EarningsCacheManager.getEarningsCache().getLastUpdated();
        }
    }

    /**
     * Resets test-specific ticker entries before each test execution to ensure test isolation.
     */
    @BeforeMethod
    public void cleanupTestTickers() {
        if (EarningsCacheManager.getEarningsCache() != null && EarningsCacheManager.getEarningsCache().getCache() != null) {
            EarningsCacheManager.getEarningsCache().getCache().remove("TEST_TICKER");
            EarningsCacheManager.getEarningsCache().getCache().remove("STALE_TICKER");
            EarningsCacheManager.getEarningsCache().getCache().remove("META_TEST");
        }
    }

    @AfterClass
    public void restoreCacheState() throws Exception {
        EarningsCache cache = EarningsCacheManager.getEarningsCache();
        if (cache != null) {
            cache.setLastUpdated(originalLastUpdated);
            if (cache.getCache() != null) {
                cache.getCache().remove("TEST_TICKER");
                cache.getCache().remove("STALE_TICKER");
                cache.getCache().remove("META_TEST");
            }
            Method saveMethod = EarningsCacheManager.class.getDeclaredMethod("saveCache");
            saveMethod.setAccessible(true);
            saveMethod.invoke(null);
        }
    }

    @Test
    public void testUpdateAndGetCache() {
        String symbol = "TEST_TICKER";
        EarningsCalendarResponse.EarningCalendar event = new EarningsCalendarResponse.EarningCalendar();
        event.setSymbol(symbol);
        event.setDate(LocalDate.now().plusDays(5));

        List<EarningsCalendarResponse.EarningCalendar> earnings = Collections.singletonList(event);

        // Update cache
        EarningsCacheManager.updateCache(symbol, earnings);

        // Retrieve from cache
        List<EarningsCalendarResponse.EarningCalendar> cached = EarningsCacheManager.getEarningsFromCache(symbol, LocalDate.now().plusDays(10));

        assertNotNull(cached);
        assertEquals(cached.size(), 1);
        assertEquals(cached.get(0).getSymbol(), symbol);
    }

    @Test
    public void testGetFromCache_FreshAt5Days_StaleAt6Days() throws Exception {
        String symbol = "STALE_TICKER";

        Field field = EarningsCacheManager.class.getDeclaredField("earningsCache");
        field.setAccessible(true);
        EarningsCache cache = (EarningsCache) field.get(null);
        if (cache == null) {
            cache = EarningsCache.builder().build();
            field.set(null, cache);
        }

        // 5 days old -> valid (<= 5 days)
        EarningsCache.CacheEntry validEntry = EarningsCache.CacheEntry.builder()
                .lastFetched(LocalDate.now().minusDays(5).toString())
                .earnings(Collections.emptyList())
                .build();
        cache.getCache().put(symbol, validEntry);
        assertNotNull(EarningsCacheManager.getEarningsFromCache(symbol, LocalDate.now()));

        // 6 days old -> stale (> 5 days)
        EarningsCache.CacheEntry staleEntry = EarningsCache.CacheEntry.builder()
                .lastFetched(LocalDate.now().minusDays(6).toString())
                .earnings(Collections.emptyList())
                .build();
        cache.getCache().put(symbol, staleEntry);
        assertNull(EarningsCacheManager.getEarningsFromCache(symbol, LocalDate.now()));
    }

    @Test
    public void testUpdateCache_RetainsPastDataAndReplacesFullFutureData() {
        String symbol = "META_TEST";
        LocalDate pastDate = LocalDate.now().minusDays(30);
        LocalDate staleFutureDate = LocalDate.now().plusDays(6);
        LocalDate newFutureDate = LocalDate.now().plusDays(90);

        EarningsCalendarResponse.EarningCalendar pastEvent = new EarningsCalendarResponse.EarningCalendar();
        pastEvent.setSymbol(symbol);
        pastEvent.setDate(pastDate);

        EarningsCalendarResponse.EarningCalendar staleFutureEvent = new EarningsCalendarResponse.EarningCalendar();
        staleFutureEvent.setSymbol(symbol);
        staleFutureEvent.setDate(staleFutureDate);

        // Seed existing cache with 1 past event and 1 stale future event
        try {
            Field field = EarningsCacheManager.class.getDeclaredField("earningsCache");
            field.setAccessible(true);
            EarningsCache cache = (EarningsCache) field.get(null);
            if (cache == null) {
                cache = EarningsCache.builder().build();
                field.set(null, cache);
            }
            cache.getCache().put(symbol, EarningsCache.CacheEntry.builder()
                    .lastFetched(LocalDate.now().minusDays(10).toString())
                    .earnings(List.of(pastEvent, staleFutureEvent))
                    .build());
        } catch (Exception e) {
            fail("Failed to seed cache via reflection: " + e.getMessage());
        }

        // Now update cache with fresh forward-only future event
        EarningsCalendarResponse.EarningCalendar freshFutureEvent = new EarningsCalendarResponse.EarningCalendar();
        freshFutureEvent.setSymbol(symbol);
        freshFutureEvent.setDate(newFutureDate);

        EarningsCacheManager.updateCache(symbol, List.of(freshFutureEvent));

        List<EarningsCalendarResponse.EarningCalendar> updated = EarningsCacheManager.getEarningsFromCache(symbol, LocalDate.now().plusDays(120));
        assertNotNull(updated);
        assertEquals(updated.size(), 2);
        assertEquals(updated.get(0).getDate(), pastDate, "Historical past earnings should be preserved");
        assertEquals(updated.get(1).getDate(), newFutureDate, "Stale future earnings should be replaced by fresh future earnings");
        assertTrue(EarningsCacheManager.getEarningsCache().getCache().get(symbol).getLastFetched().contains("T"),
                "lastFetched should store full ISO offset timestamp in America/Chicago");

        // Clean up test symbols from runtime cache
        EarningsCacheManager.getEarningsCache().getCache().remove("TEST_TICKER");
        EarningsCacheManager.getEarningsCache().getCache().remove("STALE_TICKER");
        EarningsCacheManager.getEarningsCache().getCache().remove("META_TEST");
    }
}
