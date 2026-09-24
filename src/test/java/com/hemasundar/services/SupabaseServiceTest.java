package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.services.supabase.*;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class SupabaseServiceTest {

    private SupabaseService supabaseService;

    @Mock
    private SupabaseClient client;
    @Mock
    private IVDataRepository ivDataRepository;
    @Mock
    private StrategyResultRepository strategyResultRepository;
    @Mock
    private ScreenerResultRepository screenerResultRepository;
    @Mock
    private CustomExecutionRepository customExecutionRepository;
    @Mock
    private CustomScreenerRepository customScreenerRepository;
    @Mock
    private SecurityIndicatorsRepository securityIndicatorsRepository;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        supabaseService = new SupabaseService(
                client,
                ivDataRepository,
                strategyResultRepository,
                screenerResultRepository,
                customExecutionRepository,
                customScreenerRepository,
                securityIndicatorsRepository
        );
    }

    @Test
    public void testTestConnection() throws IOException {
        when(client.testConnection()).thenReturn(true);
        Assert.assertTrue(supabaseService.testConnection());
        verify(client).testConnection();
    }

    @Test
    public void testUpsertIVData() throws IOException {
        com.hemasundar.pojos.IVDataPoint data = mock(com.hemasundar.pojos.IVDataPoint.class);
        supabaseService.upsertIVData(data);
        verify(ivDataRepository).upsertIVData(data);
    }

    @Test
    public void testSaveExecutionResult() throws IOException {
        ExecutionResult result = mock(ExecutionResult.class);
        supabaseService.saveExecutionResult(result);
        verify(customExecutionRepository).saveExecutionResult(result);
    }

    @Test
    public void testGetLatestExecutionResult() throws IOException {
        ExecutionResult result = mock(ExecutionResult.class);
        when(customExecutionRepository.getLatestExecutionResult()).thenReturn(Optional.of(result));
        
        Optional<ExecutionResult> latest = supabaseService.getLatestExecutionResult();
        Assert.assertTrue(latest.isPresent());
        verify(customExecutionRepository).getLatestExecutionResult();
    }

    @Test
    public void testSaveStrategyResult() throws IOException {
        com.hemasundar.dto.StrategyResult result = mock(com.hemasundar.dto.StrategyResult.class);
        supabaseService.saveStrategyResult(result);
        verify(strategyResultRepository).saveStrategyResult(result);
    }

    @Test
    public void testGetAllLatestStrategyResults() throws IOException {
        supabaseService.getAllLatestStrategyResults();
        verify(strategyResultRepository).getAllLatestStrategyResults();
    }

    @Test
    public void testSaveScreenerResult() throws IOException {
        com.hemasundar.dto.ScreenerExecutionResult result = mock(com.hemasundar.dto.ScreenerExecutionResult.class);
        supabaseService.saveScreenerResult(result);
        verify(screenerResultRepository).saveScreenerResult(result);
    }

    @Test
    public void testGetAllLatestScreenerResults() throws IOException {
        supabaseService.getAllLatestScreenerResults();
        verify(screenerResultRepository).getAllLatestScreenerResults();
    }

    @Test
    public void testSaveCustomExecutionResult() throws IOException {
        com.hemasundar.dto.StrategyResult result = mock(com.hemasundar.dto.StrategyResult.class);
        List<String> secs = List.of("AAPL");
        supabaseService.saveCustomExecutionResult(result, secs);
        verify(customExecutionRepository).saveCustomExecutionResult(result, secs);
    }

    @Test
    public void testGetRecentCustomExecutions() throws IOException {
        supabaseService.getRecentCustomExecutions(10);
        verify(customExecutionRepository).getRecentCustomExecutions(10);
    }

    @Test
    public void testSaveCustomScreenerResult() throws IOException {
        com.hemasundar.dto.ScreenerExecutionResult result = mock(com.hemasundar.dto.ScreenerExecutionResult.class);
        List<String> secs = List.of("AAPL");
        Map<String, Object> params = Map.of("type", "RSI");
        supabaseService.saveCustomScreenerResult(result, secs, params);
        verify(customScreenerRepository).saveCustomScreenerResult(result, secs, params);
    }

    @Test
    public void testGetRecentCustomScreenerExecutions() throws IOException {
        supabaseService.getRecentCustomScreenerExecutions(10);
        verify(customScreenerRepository).getRecentCustomScreenerExecutions(10);
    }

    @Test
    public void testDeleteCustomScreenerExecution() throws IOException {
        supabaseService.deleteCustomScreenerExecution("123");
        verify(customScreenerRepository).deleteCustomScreenerExecution("123");
    }

    @Test
    public void testSaveSecurityIndicators() throws IOException {
        ScreeningResult res = ScreeningResult.builder()
                .symbol("NVDA")
                .companyName("NVIDIA Corporation")
                .currentPrice(120.5)
                .build();
        List<ScreeningResult> results = List.of(res);
        when(ivDataRepository.getIVStatsForSymbols(any())).thenReturn(Map.of("NVDA", Map.of(
                "ivPercentile", 45.0,
                "ivRank", 35.5,
                "currentIV", 0.38,
                "recordCount", 120
        )));

        supabaseService.saveSecurityIndicators(results);
        Assert.assertEquals(res.getIvPercentile(), 45.0);
        Assert.assertEquals(res.getIvRank(), 35.5);
        Assert.assertEquals(res.getCurrentIV(), 0.38);
        Assert.assertEquals(res.getIvDays(), Integer.valueOf(120));
        verify(securityIndicatorsRepository).saveSecurityIndicators(results);
    }

    @Test
    public void testGetAllSecurityIndicators() throws IOException {
        supabaseService.getAllSecurityIndicators();
        verify(securityIndicatorsRepository).getAllSecurityIndicators();
    }

    @Test
    public void testGetSecurityIndicatorsForSymbols() throws IOException {
        List<String> symbols = List.of("NVDA");
        ScreeningResult res = ScreeningResult.builder()
                .symbol("NVDA")
                .companyName("NVIDIA Corporation")
                .currentPrice(120.5)
                .ivPercentile(50.0)
                .ivRank(40.0)
                .currentIV(0.35)
                .ivDays(150)
                .build();
        Map<String, ScreeningResult> repoResult = Map.of("NVDA", res);
        when(securityIndicatorsRepository.getSecurityIndicatorsForSymbols(symbols)).thenReturn(repoResult);

        Map<String, ScreeningResult> result = supabaseService.getSecurityIndicatorsForSymbols(symbols);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.get("NVDA").getIvPercentile(), 50.0);
        Assert.assertEquals(result.get("NVDA").getIvDays(), Integer.valueOf(150));
        verify(securityIndicatorsRepository).getSecurityIndicatorsForSymbols(symbols);
        verify(ivDataRepository, never()).getIVStatsForSymbols(any());
        verify(ivDataRepository, never()).getIVStats(any());
    }

    @Test
    public void testUpdateSecurityIndicatorsIV() throws IOException {
        List<String> symbols = List.of("NVDA");
        ScreeningResult res = ScreeningResult.builder()
                .symbol("NVDA")
                .companyName("NVIDIA Corporation")
                .currentPrice(120.5)
                .build();
        when(securityIndicatorsRepository.getSecurityIndicatorsForSymbols(symbols))
                .thenReturn(new java.util.HashMap<>(Map.of("NVDA", res)));
        when(ivDataRepository.getIVStatsForSymbols(any())).thenReturn(Map.of("NVDA", Map.of(
                "ivPercentile", 60.0,
                "ivRank", 55.0,
                "currentIV", 0.45,
                "recordCount", 200
        )));

        supabaseService.updateSecurityIndicatorsIV(symbols);
        Assert.assertEquals(res.getIvPercentile(), 60.0);
        Assert.assertEquals(res.getIvRank(), 55.0);
        Assert.assertEquals(res.getCurrentIV(), 0.45);
        Assert.assertEquals(res.getIvDays(), Integer.valueOf(200));
        verify(securityIndicatorsRepository).saveSecurityIndicators(anyList());
    }
        verify(securityIndicatorsRepository).saveSecurityIndicators(anyList());
    }
}

