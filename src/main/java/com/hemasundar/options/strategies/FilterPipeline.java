package com.hemasundar.options.strategies;

import com.hemasundar.services.FilterLogStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A log-aware filter runner that applies a sequence of {@link NamedFilter} steps to an input list.
 *
 * <p>This class has a single, explicit responsibility: execute a named filter chain and record
 * each step's result to {@link FilterLogStore}. It is <em>not</em> a generic collection utility —
 * the logging context (strategy, symbol, expiry) is a first-class part of its identity.
 *
 * <p>Usage:
 * <pre>{@code
 * List<PutSpreadCandidate> result = FilterPipeline
 *     .<PutSpreadCandidate>forContext(strategyName, symbol, expiryDate)
 *     .step(FilterStage.DELTA_FILTER,           deltaFilter(shortLegFilter, longLegFilter))
 *     .step(FilterStage.POSITIVE_CREDIT_FILTER,  creditFilter())
 *     .step(FilterStage.MAX_LOSS_FILTER,         commonMaxLossFilter(filter, PutSpreadCandidate::maxLoss))
 *     .run(candidates);
 * }</pre>
 *
 * @param <T> the type of candidate or trade being filtered
 */
public class FilterPipeline<T> {

    private final String strategy;
    private final String symbol;
    private final String expiry;
    private final List<NamedFilter<T>> steps = new ArrayList<>();

    private FilterPipeline(String strategy, String symbol, String expiry) {
        this.strategy = strategy;
        this.symbol = symbol;
        this.expiry = expiry;
    }

    /**
     * Creates a new pipeline bound to the given logging context.
     *
     * @param strategy the strategy display name (for log grouping)
     * @param symbol   the ticker symbol (for log grouping)
     * @param expiry   the expiry date string, or {@code null} for symbol-level filters
     */
    public static <T> FilterPipeline<T> forContext(String strategy, String symbol, String expiry) {
        return new FilterPipeline<>(strategy, symbol, expiry);
    }

    /**
     * Adds a named filter step to this pipeline.
     *
     * @param name      the human-readable label shown in the logs UI
     * @param predicate the filter condition
     * @return this pipeline, for method chaining
     */
    public FilterPipeline<T> step(String name, Predicate<T> predicate) {
        steps.add(new NamedFilter<>(name, predicate));
        return this;
    }

    /**
     * Adds a named filter step to this pipeline using a {@link FilterStage} enum constant.
     * The enum's {@link FilterStage#displayName()} is used as the label in the logs UI.
     *
     * @param stage     the canonical filter stage identifier
     * @param predicate the filter condition
     * @return this pipeline, for method chaining
     */
    public FilterPipeline<T> step(FilterStage stage, Predicate<T> predicate) {
        return step(stage.displayName(), predicate);
    }

    /**
     * Executes all registered filter steps in order against {@code input}.
     * After each step, the result count is recorded in {@link FilterLogStore}.
     *
     * @param input the full list of candidates to filter
     * @return the list of candidates that survived all filter steps
     */
    public List<T> run(List<T> input) {
        List<T> current = input;
        FilterLogStore log = FilterLogStore.getInstance();
        for (NamedFilter<T> step : steps) {
            List<T> next = current.stream().filter(step.predicate()).toList();
            log.logFilter(strategy, symbol, expiry, step.name(), current.size(), next.size());
            current = next;
        }
        return current;
    }
}
