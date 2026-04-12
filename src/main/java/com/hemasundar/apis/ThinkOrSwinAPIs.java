package com.hemasundar.apis;

import com.hemasundar.options.models.ExpirationChainResponse;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.MarketHoursResponse;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.ApiErrorHandler;
import com.hemasundar.utils.TokenProvider;
import com.hemasundar.utils.JavaUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Spring-managed client for the Schwab Market Data API.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ThinkOrSwinAPIs {

    private final TokenProvider tokenProvider;
    private final ApiErrorHandler apiErrorHandler;

    /**
     * Fetches quotes for multiple symbols in a single API call.
     */
    public Map<String, QuotesResponse.QuoteData> getQuotes(List<String> symbols, String fields,
                                                                  Boolean indicative) {

        String symbolsParam = String.join(",", symbols);

        var requestSpec = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("symbols", symbolsParam);

        if (fields != null && !fields.isBlank()) {
            requestSpec.queryParam("fields", fields);
        }
        if (indicative != null) {
            requestSpec.queryParam("indicative", indicative);
        }

        Response response = requestSpec.get("https://api.schwabapi.com/marketdata/v1/quotes");

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Quotes API failed: " + response.statusCode() + " - " + response.asString());
        }

        return JavaUtils.convertJsonToMap(response.asString(), QuotesResponse.QuoteData.class);
    }

    /**
     * Fetches quotes for multiple symbols with all available fields.
     */
    public Map<String, QuotesResponse.QuoteData> getQuotes(List<String> symbols) {
        return getQuotes(symbols, null, null);
    }

    /**
     * Fetches quote for a single symbol.
     */
    public QuotesResponse.QuoteData getQuote(String symbol, String fields) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("fields", fields)
                .get("https://api.schwabapi.com/marketdata/v1/" + symbol + "/quotes");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Quote API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Quote API failed for " + symbol + ": " + response.statusCode() + " - " + response.asString());
        }

        Map<String, QuotesResponse.QuoteData> result = JavaUtils.convertJsonToMap(response.asString(),
                QuotesResponse.QuoteData.class);
        return result.get(symbol);
    }

    /**
     * Fetches quote for a single symbol with default fields (quote and reference).
     */
    public QuotesResponse.QuoteData getQuote(String symbol) {
        return getQuote(symbol, "quote,reference");
    }

    public OptionChainResponse getOptionChain(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("strikeCount", 150)
                .queryParam("strategy", "SINGLE")
                .get("https://api.schwabapi.com/marketdata/v1/chains");
        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Option Chain API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException("Option Chain API failed for " + symbol + ": " + response.statusCode() + " - "
                    + response.asString());
        }

        OptionChainResponse optionChainResponse = JavaUtils.convertJsonToPojo(response.asString(),
                OptionChainResponse.class);
        optionChainResponse.removeInvalidOptions();
        log.debug("[{}] Current Market Price: {}", symbol, optionChainResponse.getUnderlyingPrice());
        return optionChainResponse;
    }

    /**
     * Fetches the expiration chain for a symbol.
     */
    public ExpirationChainResponse getExpirationChain(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("symbol", symbol)
                .get("https://api.schwabapi.com/marketdata/v1/expirationchain");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Expiration Chain API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Expiration Chain API failed for " + symbol + ": " + response.statusCode() + " - "
                            + response.asString());
        }

        return JavaUtils.convertJsonToPojo(response.asString(), ExpirationChainResponse.class);
    }

    /**
     * Fetches historical price data from Schwab API with full parameter control.
     */
    public PriceHistoryResponse getPriceHistory(
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
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
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
                apiErrorHandler.handle400Error("Price History API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Price History API failed: " + response.statusCode() + " - " + response.asString());
        }

        return JavaUtils.convertJsonToPojo(response.asString(), PriceHistoryResponse.class);
    }

    /**
     * Simplified method to fetch yearly price history with daily frequency.
     */
    public PriceHistoryResponse getYearlyPriceHistory(String symbol, int years) {
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
     * Fetches current market hours and status for Equities and Options.
     */
    public MarketHoursResponse getMarketHours() {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("markets", "equity")
                .queryParam("markets", "option")
                .get("https://api.schwabapi.com/marketdata/v1/markets");

        if (response.statusCode() != 200) {
            throw new RuntimeException("Market Hours API failed: " + response.statusCode() + " - " + response.asString());
        }

        return JavaUtils.convertJsonToPojo(response.asString(), com.hemasundar.pojos.MarketHoursResponse.class);
    }

    /**
     * Fetches market hours for a single specific market type.
     */
    public String getMarketHour(String marketId, String date) {
        var request = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken());

        if (date != null && !date.isBlank()) {
            request.queryParam("date", date);
        }

        Response response = request.get("https://api.schwabapi.com/marketdata/v1/markets/" + marketId);

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Market Hour API failed for " + marketId + ": " + response.statusCode() + " - " + response.asString());
        }

        return response.asString();
    }

    /**
     * Fetches the top 10 movers for a specific index.
     */
    public String getMovers(String indexSymbol, String sort, Integer frequency) {
        var request = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken());

        if (sort != null && !sort.isBlank()) {
            request.queryParam("sort", sort);
        }
        if (frequency != null) {
            request.queryParam("frequency", frequency);
        }

        Response response = request.get("https://api.schwabapi.com/marketdata/v1/movers/" + indexSymbol);

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Movers API", indexSymbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Movers API failed for " + indexSymbol + ": " + response.statusCode() + " - " + response.asString());
        }

        return response.asString();
    }

    /**
     * Searches for instruments by symbol and projection type.
     */
    public String getInstruments(String symbol, String projection) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("projection", projection)
                .get("https://api.schwabapi.com/marketdata/v1/instruments");

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Instruments API", symbol, response.asString());
                return null;
            }
            throw new RuntimeException(
                    "Instruments API failed for " + symbol + ": " + response.statusCode() + " - " + response.asString());
        }

        return response.asString();
    }

    /**
     * Fetches basic instrument details by CUSIP identifier.
     */
    public String getInstrumentByCusip(String cusipId) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .get("https://api.schwabapi.com/marketdata/v1/instruments/" + cusipId);

        if (response.statusCode() != 200) {
            if (response.statusCode() == 400) {
                apiErrorHandler.handle400Error("Instruments CUSIP API", cusipId, response.asString());
                return null;
            }
            if (response.statusCode() == 404) {
                log.warn("Instrument not found for CUSIP: {}", cusipId);
                return null;
            }
            throw new RuntimeException(
                    "Instruments CUSIP API failed for " + cusipId + ": " + response.statusCode() + " - " + response.asString());
        }

        return response.asString();
    }
}
