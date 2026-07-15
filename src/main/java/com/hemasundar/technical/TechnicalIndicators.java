package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;
import java.util.HashMap;

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
 *         .maFilters(new HashMap<>(Map.of(
 *             20, MovingAverageFilter.builder().period(20).build(),
 *             50, MovingAverageFilter.builder().period(50).build()
 *         )))
 *         .emaFilters(new HashMap<>(Map.of(
 *             9, ExponentialMovingAverageFilter.builder().period(9).build(),
 *             21, ExponentialMovingAverageFilter.builder().period(21).build()
 *         )))
 *         .volumeFilter(VolumeFilter.builder().minVolume(1_000_000L).build())
 *         .build();
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
public class TechnicalIndicators {

    public static TechnicalIndicators createDefaults() {
        return TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().period(14).oversoldThreshold(30.0).overboughtThreshold(70.0).build())
                .bollingerFilter(BollingerBandsFilter.builder().period(20).standardDeviations(2.0).build())
                .maFilters(new HashMap<>(Map.of(
                        20, MovingAverageFilter.builder().period(20).build(),
                        50, MovingAverageFilter.builder().period(50).build(),
                        100, MovingAverageFilter.builder().period(100).build(),
                        200, MovingAverageFilter.builder().period(200).build()
                )))
                .emaFilters(new HashMap<>(Map.of(
                        9, ExponentialMovingAverageFilter.builder().period(9).build(),
                        21, ExponentialMovingAverageFilter.builder().period(21).build()
                )))
                .volumeFilter(VolumeFilter.builder().build())
                .atrFilter(AverageTrueRangeFilter.builder().period(14).build())
                .build();
    }

    /**
     * RSI (Relative Strength Index) indicator configuration.
     */
    private final RSIFilter rsiFilter;

    /**
     * Bollinger Bands indicator configuration.
     */
    private final BollingerBandsFilter bollingerFilter;

    /**
     * Map of Moving Average filters, keyed by their period (e.g., 20, 50).
     */
    @Builder.Default
    private final Map<Integer, MovingAverageFilter> maFilters = new HashMap<>();

    /**
     * Map of Exponential Moving Average filters, keyed by their period (e.g., 9, 21).
     */
    @Builder.Default
    private final Map<Integer, ExponentialMovingAverageFilter> emaFilters = new HashMap<>();

    /**
     * Volume filter configuration.
     */
    private final VolumeFilter volumeFilter;

    /**
     * Average True Range (ATR) configuration.
     */
    private final AverageTrueRangeFilter atrFilter;
}
