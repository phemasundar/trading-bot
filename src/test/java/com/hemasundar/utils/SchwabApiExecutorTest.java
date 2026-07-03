package com.hemasundar.utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.testng.Assert.*;

public class SchwabApiExecutorTest {

    private SchwabApiExecutor executor;

    @BeforeMethod
    public void setUp() {
        // Use a small thread pool and 50ms pause for faster tests
        executor = new SchwabApiExecutor(2, 50);
    }

    @AfterMethod
    public void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    public void testExecuteParallel_Success() {
        List<String> symbols = Arrays.asList("AAPL", "MSFT", "GOOG");
        Function<String, String> apiCall = symbol -> symbol + "_processed";

        List<String> results = executor.executeParallel(symbols, apiCall);

        assertNotNull(results);
        assertEquals(results.size(), 3);
        assertEquals(results.get(0), "AAPL_processed");
        assertEquals(results.get(1), "MSFT_processed");
        assertEquals(results.get(2), "GOOG_processed");
    }

    @Test
    public void testExecuteParallel_RateLimitRetrySuccess() {
        List<String> symbols = Arrays.asList("AAPL");
        AtomicInteger attempts = new AtomicInteger(0);

        Function<String, String> apiCall = symbol -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new RuntimeException("429 Too Many Requests");
            }
            return symbol + "_success";
        };

        List<String> results = executor.executeParallel(symbols, apiCall);

        assertNotNull(results);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), "AAPL_success");
        assertEquals(attempts.get(), 2);
    }

    @Test
    public void testExecuteParallel_FailureReturnsNull() {
        List<String> symbols = Arrays.asList("AAPL", "FAIL", "MSFT");
        Function<String, String> apiCall = symbol -> {
            if ("FAIL".equals(symbol)) {
                throw new RuntimeException("API Down");
            }
            return symbol + "_processed";
        };

        List<String> results = executor.executeParallel(symbols, apiCall);

        assertNotNull(results);
        assertEquals(results.size(), 3);
        assertEquals(results.get(0), "AAPL_processed");
        assertNull(results.get(1));
        assertEquals(results.get(2), "MSFT_processed");
    }
}
