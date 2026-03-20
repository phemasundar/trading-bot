package com.hemasundar.apis;

import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.utils.EarningsCacheManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class FinnHubAPIsTest {

    private MockedStatic<RestAssured> mockedRestAssured;
    private MockedStatic<TestConfig> mockedTestConfig;
    private MockedStatic<EarningsCacheManager> mockedCacheManager;

    @BeforeMethod
    public void setUp() {
        mockedRestAssured = mockStatic(RestAssured.class);
        mockedTestConfig = mockStatic(TestConfig.class);
        mockedCacheManager = mockStatic(EarningsCacheManager.class);
        
        TestConfig mockConfig = mock(TestConfig.class);
        when(TestConfig.getInstance()).thenReturn(mockConfig);
        when(mockConfig.finnhubApiKey()).thenReturn("test-key");
    }

    @AfterMethod
    public void tearDown() {
        mockedRestAssured.close();
        mockedTestConfig.close();
        mockedCacheManager.close();
    }

    @Test
    public void testGetEarningsByTicker_CacheHit() {
        EarningsCalendarResponse.EarningCalendar earning = new EarningsCalendarResponse.EarningCalendar();
        earning.setSymbol("AAPL");
        earning.setDate(LocalDate.now().plusDays(1));
        
        when(EarningsCacheManager.getEarningsFromCache(anyString(), any())).thenReturn(Collections.singletonList(earning));

        EarningsCalendarResponse response = FinnHubAPIs.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
        
        assertNotNull(response);
        assertEquals(response.getEarningsCalendar().size(), 1);
        mockedRestAssured.verify(() -> RestAssured.given(), never());
    }

    @Test
    public void testGetEarningsByTicker_FreshFetch() {
        when(EarningsCacheManager.getEarningsFromCache(anyString(), any())).thenReturn(null);
        
        RequestSpecification mockRequest = mock(RequestSpecification.class);
        Response mockResponse = mock(Response.class);
        
        when(RestAssured.given()).thenReturn(mockRequest);
        when(mockRequest.baseUri(anyString())).thenReturn(mockRequest);
        when(mockRequest.queryParam(anyString(), (Object) any())).thenReturn(mockRequest);
        when(mockRequest.get(anyString())).thenReturn(mockResponse);
        
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asPrettyString()).thenReturn("{\"earningsCalendar\": []}");

        EarningsCalendarResponse response = FinnHubAPIs.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
        
        assertNotNull(response);
        mockedCacheManager.verify(() -> EarningsCacheManager.updateCache(eq("AAPL"), any()), times(1));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetEarningsByTicker_Failure() {
        when(EarningsCacheManager.getEarningsFromCache(anyString(), any())).thenReturn(null);
        
        RequestSpecification mockRequest = mock(RequestSpecification.class);
        Response mockResponse = mock(Response.class);
        
        when(RestAssured.given()).thenReturn(mockRequest);
        when(mockRequest.baseUri(anyString())).thenReturn(mockRequest);
        when(mockRequest.queryParam(anyString(), (Object) any())).thenReturn(mockRequest);
        when(mockRequest.get(anyString())).thenReturn(mockResponse);
        
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.statusLine()).thenReturn("Internal Server Error");

        FinnHubAPIs.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
    }
}
