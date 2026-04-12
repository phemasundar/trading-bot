package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.services.supabase.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        supabaseService = new SupabaseService(
                client,
                ivDataRepository,
                strategyResultRepository,
                screenerResultRepository,
                customExecutionRepository
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
}
