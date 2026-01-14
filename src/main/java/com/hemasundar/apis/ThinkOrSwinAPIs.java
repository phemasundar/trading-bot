package com.hemasundar.apis;

import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.TokenProvider;
import io.restassured.RestAssured;
import io.restassured.response.Response;

public class ThinkOrSwinAPIs {
    public static OptionChainResponse getOptionChainResponse(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("strategy", "SINGLE")
                // .log().all()
                .get("https://api.schwabapi.com/marketdata/v1/chains");
        if (response.statusCode() != 200)
            throw new RuntimeException("Option Chain API failed");

        // System.out.println("Response code for Option chain API: " +
        // response.getStatusCode());
        // System.out.println("Response: \n" + response.asPrettyString());

        OptionChainResponse optionChainResponse = JavaUtils.convertJsonToPojo(response.asString(),
                OptionChainResponse.class);
        System.out.println("Current Maket Price: " + optionChainResponse.getUnderlyingPrice());
        return optionChainResponse;
    }

    /**
     * Fetches historical price data from Schwab API with full parameter control.
     *
     * @param symbol                The stock symbol (e.g., "AAPL")
     * @param periodType            Period type: "day", "month", "year", "ytd"
     * @param period                Number of periods (e.g., 1 for 1 year)
     * @param frequencyType         Frequency type: "minute", "daily", "weekly",
     *                              "monthly"
     * @param frequency             Frequency value (e.g., 1 for daily)
     * @param startDate             Start date as Unix timestamp in seconds
     *                              (nullable)
     * @param endDate               End date as Unix timestamp in seconds (nullable)
     * @param needExtendedHoursData Include extended hours data
     * @param needPreviousClose     Include previous close price
     * @return PriceHistoryResponse containing OHLCV candle data
     */
    public static PriceHistoryResponse getPriceHistory(
            String symbol,
            String periodType,
            int period,
            String frequencyType,
            int frequency,
            Long startDate,
            Long endDate,
            boolean needExtendedHoursData,
            boolean needPreviousClose) {

        var request = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("periodType", periodType)
                .queryParam("period", period)
                .queryParam("frequencyType", frequencyType)
                .queryParam("frequency", frequency)
                .queryParam("needExtendedHoursData", needExtendedHoursData)
                .queryParam("needPreviousClose", needPreviousClose);

        if (startDate != null) {
            request.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            request.queryParam("endDate", endDate);
        }

        Response response = request.get("https://api.schwabapi.com/marketdata/v1/pricehistory");

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Price History API failed: " + response.statusCode() + " - " + response.asString());
        }

        return JavaUtils.convertJsonToPojo(response.asString(), PriceHistoryResponse.class);
    }

    /**
     * Simplified method to fetch yearly price history with daily frequency.
     * Useful for technical indicator calculations like RSI and Bollinger Bands.
     *
     * @param symbol The stock symbol (e.g., "AAPL")
     * @param years  Number of years of historical data to fetch
     * @return PriceHistoryResponse containing OHLCV candle data
     */
    public static PriceHistoryResponse getYearlyPriceHistory(String symbol, int years) {
        return getPriceHistory(
                symbol,
                "year",
                years,
                "daily",
                1,
                null,
                null,
                false,
                true);
    }

}
