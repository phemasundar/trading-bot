package com.hemasundar.options.models;

import com.hemasundar.technical.MathExpression;
import com.hemasundar.utils.MathExpressionParser;
import lombok.extern.log4j.Log4j2;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Unit tests for {@link OptionFilterValueResolver}.
 */
@Log4j2
public class OptionFilterValueResolverTest {

    @Test
    public void testResolveLegValue() {
        OptionChainResponse.OptionData leg = new OptionChainResponse.OptionData();
        leg.setDelta(-0.18);
        leg.setOpenInterest(1200);
        leg.setTotalVolume(350);
        leg.setMark(1.45);
        leg.setBid(1.40);
        leg.setAsk(1.50);
        leg.setStrikePrice(150.0);
        leg.setDaysToExpiration(35);
        leg.setVolatility(28.5);

        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "DELTA"), 0.18, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "SIGNED_DELTA"), -0.18, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveLegValue(leg, "RAW_DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "OPEN_INTEREST"), 1200.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "VOLUME"), 350.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "MARK"), 1.45);
        Assert.assertNull(OptionFilterValueResolver.resolveLegValue(leg, "PREMIUM"));
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "STRIKE_PRICE"), 150.0);
        Assert.assertNull(OptionFilterValueResolver.resolveLegValue(leg, "STRIKE"));
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "DTE"), 35.0);
        Assert.assertNull(OptionFilterValueResolver.resolveLegValue(leg, "DAYS_TO_EXPIRATION"));
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "IV"), 28.5);
    }

    @Test
    public void testResolveTradeValue() {
        OptionChainResponse.OptionData shortLeg = new OptionChainResponse.OptionData();
        shortLeg.setDelta(-0.20);
        shortLeg.setStrikePrice(100.0);
        shortLeg.setDaysToExpiration(45);

        OptionChainResponse.OptionData longLeg = new OptionChainResponse.OptionData();
        longLeg.setDelta(-0.10);
        longLeg.setStrikePrice(95.0);
        longLeg.setDaysToExpiration(45);

        PutCreditSpread spread = PutCreditSpread.builder()
                .shortPut(shortLeg)
                .longPut(longLeg)
                .netCredit(100.0)
                .maxLoss(400.0)
                .returnOnRisk(25.0)
                .breakEvenPrice(99.0)
                .breakEvenPercentage(2.5)
                .currentPrice(101.5)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "MAX_LOSS"), 400.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "NET_CREDIT"), 100.0);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "TOTAL_CREDIT"));
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "CREDIT"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "RETURN_ON_RISK"), 25.0);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "ROR"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "BREAK_EVEN_PCT"), 2.5);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "BREAK_EVEN_PERCENTAGE"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "CURRENT_PRICE"), 101.5);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "UNDERLYING_PRICE"));

        // Test dotted navigation
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "SHORT_LEG.DELTA"), 0.20, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "LONG_LEG.DELTA"), 0.10, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "SHORT_LEG.STRIKE_PRICE"), 100.0);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(spread, "SHORT_LEG.STRIKE"));
    }

    @Test
    public void testMathExpressionEvaluationOnTrade() {
        OptionChainResponse.OptionData shortLeg = new OptionChainResponse.OptionData();
        shortLeg.setDelta(-0.18);
        shortLeg.setStrikePrice(100.0);

        OptionChainResponse.OptionData longLeg = new OptionChainResponse.OptionData();
        longLeg.setDelta(-0.08);
        longLeg.setStrikePrice(95.0);

        PutCreditSpread spread = PutCreditSpread.builder()
                .shortPut(shortLeg)
                .longPut(longLeg)
                .netCredit(120.0)
                .maxLoss(380.0)
                .returnOnRisk(31.5)
                .breakEvenPrice(98.8)
                .breakEvenPercentage(2.2)
                .currentPrice(101.0)
                .build();

        MathExpression maxLossExpr = MathExpressionParser.parseExpression("MAX_LOSS <= 500");
        Assert.assertTrue(maxLossExpr.evaluate(var -> OptionFilterValueResolver.resolveTradeValue(spread, var)));

        MathExpression rorExpr = MathExpressionParser.parseExpression("RETURN_ON_RISK >= 20%");
        Assert.assertTrue(rorExpr.evaluate(var -> OptionFilterValueResolver.resolveTradeValue(spread, var)));

        MathExpression legDeltaExpr = MathExpressionParser.parseExpression("SHORT_LEG.DELTA <= 0.2");
        Assert.assertTrue(legDeltaExpr.evaluate(var -> OptionFilterValueResolver.resolveTradeValue(spread, var)));

        MathExpression strictDeltaExpr = MathExpressionParser.parseExpression("SHORT_LEG.DELTA <= 0.15");
        Assert.assertFalse(strictDeltaExpr.evaluate(var -> OptionFilterValueResolver.resolveTradeValue(spread, var)));
    }

    @Test
    public void testResolveIronCondorAndStrangleTradeValues() {
        OptionChainResponse.OptionData putShort = new OptionChainResponse.OptionData();
        putShort.setDelta(-0.14);
        putShort.setStrikePrice(90.0);
        putShort.setDaysToExpiration(30);

        OptionChainResponse.OptionData putLong = new OptionChainResponse.OptionData();
        putLong.setDelta(-0.05);
        putLong.setStrikePrice(85.0);
        putLong.setDaysToExpiration(30);

        OptionChainResponse.OptionData callShort = new OptionChainResponse.OptionData();
        callShort.setDelta(0.12);
        callShort.setStrikePrice(110.0);
        callShort.setDaysToExpiration(30);

        OptionChainResponse.OptionData callLong = new OptionChainResponse.OptionData();
        callLong.setDelta(0.04);
        callLong.setStrikePrice(115.0);
        callLong.setDaysToExpiration(30);

        IronCondor condor = IronCondor.builder()
                .putLeg(PutCreditSpread.builder().shortPut(putShort).longPut(putLong).build())
                .callLeg(CallCreditSpread.builder().shortCall(callShort).longCall(callLong).build())
                .netCredit(150.0)
                .maxLoss(350.0)
                .returnOnRisk(42.8)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "PUT_SHORT.DELTA"), 0.14, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(condor, "PUT_SHORT_LEG.DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_SHORT.DELTA"), 0.12, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_SHORT_LEG.DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "PUT_LONG.DELTA"), 0.05, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_LONG.DELTA"), 0.04, 0.001);

        ShortStrangle strangle = ShortStrangle.builder()
                .shortPut(putShort)
                .shortCall(callShort)
                .netCredit(200.0)
                .maxLoss(8800.0)
                .returnOnRisk(2.27)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "PUT_SHORT.DELTA"), 0.14, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "CALL_SHORT.DELTA"), 0.12, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(strangle, "PUT.DELTA"));
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(strangle, "CALL.DELTA"));
    }

    @Test
    public void testResolveBWBAndLeapTradeValues() {
        OptionChainResponse.OptionData leg1 = new OptionChainResponse.OptionData();
        leg1.setDelta(0.55);
        leg1.setStrikePrice(100.0);

        OptionChainResponse.OptionData leg2 = new OptionChainResponse.OptionData();
        leg2.setDelta(0.35);
        leg2.setStrikePrice(105.0);

        OptionChainResponse.OptionData leg3 = new OptionChainResponse.OptionData();
        leg3.setDelta(0.15);
        leg3.setStrikePrice(115.0);

        BrokenWingButterfly bwb = BrokenWingButterfly.builder()
                .leg1LongCall(leg1)
                .leg2ShortCalls(leg2)
                .leg3LongCall(leg3)
                .lowerWingWidth(500.0)
                .upperWingWidth(1000.0)
                .totalDebit(75.0)
                .maxLoss(575.0)
                .maxLossUpside(575.0)
                .maxLossDownside(75.0)
                .returnOnRisk(73.9)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG1.DELTA"), 0.55, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG1_LONG.DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG2.DELTA"), 0.35, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG2_SHORT.DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG3.DELTA"), 0.15, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG3_LONG.DELTA"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "TOTAL_DEBIT"), 75.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "MAX_LOSS_UPSIDE"), 575.0);

        LongCallLeap leap = LongCallLeap.builder()
                .longCall(leg1)
                .optionPrice(21.0)
                .currentPrice(105.0)
                .finalCostOfOption(12.0)
                .costSavingsPercent(18.5)
                .breakevenCAGR(8.2)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "COST_SAVINGS_PCT"), 18.5);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(leap, "COST_SAVINGS_PERCENT"));
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(leap, "OPTION_PRICE_PERCENT"));
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "OPTION_PRICE_PCT"), 20.0, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "OPTION_PRICE"), 21.0, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "BREAKEVEN_CAGR"), 8.2);
    }

    @Test
    public void testAnnualizedExtrinsicPercentage() {
        OptionChainResponse.OptionData call = new OptionChainResponse.OptionData();
        call.setDaysToExpiration(365);
        LongCallLeap leap = LongCallLeap.builder()
                .longCall(call)
                .extrinsicValue(100.0)
                .maxLoss(1000.0)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "ANNUALIZED_EXTRINSIC_PCT"), 10.0, 0.001);
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(leap, "NET_EXTRINSIC_VALUE_PCT"));
        Assert.assertNull(OptionFilterValueResolver.resolveTradeValue(leap, "EXTRINSIC_VALUE_PCT"));

        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("ANNUALIZED_EXTRINSIC_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("NET_EXTRINSIC_VALUE_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("EXTRINSIC_VALUE_PCT"));
    }

    @Test
    public void testRemovedAliasesNotSupported() {
        // Trade variables
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("MAX_LOSS"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("MAX_PROFIT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("PROFIT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("NET_CREDIT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("TOTAL_CREDIT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("CREDIT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("TOTAL_DEBIT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("NET_DEBIT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("DEBIT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("RETURN_ON_RISK"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("ROR"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("CAGR"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("RETURN_ON_RISK_CAGR"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("ROR_CAGR"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("BREAK_EVEN_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("BREAK_EVEN"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("BREAKEVEN"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("BREAK_EVEN_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("BREAK_EVEN_PERCENTAGE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("BREAKEVEN_PCT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAK_EVEN_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAK_EVEN"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAKEVEN"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAK_EVEN_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAK_EVEN_PERCENTAGE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("UPPER_BREAKEVEN_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAK_EVEN_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAK_EVEN"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAKEVEN"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAK_EVEN_PERCENTAGE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAK_EVEN_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("LOWER_BREAKEVEN_PCT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("CURRENT_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("UNDERLYING_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("PRICE"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("DTE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("DAYS_TO_EXPIRATION"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("NET_EXTRINSIC_VALUE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("EXTRINSIC_VALUE"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("COST_SAVINGS_PCT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("COST_SAVINGS_PERCENT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("OPTION_PRICE_PCT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedTradeVariable("OPTION_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("OPTION_PRICE_PERCENT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedTradeVariable("EARNINGS"));

        // Leg variables
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("DELTA"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("ABS_DELTA"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("SIGNED_DELTA"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("RAW_DELTA"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("MARK"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("PREMIUM"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("PRICE"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("STRIKE_PRICE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("STRIKE"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("VOLUME"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("TOTAL_VOLUME"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("OPEN_INTEREST"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("OI"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("IV"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("VOLATILITY"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegVariable("DTE"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegVariable("DAYS_TO_EXPIRATION"));

        // Leg prefixes
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("SHORT_LEG"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("LONG_LEG"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("PUT_SHORT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("PUT_LONG"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("CALL_SHORT"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("CALL_LONG"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("LEG1"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("LEG2"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("LEG3"));
        Assert.assertTrue(OptionFilterValueResolver.isSupportedLegPrefix("LEG4"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("SHORT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LONG"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("SHORT_PUT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LONG_PUT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("SHORT_CALL"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LONG_CALL"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LEG1_LONG"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LEG2_SHORT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("LEG3_LONG"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("PUT"));
        Assert.assertFalse(OptionFilterValueResolver.isSupportedLegPrefix("CALL"));
    }

    @Test
    public void testLongCallLeapOptionPriceFallbacks() {
        // Fallback to longCall ask
        OptionChainResponse.OptionData callWithAsk = new OptionChainResponse.OptionData();
        callWithAsk.setAsk(30.0);
        LongCallLeap leap1 = LongCallLeap.builder().longCall(callWithAsk).currentPrice(100.0).build();
        Assert.assertEquals(leap1.getOptionPrice(), 30.0);
        Assert.assertEquals(leap1.getOptionPricePercent(), 30.0);

        // Fallback to maxLoss / 100
        LongCallLeap leap2 = LongCallLeap.builder().maxLoss(2500.0).currentPrice(100.0).build();
        Assert.assertEquals(leap2.getOptionPrice(), 25.0);
        Assert.assertEquals(leap2.getOptionPricePercent(), 25.0);

        // Fallback to mark
        OptionChainResponse.OptionData callWithMark = new OptionChainResponse.OptionData();
        callWithMark.setMark(15.0);
        LongCallLeap leap3 = LongCallLeap.builder().longCall(callWithMark).currentPrice(100.0).build();
        Assert.assertEquals(leap3.getOptionPrice(), 15.0);
        Assert.assertEquals(leap3.getOptionPricePercent(), 15.0);

        // currentPrice <= 0
        LongCallLeap leap4 = LongCallLeap.builder().optionPrice(10.0).currentPrice(0.0).build();
        Assert.assertEquals(leap4.getOptionPricePercent(), 0.0);
    }
}
