package com.hemasundar.utils;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class OptionChainCacheTest {

    private OptionChainCache cache;
    private ThinkOrSwinAPIs thinkOrSwinAPIs;

    @BeforeMethod
    public void setUp() {
        thinkOrSwinAPIs = mock(ThinkOrSwinAPIs.class);
        cache = new OptionChainCache(thinkOrSwinAPIs);
    }

    @Test
    public void testGet_LazyLoad() {
        String symbol = "AAPL";
        OptionChainResponse mockResponse = new OptionChainResponse();
        mockResponse.setSymbol(symbol);
        
        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockResponse);
        
        // First call - should fetch from API
        OptionChainResponse result1 = cache.get(symbol);
        assertNotNull(result1);
        assertEquals(result1.getSymbol(), symbol);
        assertEquals(cache.getApiCallCount(), 1);
        
        // Second call - should return from cache
        OptionChainResponse result2 = cache.get(symbol);
        assertSame(result1, result2);
        assertEquals(cache.getApiCallCount(), 1);
        
        verify(thinkOrSwinAPIs, times(1)).getOptionChain(symbol);
    }

    @Test
    public void testIsCached() {
        String symbol = "TSLA";
        assertFalse(cache.isCached(symbol));
        
        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(new OptionChainResponse());
        cache.get(symbol);
        
        assertTrue(cache.isCached(symbol));
    }

    @Test
    public void testClear() {
        // Mock API call to avoid null pointer or actual network call
        when(thinkOrSwinAPIs.getOptionChain(anyString())).thenReturn(new OptionChainResponse());
        
        cache.get("AAPL"); 
        cache.clear();
        assertEquals(cache.size(), 0);
        assertEquals(cache.getApiCallCount(), 0);
    }
}
