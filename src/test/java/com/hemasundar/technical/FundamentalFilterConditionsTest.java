package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

public class FundamentalFilterConditionsTest {

    @Test
    public void testEmptySummary() {
        FundamentalFilterConditions conditions = FundamentalFilterConditions.builder().build();
        Assert.assertEquals(conditions.getSummary(), "No fundamental conditions set");

        FundamentalFilterConditions emptyConditions = FundamentalFilterConditions.builder()
                .filterExpressions(Collections.emptyList())
                .build();
        Assert.assertEquals(emptyConditions.getSummary(), "No fundamental conditions set");
    }

    @Test
    public void testSummaryWithExpressions() {
        MathExpression expr1 = MathExpression.builder()
                .leftVariable("MARKET_CAP_B")
                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                .rightVariable("10")
                .build();

        MathExpression expr2 = MathExpression.builder()
                .leftVariable("PE_RATIO")
                .operator(RelationalOperator.LESS_THAN)
                .rightVariable("25")
                .build();

        FundamentalFilterConditions conditions = FundamentalFilterConditions.builder()
                .filterExpressions(List.of(expr1, expr2))
                .build();

        String summary = conditions.getSummary();
        Assert.assertTrue(summary.contains("MARKET_CAP_B >= 10"));
        Assert.assertTrue(summary.contains("PE_RATIO < 25"));
        Assert.assertTrue(summary.contains(" | "));
    }
}
