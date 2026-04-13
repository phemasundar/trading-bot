package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.sheets")
@Data
public class GoogleSheetsConfig {
    private String spreadsheetId;
    private Boolean enabled;
}
