package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.dto.StrategyResult;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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

    @Test(expectedExceptions = IOException.class)
    public void testSaveCustomExecutionResult_Error() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("custom-strat")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(50)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("error");

        repository.saveCustomExecutionResult(result, List.of("AAPL"));
    }

    @Test
    public void testUpdateCustomExecutionResult_Success() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("custom-strat")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(50)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.patch(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(204);

        repository.updateCustomExecutionResult(123L, result, List.of("AAPL"));

        verify(requestSpec).patch(contains("custom_execution_results?id=eq.123"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testUpdateCustomExecutionResult_Error() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyName("custom-strat")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(50)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.patch(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(404);
        when(response.getStatusLine()).thenReturn("Not Found");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("not found");

        repository.updateCustomExecutionResult(123L, result, List.of("AAPL"));
    }

    @Test
    public void testGetRecentCustomExecutions_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"id\":1," +
                "\"strategy_name\":\"custom-strat\"," +
                "\"executed_at\":\"2024-03-20T10:00:00\"," +
                "\"trades\":[]," +
                "\"trades_found\":0," +
                "\"execution_time_ms\":50," +
                "\"securities\":[\"AAPL\"]," +
                "\"filter_config\":\"{}\"" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<StrategyResult> results = repository.getRecentCustomExecutions(10);

        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getStrategyName(), "custom-strat");
    }

    @Test
    public void testDeleteCustomExecution_Success() throws IOException {
        when(requestSpec.delete(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(204);

        repository.deleteCustomExecution("123");

        verify(requestSpec).delete(contains("custom_execution_results?id=eq.123"));
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
