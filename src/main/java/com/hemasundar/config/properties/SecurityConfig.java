package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security configuration properties for user access control.
 * Binds properties under the "security" prefix.
 */
@Component
@ConfigurationProperties(prefix = "security")
@Data
public class SecurityConfig {
    /** Comma-separated list of allowed user emails. */
    private String allowedEmails;

    /** Comma-separated list of read-only user emails (subset of allowedEmails). */
    private String readonlyEmails;
}
