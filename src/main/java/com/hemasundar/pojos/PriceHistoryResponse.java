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
    private Boolean empty;
    private Double previousClose;
    private Long previousCloseDate;
    private List<CandleData> candles;

    /**
     * Represents a single price candle with OHLCV data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandleData {
        private Double open;
        private Double high;
        private Double low;
        private Double close;
        private Long volume;
        private Long datetime; // Unix timestamp in milliseconds
    }
}
