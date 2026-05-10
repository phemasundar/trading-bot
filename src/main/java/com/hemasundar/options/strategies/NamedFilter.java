package com.hemasundar.options.strategies;

import java.util.function.Predicate;

/**
 * Pure data record that pairs a human-readable display name with a filter predicate.
 * Contains no logging or framework logic — it is a value object only.
 *
 * <p>Used by {@link FilterPipeline} to describe each step in a named filter chain.
 *
 * @param <T> the type of candidate or trade being filtered
 */
public record NamedFilter<T>(String name, Predicate<T> predicate) {}
