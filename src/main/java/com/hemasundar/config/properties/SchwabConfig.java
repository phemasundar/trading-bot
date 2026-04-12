package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "schwab")
@Data
public class SchwabConfig {
    private String refreshToken;
    private String appKey;
    private String appSecret;
}
