package com.hemasundar.technical;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines technical indicators with filter conditions for strategy evaluation.
 * 
 * Usage pattern:
 * 
 * <pre>
 * // Step 1: Define indicators (WHAT to measure)
 * TechnicalIndicators indicators = TechnicalIndicators.builder()
 *         .rsiFilter(RSIFilter.builder().period(14).oversoldThreshold(30.0).overboughtThreshold(70.0).build())
 *         .bollingerFilter(BollingerBandsFilter.builder().period(20).standardDeviations(2.0).build())
 *         .volumeFilter(VolumeFilter.builder().minVolume(1_000_000L).build())
 *         .build();
 * 
 * // Step 2: Define conditions (WHAT CONDITIONS to look for)
 * FilterConditions oversoldConditions = FilterConditions.builder()
 *         .rsiCondition(RSICondition.OVERSOLD)
 *         .bollingerCondition(BollingerCondition.LOWER_BAND)
 *         .build();
 * 
 * // Step 3: Combine into filter chain
 * TechnicalFilterChain filterChain = TechnicalFilterChain.of(indicators, oversoldConditions);
 * </pre>
 */
@Log4j2
@Getter
public class TechnicalFilterChain {
    private final TechnicalIndicators indicators;
    private final FilterConditions conditions;
    private final List<TechnicalFilter> filters;

    private TechnicalFilterChain(TechnicalIndicators indicators, FilterConditions conditions) {
        this.indicators = indicators;
        this.conditions = conditions;
        this.filters = buildFilterList(indicators);
    }

    /**
     * Creates a TechnicalFilterChain from indicators and conditions.
     * This is the recommended way to create a filter chain.
     */
    public static TechnicalFilterChain of(TechnicalIndicators indicators, FilterConditions conditions) {
        return new TechnicalFilterChain(indicators, conditions);
    }

    /**
     * Alternative builder pattern for backward compatibility.
     */
    public static TechnicalFilterChainBuilder builder() {
        return new TechnicalFilterChainBuilder();
    }

    private List<TechnicalFilter> buildFilterList(TechnicalIndicators indicators) {
        List<TechnicalFilter> list = new ArrayList<>();
        if (indicators.getRsiFilter() != null) {
            list.add(indicators.getRsiFilter());
        }
        if (indicators.getBollingerFilter() != null) {
            list.add(indicators.getBollingerFilter());
        }
        if (indicators.getMa20Filter() != null) {
            list.add(indicators.getMa20Filter());
        }
        if (indicators.getMa50Filter() != null) {
            list.add(indicators.getMa50Filter());
        }
        if (indicators.getVolumeFilter() != null) {
            list.add(indicators.getVolumeFilter());
        }
        return list;
    }

    // Convenience getters for individual filters
    public RSIFilter getRsiFilter() {
        return indicators != null ? indicators.getRsiFilter() : null;
    }

    public BollingerBandsFilter getBollingerFilter() {
        return indicators != null ? indicators.getBollingerFilter() : null;
    }

    public VolumeFilter getVolumeFilter() {
        return indicators != null ? indicators.getVolumeFilter() : null;
    }

    // Convenience getters for conditions
    public RSICondition getRsiCondition() {
        return conditions != null ? conditions.getRsiCondition() : null;
    }

    public BollingerCondition getBollingerCondition() {
        return conditions != null ? conditions.getBollingerCondition() : null;
    }

    public Long getMinVolume() {
        return conditions != null ? conditions.getMinVolume() : null;
    }

    // Convenience getters for MA filters
    public MovingAverageFilter getMa20Filter() {
        return indicators != null ? indicators.getMa20Filter() : null;
    }

    public MovingAverageFilter getMa50Filter() {
        return indicators != null ? indicators.getMa50Filter() : null;
    }

    /**
     * Gets a specific filter by its class type.
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
     * Returns a summary of all indicators and conditions in the chain.
     */
    public String getFiltersSummary() {
        StringBuilder sb = new StringBuilder("Technical Filter Chain:\n");
        sb.append("  Conditions: ").append(conditions != null ? conditions.getSummary() : "NOT SET").append("\n");
        sb.append("  Indicators:\n");
        for (TechnicalFilter filter : filters) {
            sb.append("    - ").append(filter.getFilterName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Builder class for backward compatibility.
     */
    public static class TechnicalFilterChainBuilder {
        private RSIFilter rsiFilter;
        private BollingerBandsFilter bollingerFilter;
        private VolumeFilter volumeFilter;
        private RSICondition rsiCondition;
        private BollingerCondition bollingerCondition;

        public TechnicalFilterChainBuilder withRSI(RSIFilter rsiFilter) {
            this.rsiFilter = rsiFilter;
            return this;
        }

        public TechnicalFilterChainBuilder withBollingerBands(BollingerBandsFilter bbFilter) {
            this.bollingerFilter = bbFilter;
            return this;
        }

        public TechnicalFilterChainBuilder withVolume(VolumeFilter volumeFilter) {
            this.volumeFilter = volumeFilter;
            return this;
        }

        public TechnicalFilterChainBuilder rsiCondition(RSICondition condition) {
            this.rsiCondition = condition;
            return this;
        }

        public TechnicalFilterChainBuilder bollingerCondition(BollingerCondition condition) {
            this.bollingerCondition = condition;
            return this;
        }

        public TechnicalFilterChain build() {
            TechnicalIndicators indicators = TechnicalIndicators.builder()
                    .rsiFilter(rsiFilter)
                    .bollingerFilter(bollingerFilter)
                    .volumeFilter(volumeFilter)
                    .build();

            FilterConditions conditions = FilterConditions.builder()
                    .rsiCondition(rsiCondition)
                    .bollingerCondition(bollingerCondition)
                    .build();

            return new TechnicalFilterChain(indicators, conditions);
        }
    }
}
