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
        // 21 records: row 0 (today) avg=0.35, row 1 avg=0.15 (min), row 2 avg=0.55 (max), rows 3-20 avg=0.35
        // Historical baseline: 20 rows (indices 1-20). IV Rank = (0.35 - 0.15) / (0.55 - 0.15) * 100 = 50.0%
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.40,\"call_iv\":0.30},");
        sb.append("{\"date\":\"2026-07-20\",\"put_iv\":0.20,\"call_iv\":0.10},");
        sb.append("{\"date\":\"2026-07-19\",\"put_iv\":0.60,\"call_iv\":0.50}");
        for (int i = 3; i < 21; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.35,\"call_iv\":0.35}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

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
        // 20 rows total → only 19 historical rows after excluding today → insufficient
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"date\":\"2026-07-%02d\",\"put_iv\":0.40,\"call_iv\":0.30}", i + 1));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

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
        // 21 records all identical → historical baseline has 20 identical rows
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 21; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"date\":\"2026-07-%02d\",\"put_iv\":0.30,\"call_iv\":0.30}", i + 1));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

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
        // 21 records with null put/call IV
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 21; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"date\":\"2026-07-%02d\"}", i + 1));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

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
        // 21 records: row 0 (today) avg=0.35, row 1 avg=0.15 (min), row 2 avg=0.55 (max), rows 3-20 avg=0.35
        // Historical baseline: 20 rows. IV Rank = 50%, recordCount = 20
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.40,\"call_iv\":0.30},");
        sb.append("{\"date\":\"2026-07-20\",\"put_iv\":0.20,\"call_iv\":0.10},");
        sb.append("{\"date\":\"2026-07-19\",\"put_iv\":0.60,\"call_iv\":0.50}");
        for (int i = 3; i < 21; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.35,\"call_iv\":0.35}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

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
        assertEquals(stats.get("ivRank"), 50.0);
        assertEquals(stats.get("recordCount"), 20);

        // Second call should hit the cache without calling client again
        java.util.Map<String, Object> cachedStats = repository.getIVStats("AAPL");
        assertEquals(cachedStats, stats);
        verify(requestSpec, times(1)).get(anyString());
    }

    @Test
    public void testGetIVStats_InsufficientData() throws IOException {
        // 20 rows total → 19 historical rows after excluding today → insufficient
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"date\":\"2026-07-%02d\",\"put_iv\":0.40,\"call_iv\":0.30}", i + 1));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        java.util.Map<String, Object> stats = repository.getIVStats("AAPL");

        assertNull(stats);
    }

    @Test
    public void testGetIVPercentile_Success() throws IOException {
        // 21 records: row 0 is current (avg 0.35). Historical: 20 rows.
        // 5 historical records below 0.35 (avg 0.20), 15 historical at or above 0.35.
        // Percentile = 5 / 20 * 100 = 25.0%
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.35,\"call_iv\":0.35}");
        for (int i = 1; i <= 5; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.20,\"call_iv\":0.20}", 21 - i));
        }
        for (int i = 6; i <= 20; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.50,\"call_iv\":0.50}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivPercentile = repository.getIVPercentile("AAPL");

        assertNotNull(ivPercentile);
        assertEquals(ivPercentile, 25.0, 0.01);
    }

    @Test
    public void testGetIVPercentile_InsufficientData() throws IOException {
        // 20 rows total → 19 historical rows after excluding today → insufficient
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "{\"date\":\"2026-07-%02d\",\"put_iv\":0.40,\"call_iv\":0.30}", i + 1));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivPercentile = repository.getIVPercentile("AAPL");

        assertNull(ivPercentile);
    }

    @Test
    public void testGetIVPercentile_CurrentHighest_Returns100() throws IOException {
        // 21 records: row 0 (today) avg=0.60, all 20 historical rows avg=0.30 (all below)
        // Percentile = 20 / 20 * 100 = 100.0%
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.60,\"call_iv\":0.60}");
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.30,\"call_iv\":0.30}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivPercentile = repository.getIVPercentile("AAPL");

        assertNotNull(ivPercentile);
        assertEquals(ivPercentile, 100.0, 0.01);
    }

    @Test
    public void testGetIVPercentile_CurrentLowest_Returns0() throws IOException {
        // 21 records: row 0 (today) avg=0.10, all 20 historical rows avg=0.50 (all above)
        // Percentile = 0 / 20 * 100 = 0.0%
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.10,\"call_iv\":0.10}");
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.50,\"call_iv\":0.50}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        Double ivPercentile = repository.getIVPercentile("AAPL");

        assertNotNull(ivPercentile);
        assertEquals(ivPercentile, 0.0, 0.01);
    }

    @Test
    public void testGetIVStatsForSymbols_Success() throws IOException {
        StringBuilder sb = new StringBuilder("[");
        sb.append("{\"date\":\"2026-07-21\",\"put_iv\":0.40,\"call_iv\":0.30},");
        sb.append("{\"date\":\"2026-07-20\",\"put_iv\":0.20,\"call_iv\":0.10},");
        sb.append("{\"date\":\"2026-07-19\",\"put_iv\":0.60,\"call_iv\":0.50}");
        for (int i = 3; i < 21; i++) {
            sb.append(String.format(java.util.Locale.US, ",{\"date\":\"2026-07-%02d\",\"put_iv\":0.35,\"call_iv\":0.35}", 21 - i));
        }
        sb.append("]");
        String mockResponseString = sb.toString();

        when(client.getObjectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(mockResponseString);

        java.util.Map<String, java.util.Map<String, Object>> map = repository.getIVStatsForSymbols(java.util.List.of("AAPL"));
        assertNotNull(map);
        assertTrue(map.containsKey("AAPL"));
        assertEquals(map.get("AAPL").get("currentIV"), 0.35);
        assertEquals(map.get("AAPL").get("ivRank"), 50.0);
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
