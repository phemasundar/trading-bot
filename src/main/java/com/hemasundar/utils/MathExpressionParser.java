package com.hemasundar.utils;

import com.hemasundar.technical.MathExpression;
import com.hemasundar.technical.RelationalOperator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parses math-formatted filter strings into {@link MathExpression} objects.
 *
 * <p>
 * Supported formats:
 * <ul>
 *   <li>Simple threshold: {@code >= 25}, {@code < 5}</li>
 *   <li>Variable vs constant: {@code RSI >= 30}, {@code VOLUME >= 1000000}</li>
 *   <li>Variable vs variable: {@code PRICE >= SMA50}, {@code SMA50 >= SMA200}</li>
 *   <li>Scaled variable: {@code VOLUME_SMA20 >= VOLUME_SMA50 * 90%}</li>
 * </ul>
 *
 * <p>
 * For simple threshold rules (no left-hand variable), the right-hand side is
 * assumed to be a numeric constant and the parser returns an expression with a
 * caller-supplied left-hand variable. This keeps backward compatibility with
 * existing {@code HISTORICAL_VOLATILITY} and {@code PRICE_DROP} configurations.
 */
@UtilityClass
public final class MathExpressionParser {

    private static final Pattern SCALED_RIGHT_PATTERN = Pattern.compile(
            "^(\\w+)\\s*\\*\\s*(\\d+(?:\\.\\d+)?)%$");

    private static final Pattern FULL_EXPRESSION_PATTERN = Pattern.compile(
            "^(\\w+)\\s*(>=|<=|==|>|<)\\s*(.+)$");

    /**
     * Parses a full expression (e.g. {@code "PRICE >= SMA50"}) into a
     * {@link MathExpression}.
     *
     * @param expression full expression string
     * @return parsed expression, or null if the expression is blank
     * @throws IllegalArgumentException if the expression cannot be parsed
     */
    public static MathExpression parseExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            return null;
        }
        String trimmed = expression.trim();
        Matcher matcher = FULL_EXPRESSION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid expression: " + expression);
        }

        String left = matcher.group(1);
        RelationalOperator op = RelationalOperator.fromSymbol(matcher.group(2));
        String rightSide = matcher.group(3).trim();

        double scale = 1.0;
        String rightVariable = rightSide;

        Matcher scaleMatcher = SCALED_RIGHT_PATTERN.matcher(rightSide);
        if (scaleMatcher.matches()) {
            rightVariable = scaleMatcher.group(1);
            scale = Double.parseDouble(scaleMatcher.group(2)) / 100.0;
        }

        Validate.isTrue(isVariableOrNumber(rightVariable),
                "Invalid right-hand side in expression: " + expression);

        return MathExpression.builder()
                .leftVariable(left)
                .operator(op)
                .rightVariable(rightVariable)
                .rightScale(scale)
                .build();
    }

    /**
     * Parses a list of expressions.
     *
     * @param rules list of rule strings
     * @return list of parsed expressions
     */
    public static List<MathExpression> parseRules(List<String> rules) {
        List<MathExpression> expressions = new ArrayList<>();
        if (rules == null) {
            return expressions;
        }
        for (String rule : rules) {
            MathExpression expression = parseExpression(rule);
            if (expression != null) {
                expressions.add(expression);
            }
        }
        return expressions;
    }

    private static boolean isVariableOrNumber(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return value.matches("^[A-Za-z_][A-Za-z0-9_]*$|^-?\\d+(?:\\.\\d+)?$");
    }
}
