package com.hemasundar.utils;

import com.hemasundar.technical.MathExpression;
import com.hemasundar.technical.RelationalOperator;
import org.apache.commons.lang3.StringUtils;

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
public final class MathExpressionParser {

    private static final Pattern SCALED_RIGHT_PATTERN = Pattern.compile(
            "^(\\w+)\\s*\\*\\s*(\\d+(?:\\.\\d+)?)%$");

    private static final Pattern FULL_EXPRESSION_PATTERN = Pattern.compile(
            "^(\\w+)\\s*(>=|<=|==|>|<)\\s*(.+)$");

    private MathExpressionParser() {
        // Utility class
    }

    /**
     * Parses a simple threshold rule (e.g. {@code ">= 25"}) into a
     * {@link MathExpression} using the supplied left-hand variable.
     *
     * @param rule threshold rule string
     * @param leftVariable variable name to use on the left-hand side
     * @return parsed expression, or null if the rule is blank
     * @throws IllegalArgumentException if the rule cannot be parsed
     */
    public static MathExpression parseThresholdRule(String rule, String leftVariable) {
        if (StringUtils.isBlank(rule)) {
            return null;
        }
        RelationalOperator op = findOperator(rule.trim());
        if (op == null) {
            throw new IllegalArgumentException("Invalid operator in rule: " + rule);
        }
        String valueStr = rule.substring(op.getSymbol().length()).trim().replace(",", "");
        validateNumber(valueStr, rule);
        return MathExpression.builder()
                .leftVariable(leftVariable)
                .operator(op)
                .rightVariable(valueStr)
                .rightScale(1.0)
                .build();
    }

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

        if (!isVariableOrNumber(rightVariable)) {
            throw new IllegalArgumentException("Invalid right-hand side in expression: " + expression);
        }

        return MathExpression.builder()
                .leftVariable(left)
                .operator(op)
                .rightVariable(rightVariable)
                .rightScale(scale)
                .build();
    }

    /**
     * Parses a list of expressions or threshold rules.
     *
     * @param rules list of rule strings
     * @param defaultLeftVariable variable name used for simple threshold rules
     * @return list of parsed expressions
     */
    public static List<MathExpression> parseRules(List<String> rules, String defaultLeftVariable) {
        List<MathExpression> expressions = new ArrayList<>();
        if (rules == null) {
            return expressions;
        }
        for (String rule : rules) {
            MathExpression expression = parse(rule, defaultLeftVariable);
            if (expression != null) {
                expressions.add(expression);
            }
        }
        return expressions;
    }

    /**
     * Parses a single rule that may be either a full expression or a simple
     * threshold rule.
     */
    public static MathExpression parse(String rule, String defaultLeftVariable) {
        if (StringUtils.isBlank(rule)) {
            return null;
        }
        String trimmed = rule.trim();
        // A simple threshold rule starts with an operator (e.g. ">= 25")
        if (startsWithOperator(trimmed)) {
            return parseThresholdRule(trimmed, defaultLeftVariable);
        }
        return parseExpression(trimmed);
    }

    private static RelationalOperator findOperator(String rule) {
        for (RelationalOperator op : RelationalOperator.values()) {
            if (rule.startsWith(op.getSymbol())) {
                return op;
            }
        }
        return null;
    }

    private static boolean startsWithOperator(String rule) {
        return findOperator(rule) != null;
    }

    private static void validateNumber(String valueStr, String originalRule) {
        try {
            Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in rule: " + originalRule, e);
        }
    }

    private static boolean isVariableOrNumber(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        return value.matches("^[A-Za-z_][A-Za-z0-9_]*$|^-?\\d+(?:\\.\\d+)?$");
    }
}
