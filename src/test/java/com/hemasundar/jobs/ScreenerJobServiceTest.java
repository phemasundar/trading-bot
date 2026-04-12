package com.hemasundar.jobs;

import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.services.ScreenerExecutionService;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.technical.ScreenerConfig;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScreenerJobServiceTest {

    @Mock
    private StrategyExecutionService strategyExecutionService;

    @Mock
    private ScreenerExecutionService screenerExecutionService;

    private ScreenerJobService screenerJobService;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        screenerJobService = new ScreenerJobService(strategyExecutionService, screenerExecutionService);
    }

    @Test
    public void testRunScheduledScreeners_Success() throws Exception {
        // Mock strategies
        OptionsConfig strategy = OptionsConfig.builder().build();
        when(strategyExecutionService.getEnabledStrategies()).thenReturn(List.of(strategy));

        // Mock screeners
        ScreenerConfig screener = ScreenerConfig.builder().build();
        when(screenerExecutionService.getEnabledScreeners()).thenReturn(List.of(screener));

        screenerJobService.runScheduledScreeners();

        verify(strategyExecutionService).executeStrategies(anySet());
        verify(screenerExecutionService).executeScreeners(anySet(), anyList());
    }

    @Test
    public void testRunScheduledScreeners_NoneEnabled() throws Exception {
        when(strategyExecutionService.getEnabledStrategies()).thenReturn(Collections.emptyList());
        when(screenerExecutionService.getEnabledScreeners()).thenReturn(Collections.emptyList());

        screenerJobService.runScheduledScreeners();

        verify(strategyExecutionService, never()).executeStrategies(anySet());
        verify(screenerExecutionService, never()).executeScreeners(anySet(), anyList());
    }

    @Test
    public void testRunScheduledScreeners_ExceptionHandling() throws Exception {
        when(strategyExecutionService.getEnabledStrategies()).thenThrow(new RuntimeException("Test exception"));

        // Should not throw exception out, just log it
        screenerJobService.runScheduledScreeners();

        verify(screenerExecutionService, never()).getEnabledScreeners();
    }
}
