package com.hemasundar.apis;

import com.hemasundar.pojos.earningsCalendarResponse;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.utils.BaseURLs;
import com.hemasundar.utils.JavaUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.time.LocalDate;

public class FinnHubAPIs {
    /**
     * Fetches the Earnings Calendar for a specific ticker.
     * Finnhub supports server-side filtering by symbol for this endpoint.
     *
     * @return
     */
    public static earningsCalendarResponse getEarningsByTicker(String ticker, LocalDate toDate) {
        System.out.println("====== EARNINGS CALENDAR FOR: " + ticker + " ======");

        Response response = RestAssured.given()
                .baseUri(BaseURLs.FINNHUB_BASE_URL)
                .queryParam("symbol", ticker)
                .queryParam("from", LocalDate.now().toString())
                .queryParam("to", toDate.toString())
                .queryParam("token", TestConfig.getInstance().finnhubApiKey())
                .get("/calendar/earnings");

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching earnings: " + response.statusLine());
        }
        System.out.println(response.asPrettyString());
        return JavaUtils.convertJsonToPojo(response.asPrettyString(), earningsCalendarResponse.class);
    }
}
