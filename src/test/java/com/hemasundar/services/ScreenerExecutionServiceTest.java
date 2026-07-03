package com.hemasundar.services;

import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.*;
import com.hemasundar.utils.SecuritiesResolver;
import com.hemasundar.utils.TelegramUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ScreenerExecutionServiceTest {

    @Mock
    private TechnicalScreener technicalScreener;

    @Mock
    private PriceDropScreener priceDropScreener;

    @InjectMocks
    private ScreenerExecutionService screenerExecutionService;

    @Mock
    private SupabaseService supabaseService;

    @Mock
    private com.hemasundar.utils.SecuritiesResolver securitiesResolver;

    @Mock
    private StrategyExecutionService strategyExecutionService;

    @Mock
    private com.hemasundar.apis.ThinkOrSwinAPIs thinkOrSwinAPIs;

    @Mock
    private com.hemasundar.utils.TelegramUtils telegramUtils;

    @Mock
    private com.hemasundar.config.StrategiesConfigLoader strategiesConfigLoader;

    @Mock
    private com.hemasundar.utils.SchwabApiExecutor schwabApiExecutor;

    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Explicit initialization to ensure all mocks are correctly injected into final fields
        screenerExecutionService = new ScreenerExecutionService(
                supabaseService,
                securitiesResolver,
                strategyExecutionService,
                thinkOrSwinAPIs,
                telegramUtils,
                technicalScreener,
                priceDropScreener,
                strategiesConfigLoader,
                schwabApiExecutor,
                volatilityCalculator
        );
        
        when(schwabApiExecutor.executeParallel(anyList(), any(), any())).thenAnswer(inv -> {
            List<String> symbols = inv.getArgument(0);
            java.util.function.Function<String, Object> func = inv.getArgument(1);
            List<Object> res = new ArrayList<>();
            for (String s : symbols) {
                res.add(func.apply(s));
            }
            return res;
        });
    }

    @AfterMethod
    public void tearDown() {
    }

    @Test
    public void testExecuteScreeners_PriceDrop() throws IOException {
        ScreenerConfig config = ScreenerConfig.builder()
                .alias("Price Drop Test")
                .screenerType(ScreenerType.PRICE_DROP)
                .securities(List.of("AAPL"))
                .filterChain(com.hemasundar.technical.TechnicalFilterChain.of(com.hemasundar.technical.TechnicalIndicators.builder().build(), com.hemasundar.technical.TechFilterConditions.builder().build()))
                .build();

        when(priceDropScreener.screenPriceDrop(anyList(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(TechnicalScreener.ScreeningResult.builder().symbol("AAPL").build()));

        screenerExecutionService.executeScreeners(Set.of(0), List.of(config));

        verify(supabaseService, times(1)).saveScreenerResult(any(ScreenerExecutionResult.class));
        verify(telegramUtils, times(1)).sendTechnicalScreenerAlert(anyString(), anyList());
    }

    @Test
    public void testExecuteScreeners_High52WDrop() throws IOException {
        ScreenerConfig config = ScreenerConfig.builder()
                .alias("52W High Drop Test")
                .screenerType(ScreenerType.HIGH_52W_DROP)
                .securities(List.of("TSLA"))
                .filterChain(com.hemasundar.technical.TechnicalFilterChain.of(com.hemasundar.technical.TechnicalIndicators.builder().build(), com.hemasundar.technical.TechFilterConditions.builder().build()))
                .build();

        when(priceDropScreener.screen52WeekHighDrop(anyList(), anyDouble(), any()))
                .thenReturn(List.of(TechnicalScreener.ScreeningResult.builder().symbol("TSLA").build()));

        screenerExecutionService.executeScreeners(Set.of(0), List.of(config));

        verify(supabaseService, times(1)).saveScreenerResult(any(ScreenerExecutionResult.class));
    }

    @Test
    public void testExecuteScreeners_EmptySelection() throws IOException {
        screenerExecutionService.executeScreeners(Collections.emptySet(), List.of(ScreenerConfig.builder().screenerType(ScreenerType.PRICE_DROP).build()));
        verify(supabaseService, never()).saveScreenerResult(any());
    }

    @Test
    public void testGetLatestScreenerResults() throws IOException {
        when(supabaseService.getAllLatestScreenerResults()).thenReturn(Collections.emptyList());
        List<ScreenerExecutionResult> results = screenerExecutionService.getLatestScreenerResults();
        assertNotNull(results);
        verify(supabaseService).getAllLatestScreenerResults();
    }

    @Test
    public void testExecuteCustomScreener_Success() throws IOException {
        ScreenerConfig config = ScreenerConfig.builder()
                .alias("Custom Screener Test")
                .screenerType(ScreenerType.PRICE_DROP)
                .securities(List.of("AAPL"))
                .filterChain(com.hemasundar.technical.TechnicalFilterChain.of(com.hemasundar.technical.TechnicalIndicators.builder().build(), com.hemasundar.technical.TechFilterConditions.builder().build()))
                .build();
        Map<String, Object> requestParams = Map.of("alias", "Custom Screener Test");

        when(priceDropScreener.screenPriceDrop(anyList(), anyDouble(), anyInt(), any()))
                .thenReturn(List.of(TechnicalScreener.ScreeningResult.builder().symbol("AAPL").build()));

        screenerExecutionService.executeCustomScreener(config, requestParams);

        verify(supabaseService, times(1)).saveCustomScreenerResult(any(ScreenerExecutionResult.class), anyList(), eq(requestParams));
    }
}
