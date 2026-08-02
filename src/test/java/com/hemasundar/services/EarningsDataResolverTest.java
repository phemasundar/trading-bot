package com.hemasundar.services;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.pojos.EarningsCalendarResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsDataResolverTest {

    @Mock
    private FinnHubAPIs finnHubAPIs;

    private static final String SYMBOL = "AAPL";
    private LocalDate today;
    
    @BeforeEach
    void setUp() {
        today = LocalDate.now();
    }

    @Test
    void testResolve_NoEarningsFound() {
        when(finnHubAPIs.getEarningsByTicker(eq(SYMBOL), any(LocalDate.class))).thenReturn(new EarningsCalendarResponse(new ArrayList<>()));

        String expiry = today.plusDays(30).toString();
        Map<String, Double> variables = EarningsDataResolver.resolve(SYMBOL, expiry, finnHubAPIs);

        assertEquals(30.0, variables.get("DTE"));
        assertEquals(EarningsDataResolver.NO_EARNINGS_DAYS_TO_NEXT, variables.get("DAYS_TO_NEXT_EARNINGS"));
        assertEquals(EarningsDataResolver.NO_EARNINGS_BEFORE_DTE, variables.get("EARNINGS_NEAREST_TO_DTE"));
    }

    @Test
    void testResolve_MultipleEarningsBeforeDte() {
        List<EarningsCalendarResponse.EarningCalendar> earnings = new ArrayList<>();
        
        // Earning 1: 5 days from now
        EarningsCalendarResponse.EarningCalendar e1 = new EarningsCalendarResponse.EarningCalendar();
        e1.setDate(today.plusDays(5));
        
        // Earning 2: 15 days from now
        EarningsCalendarResponse.EarningCalendar e2 = new EarningsCalendarResponse.EarningCalendar();
        e2.setDate(today.plusDays(15));
        
        // Earning 3: 45 days from now (After DTE)
        EarningsCalendarResponse.EarningCalendar e3 = new EarningsCalendarResponse.EarningCalendar();
        e3.setDate(today.plusDays(45));

        earnings.add(e1);
        earnings.add(e2);
        earnings.add(e3);
        
        when(finnHubAPIs.getEarningsByTicker(eq(SYMBOL), any(LocalDate.class))).thenReturn(new EarningsCalendarResponse(earnings));

        // DTE = 30
        String expiry = today.plusDays(30).toString();
        Map<String, Double> variables = EarningsDataResolver.resolve(SYMBOL, expiry, finnHubAPIs);

        assertEquals(30.0, variables.get("DTE"));
        // Soonest from today is e1 (5 days)
        assertEquals(5.0, variables.get("DAYS_TO_NEXT_EARNINGS"));
        // Closest to DTE but <= DTE is e2 (15 days)
        assertEquals(15.0, variables.get("EARNINGS_NEAREST_TO_DTE"));
    }

    @Test
    void testResolve_EarningsExactlyOnDte() {
        List<EarningsCalendarResponse.EarningCalendar> earnings = new ArrayList<>();
        
        // Earning exactly on DTE
        EarningsCalendarResponse.EarningCalendar e1 = new EarningsCalendarResponse.EarningCalendar();
        e1.setDate(today.plusDays(30));
        
        earnings.add(e1);
        
        when(finnHubAPIs.getEarningsByTicker(eq(SYMBOL), any(LocalDate.class))).thenReturn(new EarningsCalendarResponse(earnings));

        String expiry = today.plusDays(30).toString();
        Map<String, Double> variables = EarningsDataResolver.resolve(SYMBOL, expiry, finnHubAPIs);

        assertEquals(30.0, variables.get("DTE"));
        assertEquals(30.0, variables.get("DAYS_TO_NEXT_EARNINGS"));
        assertEquals(30.0, variables.get("EARNINGS_NEAREST_TO_DTE"));
    }

    @Test
    void testResolve_EarningsAfterDte() {
        List<EarningsCalendarResponse.EarningCalendar> earnings = new ArrayList<>();
        
        // Earning after DTE
        EarningsCalendarResponse.EarningCalendar e1 = new EarningsCalendarResponse.EarningCalendar();
        e1.setDate(today.plusDays(40));
        
        earnings.add(e1);
        
        when(finnHubAPIs.getEarningsByTicker(eq(SYMBOL), any(LocalDate.class))).thenReturn(new EarningsCalendarResponse(earnings));

        String expiry = today.plusDays(30).toString();
        Map<String, Double> variables = EarningsDataResolver.resolve(SYMBOL, expiry, finnHubAPIs);

        assertEquals(30.0, variables.get("DTE"));
        // There is an earning event 40 days away
        assertEquals(40.0, variables.get("DAYS_TO_NEXT_EARNINGS"));
        // But no earnings BEFORE or ON DTE
        assertEquals(EarningsDataResolver.NO_EARNINGS_BEFORE_DTE, variables.get("EARNINGS_NEAREST_TO_DTE"));
    }
}
