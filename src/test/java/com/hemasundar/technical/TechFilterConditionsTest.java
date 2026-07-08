package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TechFilterConditionsTest {

    @Test
    public void testTechFilterConditionsBuilderAndGetters() {
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .minVolume(1000000L)
                .priceConditions(java.util.List.of(
                        new com.hemasundar.config.StrategiesConfig.PriceCondition() {{
                            setPeriod(20);
                            setPosition(com.hemasundar.config.StrategiesConfig.Position.BELOW);
                        }}
                ))

                .priceDropRules(java.util.List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 5.5)))
                .lookbackDays(10)
                .build();

        Assert.assertEquals(conditions.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(conditions.getBollingerCondition(), BollingerCondition.LOWER_BAND);
        Assert.assertEquals(conditions.getMinVolume(), Long.valueOf(1000000L));
        Assert.assertNotNull(conditions.getPriceConditions());
        Assert.assertEquals(1, conditions.getPriceConditions().size());
        Assert.assertEquals(20, conditions.getPriceConditions().get(0).getPeriod());
        Assert.assertEquals(com.hemasundar.config.StrategiesConfig.Position.BELOW, conditions.getPriceConditions().get(0).getPosition());
        Assert.assertEquals(conditions.getPriceDropRules().get(0).getValue(), 5.5, 0.01);
        Assert.assertEquals(conditions.getLookbackDays(), Integer.valueOf(10));
    }

    @Test
    public void testGetSummary_AllSet() {
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .minVolume(1000000L)
                .volumeCondition(VolumeCondition.MIN_VOLUME)
                .priceConditions(java.util.List.of(
                        new com.hemasundar.config.StrategiesConfig.PriceCondition() {{
                            setPeriod(20);
                            setPosition(com.hemasundar.config.StrategiesConfig.Position.BELOW);
                        }}
                ))

                .build();

        String summary = conditions.getSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue(summary.contains("RSI: OVERSOLD"));
        Assert.assertTrue(summary.contains("BB: LOWER_BAND"));
        Assert.assertTrue(summary.contains("Price < SMA20"));
        Assert.assertTrue(summary.contains("Volume >= 1,000,000"));
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
