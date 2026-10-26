package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.pojos.IVDataPoint;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));
        repository = new IVDataRepository(client);
        repository.setMinIVRecords(20);
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
    public void testUpsertIVDataBatch_Success() throws IOException {
        IVDataPoint dp1 = createSampleDataPoint();
        IVDataPoint dp2 = createSampleDataPoint();
        dp2.setSymbol("MSFT");

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.upsertIVDataBatch(List.of(dp1, dp2));

        verify(requestSpec).post(contains("iv_data"));
    }

    @Test
    public void testUpsertIVData_RetryOn429() throws IOException {
        IVDataPoint dataPoint = createSampleDataPoint();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);

        // Return 429 once, then 201
        when(response.getStatusCode()).thenReturn(429, 201);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("{\"error\":\"rate_limited\"}");

        repository.upsertIVData(dataPoint);

        verify(requestSpec, times(2)).post(anyString());
    }

    @Test(expectedExceptions = IOException.class)
    public void testUpsertIVData_FailureAfterRetries() throws IOException {
        IVDataPoint dataPoint = createSampleDataPoint();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(400);
        when(response.getStatusLine()).thenReturn("Bad Request");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("{\"error\":\"bad\"}");

        repository.upsertIVData(dataPoint);
    }

    @Test
    public void testGetIVRank_Success() throws IOException {
        String mockRpcResponse = "[{" +
                "\"symbol\":\"AAPL\"," +
                "\"current_iv\":0.35," +
                "\"min_iv\":0.15," +
                "\"max_iv\":0.55," +
                "\"iv_percentile\":50.0," +
                "\"iv_rank\":50.0," +
                "\"record_count\":21" +
                "}]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        Double ivRank = repository.getIVRank("AAPL");

        assertNotNull(ivRank);
        assertEquals(ivRank, 50.0, 0.01);
        verify(requestSpec).post(contains("rpc/get_iv_stats_bulk"));
    }

    @Test
    public void testGetIVRank_InsufficientData() throws IOException {
        String mockRpcResponse = "[]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        Double ivRank = repository.getIVRank("AAPL");

        assertNull(ivRank);
    }

    @Test
    public void testGetIVPercentile_Success() throws IOException {
        String mockRpcResponse = "[{" +
                "\"symbol\":\"AAPL\"," +
                "\"current_iv\":0.35," +
                "\"min_iv\":0.15," +
                "\"max_iv\":0.55," +
                "\"iv_percentile\":25.0," +
                "\"iv_rank\":50.0," +
                "\"record_count\":21" +
                "}]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        Double ivPercentile = repository.getIVPercentile("AAPL");

        assertNotNull(ivPercentile);
        assertEquals(ivPercentile, 25.0, 0.01);
    }

    @Test
    public void testGetIVStats_SuccessAndCaching() throws IOException {
        String mockRpcResponse = "[{" +
                "\"symbol\":\"AAPL\"," +
                "\"current_iv\":0.35," +
                "\"min_iv\":0.15," +
                "\"max_iv\":0.55," +
                "\"iv_percentile\":50.0," +
                "\"iv_rank\":50.0," +
                "\"record_count\":21" +
                "}]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        Map<String, Object> stats = repository.getIVStats("AAPL");

        assertNotNull(stats);
        assertEquals(stats.get("currentIV"), 0.35);
        assertEquals(stats.get("minIV"), 0.15);
        assertEquals(stats.get("maxIV"), 0.55);
        assertEquals(stats.get("ivRank"), 50.0);
        assertEquals(stats.get("ivPercentile"), 50.0);
        assertEquals(stats.get("recordCount"), 21);

        // Second call should hit the cache without calling client again
        Map<String, Object> cachedStats = repository.getIVStats("AAPL");
        assertEquals(cachedStats, stats);
        verify(requestSpec, times(1)).post(anyString());
    }

    @Test
    public void testGetIVStatsForSymbols_Success() {
        String mockRpcResponse = "[{" +
                "\"symbol\":\"AAPL\"," +
                "\"current_iv\":0.35," +
                "\"min_iv\":0.15," +
                "\"max_iv\":0.55," +
                "\"iv_percentile\":50.0," +
                "\"iv_rank\":50.0," +
                "\"record_count\":21" +
                "},{" +
                "\"symbol\":\"MSFT\"," +
                "\"current_iv\":0.28," +
                "\"min_iv\":0.18," +
                "\"max_iv\":0.42," +
                "\"iv_percentile\":65.0," +
                "\"iv_rank\":41.6," +
                "\"record_count\":35" +
                "}]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        Map<String, Map<String, Object>> map = repository.getIVStatsForSymbols(List.of("AAPL", "MSFT"));
        assertNotNull(map);
        assertTrue(map.containsKey("AAPL"));
        assertTrue(map.containsKey("MSFT"));
        assertEquals(map.get("AAPL").get("currentIV"), 0.35);
        assertEquals(map.get("MSFT").get("currentIV"), 0.28);
    }

    @Test
    public void testClearIVStatsCache() throws IOException {
        String mockRpcResponse = "[{" +
                "\"symbol\":\"AAPL\"," +
                "\"current_iv\":0.35," +
                "\"min_iv\":0.15," +
                "\"max_iv\":0.55," +
                "\"iv_percentile\":50.0," +
                "\"iv_rank\":50.0," +
                "\"record_count\":21" +
                "}]";

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockRpcResponse);

        repository.getIVStats("AAPL");
        repository.clearIVStatsCache();
        repository.getIVStats("AAPL");

        // With cache cleared, client post is called twice
        verify(requestSpec, times(2)).post(anyString());
    }

    @Test
    public void testGetIVStats_NegativeCaching() throws IOException {
        // Mock empty RPC response ([]), meaning ticker has < 20 records
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        Map<String, Object> stats = repository.getIVStats("UNKNOWN");
        assertNotNull(stats);
        assertTrue(stats.isEmpty());

        // Second call should hit the negative cache without sending another RPC request
        Map<String, Object> cachedStats = repository.getIVStats("UNKNOWN");
        assertNotNull(cachedStats);
        assertTrue(cachedStats.isEmpty());
        verify(requestSpec, times(1)).post(anyString());
    }

    @Test
    public void testGetIVStats_BlankSymbol() throws IOException {
        Map<String, Object> stats = repository.getIVStats("  ");
        assertNotNull(stats);
        assertTrue(stats.isEmpty());
        verify(requestSpec, never()).post(anyString());
    }

    @Test
    public void testFetchAndCacheBulkIVStats_IncludesMinRecordsParameter() throws IOException {
        repository.setMinIVRecords(15);
        assertEquals(repository.getMinIVRecords(), 15);

        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        repository.getIVStats("TEST_MIN");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requestSpec).body(captor.capture());
        assertTrue(captor.getValue().contains("\"p_min_records\":15"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testGetIVRank_ThrowsWhenMinRecordsNotConfigured() throws Exception {
        repository.setMinIVRecords(0);
        repository.getIVRank("AAPL");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testGetIVStats_ThrowsWhenMinRecordsNotConfigured() throws Exception {
        repository.setMinIVRecords(0);
        repository.getIVStats("AAPL");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testGetIVStatsBulk_ThrowsWhenMinRecordsNotConfigured() {
        repository.setMinIVRecords(0);
        repository.getIVStatsForSymbols(List.of("AAPL"));
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
