package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.TechnicalScreener;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class CustomScreenerRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private CustomScreenerRepository repository;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));

        repository = new CustomScreenerRepository(client);
    }

    @Test
    public void testSaveCustomScreenerResult_Success() throws IOException {
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerId("1")
                .screenerName("RSI Oversold")
                .executionTimeMs(150)
                .resultsFound(1)
                .results(Collections.singletonList(
                        TechnicalScreener.ScreeningResult.builder()
                                .symbol("AAPL")
                                .currentPrice(150.0)
                                .rsi(25.0)
                                .build()
                ))
                .build();

        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("screenerType", "RSI_OVERSOLD");
        requestParams.put("minVolume", 500000L);

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveCustomScreenerResult(result, List.of("AAPL"), requestParams);

        verify(requestSpec).post(contains("custom_screener_results"));
    }

    @Test
    public void testSaveCustomScreenerResult_NullSecuritiesAndParams() throws IOException {
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerId("2")
                .screenerName("BB Lower")
                .executionTimeMs(200)
                .resultsFound(0)
                .results(Collections.emptyList())
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        repository.saveCustomScreenerResult(result, null, null);

        verify(requestSpec).post(contains("custom_screener_results"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveCustomScreenerResult_Failure() throws IOException {
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerName("Price Drop")
                .results(Collections.emptyList())
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("error details");

        repository.saveCustomScreenerResult(result, Collections.emptyList(), null);
    }

    @Test
    public void testGetRecentCustomScreenerExecutions_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"id\":101," +
                "\"screener_name\":\"RSI Oversold\"," +
                "\"execution_time_ms\":120," +
                "\"results_found\":1," +
                "\"created_at\":\"2026-05-31T22:00:00Z\"," +
                "\"results\":[{\"symbol\":\"AAPL\",\"currentPrice\":150.0}]," +
                "\"request_params\":{\"screenerType\":\"RSI_OVERSOLD\"}" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<ScreenerExecutionResult> results = repository.getRecentCustomScreenerExecutions(5);

        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getScreenerId(), "101");
        Assert.assertEquals(results.get(0).getScreenerName(), "RSI Oversold");
        Assert.assertEquals(results.get(0).getResultsFound(), 1);
        Assert.assertNotNull(results.get(0).getRequestParams());
        Assert.assertEquals(results.get(0).getRequestParams().get("screenerType"), "RSI_OVERSOLD");
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetRecentCustomScreenerExecutions_Failure() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(400);

        repository.getRecentCustomScreenerExecutions(5);
    }

    @Test
    public void testDeleteCustomScreenerExecution_Success() throws IOException {
        when(requestSpec.delete(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(204);

        repository.deleteCustomScreenerExecution("101");

        verify(requestSpec).delete(contains("custom_screener_results?id=eq.101"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testDeleteCustomScreenerExecution_Failure() throws IOException {
        when(requestSpec.delete(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(404);
        when(response.getStatusLine()).thenReturn("Not Found");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("does not exist");

        repository.deleteCustomScreenerExecution("101");
    }
}
