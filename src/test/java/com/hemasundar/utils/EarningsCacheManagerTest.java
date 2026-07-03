package com.hemasundar.utils;

import com.hemasundar.pojos.EarningsCalendarResponse;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

public class EarningsCacheManagerTest {

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
    public void testGetFromCache_Stale() throws Exception {
        String symbol = "STALE_TICKER";
        
        // Manually build a stale entry
        com.hemasundar.pojos.EarningsCache.CacheEntry entry = com.hemasundar.pojos.EarningsCache.CacheEntry.builder()
                .lastFetched(LocalDate.now().minusDays(40).toString())
                .earnings(Collections.emptyList())
                .build();
                
        // Inject via reflection into EarningsCacheManager
        java.lang.reflect.Field field = EarningsCacheManager.class.getDeclaredField("earningsCache");
        field.setAccessible(true);
        com.hemasundar.pojos.EarningsCache cache = (com.hemasundar.pojos.EarningsCache) field.get(null);
        if (cache == null) {
            cache = com.hemasundar.pojos.EarningsCache.builder().build();
            field.set(null, cache);
        }
        cache.getCache().put(symbol, entry);

        // Retrieve from cache - should be null since it's stale (> 30 days)
        List<EarningsCalendarResponse.EarningCalendar> cached = EarningsCacheManager.getEarningsFromCache(symbol, LocalDate.now());
        assertNull(cached);
    }
}
