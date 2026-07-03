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
                .requirePriceBelowMA20(true)
                .requirePriceAboveMA20(true)
                .requirePriceBelowMA50(true)
                .requirePriceAboveMA50(true)
                .requirePriceBelowMA100(true)
                .requirePriceAboveMA100(true)
                .requirePriceBelowMA200(true)
                .requirePriceAboveMA200(true)
                .minDropPercent(5.5)
                .lookbackDays(10)
                .build();

        Assert.assertEquals(conditions.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(conditions.getBollingerCondition(), BollingerCondition.LOWER_BAND);
        Assert.assertEquals(conditions.getMinVolume(), Long.valueOf(1000000L));
        Assert.assertTrue(conditions.isRequirePriceBelowMA20());
        Assert.assertTrue(conditions.isRequirePriceAboveMA20());
        Assert.assertTrue(conditions.isRequirePriceBelowMA50());
        Assert.assertTrue(conditions.isRequirePriceAboveMA50());
        Assert.assertTrue(conditions.isRequirePriceBelowMA100());
        Assert.assertTrue(conditions.isRequirePriceAboveMA100());
        Assert.assertTrue(conditions.isRequirePriceBelowMA200());
        Assert.assertTrue(conditions.isRequirePriceAboveMA200());
        Assert.assertEquals(conditions.getMinDropPercent(), 5.5, 0.01);
        Assert.assertEquals(conditions.getLookbackDays(), Integer.valueOf(10));
    }

    @Test
    public void testGetSummary_AllSet() {
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .minVolume(1000000L)
                .requirePriceBelowMA20(true)
                .requirePriceAboveMA20(true)
                .requirePriceBelowMA50(true)
                .requirePriceAboveMA50(true)
                .requirePriceBelowMA100(true)
                .requirePriceAboveMA100(true)
                .requirePriceBelowMA200(true)
                .requirePriceAboveMA200(true)
                .build();

        String summary = conditions.getSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue(summary.contains("RSI: OVERSOLD"));
        Assert.assertTrue(summary.contains("BB: LOWER_BAND"));
        Assert.assertTrue(summary.contains("Price < MA20"));
        Assert.assertTrue(summary.contains("Price > MA20"));
        Assert.assertTrue(summary.contains("Price < MA50"));
        Assert.assertTrue(summary.contains("Price > MA50"));
        Assert.assertTrue(summary.contains("Price < MA100"));
        Assert.assertTrue(summary.contains("Price > MA100"));
        Assert.assertTrue(summary.contains("Price < MA200"));
        Assert.assertTrue(summary.contains("Price > MA200"));
        Assert.assertTrue(summary.contains("Volume >= 1,000,000"));
    }

    @Test
    public void testGetSummary_NoneSet() {
        TechFilterConditions conditions = TechFilterConditions.builder().build();
        String summary = conditions.getSummary();
        Assert.assertEquals(summary, "No conditions set");
    }
}
