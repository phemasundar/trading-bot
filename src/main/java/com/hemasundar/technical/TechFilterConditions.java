package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.ArrayList;
import com.hemasundar.config.StrategiesConfig.PriceCondition;
import com.hemasundar.config.StrategiesConfig.SmaCondition;
import com.hemasundar.config.StrategiesConfig.Position;

/**
 * Container for filter conditions to check.
 * Defines WHAT CONDITIONS to look for in the indicators.
 * Separated from indicator definitions to allow reuse of indicators with
 * different conditions.
 * 
 * Example:
 * 
 * <pre>
 * FilterConditions oversoldConditions = FilterConditions.builder()
 *         .rsiCondition(RSICondition.BULLISH_CROSSOVER) // RSI crossed from <30 to >=30
 *         .bollingerCondition(BollingerCondition.LOWER_BAND) // Price at lower band
 *         .priceConditions(List.of(
 *             new PriceCondition() {{ setPeriod(20); setPosition(Position.BELOW); }}, // Price below SMA(20)
 *             new PriceCondition() {{ setPeriod(50); setPosition(Position.BELOW); }}  // Price below SMA(50)
 *         ))
 *         .minVolume(1_000_000L) // Minimum 1M shares
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class TechFilterConditions {

    /**
     * RSI condition to check: OVERSOLD, OVERBOUGHT, BULLISH_CROSSOVER, BEARISH_CROSSOVER, or CUSTOM_RANGE.
     */
    private final RSICondition rsiCondition;

    /**
     * Minimum RSI value for CUSTOM_RANGE condition.
     */
    private final Double minRsi;

    /**
     * Maximum RSI value for CUSTOM_RANGE condition.
     */
    private final Double maxRsi;

    /**
     * Bollinger Band condition to check: LOWER_BAND or UPPER_BAND.
     */
    private final BollingerCondition bollingerCondition;

    /**
     * Minimum volume threshold. Stock must have at least this many shares traded.
     * Set to 0 or null to disable volume check.
     */
    private final Long minVolume;

    /**
     * Maximum volume threshold.
     */
    private final Long maxVolume;

    /**
     * Volume condition (e.g. MIN_VOLUME, STABLE_OR_EXPANDING).
     */
    private final VolumeCondition volumeCondition;

    // Parameters for volume STABLE_OR_EXPANDING condition
    private final Integer volumeShortSmaPeriod;
    private final Integer volumeLongSmaPeriod;
    private final Double volumeThresholdPercent;

    /**
     * Dynamic conditions for comparing price to SMA.
     */
    @Builder.Default
    private final List<PriceCondition> priceConditions = new ArrayList<>();

    /**
     * Dynamic conditions for comparing an SMA to another SMA.
     */
    @Builder.Default
    private final List<SmaCondition> smaConditions = new ArrayList<>();

    /**
     * Rules for PRICE_DROP and HIGH_52W_DROP screeners (e.g. >= 10.0).
     */
    @Builder.Default
    private final List<NumericRule> priceDropRules = new ArrayList<>();

    /**
     * Rolling period (in days) for Historical Volatility calculation.
     */
    @Builder.Default
    private final Integer hvPeriod = 20;

    /**
     * Rules for Historical Volatility Rank (percentile).
     * Example: >= 25.0 means current rolling HV must be >= the 25th percentile of the past year.
     */
    @Builder.Default
    private final List<NumericRule> hvRules = new ArrayList<>();

    /**
     * Number of trading days to look back for PRICE_DROP screener.
     * 0 = intraday (uses daily change), >0 = multi-day lookback.
     */
    private final Integer lookbackDays;

    /**
     * Minimum historical volatility required.
     */
    private final Double minHistoricalVolatility;

    /**
     * Maximum historical volatility allowed.
     */
    private final Double maxHistoricalVolatility;

    /**
     * Returns a readable summary of the conditions.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();

        if (rsiCondition != null) {
            if (rsiCondition == RSICondition.CUSTOM_RANGE) {
                sb.append("RSI: ").append(String.format("%.1f-%.1f", minRsi, maxRsi)).append(" | ");
            } else {
                sb.append("RSI: ").append(rsiCondition.name()).append(" | ");
            }
        }
        if (bollingerCondition != null) {
            sb.append("BB: ").append(bollingerCondition.name()).append(" | ");
        }
        if (priceConditions != null) {
            for (PriceCondition condition : priceConditions) {
                sb.append("Price ")
                  .append(condition.getPosition() == Position.ABOVE ? ">" : "<")
                  .append(" SMA")
                  .append(condition.getPeriod())
                  .append(" | ");
            }
        }
        
        if (smaConditions != null) {
            for (SmaCondition condition : smaConditions) {
                sb.append("SMA")
                  .append(condition.getPeriod1())
                  .append(" ")
                  .append(condition.getPosition() == Position.ABOVE ? ">" : "<")
                  .append(" SMA")
                  .append(condition.getPeriod2())
                  .append(" | ");
            }
        }
        if (volumeCondition != null) {
            switch (volumeCondition) {
                case MIN_VOLUME:
                    if (minVolume != null) sb.append(String.format("Volume >= %,d | ", minVolume));
                    break;
                case MAX_VOLUME:
                    if (maxVolume != null) sb.append(String.format("Volume <= %,d | ", maxVolume));
                    break;
                case RANGE_VOLUME:
                    if (minVolume != null && maxVolume != null) sb.append(String.format("Volume %,d - %,d | ", minVolume, maxVolume));
                    break;
                case SMA_COMPARISON:
                    sb.append(String.format("Volume SMA%d >= SMA%d * %.0f%% | ", 
                        volumeShortSmaPeriod != null ? volumeShortSmaPeriod : 20, 
                        volumeLongSmaPeriod != null ? volumeLongSmaPeriod : 50, 
                        volumeThresholdPercent != null ? volumeThresholdPercent : 90.0));
                    break;
            }
        }
        if (hvRules != null) {
            for (NumericRule rule : hvRules) {
                sb.append(String.format("HV(%d) Rank %s | ", hvPeriod, rule.toString()));
            }
        }
        if (priceDropRules != null) {
            for (NumericRule rule : priceDropRules) {
                sb.append(String.format("Price Drop %s%% | ", rule.toString()));
            }
        }

        if (sb.length() == 0)
            return "No conditions set";
        String result = sb.toString();
        return result.endsWith(" | ") ? result.substring(0, result.length() - 3) : result;
    }
}
