package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class CustomExecutionRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private CustomExecutionRepository repository;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));

        repository = new CustomExecutionRepository(client);
    }

    @Test
    public void testSaveExecutionResult_Success() throws IOException {
        ExecutionResult result = ExecutionResult.builder()
                .executionId("exec-123")
                .timestamp(LocalDateTime.now())
                .results(Collections.singletonList(
                        StrategyResult.builder()
                                .strategyId("s1")
                                .strategyName("strat")
                                .tradesFound(1)
                                .trades(Collections.emptyList())
                                .executionTimeMs(100)
                                .build()
                ))
                .totalTradesFound(1)
                .totalExecutionTimeMs(500)
                .telegramSent(true)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveExecutionResult(result);

        verify(requestSpec).post(contains("strategy_executions"));
    }

    @Test
    public void testGetLatestExecutionResult_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"execution_id\":\"exec-123\"," +
                "\"executed_at\":\"2024-03-20T10:00:00\"," +
                "\"total_trades_found\":5," +
                "\"execution_time_ms\":1000," +
                "\"telegram_sent\":true," +
                "\"results\":[]" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        Optional<ExecutionResult> result = repository.getLatestExecutionResult();

        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.get().getExecutionId(), "exec-123");
    }

    @Test
    public void testSaveCustomExecutionResult_Success() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("custom-strat")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(50)
                .filterConfig("{\"key\":\"val\"}")
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveCustomExecutionResult(result, List.of("AAPL"));

        verify(requestSpec).post(contains("custom_execution_results"));
    }

    @Test
    public void testGetRecentCustomExecutions_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"id\":123," +
                "\"strategy_name\":\"custom\"," +
                "\"execution_time_ms\":50," +
                "\"trades_found\":0," +
                "\"trades\":[]," +
                "\"created_at\":\"2024-03-20T10:00:00Z\"," +
                "\"filter_config\":null" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<StrategyResult> results = repository.getRecentCustomExecutions(5);

        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getStrategyName(), "custom");
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetLatestExecutionResult_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("error");

        repository.getLatestExecutionResult();
    }

    @Test
    public void testGetLatestExecutionResult_Empty() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        Optional<ExecutionResult> result = repository.getLatestExecutionResult();
        Assert.assertFalse(result.isPresent());
    }

    @Test(expectedExceptions = IOException.class)
    public void testParseExecutionResult_MalformedJson() throws IOException {
        String malformedJson = "[{\"execution_id\":\"exec-123\", \"executed_at\":\"invalid-date\"}]";
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(malformedJson);

        repository.getLatestExecutionResult();
    }

    @Test
    public void testSaveCustomExecutionResult_NullSecurities() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("custom-strat")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(50)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveCustomExecutionResult(result, null);

        verify(requestSpec).post(anyString());
    }
}
