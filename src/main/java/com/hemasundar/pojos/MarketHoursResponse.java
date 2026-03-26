package com.hemasundar.pojos;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        private boolean isOpen;
    }
}
