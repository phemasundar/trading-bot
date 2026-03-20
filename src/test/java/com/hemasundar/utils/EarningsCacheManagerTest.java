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
    public void testGetFromCache_Stale() {
        // This is tricky as we can't easily mock the time inside the static class without PowerMock
        // or refactoring to use a Clock.
        // But we can test the null case for non-existent symbols.
        List<EarningsCalendarResponse.EarningCalendar> cached = EarningsCacheManager.getEarningsFromCache("NON_EXISTENT", LocalDate.now());
        assertNull(cached);
    }
}
