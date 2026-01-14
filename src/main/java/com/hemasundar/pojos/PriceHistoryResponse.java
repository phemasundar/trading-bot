package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response POJO for Schwab Price History API.
 * Represents historical OHLCV (Open, High, Low, Close, Volume) candle data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceHistoryResponse {
    private String symbol;
    private boolean empty;
    private double previousClose;
    private long previousCloseDate;
    private List<CandleData> candles;

    /**
     * Represents a single price candle with OHLCV data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandleData {
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;
        private long datetime; // Unix timestamp in milliseconds
    }
}
