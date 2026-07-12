package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.ArrayList;

/**
 * Container for technical filter conditions.
 *
 * <p>
 * All numeric comparisons are represented as {@link MathExpression} objects and
 * evaluated centrally by {@link MathExpressionEvaluator}. RSI and Bollinger
 * Band conditions keep their enum representation in this object for display
 * purposes, but they are also converted to math expressions during loading.
 */
@Getter
@Builder
public class TechFilterConditions {

    /**
     * RSI condition to check: OVERSOLD, OVERBOUGHT, BULLISH_CROSSOVER, BEARISH_CROSSOVER, or CUSTOM_RANGE.
     * Retained for display; the actual evaluation uses {@link #filterExpressions}.
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
     * Retained for display; the actual evaluation uses {@link #filterExpressions}.
     */
    private final BollingerCondition bollingerCondition;

    /**
     * Unified list of math expressions representing every configured technical
     * filter condition. Populated by {@link com.hemasundar.config.StrategiesConfigLoader}.
     */
    @Builder.Default
    private final List<MathExpression> filterExpressions = new ArrayList<>();

    /**
     * Rolling period (in days) for Historical Volatility calculation.
     */
    @Builder.Default
    private final Integer hvPeriod = 20;

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
            if (rsiCondition == RSICondition.CUSTOM_RANGE) {
                sb.append("RSI: ").append(String.format("%.1f-%.1f", minRsi, maxRsi)).append(" | ");
            } else {
                sb.append("RSI: ").append(rsiCondition.name()).append(" | ");
            }
        }
        if (bollingerCondition != null) {
            sb.append("BB: ").append(bollingerCondition.name()).append(" | ");
        }
        if (filterExpressions != null && !filterExpressions.isEmpty()) {
            for (MathExpression expression : filterExpressions) {
                sb.append(expression.toString()).append(" | ");
            }
        }

        if (sb.length() == 0)
            return "No conditions set";
        String result = sb.toString();
        return result.endsWith(" | ") ? result.substring(0, result.length() - 3) : result;
    }
}
