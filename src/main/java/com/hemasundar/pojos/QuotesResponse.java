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
        private boolean realtime;
        private long ssid;
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
        private double askPrice;
        private int askSize;
        private double bidPrice;
        private int bidSize;
        private double lastPrice;
        private int lastSize;
        private double mark;
        private long quoteTime;
        private long totalVolume;
        private long tradeTime;
    }

    /**
     * Fundamental data including dividends, earnings, PE ratio.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fundamental {
        private long avg10DaysVolume;
        private long avg1YearVolume;
        private String declarationDate;
        private double divAmount;
        private String divExDate;
        private int divFreq;
        private double divPayAmount;
        private String divPayDate;
        private double divYield;
        private double eps;
        private double fundLeverageFactor;
        private String lastEarningsDate;
        private String nextDivExDate;
        private String nextDivPayDate;
        private double peRatio;
        private long sharesOutstanding;
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
        private double fiftyTwoWeekHigh;
        @JsonProperty("52WeekLow")
        private double fiftyTwoWeekLow;
        private String askMICId;
        private double askPrice;
        private int askSize;
        private long askTime;
        private String bidMICId;
        private double bidPrice;
        private int bidSize;
        private long bidTime;
        private double closePrice;
        private double highPrice;
        private String lastMICId;
        private double lastPrice;
        private int lastSize;
        private double lowPrice;
        private double mark;
        private double markChange;
        private double markPercentChange;
        private double nAV; // Net Asset Value for mutual funds
        private double netChange;
        private double netPercentChange;
        private double openPrice;
        private double postMarketChange;
        private double postMarketPercentChange;
        private long quoteTime;
        private String securityStatus;
        private int tick;
        private double tickAmount;
        private long totalVolume;
        private long tradeTime;
        private double volatility;
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
        private boolean isHardToBorrow;
        private boolean isShortable;
        private boolean isTradable;
        private long htbQuantity;
        private double htbRate;
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
        private double regularMarketLastPrice;
        private long regularMarketLastSize;
        private double regularMarketNetChange;
        private double regularMarketPercentChange;
        private long regularMarketTradeTime;
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
