package com.hemasundar.pojos;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing the JSON response from Schwab's /v1/markets endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketHoursResponse {

    private Map<String, MarketData> equity;
    private Map<String, MarketData> option;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketData {
        private String date;
        private String marketType;
        private String exchange;
        private String category;
        private String product;
        private String productName;

        @JsonProperty("isOpen")
        private boolean isOpen;

        private SessionHours sessionHours;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionHours {
        private List<TimeWindow> preMarket;
        private List<TimeWindow> regularMarket;
        private List<TimeWindow> postMarket;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeWindow {
        private String start;
        private String end;
    }
}
