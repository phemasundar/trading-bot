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

    private static final String IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_.]*";

    private static final Pattern FULL_EXPRESSION_PATTERN = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*(>=|<=|==|>|<)\\s*(.+)$");

    private static final Pattern SCALED_OFFSET_RIGHT_PATTERN = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*\\*\\s*(\\d+(?:\\.\\d+)?)(%?)\\s*([+-])\\s*(\\d+(?:\\.\\d+)?)$");

    private static final Pattern SCALED_RIGHT_PATTERN_PCT = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*\\*\\s*(\\d+(?:\\.\\d+)?)%$");

    private static final Pattern SCALED_RIGHT_PATTERN_MULT_1 = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*\\*\\s*(\\d+(?:\\.\\d+)?)$"); // VAR * X

    private static final Pattern SCALED_RIGHT_PATTERN_MULT_2 = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s*\\*\\s*(" + IDENTIFIER_PATTERN + ")$"); // X * VAR

    private static final Pattern OFFSET_RIGHT_PATTERN_ADD = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*\\+\\s*(\\d+(?:\\.\\d+)?)$"); // VAR + X

    private static final Pattern OFFSET_RIGHT_PATTERN_SUB = Pattern.compile(
            "^(" + IDENTIFIER_PATTERN + ")\\s*-\\s*(\\d+(?:\\.\\d+)?)$"); // VAR - X

    private static final Pattern LITERAL_PCT_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)%$"); // e.g. 20%

    /**
     * Parses a full expression (e.g. {@code "PRICE >= SMA50"} or {@code "SHORT_LEG.DELTA <= 0.2"} or {@code "EARNINGS_NEAREST_TO_DTE <= DTE - 5"})
     * into a {@link MathExpression}.
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
        double offset = 0.0;
        String rightVariable = rightSide;

        Matcher scaledOffsetMatcher = SCALED_OFFSET_RIGHT_PATTERN.matcher(rightSide);
        Matcher scalePctMatcher = SCALED_RIGHT_PATTERN_PCT.matcher(rightSide);
        Matcher scaleMult1Matcher = SCALED_RIGHT_PATTERN_MULT_1.matcher(rightSide);
        Matcher scaleMult2Matcher = SCALED_RIGHT_PATTERN_MULT_2.matcher(rightSide);
        Matcher offsetAddMatcher = OFFSET_RIGHT_PATTERN_ADD.matcher(rightSide);
        Matcher offsetSubMatcher = OFFSET_RIGHT_PATTERN_SUB.matcher(rightSide);
        Matcher literalPctMatcher = LITERAL_PCT_PATTERN.matcher(rightSide);

        if (scaledOffsetMatcher.matches()) {
            rightVariable = scaledOffsetMatcher.group(1);
            boolean isPct = "%".equals(scaledOffsetMatcher.group(3));
            scale = Double.parseDouble(scaledOffsetMatcher.group(2)) / (isPct ? 100.0 : 1.0);
            double offsetVal = Double.parseDouble(scaledOffsetMatcher.group(5));
            offset = "-".equals(scaledOffsetMatcher.group(4)) ? -offsetVal : offsetVal;
        } else if (scalePctMatcher.matches()) {
            rightVariable = scalePctMatcher.group(1);
            scale = Double.parseDouble(scalePctMatcher.group(2)) / 100.0;
        } else if (scaleMult1Matcher.matches()) {
            rightVariable = scaleMult1Matcher.group(1);
            scale = Double.parseDouble(scaleMult1Matcher.group(2));
        } else if (scaleMult2Matcher.matches()) {
            scale = Double.parseDouble(scaleMult2Matcher.group(1));
            rightVariable = scaleMult2Matcher.group(2);
        } else if (offsetAddMatcher.matches()) {
            rightVariable = offsetAddMatcher.group(1);
            offset = Double.parseDouble(offsetAddMatcher.group(2));
        } else if (offsetSubMatcher.matches()) {
            rightVariable = offsetSubMatcher.group(1);
            offset = -Double.parseDouble(offsetSubMatcher.group(2));
        } else if (literalPctMatcher.matches()) {
            rightVariable = literalPctMatcher.group(1);
        }

        Validate.isTrue(isVariableOrNumber(rightVariable),
                "Invalid right-hand side in expression: " + expression);

        return MathExpression.builder()
                .leftVariable(left)
                .operator(op)
                .rightVariable(rightVariable)
                .rightScale(scale)
                .rightOffset(offset)
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
        return value.matches("^[A-Za-z_][A-Za-z0-9_.]*$|^-?\\d+(?:\\.\\d+)?$");
    }
}
