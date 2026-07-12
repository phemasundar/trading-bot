package com.hemasundar.technical;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.function.Function;

/**
 * Centralized evaluator for a list of {@link MathExpression} rules.
 *
 * <p>
 * This class is the single source of truth for evaluating all math-formatted
 * technical filter conditions. It removes the duplication of hardcoded
 * {@code >}/{@code <} logic that previously existed in
 * {@link TechnicalScreener}, {@link PriceDropScreener}, and
 * {@link StrategiesConfigLoader}.
 *
 * <p>
 * Usage:
 *
 * <pre>
 * List&lt;MathExpression&gt; rules = ...;
 * boolean passes = MathExpressionEvaluator.evaluateAll(rules, variableName -&gt; {
 *     return resolveValueFromScreeningResult(result, variableName);
 * });
 * </pre>
 */
public final class MathExpressionEvaluator {

    private MathExpressionEvaluator() {
        // Utility class
    }

    /**
     * Evaluates all expressions against the given value provider.
     *
     * @param expressions list of expressions; empty/null list evaluates to true
     * @param valueProvider function that resolves a variable name to a value
     * @return true if all expressions pass, false otherwise
     */
    public static boolean evaluateAll(List<MathExpression> expressions, Function<String, Double> valueProvider) {
        if (CollectionUtils.isEmpty(expressions)) {
            return true;
        }
        for (MathExpression expression : expressions) {
            if (!expression.evaluate(valueProvider)) {
                return false;
            }
        }
        return true;
    }
}
