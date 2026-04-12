package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "finnhub")
@Data
public class FinnHubConfig {
    private String apiKey;
}
