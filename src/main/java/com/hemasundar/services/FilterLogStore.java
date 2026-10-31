package com.hemasundar.services;

import com.hemasundar.dto.ExecutionLogEntry;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe singleton store for filter-stage execution log entries.
 * Strategy classes write entries directly here during execution.
 * The store is cleared at the start of each execution.
 * The UI polls /api/filter-logs to display these entries in real-time.
 * <p>
 * Backed by a bounded synchronized {@link ArrayList} (instead of {@code CopyOnWriteArrayList})
 * to avoid O(N^2) array-copy heap churn across tens of thousands of filter stage logs.
 */
@Log4j2
public class FilterLogStore {

    public static final int MAX_ENTRIES = 50_000;
    private static final FilterLogStore INSTANCE = new FilterLogStore();

    private final List<ExecutionLogEntry> entries = new ArrayList<>();

    private FilterLogStore() {}

    public static FilterLogStore getInstance() {
        return INSTANCE;
    }

    /**
     * Logs a symbol-level filter stage (no expiry context, e.g. Historical Volatility, DTE Filter).
     * These entries appear in the "Other" block in the UI.
     */
    public void logFilter(String strategyName, String symbol, String filterStage, int tradesIn, int tradesOut) {
        logFilter(strategyName, symbol, null, filterStage, null, tradesIn, tradesOut);
    }

    /**
     * Logs a symbol-level filter stage with evaluated context/details.
     */
    public void logSymbolFilter(String strategyName, String symbol, String filterStage, String details, int tradesIn, int tradesOut) {
        logFilter(strategyName, symbol, null, filterStage, details, tradesIn, tradesOut);
    }

    /**
     * Logs a per-expiry filter stage. Pass the expiry date string (e.g. "2025-01-17") so the UI
     * can group filters under the correct expiry sub-block.
     *
     * @param expiry null for symbol-level filters; a date string for per-expiry filters
     */
    public void logFilter(String strategyName, String symbol, String expiry, String filterStage, int tradesIn, int tradesOut) {
        logFilter(strategyName, symbol, expiry, filterStage, null, tradesIn, tradesOut);
    }

    /**
     * Logs a filter stage with optional expiry date and contextual details.
     *
     * @param expiry null for symbol-level filters; a date string for per-expiry filters
     * @param details optional string with evaluated metric values or condition bounds
     */
    public void logFilter(String strategyName, String symbol, String expiry, String filterStage, String details, int tradesIn, int tradesOut) {
        int filtered = tradesIn - tradesOut;
        log.debug("[FILTER][{}][{}][{}] {}{} — in: {}, passed: {}, filtered: {}",
                strategyName, symbol, expiry != null ? expiry : "symbol-level",
                filterStage, details != null ? " (" + details + ")" : "", tradesIn, tradesOut, filtered);

        ExecutionLogEntry entry = ExecutionLogEntry.builder()
                .strategyName(strategyName)
                .symbol(symbol)
                .expiry(expiry)
                .filterStage(filterStage)
                .details(details)
                .tradesIn(tradesIn)
                .tradesOut(tradesOut)
                .build();

        synchronized (entries) {
            if (entries.size() < MAX_ENTRIES) {
                entries.add(entry);
            }
        }
    }

    /**
     * Returns a snapshot of all collected log entries.
     */
    public List<ExecutionLogEntry> getEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /**
     * Clears all log entries. Called at the start of each execution.
     */
    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
        log.debug("[FILTER] Filter log store cleared for new execution");
    }
}
