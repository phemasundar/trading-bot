package com.hemasundar.services;

import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.*;
import com.hemasundar.utils.TelegramUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ScreenerExecutionService {

    @Autowired
    private SupabaseService supabaseService;

    @Autowired
    private com.hemasundar.utils.SecuritiesResolver securitiesResolver;

    @Autowired
    private StrategyExecutionService strategyExecutionService;

    /**
     * Loads all enabled technical screeners from strategies-config.json
     */
    public List<ScreenerConfig> getEnabledScreeners() throws IOException {
        return com.hemasundar.config.StrategiesConfigLoader.loadScreeners(securitiesResolver.loadSecuritiesMaps());
    }

    /**
     * Retrieves all latest technical screener results from Supabase.
     */
    public List<ScreenerExecutionResult> getLatestScreenerResults() throws IOException {
        return supabaseService.getAllLatestScreenerResults();
    }

    public void executeScreeners(Set<Integer> screenerIndices, List<ScreenerConfig> allScreeners) {
        if (screenerIndices == null || screenerIndices.isEmpty() || allScreeners == null) {
            log.info("No screener indices provided, skipping technical screeners");
            return;
        }

        TechnicalIndicators allIndicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().period(14).oversoldThreshold(30.0).overboughtThreshold(70.0).build())
                .bollingerFilter(BollingerBandsFilter.builder().period(20).standardDeviations(2.0).build())
                .ma20Filter(MovingAverageFilter.builder().period(20).build())
                .ma50Filter(MovingAverageFilter.builder().period(50).build())
                .ma100Filter(MovingAverageFilter.builder().period(100).build())
                .ma200Filter(MovingAverageFilter.builder().period(200).build())
                .volumeFilter(VolumeFilter.builder().build())
                .build();

        // Filter to only selected screener indices
        List<ScreenerConfig> selectedScreeners = screenerIndices.stream()
                .filter(i -> i >= 0 && i < allScreeners.size())
                .map(i -> allScreeners.get(i))
                .collect(Collectors.toList());

        for (ScreenerConfig screenerConfig : selectedScreeners) {
            log.info("Running screener: {}", screenerConfig.getName());
            if (strategyExecutionService != null) {
                strategyExecutionService.setCurrentExecutionTask("Screener: " + screenerConfig.getName());
            }
            long screenerStartTime = System.currentTimeMillis();

            // Get securities from config
            List<String> securitiesToScan = screenerConfig.getSecurities();
            if (securitiesToScan == null || securitiesToScan.isEmpty()) {
                log.warn("No securities configured for screener {}, skipping.", screenerConfig.getName());
                continue;
            }

            // Route to appropriate screener based on type
            List<TechnicalScreener.ScreeningResult> screenerResults = switch (screenerConfig.getScreenerType()) {
                case PRICE_DROP -> {
                    TechFilterConditions cond = screenerConfig.getConditions();
                    double minDrop = cond.getMinDropPercent() != null ? cond.getMinDropPercent() : 5.0;
                    int days = cond.getLookbackDays() != null ? cond.getLookbackDays() : 0;
                    yield PriceDropScreener.screenPriceDrop(securitiesToScan, minDrop, days);
                }
                case HIGH_52W_DROP -> {
                    TechFilterConditions cond = screenerConfig.getConditions();
                    double minDrop = cond.getMinDropPercent() != null ? cond.getMinDropPercent() : 20.0;
                    yield PriceDropScreener.screen52WeekHighDrop(securitiesToScan, minDrop);
                }
                default -> {
                    TechnicalFilterChain filterChain = TechnicalFilterChain.of(allIndicators,
                            screenerConfig.getConditions());
                    yield TechnicalScreener.screenStocks(securitiesToScan, filterChain);
                }
            };

            log.info("[{}] Found {} stocks matching criteria", screenerConfig.getName(), screenerResults.size());

            if (!screenerResults.isEmpty()) {
                log.info("[{}] Matching stocks: {}", screenerConfig.getName(),
                        screenerResults.stream().map(TechnicalScreener.ScreeningResult::getSymbol)
                                .toList());
                TelegramUtils.sendTechnicalScreenerAlert(screenerConfig.getName(), screenerResults);
            }

            // Save screener result
            long screenerExecutionTime = System.currentTimeMillis() - screenerStartTime;
            ScreenerExecutionResult scrResult = ScreenerExecutionResult.builder()
                    .screenerId(screenerConfig.getName())
                    .screenerName(screenerConfig.getName())
                    .executionTimeMs(screenerExecutionTime)
                    .resultsFound(screenerResults.size())
                    .results(screenerResults)
                    .build();
            try {
                supabaseService.saveScreenerResult(scrResult);
                log.info("[{}] Saved screener result to Supabase", screenerConfig.getName());
            } catch (Exception e) {
                log.error("[{}] Failed to save screener result: {}", screenerConfig.getName(), e.getMessage());
            }
        }
    }
}
