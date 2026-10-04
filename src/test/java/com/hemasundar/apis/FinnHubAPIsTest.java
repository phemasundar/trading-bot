package com.hemasundar.apis;

import com.hemasundar.config.properties.FinnHubConfig;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.utils.ApiErrorHandler;
import com.hemasundar.utils.EarningsCacheManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class FinnHubAPIsTest {

    @Mock
    private FinnHubConfig mockConfig;
    
    @Mock
    private ApiErrorHandler mockApiErrorHandler;
    
    private FinnHubAPIs apis;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConfig.getApiKey()).thenReturn("test-key");
        apis = new FinnHubAPIs(mockConfig, mockApiErrorHandler);
    }

    @Test
    public void testGetEarningsByTicker_CacheHit() {
        try (MockedStatic<EarningsCacheManager> mockedCacheManager = mockStatic(EarningsCacheManager.class)) {
            EarningsCalendarResponse.EarningCalendar earning = new EarningsCalendarResponse.EarningCalendar();
            earning.setSymbol("AAPL");
            earning.setDate(LocalDate.now().plusDays(1));
            
            mockedCacheManager.when(() -> EarningsCacheManager.getEarningsFromCache(anyString(), any()))
                    .thenReturn(Collections.singletonList(earning));

            EarningsCalendarResponse response = apis.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
            
            assertNotNull(response);
            assertEquals(response.getEarningsCalendar().size(), 1);
        }
    }

    @Test
    public void testGetEarningsByTicker_FreshFetch() {
        try (MockedStatic<RestAssured> mockedRestAssured = mockStatic(RestAssured.class);
             MockedStatic<EarningsCacheManager> mockedCacheManager = mockStatic(EarningsCacheManager.class)) {
            mockedCacheManager.when(() -> EarningsCacheManager.getEarningsFromCache(anyString(), any())).thenReturn(null);
            
            RequestSpecification mockRequest = mock(RequestSpecification.class);
            Response mockResponse = mock(Response.class);
            
            mockedRestAssured.when(RestAssured::given).thenReturn(mockRequest);
            when(mockRequest.baseUri(anyString())).thenReturn(mockRequest);
            when(mockRequest.queryParam(anyString(), (Object) any())).thenReturn(mockRequest);
            when(mockRequest.get(anyString())).thenReturn(mockResponse);
            
            LocalDate futureDate = LocalDate.now().plusDays(5);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.asPrettyString()).thenReturn("{\"earningsCalendar\": [{\"symbol\": \"AAPL\", \"date\": \"" + futureDate + "\"}]}");

            EarningsCalendarResponse response = apis.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
            
            assertNotNull(response);
            assertEquals(response.getEarningsCalendar().size(), 1);
            assertEquals(response.getEarningsCalendar().get(0).getDate(), futureDate);
            mockedCacheManager.verify(() -> EarningsCacheManager.updateCache(eq("AAPL"), any()), times(1));
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetEarningsByTicker_Failure() {
        try (MockedStatic<RestAssured> mockedRestAssured = mockStatic(RestAssured.class);
             MockedStatic<EarningsCacheManager> mockedCacheManager = mockStatic(EarningsCacheManager.class)) {
            mockedCacheManager.when(() -> EarningsCacheManager.getEarningsFromCache(anyString(), any())).thenReturn(null);
            
            RequestSpecification mockRequest = mock(RequestSpecification.class);
            Response mockResponse = mock(Response.class);
            
            mockedRestAssured.when(RestAssured::given).thenReturn(mockRequest);
            when(mockRequest.baseUri(anyString())).thenReturn(mockRequest);
            when(mockRequest.queryParam(anyString(), (Object) any())).thenReturn(mockRequest);
            when(mockRequest.get(anyString())).thenReturn(mockResponse);
            
            when(mockResponse.statusCode()).thenReturn(500);
            when(mockResponse.statusLine()).thenReturn("Internal Server Error");

            apis.getEarningsByTicker("AAPL", LocalDate.now().plusDays(10));
        }
    }
}
