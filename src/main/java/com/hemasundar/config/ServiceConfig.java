package com.hemasundar.config;

import com.hemasundar.services.SupabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for creating service beans.
 */
@Configuration
public class ServiceConfig {

    @Value("${supabase.url:#{environment.SUPABASE_URL}}")
    private String supabaseUrl;

    @Value("${supabase.anon.key:#{environment.SUPABASE_ANON_KEY}}")
    private String supabaseApiKey;

    /**
     * Creates SupabaseService bean with configuration from application.properties
     * or environment variables.
     */
    @Bean
    public SupabaseService supabaseService() {
        return new SupabaseService(supabaseUrl, supabaseApiKey);
    }
}
