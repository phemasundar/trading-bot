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
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "RAW_DELTA"), -0.18, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "OPEN_INTEREST"), 1200.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "VOLUME"), 350.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "PREMIUM"), 1.45);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "STRIKE"), 150.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveLegValue(leg, "DTE"), 35.0);
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
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "RETURN_ON_RISK"), 25.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "ROR"), 25.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "BREAK_EVEN_PCT"), 2.5);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "CURRENT_PRICE"), 101.5);

        // Test dotted navigation
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "SHORT_LEG.DELTA"), 0.20, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "LONG_LEG.DELTA"), 0.10, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(spread, "SHORT_LEG.STRIKE"), 100.0);
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
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "PUT_SHORT_LEG.DELTA"), 0.14, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_SHORT.DELTA"), 0.12, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_SHORT_LEG.DELTA"), 0.12, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "PUT_LONG.DELTA"), 0.05, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(condor, "CALL_LONG.DELTA"), 0.04, 0.001);

        ShortStrangle strangle = ShortStrangle.builder()
                .shortPut(putShort)
                .shortCall(callShort)
                .netCredit(200.0)
                .maxLoss(8800.0)
                .returnOnRisk(2.27)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "PUT.DELTA"), 0.14, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "CALL.DELTA"), 0.12, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "PUT_SHORT.DELTA"), 0.14, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(strangle, "CALL_SHORT.DELTA"), 0.12, 0.001);
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
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG1_LONG.DELTA"), 0.55, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG2_SHORT.DELTA"), 0.35, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "LEG3_LONG.DELTA"), 0.15, 0.001);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "TOTAL_DEBIT"), 75.0);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(bwb, "MAX_LOSS_UPSIDE"), 575.0);

        LongCallLeap leap = LongCallLeap.builder()
                .longCall(leg1)
                .currentPrice(105.0)
                .finalCostOfOption(12.0)
                .costSavingsPercent(18.5)
                .breakevenCAGR(8.2)
                .build();

        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "COST_SAVINGS_PCT"), 18.5);
        Assert.assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "BREAKEVEN_CAGR"), 8.2);
    }
}
