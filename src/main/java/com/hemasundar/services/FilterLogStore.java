package com.hemasundar.services;

import com.hemasundar.dto.ExecutionLogEntry;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe singleton store for filter-stage execution log entries.
 * Strategy classes write entries directly here during execution.
 * The store is cleared at the start of each new execution.
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
     * Adds a filter-stage log entry and emits a corresponding INFO log line.
     */
    public void logFilter(String strategyName, String symbol, String filterStage, int tradesIn, int tradesOut) {
        int filtered = tradesIn - tradesOut;
        log.info("[FILTER][{}][{}] {} — in: {}, passed: {}, filtered: {}",
                strategyName, symbol, filterStage, tradesIn, tradesOut, filtered);

        entries.add(ExecutionLogEntry.builder()
                .strategyName(strategyName)
                .symbol(symbol)
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
