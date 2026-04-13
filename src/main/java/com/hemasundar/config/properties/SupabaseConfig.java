package com.hemasundar.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supabase")
@Data
public class SupabaseConfig {
    private Boolean enabled;
    private String url;
    private String anonKey;
    private String serviceRoleKey;
}
