package com.hemasundar.jobs;

import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.ScreenerExecutionService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Log4j2
public class ScreenerJobService {

    @Autowired
    private StrategyExecutionService strategyExecutionService;

    @Autowired
    private ScreenerExecutionService screenerExecutionService;

    public void runScheduledScreeners() {
        log.info("Starting scheduled Options Strategies and Technical Screeners execution...");
        try {
            // 1. Run Options Strategies
            List<OptionsConfig> enabledStrategies = strategyExecutionService.getEnabledStrategies();
            if (!enabledStrategies.isEmpty()) {
                Set<Integer> strategyIndices = IntStream.range(0, enabledStrategies.size()).boxed().collect(Collectors.toSet());
                strategyExecutionService.executeStrategies(strategyIndices);
                log.info("Successfully executed {} Options Strategies", enabledStrategies.size());
            } else {
                log.info("No Options Strategies enabled.");
            }

            // 2. Run Technical Screeners
            List<ScreenerConfig> enabledScreeners = screenerExecutionService.getEnabledScreeners();
            if (!enabledScreeners.isEmpty()) {
                Set<Integer> screenerIndices = IntStream.range(0, enabledScreeners.size()).boxed().collect(Collectors.toSet());
                screenerExecutionService.executeScreeners(screenerIndices, enabledScreeners);
                log.info("Successfully executed {} Technical Screeners", enabledScreeners.size());
            } else {
                log.info("No Technical Screeners enabled.");
            }

        } catch (Exception e) {
            log.error("Failed to run scheduled screeners: {}", e.getMessage(), e);
        }
    }
}
