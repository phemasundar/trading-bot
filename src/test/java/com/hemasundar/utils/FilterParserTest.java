package com.hemasundar.utils;

import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterParserTest {

    @Test
    public void testBuildFilter_CommonFields() {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("targetDTE", 45);
        filterMap.put("minDTE", "30"); // Test string to int conversion
        filterMap.put("maxDTE", 60.5); // Test number to int conversion
        filterMap.put("maxLossLimit", 500);
        filterMap.put("minReturnOnRisk", "15");
        filterMap.put("ignoreEarnings", "true");
        filterMap.put("maxTotalDebit", 2.5);
        filterMap.put("maxTotalCredit", "3.0");
        filterMap.put("minTotalCredit", 1.0);
        filterMap.put("priceVsMaxDebitRatio", 0.5);
        filterMap.put("maxCAGRForBreakEven", 20.0);
        filterMap.put("maxOptionPricePercent", 5.0);
        filterMap.put("marginInterestRate", 0.08);
        filterMap.put("savingsInterestRate", 0.04);
        filterMap.put("maxNetExtrinsicValueToPricePercentage", 2.0);
        filterMap.put("minNetExtrinsicValueToPricePercentage", 1.0);
        filterMap.put("includeOnly", "AAPL,MSFT");
        filterMap.put("excludeIf", List.of("TSLA"));
        filterMap.put("topTradesCount", 5);

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.PUT_CREDIT_SPREAD, filterMap);

        Assert.assertEquals(filter.getTargetDTE(), 45);
        Assert.assertEquals(filter.getMinDTE(), 30);
        Assert.assertEquals(filter.getMaxDTE(), 60);
        Assert.assertEquals(filter.getMaxLossLimit(), 500.0);
        Assert.assertEquals(filter.getMinReturnOnRisk(), 15);
        Assert.assertTrue(filter.isIgnoreEarnings());
        Assert.assertEquals(filter.getMaxTotalDebit(), 2.5);
        Assert.assertEquals(filter.getMaxTotalCredit(), 3.0);
        Assert.assertEquals(filter.getMinTotalCredit(), 1.0);
        Assert.assertEquals(filter.getPriceVsMaxDebitRatio(), 0.5);
        Assert.assertEquals(filter.getMaxCAGRForBreakEven(), 20.0);
        Assert.assertEquals(filter.getMaxOptionPricePercent(), 5.0);
        Assert.assertEquals(filter.getMarginInterestRate(), 0.08);
        Assert.assertEquals(filter.getSavingsInterestRate(), 0.04);
        Assert.assertEquals(filter.getMaxNetExtrinsicValueToPricePercentage(), 2.0);
        Assert.assertEquals(filter.getMinNetExtrinsicValueToPricePercentage(), 1.0);
        Assert.assertEquals(filter.getIncludeOnly(), List.of("AAPL", "MSFT"));
        Assert.assertEquals(filter.getExcludeIf(), List.of("TSLA"));
        Assert.assertEquals(filter.getTopTradesCount(), 5);
    }

    @Test
    public void testBuildFilter_CreditSpread() {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> legMap = new HashMap<>();
        legMap.put("minDelta", 0.2);
        legMap.put("maxDelta", 0.4);
        filterMap.put("shortLeg", legMap);

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.PUT_CREDIT_SPREAD, filterMap);
        Assert.assertTrue(filter instanceof CreditSpreadFilter);
        CreditSpreadFilter csFilter = (CreditSpreadFilter) filter;
        Assert.assertNotNull(csFilter.getShortLeg());
        Assert.assertEquals(csFilter.getShortLeg().getMinDelta(), 0.2);
    }

    @Test
    public void testBuildFilter_IronCondor() {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> legMap = new HashMap<>();
        legMap.put("minDelta", 0.1);
        filterMap.put("putShortLeg", legMap);
        filterMap.put("callShortLeg", legMap);

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.IRON_CONDOR, filterMap);
        Assert.assertTrue(filter instanceof IronCondorFilter);
        IronCondorFilter icFilter = (IronCondorFilter) filter;
        Assert.assertNotNull(icFilter.getPutShortLeg());
        Assert.assertNotNull(icFilter.getCallShortLeg());
    }

    @Test
    public void testBuildFilter_LongCallLeap() {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("minCostSavingsPercent", 10.0);
        filterMap.put("relaxationPriority", "delta,price");
        filterMap.put("sortPriority", List.of("roi", "vol"));

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.LONG_CALL_LEAP, filterMap);
        Assert.assertTrue(filter instanceof LongCallLeapFilter);
        LongCallLeapFilter leapFilter = (LongCallLeapFilter) filter;
        Assert.assertEquals(leapFilter.getMinCostSavingsPercent(), 10.0);
        Assert.assertEquals(leapFilter.getRelaxationPriority(), List.of("delta", "price"));
        Assert.assertEquals(leapFilter.getSortPriority(), List.of("roi", "vol"));
    }

    @Test
    public void testBuildFilter_BrokenWingButterfly() {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> legMap = new HashMap<>();
        legMap.put("minVolatility", 0.3);
        filterMap.put("leg1Long", legMap);

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.BULLISH_BROKEN_WING_BUTTERFLY, filterMap);
        Assert.assertTrue(filter instanceof BrokenWingButterflyFilter);
        BrokenWingButterflyFilter bwbFilter = (BrokenWingButterflyFilter) filter;
        Assert.assertNotNull(bwbFilter.getLeg1Long());
        Assert.assertEquals(bwbFilter.getLeg1Long().getMinVolatility(), 0.3);
    }

    @Test
    public void testBuildFilter_Zebra() {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> legMap = new HashMap<>();
        legMap.put("minVolume", 100);
        filterMap.put("longCall", legMap);

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.BULLISH_ZEBRA, filterMap);
        Assert.assertTrue(filter instanceof ZebraFilter);
        ZebraFilter zebraFilter = (ZebraFilter) filter;
        Assert.assertNotNull(zebraFilter.getLongCall());
        Assert.assertEquals(zebraFilter.getLongCall().getMinVolume(), 100);
    }

    @Test
    public void testApplyLegFilter_EmptyOrNull() {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("shortLeg", null);
        filterMap.put("longLeg", "not a map");
        filterMap.put("otherLeg", new HashMap<>());

        OptionsStrategyFilter filter = FilterParser.buildFilter(StrategyType.PUT_CREDIT_SPREAD, filterMap);
        CreditSpreadFilter csFilter = (CreditSpreadFilter) filter;
        Assert.assertNull(csFilter.getShortLeg());
        Assert.assertNull(csFilter.getLongLeg());
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testToInt_Invalid() {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("targetDTE", "abc");
        FilterParser.buildFilter(StrategyType.PUT_CREDIT_SPREAD, filterMap);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testToDouble_Invalid() {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("maxLossLimit", "xyz");
        FilterParser.buildFilter(StrategyType.PUT_CREDIT_SPREAD, filterMap);
    }
}
