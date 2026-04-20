package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.*;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.TelegramUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class ScreenerExecutionService {

    private final SupabaseService supabaseService;
    private final SecuritiesResolver securitiesResolver;
    private final StrategyExecutionService strategyExecutionService;
    private final ThinkOrSwinAPIs thinkOrSwinAPIs;
    private final TelegramUtils telegramUtils;
    private final TechnicalScreener technicalScreener;
    private final PriceDropScreener priceDropScreener;
    private final StrategiesConfigLoader strategiesConfigLoader;

    /**
     * Loads all enabled technical screeners from strategies-config.json
     */
    public List<ScreenerConfig> getEnabledScreeners() throws IOException {
        return strategiesConfigLoader.loadScreeners(securitiesResolver.loadSecuritiesMaps());
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
                strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                        String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                        AlertMessages.NO_SECURITIES_CONFIGURED);
                continue;
            }

            List<TechnicalScreener.ScreeningResult> screenerResults;
            try {
                // Route to appropriate screener based on type
                screenerResults = switch (screenerConfig.getScreenerType()) {
                    case PRICE_DROP -> {
                        TechFilterConditions cond = screenerConfig.getConditions();
                        double minDrop = cond.getMinDropPercent() != null ? cond.getMinDropPercent() : 5.0;
                        int days = cond.getLookbackDays() != null ? cond.getLookbackDays() : 0;
                        yield priceDropScreener.screenPriceDrop(securitiesToScan, minDrop, days);
                    }
                    case HIGH_52W_DROP -> {
                        TechFilterConditions cond = screenerConfig.getConditions();
                        double minDrop = cond.getMinDropPercent() != null ? cond.getMinDropPercent() : 20.0;
                        yield priceDropScreener.screen52WeekHighDrop(securitiesToScan, minDrop);
                    }
                    default -> {
                        TechnicalFilterChain filterChain = TechnicalFilterChain.of(allIndicators,
                                screenerConfig.getConditions());
                        yield technicalScreener.screenStocks(securitiesToScan, filterChain);
                    }
                };
            } catch (Exception e) {
                strategyExecutionService.addAlert(ExecutionAlert.Severity.ERROR,
                        String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                        String.format(AlertMessages.SCREENER_EXEC_FAILED_FMT, e.getMessage()));
                continue;
            }

            log.info("[{}] Found {} stocks matching criteria", screenerConfig.getName(), screenerResults.size());

            if (!screenerResults.isEmpty()) {
                log.info("[{}] Matching stocks: {}", screenerConfig.getName(),
                        screenerResults.stream().map(TechnicalScreener.ScreeningResult::getSymbol)
                                .toList());
                try {
                    telegramUtils.sendTechnicalScreenerAlert(screenerConfig.getName(), screenerResults);
                } catch (Exception e) {
                    strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                            String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                            AlertMessages.TELEGRAM_SEND_FAILED);
                }
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
                strategyExecutionService.addAlert(ExecutionAlert.Severity.WARNING,
                        String.format(AlertMessages.SRC_SCREENER_FMT, screenerConfig.getName()),
                        AlertMessages.SAVE_SCREENER_RESULT_FAILED);
            }
        }
    }
}
