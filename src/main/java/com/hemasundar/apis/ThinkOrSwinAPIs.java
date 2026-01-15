package com.hemasundar.apis;

import com.hemasundar.options.models.ExpirationChainResponse;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.ApiErrorHandler;
import com.hemasundar.utils.TokenProvider;
import com.hemasundar.utils.JavaUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ThinkOrSwinAPIs {
    public static OptionChainResponse getOptionChainResponse(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("strategy", "SINGLE")
                // .log().all()
                .get("https://api.schwabapi.com/marketdata/v1/chains");
        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                ApiErrorHandler.handle400Error("Option Chain API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException("Option Chain API failed for " + symbol + ": " + response.statusCode() + " - "
                    + response.asString());
        }

        // System.out.println("Response code for Option chain API: " +
        // response.getStatusCode());
        // System.out.println("Response: \n" + response.asPrettyString());

        OptionChainResponse optionChainResponse = JavaUtils.convertJsonToPojo(response.asString(),
                OptionChainResponse.class);
        log.debug("[{}] Current Market Price: {}", symbol, optionChainResponse.getUnderlyingPrice());
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
            if (response.statusCode() == 400) {
                ApiErrorHandler.handle400Error("Price History API", symbol, response.asString());
                return null;
            }
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

    /**
     * Fetches quotes for multiple symbols in a single API call.
     * Returns quote and reference data for each symbol.
     *
     * @param symbols    List of symbols to fetch quotes for (e.g., ["AAPL", "TSLA",
     *                   "AMZN"])
     * @param fields     Comma-separated fields to include (e.g., "quote,reference")
     * @param indicative Whether to include indicative quotes for non-tradeable
     *                   symbols
     * @return Map of symbol to QuoteData containing quote and reference information
     */
    public static java.util.Map<String, QuotesResponse.QuoteData> getQuotes(
            java.util.List<String> symbols,
            String fields,
            boolean indicative) {

        String symbolsParam = String.join(",", symbols);

        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbols", symbolsParam)
                .queryParam("fields", fields)
                .queryParam("indicative", indicative)
                .get("https://api.schwabapi.com/marketdata/v1/quotes");

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Quotes API failed: " + response.statusCode() + " - " + response.asString());
        }

        // The response is a direct map of symbol -> quote data
        return JavaUtils.convertJsonToMap(response.asString(), QuotesResponse.QuoteData.class);
    }

    /**
     * Fetches quotes for multiple symbols with default fields (quote and
     * reference).
     *
     * @param symbols List of symbols to fetch quotes for
     * @return Map of symbol to QuoteData
     */
    public static java.util.Map<String, QuotesResponse.QuoteData> getQuotes(java.util.List<String> symbols) {
        return getQuotes(symbols, "quote,reference", true);
    }

    /**
     * Fetches quote for a single symbol.
     *
     * @param symbol The symbol to fetch quote for (e.g., "TSLA")
     * @param fields Comma-separated fields to include (e.g., "quote,reference")
     * @return QuoteData containing quote and reference information
     */
    public static QuotesResponse.QuoteData getQuote(String symbol, String fields) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("fields", fields)
                .get("https://api.schwabapi.com/marketdata/v1/" + symbol + "/quotes");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                ApiErrorHandler.handle400Error("Quote API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Quote API failed for " + symbol + ": " + response.statusCode() + " - " + response.asString());
        }

        // The response is a map with the symbol as key
        java.util.Map<String, QuotesResponse.QuoteData> result = JavaUtils.convertJsonToMap(response.asString(),
                QuotesResponse.QuoteData.class);
        return result.get(symbol);
    }

    /**
     * Fetches quote for a single symbol with default fields (quote and reference).
     *
     * @param symbol The symbol to fetch quote for
     * @return QuoteData containing quote and reference information
     */
    public static QuotesResponse.QuoteData getQuote(String symbol) {
        return getQuote(symbol, "quote,reference");
    }

    /**
     * Fetches the expiration chain for a symbol.
     * Returns all available expiration dates for options on the symbol.
     *
     * @param symbol The symbol to fetch expiration chain for (e.g., "AAPL")
     * @return ExpirationChainResponse containing list of expiration dates
     */
    public static ExpirationChainResponse getExpirationChain(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .get("https://api.schwabapi.com/marketdata/v1/expirationchain");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                ApiErrorHandler.handle400Error("Expiration Chain API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Expiration Chain API failed for " + symbol + ": " + response.statusCode() + " - "
                            + response.asString());
        }

        return JavaUtils.convertJsonToPojo(response.asString(), ExpirationChainResponse.class);
    }

}
