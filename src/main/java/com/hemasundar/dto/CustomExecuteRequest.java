package com.hemasundar.dto;

import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import java.util.Map;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class CustomExecuteRequest {
    private String strategyType;
    private String securitiesFile; // e.g., "portfolio, top100"
    private String securities;     // e.g., "AAPL, MSFT, GOOG"
    private String alias;
    private Integer maxTradesToSend;
    private Map<String, Object> filter;
}
