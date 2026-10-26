package com.hemasundar.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for registering application interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ConfigValidationInterceptor configValidationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(configValidationInterceptor)
                .addPathPatterns(
                        "/api/execute",
                        "/api/execute/**",
                        "/api/earnings-calendar",
                        "/api/earnings-calendar/**",
                        "/api/iv-rank"
                );
    }
}
