package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;

/**
 * Container for technical indicator instances.
 * Defines WHAT indicators to use with their settings.
 * The indicators are reusable across multiple filter chains with different
 * conditions.
 * 
 * Example:
 * 
 * <pre>
 * TechnicalIndicators indicators = TechnicalIndicators.builder()
 *         .rsiFilter(RSIFilter.builder().period(14).oversoldThreshold(30.0).overboughtThreshold(70.0).build())
 *         .bollingerFilter(BollingerBandsFilter.builder().period(20).standardDeviations(2.0).build())
 *         .ma20Filter(MovingAverageFilter.builder().period(20).build())
 *         .ma50Filter(MovingAverageFilter.builder().period(50).build())
 *         .volumeFilter(VolumeFilter.builder().minVolume(1_000_000L).build())
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class TechnicalIndicators {

    /**
     * RSI (Relative Strength Index) indicator configuration.
     */
    private final RSIFilter rsiFilter;

    /**
     * Bollinger Bands indicator configuration.
     */
    private final BollingerBandsFilter bollingerFilter;

    /**
     * 20-day Moving Average filter.
     */
    private final MovingAverageFilter ma20Filter;

    /**
     * 50-day Moving Average filter.
     */
    private final MovingAverageFilter ma50Filter;

    /**
     * 100-day Moving Average filter.
     */
    private final MovingAverageFilter ma100Filter;

    /**
     * 200-day Moving Average filter.
     */
    private final MovingAverageFilter ma200Filter;

    /**
     * Volume filter configuration.
     */
    private final VolumeFilter volumeFilter;
}
