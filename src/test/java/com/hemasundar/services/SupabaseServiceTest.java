package com.hemasundar.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.pojos.IVDataPoint;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class SupabaseServiceTest {

    private SupabaseService supabaseService;
    private final String projectUrl = "https://test.supabase.co";
    private final String apiKey = "test-api-key";

    private MockedStatic<RestAssured> mockedRestAssured;
    private RequestSpecification requestSpec;
    private Response response;

    @BeforeMethod
    public void setup() {
        supabaseService = new SupabaseService(projectUrl, apiKey);
        mockedRestAssured = mockStatic(RestAssured.class);
        requestSpec = mock(RequestSpecification.class);
        response = mock(Response.class);

        mockedRestAssured.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
    }

    @AfterMethod
    public void tearDown() {
        if (mockedRestAssured != null) {
            mockedRestAssured.close();
        }
    }

    @Test
    public void testConstructor_Valid() {
        new SupabaseService("https://valid.url", "key");
        new SupabaseService("https://valid.url/", "key");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructor_InvalidUrl() {
        new SupabaseService("", "key");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructor_InvalidKey() {
        new SupabaseService("url", "");
    }

    @Test
    public void testTestConnection_Success() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        boolean result = supabaseService.testConnection();
        Assert.assertTrue(result);
    }

    @Test
    public void testTestConnection_Failure() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(401);

        boolean result = supabaseService.testConnection();
        Assert.assertFalse(result);
    }

    @Test(expectedExceptions = IOException.class)
    public void testTestConnection_Exception() throws IOException {
        when(requestSpec.get(anyString())).thenThrow(new RuntimeException("Network Error"));
        supabaseService.testConnection();
    }

    @Test
    public void testUpsertIVData_Success() throws IOException {
        IVDataPoint data = createSampleIVData();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.upsertIVData(data);
        verify(requestSpec, times(1)).post(anyString());
    }

    @Test
    public void testUpsertIVData_RetrySuccess() throws IOException {
        IVDataPoint data = createSampleIVData();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(429, 201); // Rate limit then success

        supabaseService.upsertIVData(data);
        verify(requestSpec, times(2)).post(anyString());
    }

    @Test(expectedExceptions = IOException.class)
    public void testUpsertIVData_FailureAfterRetries() throws IOException {
        IVDataPoint data = createSampleIVData();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(429); // Always rate limit

        supabaseService.upsertIVData(data);
    }

    @Test
    public void testSaveExecutionResult_Success() throws IOException {
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-123")
                .timestamp(LocalDateTime.now())
                .results(Collections.singletonList(createSampleStrategyResult()))
                .totalTradesFound(5)
                .totalExecutionTimeMs(1000)
                .telegramSent(true)
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.saveExecutionResult(result);
        verify(requestSpec).post(anyString());
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveExecutionResult_Error() throws IOException {
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-123")
                .results(Collections.emptyList())
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));

        supabaseService.saveExecutionResult(result);
    }

    @Test
    public void testGetLatestExecutionResult_Success() throws IOException {
        String jsonResponse = "[{\"execution_id\":\"exec-123\",\"executed_at\":\"2024-03-20T10:00:00\",\"total_trades_found\":5,\"execution_time_ms\":1000,\"telegram_sent\":true,\"results\":[]}]";
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn(jsonResponse);

        Optional<ExecutionResult> result = supabaseService.getLatestExecutionResult();
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getExecutionId(), "exec-123");
    }

    @Test
    public void testGetLatestExecutionResult_Empty() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("[]");

        Optional<ExecutionResult> result = supabaseService.getLatestExecutionResult();
        Assert.assertFalse(result.isPresent());
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetLatestExecutionResult_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("Error");

        supabaseService.getLatestExecutionResult();
    }

    @Test
    public void testSaveStrategyResult_Success() throws IOException {
        StrategyResult result = createSampleStrategyResult();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        supabaseService.saveStrategyResult(result);
        verify(requestSpec).post(contains("latest_strategy_results"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveStrategyResult_Error() throws IOException {
        StrategyResult result = createSampleStrategyResult();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(401);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("Unauthorized");

        supabaseService.saveStrategyResult(result);
    }

    @Test
    public void testGetAllLatestStrategyResults() throws IOException {
        String jsonResponse = "[{\"strategy_id\":\"strat-1\",\"strategy_name\":\"Test Strategy\",\"execution_time_ms\":100,\"trades_found\":1,\"trades\":[],\"updated_at\":\"2024-03-20T10:00:00Z\"}]";
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn(jsonResponse);

        List<StrategyResult> results = supabaseService.getAllLatestStrategyResults();
        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getStrategyId(), "strat-1");
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetAllLatestStrategyResults_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(403);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("Forbidden");

        supabaseService.getAllLatestStrategyResults();
    }

    @Test
    public void testSaveScreenerResult_Success() throws IOException {
        com.hemasundar.dto.ScreenerExecutionResult result = com.hemasundar.dto.ScreenerExecutionResult.builder()
                .screenerId("screener-1")
                .screenerName("Test Screener")
                .results(Collections.emptyList())
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        supabaseService.saveScreenerResult(result);
        verify(requestSpec).post(contains("latest_screener_results"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveScreenerResult_Error() throws IOException {
        com.hemasundar.dto.ScreenerExecutionResult result = com.hemasundar.dto.ScreenerExecutionResult.builder()
                .screenerId("screener-1")
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(400);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("Bad Request");

        supabaseService.saveScreenerResult(result);
    }

    @Test
    public void testGetAllLatestScreenerResults() throws IOException {
        String jsonResponse = "[]";
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn(jsonResponse);

        List<com.hemasundar.dto.ScreenerExecutionResult> results = supabaseService.getAllLatestScreenerResults();
        Assert.assertTrue(results.isEmpty());
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetAllLatestScreenerResults_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);

        supabaseService.getAllLatestScreenerResults();
    }

    @Test
    public void testSaveCustomExecutionResult() throws IOException {
        StrategyResult result = createSampleStrategyResult();
        List<String> securities = Arrays.asList("AAPL", "MSFT");

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.saveCustomExecutionResult(result, securities);
        verify(requestSpec).post(contains("custom_execution_results"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveCustomExecutionResult_Error() throws IOException {
        StrategyResult result = createSampleStrategyResult();
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(401);

        supabaseService.saveCustomExecutionResult(result, Collections.emptyList());
    }

    @Test
    public void testGetRecentCustomExecutions() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        io.restassured.response.ResponseBody<?> responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn("[]");

        List<StrategyResult> results = supabaseService.getRecentCustomExecutions(5);
        Assert.assertTrue(results.isEmpty());
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetRecentCustomExecutions_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(404);

        supabaseService.getRecentCustomExecutions(5);
    }

    private IVDataPoint createSampleIVData() {
        return IVDataPoint.builder()
                .symbol("AAPL")
                .currentDate(LocalDate.now())
                .strike(150.0)
                .dte(30)
                .expiryDate("2024-12-20")
                .atmPutIV(0.25)
                .atmCallIV(0.24)
                .underlyingPrice(152.0)
                .build();
    }

    private StrategyResult createSampleStrategyResult() {
        return StrategyResult.builder()
                .strategyId("strat-1")
                .strategyName("Test Strategy")
                .tradesFound(1)
                .trades(Collections.emptyList())
                .executionTimeMs(100)
                .build();
    }
}
