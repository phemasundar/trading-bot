package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "allowed")
@Data
public class SecurityConfig {
    /**
     * Mapped from allowed.emails in application.properties
     */
    private String emails;
}
