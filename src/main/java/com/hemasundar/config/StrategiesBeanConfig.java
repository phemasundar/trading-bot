package com.hemasundar.config;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.strategies.*;
import com.hemasundar.services.SupabaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class StrategiesBeanConfig {

    // ==================== PUT CREDIT SPREAD ====================

    @Bean
    public PutCreditSpreadStrategy putCreditSpreadStrategy(FinnHubAPIs finnHubAPIs,
                                                          ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                                          Optional<SupabaseService> supabaseService) {
        return new PutCreditSpreadStrategy(StrategyType.PUT_CREDIT_SPREAD, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    // ==================== CALL CREDIT SPREAD ====================

    @Bean
    public CallCreditSpreadStrategy callCreditSpreadStrategy(FinnHubAPIs finnHubAPIs,
                                                             ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                                             Optional<SupabaseService> supabaseService) {
        return new CallCreditSpreadStrategy(StrategyType.CALL_CREDIT_SPREAD, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    // ==================== IRON CONDOR ====================

    @Bean
    public IronCondorStrategy ironCondorStrategy(FinnHubAPIs finnHubAPIs,
                                                ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                                Optional<SupabaseService> supabaseService,
                                                PutCreditSpreadStrategy putCreditSpreadStrategy,
                                                CallCreditSpreadStrategy callCreditSpreadStrategy) {
        return new IronCondorStrategy(StrategyType.IRON_CONDOR, finnHubAPIs, ThinkOrSwimAPIs, supabaseService, putCreditSpreadStrategy, callCreditSpreadStrategy);
    }

    // ==================== OTHER STRATEGIES ====================

    @Bean
    public LongCallLeapStrategy longCallLeapStrategy(FinnHubAPIs finnHubAPIs,
                                                    ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                                    Optional<SupabaseService> supabaseService) {
        return new LongCallLeapStrategy(StrategyType.LONG_CALL_LEAP, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    @Bean
    public BrokenWingButterflyStrategy brokenWingButterflyStrategy(FinnHubAPIs finnHubAPIs,
                                                                   ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                                                   Optional<SupabaseService> supabaseService) {
        return new BrokenWingButterflyStrategy(StrategyType.BULLISH_BROKEN_WING_BUTTERFLY, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    @Bean
    public ZebraStrategy zebraStrategy(FinnHubAPIs finnHubAPIs,
                                       ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                       Optional<SupabaseService> supabaseService) {
        return new ZebraStrategy(StrategyType.BULLISH_ZEBRA, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    // ==================== SHORT (NAKED) STRATEGIES ====================

    @Bean
    public ShortPutStrategy shortPutStrategy(FinnHubAPIs finnHubAPIs,
                                             ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                             Optional<SupabaseService> supabaseService) {
        return new ShortPutStrategy(StrategyType.SHORT_PUT, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    @Bean
    public ShortStrangleStrategy shortStrangleStrategy(FinnHubAPIs finnHubAPIs,
                                             ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                             Optional<SupabaseService> supabaseService) {
        return new ShortStrangleStrategy(StrategyType.SHORT_STRANGLE, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }
}
