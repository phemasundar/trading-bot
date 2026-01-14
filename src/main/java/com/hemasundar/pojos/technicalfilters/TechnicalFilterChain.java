package com.hemasundar.pojos.technicalfilters;

import lombok.Getter;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder pattern implementation for composing multiple technical filters.
 * Allows easy addition/removal of filters for complex strategy conditions.
 */
@Getter
public class TechnicalFilterChain {
    private final List<TechnicalFilter> filters;

    private TechnicalFilterChain(List<TechnicalFilter> filters) {
        this.filters = new ArrayList<>(filters);
    }

    public static TechnicalFilterChainBuilder builder() {
        return new TechnicalFilterChainBuilder();
    }

    /**
     * Evaluates all filters using AND logic.
     *
     * @param series The price data series
     * @return true only if ALL filters pass, false otherwise
     */
    public boolean evaluateAll(BarSeries series) {
        return filters.stream().allMatch(filter -> filter.evaluate(series));
    }

    /**
     * Evaluates all filters using OR logic.
     *
     * @param series The price data series
     * @return true if ANY filter passes, false otherwise
     */
    public boolean evaluateAny(BarSeries series) {
        return filters.stream().anyMatch(filter -> filter.evaluate(series));
    }

    /**
     * Gets a specific filter by its class type.
     *
     * @param filterClass The class of the filter to retrieve
     * @param <T>         The filter type
     * @return The filter instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends TechnicalFilter> T getFilter(Class<T> filterClass) {
        return filters.stream()
                .filter(filterClass::isInstance)
                .map(f -> (T) f)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns a summary of all filters in the chain.
     */
    public String getFiltersSummary() {
        StringBuilder sb = new StringBuilder("Technical Filters:\n");
        for (TechnicalFilter filter : filters) {
            sb.append("  - ").append(filter.getFilterName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Builder class for constructing TechnicalFilterChain.
     */
    public static class TechnicalFilterChainBuilder {
        private final List<TechnicalFilter> filters = new ArrayList<>();

        /**
         * Adds any TechnicalFilter to the chain.
         */
        public TechnicalFilterChainBuilder addFilter(TechnicalFilter filter) {
            if (filter != null) {
                filters.add(filter);
            }
            return this;
        }

        /**
         * Convenience method to add an RSI filter.
         */
        public TechnicalFilterChainBuilder withRSI(RSIFilter rsiFilter) {
            return addFilter(rsiFilter);
        }

        /**
         * Convenience method to add a Bollinger Bands filter.
         */
        public TechnicalFilterChainBuilder withBollingerBands(BollingerBandsFilter bbFilter) {
            return addFilter(bbFilter);
        }

        /**
         * Builds the TechnicalFilterChain.
         */
        public TechnicalFilterChain build() {
            return new TechnicalFilterChain(filters);
        }
    }
}
