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

    @InjectMocks
    private ScreenerExecutionService screenerExecutionService;

    @Mock
    private SupabaseService supabaseService;

    @Mock
    private SecuritiesResolver securitiesResolver;

    @Mock
    private StrategyExecutionService strategyExecutionService;

    private MockedStatic<PriceDropScreener> mockedPriceDrop;
    private MockedStatic<TechnicalScreener> mockedTechScreener;
    private MockedStatic<TelegramUtils> mockedTelegram;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedPriceDrop = mockStatic(PriceDropScreener.class);
        mockedTechScreener = mockStatic(TechnicalScreener.class);
        mockedTelegram = mockStatic(TelegramUtils.class);
    }

    @AfterMethod
    public void tearDown() {
        mockedPriceDrop.close();
        mockedTechScreener.close();
        mockedTelegram.close();
    }

    @Test
    public void testExecuteScreeners_PriceDrop() throws IOException {
        ScreenerConfig config = ScreenerConfig.builder()
                .alias("Price Drop Test")
                .screenerType(ScreenerType.PRICE_DROP)
                .securities(List.of("AAPL"))
                .conditions(TechFilterConditions.builder().build())
                .build();

        mockedPriceDrop.when(() -> PriceDropScreener.screenPriceDrop(anyList(), anyDouble(), anyInt()))
                .thenReturn(List.of(TechnicalScreener.ScreeningResult.builder().symbol("AAPL").build()));

        screenerExecutionService.executeScreeners(Set.of(0), List.of(config));

        verify(supabaseService, times(1)).saveScreenerResult(any(ScreenerExecutionResult.class));
        mockedTelegram.verify(() -> TelegramUtils.sendTechnicalScreenerAlert(anyString(), anyList()), times(1));
    }

    @Test
    public void testExecuteScreeners_High52WDrop() throws IOException {
        ScreenerConfig config = ScreenerConfig.builder()
                .alias("52W High Drop Test")
                .screenerType(ScreenerType.HIGH_52W_DROP)
                .securities(List.of("TSLA"))
                .conditions(TechFilterConditions.builder().build())
                .build();

        mockedPriceDrop.when(() -> PriceDropScreener.screen52WeekHighDrop(anyList(), anyDouble()))
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
}
