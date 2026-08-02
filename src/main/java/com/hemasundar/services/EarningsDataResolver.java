package com.hemasundar.services;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.pojos.EarningsCalendarResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Log4j2
@UtilityClass
public class EarningsDataResolver {

    public static final double NO_EARNINGS_DAYS_TO_NEXT = 999999.0;
    public static final double NO_EARNINGS_BEFORE_DTE = 0.0;

    /**
     * Resolves earnings data for a given symbol and expiry date, calculating variables
     * for mathematical expression evaluation.
     *
     * @param symbol      The stock symbol
     * @param expiryDate  The expiry date of the option chain (YYYY-MM-DD)
     * @param finnHubAPIs API client to fetch earnings
     * @return Map of variable names to their computed double values
     */
    public static Map<String, Double> resolve(String symbol, String expiryDate, FinnHubAPIs finnHubAPIs) {
        Map<String, Double> variables = new HashMap<>();
        
        LocalDate today = LocalDate.now();
        LocalDate expiry = LocalDate.parse(expiryDate);
        long dte = ChronoUnit.DAYS.between(today, expiry);
        if (dte < 0) dte = 0;
        
        variables.put("DTE", (double) dte);

        try {
            // Fetch earnings up to expiry date + 1 year
            LocalDate fetchUntilDate = expiry.isAfter(today.plusYears(1)) ? expiry : today.plusYears(1);
            EarningsCalendarResponse response = finnHubAPIs.getEarningsByTicker(symbol, fetchUntilDate);
            
            List<EarningsCalendarResponse.EarningCalendar> earnings = response != null ? response.getEarningsCalendar() : null;

            if (CollectionUtils.isEmpty(earnings)) {
                variables.put("DAYS_TO_NEXT_EARNINGS", NO_EARNINGS_DAYS_TO_NEXT);
                variables.put("EARNINGS_NEAREST_TO_DTE", NO_EARNINGS_BEFORE_DTE);
                return variables;
            }

            // DAYS_TO_NEXT_EARNINGS: The soonest earnings event from today
            long daysToNext = Long.MAX_VALUE;
            // EARNINGS_NEAREST_TO_DTE: The earnings event closest to DTE (must be <= DTE)
            long earningsNearestToDte = -1;
            
            for (EarningsCalendarResponse.EarningCalendar event : earnings) {
                if (event.getDate() == null) continue;
                
                // Only consider earnings from today onwards
                if (event.getDate().isBefore(today)) continue;
                
                long daysToEvent = ChronoUnit.DAYS.between(today, event.getDate());
                
                if (daysToEvent < daysToNext) {
                    daysToNext = daysToEvent;
                }
                
                // Only consider events on or before DTE
                if (daysToEvent <= dte) {
                    // Pick the one closest to DTE (i.e., the largest daysToEvent that is <= dte)
                    if (daysToEvent > earningsNearestToDte) {
                        earningsNearestToDte = daysToEvent;
                    }
                }
            }
            
            variables.put("DAYS_TO_NEXT_EARNINGS", daysToNext == Long.MAX_VALUE ? NO_EARNINGS_DAYS_TO_NEXT : (double) daysToNext);
            variables.put("EARNINGS_NEAREST_TO_DTE", earningsNearestToDte == -1 ? NO_EARNINGS_BEFORE_DTE : (double) earningsNearestToDte);

        } catch (Exception e) {
            log.error("[{}] Error resolving earnings variables: {}", symbol, e.getMessage());
            variables.put("DAYS_TO_NEXT_EARNINGS", NO_EARNINGS_DAYS_TO_NEXT);
            variables.put("EARNINGS_NEAREST_TO_DTE", NO_EARNINGS_BEFORE_DTE);
        }

        return variables;
    }
}
