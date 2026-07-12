package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TechFilterConditionsTest {

    @Test
    public void testTechFilterConditionsBuilderAndGetters() {
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .filterExpressions(java.util.List.of(
                        MathExpression.builder()
                                .leftVariable("VOLUME")
                                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                                .rightVariable("1000000")
                                .build(),
                        MathExpression.builder()
                                .leftVariable("PRICE")
                                .operator(RelationalOperator.LESS_THAN)
                                .rightVariable("SMA20")
                                .build(),
                        MathExpression.builder()
                                .leftVariable("DROP_PCT")
                                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                                .rightVariable("5.5")
                                .build()))
                .lookbackDays(10)
                .build();

        Assert.assertEquals(conditions.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(conditions.getBollingerCondition(), BollingerCondition.LOWER_BAND);
        Assert.assertEquals(3, conditions.getFilterExpressions().size());
        Assert.assertEquals(Integer.valueOf(10), conditions.getLookbackDays());
    }

    @Test
    public void testGetSummary_AllSet() {
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .filterExpressions(java.util.List.of(
                        MathExpression.builder()
                                .leftVariable("VOLUME")
                                .operator(RelationalOperator.GREATER_THAN_OR_EQUAL)
                                .rightVariable("1000000")
                                .build(),
                        MathExpression.builder()
                                .leftVariable("PRICE")
                                .operator(RelationalOperator.LESS_THAN)
                                .rightVariable("SMA20")
                                .build()))
                .build();

        String summary = conditions.getSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue(summary.contains("RSI: OVERSOLD"));
        Assert.assertTrue(summary.contains("BB: LOWER_BAND"));
        Assert.assertTrue(summary.contains("PRICE < SMA20"));
        Assert.assertTrue(summary.contains("VOLUME >= 1000000"));
    }

    @Test
    public void testGetSummary_NoneSet() {
        TechFilterConditions conditions = TechFilterConditions.builder().build();
        String summary = conditions.getSummary();
        Assert.assertEquals(summary, "No conditions set");
    }

    @Test
    public void testEnumEvaluations() {
        RSIFilter rsiFilter = org.mockito.Mockito.mock(RSIFilter.class);
        BollingerBandsFilter bbFilter = org.mockito.Mockito.mock(BollingerBandsFilter.class);
        org.ta4j.core.BarSeries series = org.mockito.Mockito.mock(org.ta4j.core.BarSeries.class);

        org.mockito.Mockito.when(rsiFilter.isOversold(series)).thenReturn(true);
        org.mockito.Mockito.when(rsiFilter.isOverbought(series)).thenReturn(true);
        org.mockito.Mockito.when(rsiFilter.isBullishCrossover(series)).thenReturn(true);
        org.mockito.Mockito.when(rsiFilter.isBearishCrossover(series)).thenReturn(true);

        Assert.assertTrue(RSICondition.OVERSOLD.evaluate(rsiFilter, series));
        Assert.assertTrue(RSICondition.OVERBOUGHT.evaluate(rsiFilter, series));
        Assert.assertTrue(RSICondition.BULLISH_CROSSOVER.evaluate(rsiFilter, series));
        Assert.assertTrue(RSICondition.BEARISH_CROSSOVER.evaluate(rsiFilter, series));

        org.mockito.Mockito.when(bbFilter.isPriceTouchingLowerBand(series)).thenReturn(true);
        org.mockito.Mockito.when(bbFilter.isPriceTouchingUpperBand(series)).thenReturn(true);

        Assert.assertTrue(BollingerCondition.LOWER_BAND.evaluate(bbFilter, series));
        Assert.assertTrue(BollingerCondition.UPPER_BAND.evaluate(bbFilter, series));
    }
}
