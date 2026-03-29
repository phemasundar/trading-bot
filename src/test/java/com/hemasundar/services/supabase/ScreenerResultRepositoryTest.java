package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.dto.ScreenerExecutionResult;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
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

public class ScreenerResultRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private ScreenerResultRepository repository;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));
        
        repository = new ScreenerResultRepository(client);
    }

    @Test
    public void testSaveScreenerResult_Success() throws IOException {
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerId("test-screener")
                .screenerName("Test")
                .results(Collections.emptyList())
                .resultsFound(0)
                .executionTimeMs(100)
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveScreenerResult(result);

        verify(requestSpec).post(contains("latest_screener_results"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveScreenerResult_Error() throws IOException {
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerId("test-screener")
                .results(Collections.emptyList())
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(400);
        when(response.getStatusLine()).thenReturn("Bad Request");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("{\"error\":\"bad\"}");

        repository.saveScreenerResult(result);
    }

    @Test
    public void testGetAllLatestScreenerResults_Success() throws IOException {
        String jsonResponse = "[{" +
                "\"screener_id\":\"s1\"," +
                "\"screener_name\":\"n1\"," +
                "\"execution_time_ms\":100," +
                "\"results_found\":1," +
                "\"results\":[{\"symbol\":\"AAPL\"}]," +
                "\"updated_at\":\"2024-03-20T10:00:00Z\"" +
                "}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<ScreenerExecutionResult> results = repository.getAllLatestScreenerResults();

        Assert.assertEquals(results.size(), 1);
        Assert.assertEquals(results.get(0).getScreenerId(), "s1");
        Assert.assertEquals(results.get(0).getResults().get(0).getSymbol(), "AAPL");
    }

    @Test
    public void testGetAllLatestScreenerResults_Empty() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        List<ScreenerExecutionResult> results = repository.getAllLatestScreenerResults();
        Assert.assertTrue(results.isEmpty());
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetAllLatestScreenerResults_ParseError() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[{\"invalid\":\"json\"}]");

        repository.getAllLatestScreenerResults();
    }
}
