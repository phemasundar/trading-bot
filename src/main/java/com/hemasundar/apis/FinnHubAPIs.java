package com.hemasundar.apis;

import com.hemasundar.config.properties.FinnHubConfig;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.utils.ApiErrorHandler;
import com.hemasundar.utils.BaseURLs;
import com.hemasundar.utils.EarningsCacheManager;
import com.hemasundar.utils.JavaUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring-managed client for FinnHub API.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class FinnHubAPIs {

    private final FinnHubConfig finnhubConfig;
    private final ApiErrorHandler apiErrorHandler;

    /**
     * Fetches the Earnings Calendar for a specific ticker.
     * Finnhub supports server-side filtering by symbol for this endpoint.
     *
     * @return
     */
    public EarningsCalendarResponse getEarningsByTicker(String ticker, LocalDate toDate) {

        // 1. Try Cache First
        List<EarningsCalendarResponse.EarningCalendar> cachedEarnings = EarningsCacheManager
                .getEarningsFromCache(ticker, toDate);

        if (cachedEarnings != null) {
            List<EarningsCalendarResponse.EarningCalendar> filteredCached = cachedEarnings.stream()
                    .filter(e -> e.getDate() != null && !e.getDate().isAfter(toDate))
                    .toList();
            return new EarningsCalendarResponse(filteredCached);
        }

        log.info("Fetching fresh earnings for: {}", ticker);

        // 2. Fetch Fresh Data (Always fetch 1 year ahead for better caching)
        LocalDate oneYearFromNow = LocalDate.now().plusYears(1);

        // Use the further date between requested toDate and oneYearFromNow to ensure we
        // satisfy current request
        LocalDate fetchUntilDate = toDate.isAfter(oneYearFromNow) ? toDate : oneYearFromNow;

        Response response = RestAssured.given()
                .baseUri(BaseURLs.FINNHUB_BASE_URL)
                .queryParam("symbol", ticker)
                .queryParam("from", LocalDate.now().toString())
                .queryParam("to", fetchUntilDate.toString())
                .queryParam("token", finnhubConfig.getApiKey())
                .get("/calendar/earnings");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("FinnHub Earnings API", ticker, response.asString());
                return new EarningsCalendarResponse(List.of());
            }
            throw new RuntimeException("Error fetching earnings: " + response.statusLine());
        }
        log.debug("Earnings response for {}: {}", ticker, response.asPrettyString());

        EarningsCalendarResponse result = JavaUtils.convertJsonToPojo(response.asPrettyString(),
                EarningsCalendarResponse.class);

        // 3. Update Cache
        if (result != null && result.getEarningsCalendar() != null) {
            EarningsCacheManager.updateCache(ticker, result.getEarningsCalendar());

            // Filter fresh result for return
            List<EarningsCalendarResponse.EarningCalendar> filteredFresh = result.getEarningsCalendar().stream()
                    .filter(e -> e.getDate() != null && !e.getDate().isAfter(toDate))
                    .toList();
            return new EarningsCalendarResponse(filteredFresh);
        }

        return result;
    }
}
