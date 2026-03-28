package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response POJO for Schwab Quotes API.
 * Contains quote and reference data for one or more symbols.
 * The response is a map where key is the symbol and value is the quote data.
 * 
 * Possible root nodes in response: quote, fundamental, extended, reference,
 * regular
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotesResponse {

    /**
     * Map of symbol to quote data.
     * Key: Symbol (e.g., "AAPL")
     * Value: Quote details including quote and reference data
     */
    private Map<String, QuoteData> quotes;

    /**
     * Quote data for a single symbol.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteData {
        private String assetMainType; // EQUITY, MUTUAL_FUND, INDEX, FOREX
        private String assetSubType; // COE, ETF, OEF
        private String quoteType; // NBBO
        private Boolean realtime;
        private Long ssid;
        private String symbol;
        private Extended extended;
        private Fundamental fundamental;
        private Quote quote;
        private Reference reference;
        private Regular regular;
    }

    /**
     * Extended hours quote information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Extended {
        private Double askPrice;
        private Integer askSize;
        private Double bidPrice;
        private Integer bidSize;
        private Double lastPrice;
        private Integer lastSize;
        private Double mark;
        private Long quoteTime;
        private Long totalVolume;
        private Long tradeTime;
    }

    /**
     * Fundamental data including dividends, earnings, PE ratio.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fundamental {
        private Long avg10DaysVolume;
        private Long avg1YearVolume;
        private String declarationDate;
        private Double divAmount;
        private String divExDate;
        private Integer divFreq;
        private Double divPayAmount;
        private String divPayDate;
        private Double divYield;
        private Double eps;
        private Double fundLeverageFactor;
        private String lastEarningsDate;
        private String nextDivExDate;
        private String nextDivPayDate;
        private Double peRatio;
        private Long sharesOutstanding;
    }

    /**
     * Quote information including price and volume data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        @JsonProperty("52WeekHigh")
        private Double fiftyTwoWeekHigh;
        @JsonProperty("52WeekLow")
        private Double fiftyTwoWeekLow;
        private String askMICId;
        private Double askPrice;
        private Integer askSize;
        private Long askTime;
        private String bidMICId;
        private Double bidPrice;
        private Integer bidSize;
        private Long bidTime;
        private Double closePrice;
        private Double highPrice;
        private String lastMICId;
        private Double lastPrice;
        private Integer lastSize;
        private Double lowPrice;
        private Double mark;
        private Double markChange;
        private Double markPercentChange;
        private Double nAV; // Net Asset Value for mutual funds (null for equities)
        private Double netChange;
        private Double netPercentChange;
        private Double openPrice;
        private Double postMarketChange;
        private Double postMarketPercentChange;
        private Long quoteTime;
        private String securityStatus;
        private Integer tick;
        private Double tickAmount;
        private Long totalVolume;
        private Long tradeTime;
        private Double volatility;
    }

    /**
     * Reference information about the security.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reference {
        private String cusip;
        private String description;
        private String exchange;
        private String exchangeName;
        private Boolean isHardToBorrow;
        private Boolean isShortable;
        private Boolean isTradable;
        private Long htbQuantity;
        private Double htbRate;
        private String otcMarketTier; // For OTC stocks: OTCID, Pink Limited
    }

    /**
     * Regular market session data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Regular {
        private Double regularMarketLastPrice;
        private Long regularMarketLastSize;
        private Double regularMarketNetChange;
        private Double regularMarketPercentChange;
        private Long regularMarketTradeTime;
    }

    /**
     * Error information for invalid symbols.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Errors {
        private List<String> invalidSymbols;
    }
}
