package com.hemasundar.technical;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for fundamental filter conditions (e.g. Market Cap).
 *
 * <p>
 * Mirrors {@link TechFilterConditions} but for fundamental data sourced from
 * the Schwab Quotes / Fundamental API. All numeric comparisons are represented
 * as {@link MathExpression} objects and evaluated centrally by
 * {@link MathExpressionEvaluator}.
 *
 * <p>
 * Supported variable names:
 * <ul>
 *   <li>{@code MARKET_CAP_B} — market capitalisation in billions</li>
 * </ul>
 *
 * <p>
 * Example JSON in {@code strategies-config.json}:
 * <pre>
 * "fundamentalFilters": {
 *     "MARKET_CAP": {
 *         "conditions": ["MARKET_CAP_B >= 10"]
 *     }
 * }
 * </pre>
 */
@Getter
@Builder
public class FundamentalFilterConditions {

    /**
     * Unified list of math expressions representing every configured fundamental
     * filter condition. Populated by
     * {@link com.hemasundar.config.StrategiesConfigLoader}.
     */
    @Builder.Default
    private final List<MathExpression> filterExpressions = new ArrayList<>();

    /**
     * Returns a readable summary of the fundamental conditions.
     */
    public String getSummary() {
        if (filterExpressions == null || filterExpressions.isEmpty()) {
            return "No fundamental conditions set";
        }
        StringBuilder sb = new StringBuilder();
        for (MathExpression expression : filterExpressions) {
            sb.append(expression.toString()).append(" | ");
        }
        String result = sb.toString();
        return result.endsWith(" | ") ? result.substring(0, result.length() - 3) : result;
    }
}
