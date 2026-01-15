package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response POJO for Schwab Quotes API.
 * Contains quote and reference data for one or more symbols.
 * The response is a map where key is the symbol and value is the quote data.
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
        private String assetMainType;
        private String assetSubType;
        private String quoteType;
        private boolean realtime;
        private String ssid;
        private String symbol;
        private Quote quote;
        private Reference reference;
    }

    /**
     * Quote information including price and volume data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        private double fiftyTwoWeekHigh;
        private double fiftyTwoWeekLow;
        private double askPrice;
        private int askSize;
        private double bidPrice;
        private int bidSize;
        private double closePrice;
        private double highPrice;
        private double lastPrice;
        private int lastSize;
        private double lowPrice;
        private double mark;
        private double markChange;
        private double markPercentChange;
        private double netChange;
        private double netPercentChange;
        private double openPrice;
        private long quoteTime;
        private String securityStatus;
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
        private double htbRate;
    }
}
