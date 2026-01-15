package com.hemasundar.pojos.technicalfilters;

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
     * Volume filter configuration.
     */
    private final VolumeFilter volumeFilter;
}
