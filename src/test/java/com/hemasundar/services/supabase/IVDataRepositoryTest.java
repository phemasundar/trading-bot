package com.hemasundar.services.supabase;

import com.hemasundar.pojos.IVDataPoint;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class IVDataRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private IVDataRepository repository;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));
        repository = new IVDataRepository(client);
    }

    @Test
    public void testUpsertIVData_Success() throws IOException {
        IVDataPoint dataPoint = createSampleDataPoint();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.upsertIVData(dataPoint);

        verify(requestSpec).post(contains("iv_data"));
    }

    @Test
    public void testUpsertIVData_RetryOn429() throws IOException {
        IVDataPoint dataPoint = createSampleDataPoint();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        
        // Return 429 twice, then 201
        when(response.getStatusCode()).thenReturn(429, 429, 201);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("{\"error\":\"rate_limited\"}");

        // Reduce wait time for testing by mocking Thread.sleep if possible? 
        // Or just let it run since it's only a few seconds.
        // waitTime = (long) Math.pow(2, 1) * 1000 = 2000ms
        // waitTime = (long) Math.pow(2, 2) * 1000 = 4000ms
        // Overall: 6 seconds.
        repository.upsertIVData(dataPoint);

        verify(requestSpec, times(3)).post(anyString());
    }

    @Test(expectedExceptions = IOException.class)
    public void testUpsertIVData_FailureAfterRetries() throws IOException {
        IVDataPoint dataPoint = createSampleDataPoint();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(400); // Non-429 error
        when(response.getStatusLine()).thenReturn("Bad Request");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("{\"error\":\"bad\"}");

        repository.upsertIVData(dataPoint);
    }

    @Test
    public void testGetIVRank_Success() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\",\"put_iv\":0.40,\"call_iv\":0.30}," +
                "{\"date\":\"2026-07-01\",\"put_iv\":0.20,\"call_iv\":0.10}," +
                "{\"date\":\"2026-06-30\",\"put_iv\":0.60,\"call_iv\":0.50}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivRank = repository.getIVRank("AAPL");

        assertNotNull(ivRank);
        assertEquals(ivRank, 50.0, 0.01);
    }

    @Test
    public void testGetIVRank_InsufficientData() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\",\"put_iv\":0.40,\"call_iv\":0.30}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivRank = repository.getIVRank("AAPL");

        assertNull(ivRank);
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetIVRank_QueryError() throws IOException {
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");

        repository.getIVRank("AAPL");
    }

    @Test(expectedExceptions = IOException.class)
    public void testGetIVRank_ParseError() throws IOException {
        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("invalid_json_data");

        repository.getIVRank("AAPL");
    }

    @Test
    public void testGetIVRank_MaxEqualsMin() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\",\"put_iv\":0.30,\"call_iv\":0.30}," +
                "{\"date\":\"2026-07-01\",\"put_iv\":0.30,\"call_iv\":0.30}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivRank = repository.getIVRank("AAPL");

        assertNotNull(ivRank);
        assertEquals(ivRank, 0.0, 0.01);
    }

    @Test
    public void testGetIVRank_NullPutAndCallIV() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\"}," +
                "{\"date\":\"2026-07-01\"}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivRank = repository.getIVRank("AAPL");

        assertNotNull(ivRank);
        assertEquals(ivRank, 0.0, 0.01);
    }

    @Test
    public void testGetIVStats_Success() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\",\"put_iv\":0.40,\"call_iv\":0.30}," +
                "{\"date\":\"2026-07-01\",\"put_iv\":0.20,\"call_iv\":0.10}," +
                "{\"date\":\"2026-06-30\",\"put_iv\":0.60,\"call_iv\":0.50}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        java.util.Map<String, Object> stats = repository.getIVStats("AAPL");

        assertNotNull(stats);
        assertEquals(stats.get("currentIV"), 0.35);
        assertEquals(stats.get("minIV"), 0.15);
        assertEquals(stats.get("maxIV"), 0.55);
        assertEquals(stats.get("recordCount"), 3);
    }

    @Test
    public void testGetIVStats_InsufficientData() throws IOException {
        String mockResponseString = "[{\"date\":\"2026-07-02\",\"put_iv\":0.40,\"call_iv\":0.30}]";

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        java.util.Map<String, Object> stats = repository.getIVStats("AAPL");

        assertNull(stats);
    }

    private IVDataPoint createSampleDataPoint() {
        IVDataPoint dataPoint = new IVDataPoint();
        dataPoint.setSymbol("AAPL");
        dataPoint.setCurrentDate(LocalDate.now());
        dataPoint.setStrike(150.0);
        dataPoint.setDte(30);
        dataPoint.setExpiryDate("2024-04-19");
        dataPoint.setAtmPutIV(0.25);
        dataPoint.setAtmCallIV(0.24);
        dataPoint.setUnderlyingPrice(152.0);
        return dataPoint;
    }
}
