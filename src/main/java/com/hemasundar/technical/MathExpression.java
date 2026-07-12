package com.hemasundar.technical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * A single, generic math comparison expression for technical screening.
 *
 * <p>
 * Supports comparisons between a left-hand variable (e.g. {@code RSI},
 * {@code PRICE}, {@code SMA50}) and a right-hand side that may be a numeric
 * constant or another variable. The right-hand side can also be scaled by a
 * percentage, enabling expressions such as
 * {@code VOLUME_SMA20 >= VOLUME_SMA50 * 90%}.
 *
 * <p>
 * This class eliminates the need for filter-specific evaluation logic
 * (hardcoded {@code >}/{@code <} checks for RSI, SMA, volume, etc.) by
 * delegating every comparison to {@link RelationalOperator}.
 *
 * <p>
 * Example expressions:
 * <ul>
 *   <li>{@code RSI >= 30}</li>
 *   <li>{@code PRICE >= SMA50}</li>
 *   <li>{@code SMA50 >= SMA200}</li>
 *   <li>{@code VOLUME >= 1000000}</li>
 *   <li>{@code VOLUME_SMA20 >= VOLUME_SMA50 * 90%}</li>
 *   <li>{@code HV_RANK >= 25}</li>
 *   <li>{@code DROP_PCT >= 10}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MathExpression {

    private String leftVariable;
    private RelationalOperator operator;
    private String rightVariable;

    /**
     * Optional scale applied to the right-hand side value.
     * A value of {@code 0.9} represents "90%". Defaults to {@code 1.0}.
     */
    @Builder.Default
    private Double rightScale = 1.0;

    /**
     * Evaluates this expression against a value provider.
     *
     * @param valueProvider provides numeric values for variable names
     * @return true if the expression holds, false if any value is missing
     */
    public boolean evaluate(java.util.function.Function<String, Double> valueProvider) {
        if (StringUtils.isBlank(leftVariable) || operator == null || StringUtils.isBlank(rightVariable)) {
            return false;
        }

        Double leftValue = valueProvider.apply(leftVariable.trim().toUpperCase());
        if (leftValue == null) {
            return false;
        }

        Double rightValue = resolveRightValue(valueProvider);
        if (rightValue == null) {
            return false;
        }

        return operator.evaluate(leftValue, rightValue);
    }

    private Double resolveRightValue(java.util.function.Function<String, Double> valueProvider) {
        String trimmed = rightVariable.trim();
        Double baseValue;
        try {
            baseValue = Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            baseValue = valueProvider.apply(trimmed.toUpperCase());
        }
        if (baseValue == null) {
            return null;
        }
        double scale = rightScale != null ? rightScale : 1.0;
        return baseValue * scale;
    }

    @Override
    public String toString() {
        String right = rightVariable;
        if (rightScale != null && rightScale != 1.0) {
            right = right + " * " + String.format("%.0f%%", rightScale * 100);
        }
        return leftVariable + " " + operator.getSymbol() + " " + right;
    }
}
