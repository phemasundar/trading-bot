package com.hemasundar.utils;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.List;

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

    @Test
    public void testPrewarm_AllCached() {
        cache.clear();
        String symbol = "AAPL";
        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(new OptionChainResponse());
        cache.get(symbol); // caches it

        SchwabApiExecutor executor = mock(SchwabApiExecutor.class);
        cache.prewarm(List.of(symbol), executor);

        verifyNoInteractions(executor);
    }

    @Test
    public void testPrewarm_Uncached() {
        cache.clear();
        String symbol1 = "AAPL";
        String symbol2 = "MSFT";
        OptionChainResponse resp1 = new OptionChainResponse();
        OptionChainResponse resp2 = new OptionChainResponse();

        SchwabApiExecutor executor = mock(SchwabApiExecutor.class);
        when(executor.executeParallel(anyList(), any(), any())).thenAnswer(inv -> {
            List<String> symbols = inv.getArgument(0);
            java.util.function.Function<String, OptionChainResponse> func = inv.getArgument(1);
            return List.of(func.apply(symbol1), func.apply(symbol2));
        });

        when(thinkOrSwinAPIs.getOptionChain(symbol1)).thenReturn(resp1);
        when(thinkOrSwinAPIs.getOptionChain(symbol2)).thenReturn(resp2);

        cache.prewarm(List.of(symbol1, symbol2), executor);

        assertTrue(cache.isCached(symbol1));
        assertTrue(cache.isCached(symbol2));
        assertEquals(cache.getApiCallCount(), 2);
    }

    @Test
    public void testPrewarm_UncachedWithFailures() {
        cache.clear();
        String symbol1 = "AAPL";
        String symbol2 = "MSFT";
        OptionChainResponse resp1 = new OptionChainResponse();

        SchwabApiExecutor executor = mock(SchwabApiExecutor.class);
        when(executor.executeParallel(anyList(), any(), any())).thenReturn(java.util.Arrays.asList(resp1, null));

        cache.prewarm(List.of(symbol1, symbol2), executor);

        assertTrue(cache.isCached(symbol1));
        assertFalse(cache.isCached(symbol2));
    }

    @Test
    public void testGetAll() {
        cache.clear();
        String symbol1 = "AAPL";
        String symbol2 = "MSFT";
        OptionChainResponse resp1 = new OptionChainResponse();

        when(thinkOrSwinAPIs.getOptionChain(symbol1)).thenReturn(resp1);
        when(thinkOrSwinAPIs.getOptionChain(symbol2)).thenThrow(new RuntimeException("API error"));

        List<OptionChainResponse> results = cache.getAll(List.of(symbol1, symbol2));

        assertEquals(results.size(), 1);
        assertSame(results.get(0), resp1);
    }

    @Test
    public void testPrintStats() {
        cache.printStats();
    }
}
