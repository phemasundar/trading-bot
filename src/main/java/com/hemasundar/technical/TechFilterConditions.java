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
 *             new PriceCondition() {{ setPeriod(20); setPosition(Position.BELOW); }}, // Price below MA(20)
 *             new PriceCondition() {{ setPeriod(50); setPosition(Position.BELOW); }}  // Price below MA(50)
 *         ))
 *         .minVolume(1_000_000L) // Minimum 1M shares
 *         .build();
 * </pre>
 */
@Getter
@Builder
public class TechFilterConditions {

    /**
     * RSI condition to check: OVERSOLD, OVERBOUGHT, BULLISH_CROSSOVER, or
     * BEARISH_CROSSOVER.
     */
    private final RSICondition rsiCondition;

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
     * Minimum drop percentage for PRICE_DROP and HIGH_52W_DROP screeners.
     */
    private final Double minDropPercent;

    /**
     * Number of trading days to look back for PRICE_DROP screener.
     * 0 = intraday (uses daily change), >0 = multi-day lookback.
     */
    private final Integer lookbackDays;

    /**
     * Returns a readable summary of the conditions.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();

        if (rsiCondition != null) {
            sb.append("RSI: ").append(rsiCondition.name()).append(" | ");
        }
        if (bollingerCondition != null) {
            sb.append("BB: ").append(bollingerCondition.name()).append(" | ");
        }
        if (priceConditions != null) {
            for (PriceCondition condition : priceConditions) {
                sb.append("Price ")
                  .append(condition.getPosition() == Position.ABOVE ? ">" : "<")
                  .append(" MA")
                  .append(condition.getPeriod())
                  .append(" | ");
            }
        }
        
        if (smaConditions != null) {
            for (SmaCondition condition : smaConditions) {
                sb.append("MA")
                  .append(condition.getPeriod1())
                  .append(" ")
                  .append(condition.getPosition() == Position.ABOVE ? ">" : "<")
                  .append(" MA")
                  .append(condition.getPeriod2())
                  .append(" | ");
            }
        }
        if (minVolume != null && minVolume > 0) {
            sb.append(String.format("Volume >= %,d", minVolume));
        }

        if (sb.length() == 0)
            return "No conditions set";
        String result = sb.toString();
        return result.endsWith(" | ") ? result.substring(0, result.length() - 3) : result;
    }
}
