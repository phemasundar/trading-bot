package com.hemasundar.config.properties;

import com.hemasundar.utils.EarningsCacheManager;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for earnings calendar cache validity.
 * Injects from application.properties via {@code earnings.cache-validity-days}
 * and initializes {@link EarningsCacheManager}.
 */
@Component
@Getter
@Setter
public class EarningsCacheConfig {

    @Value("${earnings.cache-validity-days:${finnhub.cache-validity-days:0}}")
    private int cacheValidityDays;

    @PostConstruct
    public void init() {
        EarningsCacheManager.setCacheValidityDays(cacheValidityDays);
    }
}
