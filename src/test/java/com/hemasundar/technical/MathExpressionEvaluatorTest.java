package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class MathExpressionEvaluatorTest {

    @Test
    public void testEvaluateAll_Pass() {
        List<MathExpression> expressions = List.of(
                MathExpression.builder()
                        .leftVariable("RSI")
                        .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                        .rightVariable("30")
                        .build(),
                MathExpression.builder()
                        .leftVariable("VOLUME")
                        .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                        .rightVariable("1000000")
                        .build()
        );

        boolean result = MathExpressionEvaluator.evaluateAll(expressions, Map.of(
                "RSI", 35.0,
                "VOLUME", 2_000_000.0
        )::get);
        Assert.assertTrue(result);
    }

    @Test
    public void testEvaluateAll_Fail() {
        List<MathExpression> expressions = List.of(
                MathExpression.builder()
                        .leftVariable("RSI")
                        .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                        .rightVariable("30")
                        .build()
        );

        boolean result = MathExpressionEvaluator.evaluateAll(expressions, Map.of("RSI", 25.0)::get);
        Assert.assertFalse(result);
    }

    @Test
    public void testEvaluateAll_EmptyList() {
        Assert.assertTrue(MathExpressionEvaluator.evaluateAll(List.of(), var -> null));
        Assert.assertTrue(MathExpressionEvaluator.evaluateAll(null, var -> null));
    }

    @Test
    public void testEvaluateAll_ScaledExpression() {
        List<MathExpression> expressions = List.of(
                MathExpression.builder()
                        .leftVariable("VOLUME_SMA20")
                        .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                        .rightVariable("VOLUME_SMA50")
                        .rightScale(0.9)
                        .build()
        );

        Assert.assertTrue(MathExpressionEvaluator.evaluateAll(expressions, Map.of(
                "VOLUME_SMA20", 100.0,
                "VOLUME_SMA50", 100.0
        )::get));
    }

    @Test
    public void testEvaluateAll_MissingVariable() {
        List<MathExpression> expressions = List.of(
                MathExpression.builder()
                        .leftVariable("RSI")
                        .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                        .rightVariable("30")
                        .build()
        );

        Assert.assertFalse(MathExpressionEvaluator.evaluateAll(expressions, var -> null));
    }
}
