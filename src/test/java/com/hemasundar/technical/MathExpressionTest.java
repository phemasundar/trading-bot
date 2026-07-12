package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class MathExpressionTest {

    @Test
    public void testConstantRightSide() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("RSI")
                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                .rightVariable("30")
                .build();

        Assert.assertTrue(expr.evaluate(var -> switch (var) {
            case "RSI" -> 35.0;
            default -> null;
        }));

        Assert.assertFalse(expr.evaluate(var -> switch (var) {
            case "RSI" -> 25.0;
            default -> null;
        }));
    }

    @Test
    public void testVariableRightSide() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("PRICE")
                .operator(RelationalOperator.GREATER_THAN)
                .rightVariable("SMA50")
                .build();

        Assert.assertTrue(expr.evaluate(var -> switch (var) {
            case "PRICE" -> 105.0;
            case "SMA50" -> 100.0;
            default -> null;
        }));

        Assert.assertFalse(expr.evaluate(var -> switch (var) {
            case "PRICE" -> 95.0;
            case "SMA50" -> 100.0;
            default -> null;
        }));
    }

    @Test
    public void testScaledRightSide() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("VOLUME_SMA20")
                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                .rightVariable("VOLUME_SMA50")
                .rightScale(0.9)
                .build();

        // 100 >= 100 * 0.9 = 90 -> true
        Assert.assertTrue(expr.evaluate(Map.of("VOLUME_SMA20", 100.0, "VOLUME_SMA50", 100.0)::get));
        // 80 >= 100 * 0.9 = 90 -> false
        Assert.assertFalse(expr.evaluate(Map.of("VOLUME_SMA20", 80.0, "VOLUME_SMA50", 100.0)::get));
    }

    @Test
    public void testMissingLeftValue() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("RSI")
                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                .rightVariable("30")
                .build();

        Assert.assertFalse(expr.evaluate(var -> null));
    }

    @Test
    public void testMissingRightValue() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("PRICE")
                .operator(RelationalOperator.GREATER_THAN)
                .rightVariable("SMA50")
                .build();

        Assert.assertFalse(expr.evaluate(var -> switch (var) {
            case "PRICE" -> 100.0;
            default -> null;
        }));
    }

    @Test
    public void testEqualityWithinEpsilon() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("PRICE")
                .operator(RelationalOperator.EQUAL)
                .rightVariable("100")
                .build();

        Assert.assertTrue(expr.evaluate(var -> switch (var) {
            case "PRICE" -> 100.00005;
            default -> null;
        }));
    }

    @Test
    public void testToString() {
        MathExpression expr = MathExpression.builder()
                .leftVariable("VOLUME_SMA20")
                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                .rightVariable("VOLUME_SMA50")
                .rightScale(0.9)
                .build();

        Assert.assertEquals(expr.toString(), "VOLUME_SMA20 >= VOLUME_SMA50 * 90%");
    }
}
