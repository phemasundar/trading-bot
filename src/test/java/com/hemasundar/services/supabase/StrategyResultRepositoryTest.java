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

public class StrategyResultRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private StrategyResultRepository repository;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));
        
        repository = new StrategyResultRepository(client);
    }

    @Test
    public void testSaveStrategyResult_Success() throws IOException {
        StrategyResult result = StrategyResult.builder()
                .strategyId("test-strategy")
                .strategyName("Test")
                .trades(Collections.emptyList())
                .tradesFound(0)
                .executionTimeMs(150)
                .filterConfig("{\"minDTE\":30}")
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveStrategyResult(result);

        verify(requestSpec).post(contains("latest_strategy_results"));
    }

    @Test
    public void testGetAllLatestStrategyResults_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"strategy_id\":\"st1\"," +
                "\"strategy_name\":\"name1\"," +
                "\"execution_time_ms\":200," +
                "\"trades_found\":2," +
                "\"trades\":[{\"ticker\":\"AAPL\"}]," +
                "\"updated_at\":\"2024-03-20T11:00:00Z\"," +
                "\"filter_config\":{\"targetDTE\":45}" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<StrategyResult> results = repository.getAllLatestStrategyResults();

        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getStrategyId(), "st1");
        Assert.assertNotNull(results.get(0).getFilterConfig());
    }

    @Test
    public void testGetAllLatestStrategyResults_Empty() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        List<StrategyResult> results = repository.getAllLatestStrategyResults();
        Assert.assertTrue(results.isEmpty());
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetAllLatestStrategyResults_Error() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("error");

        repository.getAllLatestStrategyResults();
    }
}
