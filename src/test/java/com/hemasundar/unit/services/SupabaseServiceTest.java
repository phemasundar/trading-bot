package com.hemasundar.unit.services;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.SupabaseService;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SupabaseServiceTest {

    private SupabaseService supabaseService;
    private MockedStatic<RestAssured> mockedRestAssured;
    private RequestSpecification requestSpec;
    private Response response;

    private static final String PROJECT_URL = "https://test.supabase.co";
    private static final String API_KEY = "test-key";

    @BeforeMethod
    public void setUp() {
        supabaseService = new SupabaseService(PROJECT_URL, API_KEY);
        mockedRestAssured = Mockito.mockStatic(RestAssured.class);
        requestSpec = mock(RequestSpecification.class);
        response = mock(Response.class);

        mockedRestAssured.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.body(any(byte[].class))).thenReturn(requestSpec);
    }

    @AfterMethod
    public void tearDown() {
        mockedRestAssured.close();
    }

    @Test
    public void testTestConnectionSuccess() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        assertTrue(supabaseService.testConnection());
    }

    @Test
    public void testTestConnectionFailure() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");

        assertFalse(supabaseService.testConnection());
    }

    @Test
    public void testUpsertIVDataSuccess() throws IOException {
        IVDataPoint dataPoint = IVDataPoint.builder()
                .symbol("AAPL")
                .currentDate(LocalDate.now())
                .strike(150.0)
                .dte(30)
                .expiryDate("2024-12-20")
                .atmPutIV(0.25)
                .atmCallIV(0.24)
                .underlyingPrice(150.5)
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.upsertIVData(dataPoint);

        verify(requestSpec).post(contains("/rest/v1/iv_data"));
        verify(requestSpec).header("Prefer", "resolution=merge-duplicates");
    }

    @Test
    public void testUpsertIVDataRetryOn429() throws IOException {
        IVDataPoint dataPoint = IVDataPoint.builder()
                .symbol("MSFT")
                .currentDate(LocalDate.now())
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);

        // Mock consecutive status codes
        when(response.getStatusCode()).thenReturn(429, 201);

        supabaseService.upsertIVData(dataPoint);

        verify(requestSpec, times(2)).post(anyString());
    }

    @Test(expectedExceptions = IOException.class)
    public void testUpsertIVDataFailureAfterRetries() throws IOException {
        IVDataPoint dataPoint = IVDataPoint.builder()
                .symbol("GOOG")
                .currentDate(LocalDate.now())
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(429);
        when(response.getStatusLine()).thenReturn("Too Many Requests");

        io.restassured.response.ResponseBody body = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(body);
        when(body.asString()).thenReturn("Rate limit exceeded");

        supabaseService.upsertIVData(dataPoint);
    }

    @Test
    public void testSaveExecutionResult() throws IOException {
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-123")
                .timestamp(LocalDateTime.now())
                .results(Collections.singletonList(StrategyResult.builder().strategyId("strat-1").build()))
                .totalTradesFound(5)
                .totalExecutionTimeMs(1200)
                .telegramSent(true)
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.saveExecutionResult(result);

        verify(requestSpec).post(contains("/rest/v1/strategy_executions"));
    }

    @Test
    public void testGetLatestExecutionResultEmpty() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        io.restassured.response.ResponseBody body = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(body);
        when(body.asString()).thenReturn("[]");

        Optional<ExecutionResult> result = supabaseService.getLatestExecutionResult();
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetLatestExecutionResultSuccess() throws IOException {
        String json = "[{\n" +
                "  \"execution_id\": \"test-id\",\n" +
                "  \"executed_at\": \"2024-03-07T10:00:00\",\n" +
                "  \"total_trades_found\": 10,\n" +
                "  \"execution_time_ms\": 500,\n" +
                "  \"telegram_sent\": true,\n" +
                "  \"results\": []\n" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        io.restassured.response.ResponseBody body = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(body);
        when(body.asString()).thenReturn(json);

        Optional<ExecutionResult> result = supabaseService.getLatestExecutionResult();
        assertTrue(result.isPresent());
        assertEquals(result.get().getExecutionId(), "test-id");
    }

    @Test
    public void testSaveStrategyResult() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyId("strat-bull-put")
                .strategyName("Bull Put Spread")
                .executionTimeMs(500)
                .tradesFound(2)
                .trades(Collections.emptyList())
                .filterConfig("{}")
                .build();

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.saveStrategyResult(result);

        verify(requestSpec).post(contains("/rest/v1/latest_strategy_results"));
    }

    @Test
    public void testSaveCustomExecutionResult() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("Custom Strateg")
                .trades(Collections.emptyList())
                .build();
        List<String> securities = Arrays.asList("AAPL", "TSLA");

        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        supabaseService.saveCustomExecutionResult(result, securities);

        verify(requestSpec).post(contains("/rest/v1/custom_execution_results"));
    }

    @Test
    public void testGetRecentCustomExecutions() throws IOException {
        String json = "[{\n" +
                "  \"id\": 1,\n" +
                "  \"strategy_name\": \"Test\",\n" +
                "  \"execution_time_ms\": 100,\n" +
                "  \"trades_found\": 0,\n" +
                "  \"trades\": [],\n" +
                "  \"created_at\": \"2024-03-07T12:00:00Z\",\n" +
                "  \"filter_config\": {\"minDelta\": 0.3}\n" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        io.restassured.response.ResponseBody body = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(body);
        when(body.asString()).thenReturn(json);

        List<StrategyResult> results = supabaseService.getRecentCustomExecutions(10);
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getStrategyName(), "Test");
        assertNotNull(results.get(0).getFilterConfig());
    }

    @Test
    public void testGetAllLatestStrategyResults() throws IOException {
        String json = "[{\n" +
                "  \"strategy_id\": \"strat-1\",\n" +
                "  \"strategy_name\": \"Strat One\",\n" +
                "  \"execution_time_ms\": 200,\n" +
                "  \"trades_found\": 1,\n" +
                "  \"trades\": [],\n" +
                "  \"updated_at\": \"2024-03-07T12:00:00Z\",\n" +
                "  \"filter_config\": null\n" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        io.restassured.response.ResponseBody body = mock(io.restassured.response.ResponseBody.class);
        when(response.getBody()).thenReturn(body);
        when(body.asString()).thenReturn(json);

        List<StrategyResult> results = supabaseService.getAllLatestStrategyResults();
        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getStrategyId(), "strat-1");
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetAllLatestStrategyResultsFailure() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);

        supabaseService.getAllLatestStrategyResults();
    }
}
