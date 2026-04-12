package com.hemasundar.apis;

import com.hemasundar.options.models.ExpirationChainResponse;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
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

import static org.testng.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hemasundar.utils.ApiErrorHandler;
import com.hemasundar.utils.TokenProvider;

public class ThinkOrSwinAPIsTest {

    private MockedStatic<RestAssured> mockedRestAssured;
    private RequestSpecification sharedMockRequest;

    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private ApiErrorHandler apiErrorHandler;

    private ThinkOrSwinAPIs apis;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedRestAssured = mockStatic(RestAssured.class);
        
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

        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        apis = new ThinkOrSwinAPIs(tokenProvider, apiErrorHandler);
    }

    @AfterMethod
    public void tearDown() {
        mockedRestAssured.close();
    }

    @Test
    public void testGetQuotes_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        // Include mandatory primitive fields to avoid deserialization errors
        when(mockResponse.asString()).thenReturn("{\"AAPL\": {\"assetMainType\": \"EQUITY\", \"realtime\": true, \"ssid\": 12345}}");

        Map<String, QuotesResponse.QuoteData> quotes = apis.getQuotes(List.of("AAPL"));
        assertNotNull(quotes);
        assertTrue(quotes.containsKey("AAPL"));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetQuotes_Failure() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(500);
        
        apis.getQuotes(List.of("AAPL"));
    }

    @Test
    public void testGetQuote_Single_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"AAPL\": {\"assetMainType\": \"EQUITY\", \"realtime\": true, \"ssid\": 12345}}");

        QuotesResponse.QuoteData quote = apis.getQuote("AAPL");
        assertNotNull(quote);
    }

    @Test
    public void testGetQuote_Single_400Error() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.asString()).thenReturn("{\"error\": \"Invalid symbol\"}");

        QuotesResponse.QuoteData quote = apis.getQuote("INVALID");
        assertNull(quote);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetQuote_Single_500Error() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(500);
        
        apis.getQuote("AAPL");
    }

    @Test
    public void testGetOptionChain_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"symbol\": \"AAPL\", \"underlyingPrice\": 150.0}");

        OptionChainResponse chain = apis.getOptionChain("AAPL");
        assertNotNull(chain);
        assertEquals(chain.getSymbol(), "AAPL");
    }

    @Test
    public void testGetExpirationChain_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"expirations\": [{\"date\": \"2024-01-01\", \"daysToExpiration\": 10}]}");

        ExpirationChainResponse expirationChain = apis.getExpirationChain("AAPL");
        assertNotNull(expirationChain);
    }

    @Test
    public void testGetPriceHistory_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"candles\": [], \"symbol\": \"AAPL\", \"empty\": false, \"previousClose\": 150.0, \"previousCloseDate\": 0}");

        PriceHistoryResponse history = apis.getYearlyPriceHistory("AAPL", 1);
        assertNotNull(history);
        assertEquals(history.getSymbol(), "AAPL");
    }

    @Test
    public void testGetMarketHours_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"equity\": {}, \"option\": {}}");

        com.hemasundar.pojos.MarketHoursResponse res = apis.getMarketHours();
        assertNotNull(res);
    }

    @Test
    public void testGetMarketHour_Single_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"equity\": {}}");

        String res = apis.getMarketHour("equity", null);
        assertNotNull(res);
        assertTrue(res.contains("equity"));
    }

    @Test
    public void testGetMovers_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("[{\"symbol\": \"AAPL\", \"change\": 1.5, \"direction\": \"up\"}]");

        String movers = apis.getMovers("$SPX", "UP", 0);
        assertNotNull(movers);
        assertTrue(movers.contains("AAPL"));
    }

    @Test
    public void testGetMovers_400Error() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.asString()).thenReturn("Error");

        String res = apis.getMovers("INVALID", null, null);
        assertNull(res);
    }

    @Test
    public void testGetInstruments_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("{\"AAPL\": {\"symbol\": \"AAPL\", \"description\": \"Apple Inc.\"}}");

        String instruments = apis.getInstruments("AAPL", "symbol-search");
        assertNotNull(instruments);
        assertTrue(instruments.contains("AAPL"));
    }

    @Test
    public void testGetInstrumentByCusip_Success() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.asString()).thenReturn("[{\"symbol\": \"AAPL\", \"description\": \"Apple Inc.\"}]");

        String res = apis.getInstrumentByCusip("12345678");
        assertNotNull(res);
        assertTrue(res.contains("AAPL"));
    }

    @Test
    public void testGetInstrumentByCusip_404Error() {
        Response mockResponse = mock(Response.class);
        when(sharedMockRequest.get(anyString())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(404);

        String res = apis.getInstrumentByCusip("NOTFOUND");
        assertNull(res);
    }
}
