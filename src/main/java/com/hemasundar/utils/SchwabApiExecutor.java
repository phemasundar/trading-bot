package com.hemasundar.utils;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Shared thread pool for parallel Schwab API calls.
 *
 * <p>Wraps a bounded {@link ExecutorService} and provides a simple
 * {@link #executeParallel} method that submits one task per symbol,
 * waits for all to complete, and returns results in the original symbol order.
 *
 * <h3>Rate-limit safety</h3>
 * The Schwab Market Data API allows 120 requests/minute (~2 req/sec).
 * Each {@code getOptionChain} call takes 1–3 s on average, so with
 * {@code parallel-threads=8} we sustain at most 8 concurrent requests;
 * the natural blocking of slow calls (e.g. QQQ ≈ 33 s) acts as
 * organic throttling. A hard semaphore is not needed at this thread count.
 *
 * <h3>Auth failure propagation</h3>
 * Tasks check the supplied {@code authFailed} flag before starting.
 * The first 401 completes its future with {@code null}; callers must
 * filter out nulls and surface the error through the normal alert path.
 *
 * <h3>Configuration</h3>
 * <pre>
 *   schwab.api.parallel-threads=8          # number of concurrent API threads
 *   schwab.api.rate-limit-pause-ms=60000   # pause on 429 before single retry
 * </pre>
 */
@Log4j2
@Component
public class SchwabApiExecutor {

    private final ExecutorService executor;
    private final long rateLimitPauseMs;

    public SchwabApiExecutor(
            @Value("${schwab.api.parallel-threads:8}") int parallelThreads,
            @Value("${schwab.api.rate-limit-pause-ms:60000}") long rateLimitPauseMs) {
        this.executor = Executors.newFixedThreadPool(parallelThreads,
                r -> {
                    Thread t = new Thread(r, "schwab-api-pool");
                    t.setDaemon(true);
                    return t;
                });
        this.rateLimitPauseMs = rateLimitPauseMs;
        log.info("SchwabApiExecutor initialised with {} threads, rate-limit pause {}ms",
                parallelThreads, rateLimitPauseMs);
    }

    /**
     * Executes {@code apiCall} for each symbol in parallel and returns results
     * in the same order as the input list. Symbols whose call throws an exception
     * produce a {@code null} in the result list; callers should filter these out.
     *
     * <p>A single automatic retry is attempted when the exception message contains
     * "429" (rate-limited). The thread sleeps for {@code rateLimitPauseMs} before
     * retrying.
     *
     * @param symbols list of stock symbols to process
     * @param apiCall function that takes a symbol and calls the Schwab API
     * @param alertCallback optional callback to receive error messages (symbol, errorMsg)
     * @param <T>     return type of the API call
     * @return list of results in the same order as {@code symbols}; may contain nulls
     */
    public <T> List<T> executeParallel(List<String> symbols, Function<String, T> apiCall, java.util.function.BiConsumer<String, String> alertCallback) {
        List<CompletableFuture<T>> futures = new ArrayList<>(symbols.size());

        for (String symbol : symbols) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> invokeWithRetry(symbol, apiCall), executor));
        }

        List<T> results = new ArrayList<>(symbols.size());
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).join());
            } catch (Exception e) {
                log.error("Parallel API call failed for symbol [{}]: {}", symbols.get(i), e.getMessage());
                if (alertCallback != null) {
                    alertCallback.accept(symbols.get(i), e.getMessage() != null ? e.getMessage() : e.toString());
                }
                results.add(null);
            }
        }
        return results;
    }

    public <T> List<T> executeParallel(List<String> symbols, Function<String, T> apiCall) {
        return executeParallel(symbols, apiCall, null);
    }

    /**
     * Invokes the API call for a single symbol with one automatic 429 retry.
     */
    private <T> T invokeWithRetry(String symbol, Function<String, T> apiCall) {
        try {
            return apiCall.apply(symbol);
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                log.warn("[{}] Rate-limited (429) — pausing {}ms then retrying",
                        symbol, rateLimitPauseMs);
                try {
                    Thread.sleep(rateLimitPauseMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // Single retry after pause
                return apiCall.apply(symbol);
            }
            throw e;
        }
    }

    private boolean isRateLimitError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("429") || msg.toLowerCase().contains("rate limit"));
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SchwabApiExecutor thread pool");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
