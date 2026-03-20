package com.hemasundar.apis;

import com.hemasundar.options.models.ExpirationChainResponse;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.pojos.TestConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.AuthenticationSpecification;
import io.restassured.specification.PreemptiveAuthSpec;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ThinkOrSwinAPIsTest {

    private MockedStatic<RestAssured> mockedRestAssured;
    private MockedStatic<TestConfig> mockedTestConfig;
    private TestConfig mockConfig;
    private RequestSpecification sharedMockRequest;

    @BeforeMethod
    public void setUp() {
        mockedRestAssured = mockStatic(RestAssured.class);
        mockedTestConfig = mockStatic(TestConfig.class);
        
        mockConfig = mock(TestConfig.class);
        when(TestConfig.getInstance()).thenReturn(mockConfig);
        when(mockConfig.appKey()).thenReturn("test-app-key");
        when(mockConfig.ppSecret()).thenReturn("test-secret");
        when(mockConfig.refreshToken()).thenReturn("test-refresh-token");
        
        // Mock RestAssured globally for all calls (including TokenProvider refresh)
        sharedMockRequest = mock(RequestSpecification.class);
        AuthenticationSpecification mockAuth = mock(AuthenticationSpecification.class);
        PreemptiveAuthSpec mockPreemptive = mock(PreemptiveAuthSpec.class);
        Response mockResponse = mock(Response.class);

        when(RestAssured.given()).thenReturn(sharedMockRequest);
        
        // Setup the sharedMockRequest with complete fluent chain stubs
        when(sharedMockRequest.auth()).thenReturn(mockAuth);
        when(sharedMockRequest.when()).thenReturn(sharedMockRequest);
        when(sharedMockRequest.given()).thenReturn(sharedMockRequest);
        
        when(mockAuth.preemptive()).thenReturn(mockPreemptive);
        when(mockPreemptive.basic(anyString(), anyString())).thenReturn(sharedMockRequest);
        
        when(sharedMockRequest.contentType(anyString())).thenReturn(sharedMockRequest);
        when(sharedMockRequest.formParam(anyString(), (Object) any())).thenReturn(sharedMockRequest);
        when(sharedMockRequest.header(anyString(), any())).thenReturn(sharedMockRequest);
        when(sharedMockRequest.queryParam(anyString(), (Object) any())).thenReturn(sharedMockRequest);
        
        // Stub all HTTP methods to return mockResponse by default
        when(sharedMockRequest.post(anyString())).thenReturn(mockResponse);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        
        // Response for token refresh
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asPrettyString()).thenReturn("{\"access_token\": \"test-token\", \"expires_in\": 3600}");
    }

    @AfterMethod
    public void tearDown() {
        mockedRestAssured.close();
        mockedTestConfig.close();
    }

    @Test
    public void testGetQuotes_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        // Include mandatory primitive fields to avoid deserialization errors
        when(mockResponse.asString()).thenReturn("{\"AAPL\": {\"assetMainType\": \"EQUITY\", \"realtime\": true, \"ssid\": 12345}}");

        Map<String, QuotesResponse.QuoteData> quotes = ThinkOrSwinAPIs.getQuotes(List.of("AAPL"));
        assertNotNull(quotes);
        assertTrue(quotes.containsKey("AAPL"));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetQuotes_Failure() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(500);
        
        ThinkOrSwinAPIs.getQuotes(List.of("AAPL"));
    }

    @Test
    public void testGetQuote_Single_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"AAPL\": {\"assetMainType\": \"EQUITY\", \"realtime\": true, \"ssid\": 12345}}");

        QuotesResponse.QuoteData quote = ThinkOrSwinAPIs.getQuote("AAPL");
        assertNotNull(quote);
    }

    @Test
    public void testGetOptionChain_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"symbol\": \"AAPL\", \"underlyingPrice\": 150.0}");

        OptionChainResponse chain = ThinkOrSwinAPIs.getOptionChain("AAPL");
        assertNotNull(chain);
        assertEquals(chain.getSymbol(), "AAPL");
    }

    @Test
    public void testGetExpirationChain_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"expirations\": [{\"date\": \"2024-01-01\", \"daysToExpiration\": 10}]}");

        ExpirationChainResponse expirationChain = ThinkOrSwinAPIs.getExpirationChain("AAPL");
        assertNotNull(expirationChain);
    }

    @Test
    public void testGetPriceHistory_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        // Include all mandatory primitive fields: empty (boolean), previousClose (double), previousCloseDate (long)
        when(mockResponse.asString()).thenReturn("{\"candles\": [], \"symbol\": \"AAPL\", \"empty\": false, \"previousClose\": 150.0, \"previousCloseDate\": 0}");

        PriceHistoryResponse history = ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1);
        assertNotNull(history);
        assertEquals(history.getSymbol(), "AAPL");
    }
}
