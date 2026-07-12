package com.hemasundar.utils;

import com.hemasundar.technical.MathExpression;
import com.hemasundar.technical.RelationalOperator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class MathExpressionParserTest {


    @Test
    public void testParseExpression_PriceVsSma() {
        MathExpression expr = MathExpressionParser.parseExpression("PRICE >= SMA50");
        Assert.assertNotNull(expr);
        Assert.assertEquals(expr.getLeftVariable(), "PRICE");
        Assert.assertEquals(expr.getOperator(), RelationalOperator.GREATER_THAN_OR_EQUAL);
        Assert.assertEquals(expr.getRightVariable(), "SMA50");
    }

    @Test
    public void testParseExpression_SmaVsSma() {
        MathExpression expr = MathExpressionParser.parseExpression("SMA50 >= SMA200");
        Assert.assertNotNull(expr);
        Assert.assertEquals(expr.getLeftVariable(), "SMA50");
        Assert.assertEquals(expr.getRightVariable(), "SMA200");
    }

    @Test
    public void testParseExpression_VolumeThreshold() {
        MathExpression expr = MathExpressionParser.parseExpression("VOLUME >= 1000000");
        Assert.assertNotNull(expr);
        Assert.assertEquals(expr.getLeftVariable(), "VOLUME");
        Assert.assertEquals(expr.getRightVariable(), "1000000");
    }

    @Test
    public void testParseExpression_ScaledVolumeSma() {
        MathExpression expr = MathExpressionParser.parseExpression(
                "VOLUME_SMA20 >= VOLUME_SMA50 * 90%");
        Assert.assertNotNull(expr);
        Assert.assertEquals(expr.getLeftVariable(), "VOLUME_SMA20");
        Assert.assertEquals(expr.getRightVariable(), "VOLUME_SMA50");
        Assert.assertEquals(expr.getRightScale(), 0.9, 0.0001);
    }

    @Test
    public void testParse_AllOperators() {
        Assert.assertEquals(MathExpressionParser.parseExpression("RSI > 70").getOperator(), RelationalOperator.GREATER_THAN);
        Assert.assertEquals(MathExpressionParser.parseExpression("RSI < 30").getOperator(), RelationalOperator.LESS_THAN);
        Assert.assertEquals(MathExpressionParser.parseExpression("RSI >= 30").getOperator(), RelationalOperator.GREATER_THAN_OR_EQUAL);
        Assert.assertEquals(MathExpressionParser.parseExpression("RSI <= 70").getOperator(), RelationalOperator.LESS_THAN_OR_EQUAL);
        Assert.assertEquals(MathExpressionParser.parseExpression("RSI == 50").getOperator(), RelationalOperator.EQUAL);
    }

    @Test
    public void testParseRules() {
        List<MathExpression> expressions = MathExpressionParser.parseRules(
                List.of("HV_RANK >= 25", "HV_RANK <= 75"));
        Assert.assertEquals(expressions.size(), 2);
        Assert.assertEquals(expressions.get(0).getOperator(), RelationalOperator.GREATER_THAN_OR_EQUAL);
        Assert.assertEquals(expressions.get(1).getOperator(), RelationalOperator.LESS_THAN_OR_EQUAL);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testParseExpression_Invalid() {
        MathExpressionParser.parseExpression("not an expression");
    }

    @Test
    public void testBlankInput() {
        Assert.assertNull(MathExpressionParser.parseExpression("  "));
        Assert.assertTrue(MathExpressionParser.parseRules(null).isEmpty());
    }
}
