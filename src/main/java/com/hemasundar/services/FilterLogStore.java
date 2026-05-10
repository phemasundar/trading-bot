package com.hemasundar.services;

import com.hemasundar.dto.ExecutionLogEntry;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe singleton store for filter-stage execution log entries.
 * Strategy classes write entries directly here during execution.
 * The store is cleared at the start of each execution.
 * The UI polls /api/filter-logs to display these entries in real-time.
 */
@Log4j2
public class FilterLogStore {

    private static final FilterLogStore INSTANCE = new FilterLogStore();

    private final CopyOnWriteArrayList<ExecutionLogEntry> entries = new CopyOnWriteArrayList<>();

    private FilterLogStore() {}

    public static FilterLogStore getInstance() {
        return INSTANCE;
    }

    /**
     * Logs a symbol-level filter stage (no expiry context, e.g. Historical Volatility, DTE Filter).
     * These entries appear in the "Other" block in the UI.
     */
    public void logFilter(String strategyName, String symbol, String filterStage, int tradesIn, int tradesOut) {
        logFilter(strategyName, symbol, null, filterStage, tradesIn, tradesOut);
    }

    /**
     * Logs a per-expiry filter stage. Pass the expiry date string (e.g. "2025-01-17") so the UI
     * can group filters under the correct expiry sub-block.
     *
     * @param expiry null for symbol-level filters; a date string for per-expiry filters
     */
    public void logFilter(String strategyName, String symbol, String expiry, String filterStage, int tradesIn, int tradesOut) {
        int filtered = tradesIn - tradesOut;
        log.info("[FILTER][{}][{}][{}] {} — in: {}, passed: {}, filtered: {}",
                strategyName, symbol, expiry != null ? expiry : "symbol-level",
                filterStage, tradesIn, tradesOut, filtered);

        entries.add(ExecutionLogEntry.builder()
                .strategyName(strategyName)
                .symbol(symbol)
                .expiry(expiry)
                .filterStage(filterStage)
                .tradesIn(tradesIn)
                .tradesOut(tradesOut)
                .build());
    }

    /**
     * Returns a snapshot of all collected log entries.
     */
    public List<ExecutionLogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Clears all log entries. Called at the start of each execution.
     */
    public void clear() {
        entries.clear();
        log.info("[FILTER] Filter log store cleared for new execution");
    }
}
