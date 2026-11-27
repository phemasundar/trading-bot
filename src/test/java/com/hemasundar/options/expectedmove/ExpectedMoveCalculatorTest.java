package com.hemasundar.options.expectedmove;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.QuotesResponse.Quote;
import com.hemasundar.pojos.QuotesResponse.QuoteData;
import com.hemasundar.utils.StrategyTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class ExpectedMoveCalculatorTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private Clock fixedClock;
    private ExpectedMoveCalculator calculator;

    @BeforeMethod
    public void setUp() {
        ZonedDateTime fixedNow = ZonedDateTime.of(2026, 11, 26, 10, 0, 0, 0, MARKET_ZONE);
        fixedClock = Clock.fixed(fixedNow.toInstant(), MARKET_ZONE);
        calculator = new ExpectedMoveCalculator(fixedClock);
    }

    @Test
    public void testLeapsRegime_LognormalModel_ValidSavedIv() {
        String expiry = "2028-01-21"; // > 365 days from 2026-11-26
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 100.0);
        StrategyTestUtils.addOption(chain, expiry, 421, 100.0, 15.0, 15.5, 0.55, false);
        StrategyTestUtils.addOption(chain, expiry, 421, 100.0, 12.0, 12.5, -0.45, true);

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 30.0, 999999.0);

        assertTrue(res.isValid());
        assertTrue(res.isLognormal());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.LEAPS_LOGNORMAL);
        assertEquals(res.getTierUsed().getShortLabel(), "LEAPS");
        assertTrue(res.getUpsideMove() > res.getDownsideMove(), "Lognormal upside move must exceed downside move");
        assertTrue(res.getLowerBound() > 0.0);
        assertEquals(res.getUpperBound(), 100.0 + res.getUpsideMove(), 0.001);
        assertEquals(res.getLowerBound(), 100.0 - res.getDownsideMove(), 0.001);
    }

    @Test
    public void testLeapsRegime_MissingSavedIv_FallsBackToTier2ThenTier3() {
        String expiry = "2028-01-21";
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 100.0);
        StrategyTestUtils.addOption(chain, expiry, 421, 100.0, 15.0, 16.0, 0.55, false);
        StrategyTestUtils.addOption(chain, expiry, 421, 100.0, 14.0, 15.0, -0.45, true);

        // Null savedCurrentIv -> jumps to Tier 2 (0.85 * ATM straddle)
        ExpectedMoveResult tier2Fallback = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, null, 999999.0);
        assertTrue(tier2Fallback.isValid());
        assertFalse(tier2Fallback.isLognormal());
        assertEquals(tier2Fallback.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        double expectedStraddle = 15.5 + 14.5; // 30.0
        assertEquals(tier2Fallback.getUpsideMove(), 0.85 * expectedStraddle, 0.001);
        assertEquals(tier2Fallback.getDownsideMove(), 0.85 * expectedStraddle, 0.001);

        // Now zero out bids (after-hours) with chain volatility = 28.0% -> jumps to Tier 3
        OptionChainResponse afterHoursChain = StrategyTestUtils.createMockChain("AAPL", 100.0);
        afterHoursChain.setVolatility(28.0);
        StrategyTestUtils.addOption(afterHoursChain, expiry, 421, 100.0, 0.0, 16.0, 0.55, false);
        StrategyTestUtils.addOption(afterHoursChain, expiry, 421, 100.0, 0.0, 15.0, -0.45, true);

        ExpectedMoveResult tier3Fallback = calculator.calculateExpectedMoveWaterfall(afterHoursChain, expiry, null, null, 999999.0);
        assertTrue(tier3Fallback.isValid());
        assertEquals(tier3Fallback.getTierUsed(), ExpectedMoveTier.TIER_3_IV_LINEAR);
        assertTrue(tier3Fallback.getReason().contains("chain volatility"));
    }

    @Test
    public void testTier1_FiveStrikeBlend_Success() {
        String expiry = "2026-12-26"; // 30 DTE
        OptionChainResponse chain = buildLiquidFiveStrikeChain("SPY", 500.0, expiry, 30, 5.0);

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 20.0, 25.0);

        assertTrue(res.isValid());
        assertFalse(res.isLognormal());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_1_FIVE_STRIKE_BLEND);
        assertEquals(res.getTierUsed().getShortLabel(), "Tier 1");

        // ATM straddle = 10.0 + 10.0 = 20.0
        // 1st OTM strangle = 7.5 + 8.0 = 15.5
        // 2nd OTM strangle = 5.5 + 6.0 = 11.5
        // EM = 0.60*20.0 + 0.30*15.5 + 0.10*11.5 = 12.0 + 4.65 + 1.15 = 17.80
        assertEquals(res.getUpsideMove(), 17.80, 0.001);
        assertEquals(res.getDownsideMove(), 17.80, 0.001);
        assertEquals(res.getUpperBound(), 517.80, 0.001);
        assertEquals(res.getLowerBound(), 482.20, 0.001);
    }

    @Test
    public void testTier1_EarningsWithin10Days_FallsBackToTier2() {
        String expiry = "2026-12-04"; // 8 DTE
        OptionChainResponse chain = buildLiquidFiveStrikeChain("NVDA", 500.0, expiry, 8, 5.0);

        // Earnings in 10 days -> Tier 1 skipped uniformly -> falls back to Tier 2
        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 35.0, 10.0);

        assertTrue(res.isValid());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        assertEquals(res.getUpsideMove(), 0.85 * 20.0, 0.001);
        assertTrue(res.getReason().contains("earnings within 10 days"));
    }

    @Test
    public void testTier1_NonUniformStrikeSpacing_FallsBackToTier2() {
        String expiry = "2026-12-26";
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 100.0);
        // Mixed $1 and $5 steps: 95, 99, 100, 101, 105
        addSymmetricStrike(chain, expiry, 30, 95.0, 12.0, 1.0);
        addSymmetricStrike(chain, expiry, 30, 99.0, 4.0, 2.0);
        addSymmetricStrike(chain, expiry, 30, 100.0, 3.0, 3.0);
        addSymmetricStrike(chain, expiry, 30, 101.0, 2.0, 4.0);
        addSymmetricStrike(chain, expiry, 30, 105.0, 1.0, 12.0);

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 25.0, 45.0);
        assertTrue(res.isValid());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        assertTrue(res.getReason().contains("non-uniform strike spacing"));
    }

    @Test
    public void testTier1_SanityCapExceeded_FallsBackToTier2() {
        String expiry = "2026-12-26";
        OptionChainResponse chain = StrategyTestUtils.createMockChain("MEME", 100.0);
        // Outer wings corrupted / priced higher than ATM straddle (violates 1.05x sanity cap)
        addSymmetricStrike(chain, expiry, 30, 90.0, 12.0, 8.0);
        addSymmetricStrike(chain, expiry, 30, 95.0, 8.0, 7.0);
        addSymmetricStrike(chain, expiry, 30, 100.0, 2.0, 2.0); // ATM straddle = 4.0
        addSymmetricStrike(chain, expiry, 30, 105.0, 7.0, 8.0);
        addSymmetricStrike(chain, expiry, 30, 110.0, 8.0, 12.0);

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 50.0, 40.0);
        assertTrue(res.isValid());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        assertTrue(res.getReason().contains("sanity cap"));
    }

    @Test
    public void testTier2_ZeroDtePrimary() {
        String expiry = "2026-11-26"; // Same day as fixedClock (10:00 AM ET -> 360 min to 16:00 ET)
        OptionChainResponse chain = buildLiquidFiveStrikeChain("SPY", 500.0, expiry, 0, 5.0);

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 18.0, 999999.0);
        assertTrue(res.isValid());
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        assertEquals(res.getTierUsed().getShortLabel(), "Tier 2");
        assertTrue(res.getReason().contains("0DTE primary"));
        assertEquals(res.getUpsideMove(), 17.0, 0.001); // 0.85 * (10 + 10)
    }

    @Test
    public void testTier3_PrefersSavedCurrentIvOverChainVolatility() {
        String expiry = "2026-12-26";
        OptionChainResponse chain = StrategyTestUtils.createMockChain("MIDCAP", 100.0);
        chain.setVolatility(50.0); // Chain volatility = 50%
        // Wide ATM spread (> 25%) forces skip of Tier 1 and Tier 2
        StrategyTestUtils.addOption(chain, expiry, 30, 100.0, 2.00, 3.00, 0.50, false); // spread = 1.0 / 2.5 = 40%
        StrategyTestUtils.addOption(chain, expiry, 30, 100.0, 2.00, 3.00, -0.50, true);

        // Saved currentIV = 25.0% should take priority over chain.volatility = 50.0%
        ExpectedMoveResult resWithSavedIv = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 25.0, 999999.0);
        assertTrue(resWithSavedIv.isValid());
        assertEquals(resWithSavedIv.getTierUsed(), ExpectedMoveTier.TIER_3_IV_LINEAR);
        assertEquals(resWithSavedIv.getTierUsed().getShortLabel(), "Tier 3");
        assertTrue(resWithSavedIv.getReason().contains("saved currentIV"));

        // Without saved currentIV (< 20 records in Supabase), falls back to chain.volatility = 50.0%
        ExpectedMoveResult resFallbackChainIv = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, null, 999999.0);
        assertTrue(resFallbackChainIv.isValid());
        assertEquals(resFallbackChainIv.getTierUsed(), ExpectedMoveTier.TIER_3_IV_LINEAR);
        assertTrue(resFallbackChainIv.getReason().contains("chain volatility"));
        assertTrue(resFallbackChainIv.getReason().contains("Tier 2 skipped: ATM Call $100 (spread 40.0% > max 25% ($2.00/$3.00))"));
        assertEquals(resFallbackChainIv.getUpsideMove(), resWithSavedIv.getUpsideMove() * 2.0, 0.001);
    }

    @Test
    public void testDetailedHygieneDiagnostics_WingAndAtmFailures() {
        String expiry = "2026-12-26";
        OptionChainResponse chain = buildLiquidFiveStrikeChain("SPY", 500.0, expiry, 30, 5.0);
        // Corrupt only 2nd OTM Call ($510) with wide spread (> 15%): bid = 1.00, ask = 1.25 (22.2% spread)
        chain.getCallExpDateMap().entrySet().stream()
                .filter(e -> e.getKey().getDate().equals(expiry))
                .findFirst()
                .ifPresent(e -> e.getValue().get("510.0").get(0).setAsk(1.25));
        chain.getCallExpDateMap().entrySet().stream()
                .filter(e -> e.getKey().getDate().equals(expiry))
                .findFirst()
                .ifPresent(e -> e.getValue().get("510.0").get(0).setBid(1.00));

        ExpectedMoveResult res = calculator.calculateExpectedMoveWaterfall(chain, expiry, null, 20.0, 999999.0);
        assertEquals(res.getTierUsed(), ExpectedMoveTier.TIER_2_ATM_STRADDLE);
        assertTrue(res.getReason().contains("2nd OTM Call $510 (spread 22.2% > max 15% ($1.00/$1.25))"),
                "Unexpected reason: " + res.getReason());

        // 3+ contracts failing Tier 1 -> compact count summary
        OptionChainResponse multiFailChain = buildLiquidFiveStrikeChain("SPY", 500.0, expiry, 30, 5.0);
        multiFailChain.setVolatility(22.0);
        multiFailChain.getCallExpDateMap().entrySet().stream()
                .filter(e -> e.getKey().getDate().equals(expiry))
                .forEach(e -> e.getValue().values().forEach(list -> list.get(0).setBid(0.0)));
        ExpectedMoveResult multiFailRes = calculator.calculateExpectedMoveWaterfall(multiFailChain, expiry, null, null, 999999.0);
        assertEquals(multiFailRes.getTierUsed(), ExpectedMoveTier.TIER_3_IV_LINEAR);
        assertTrue(multiFailRes.getReason().contains("+ 2 other contracts") || multiFailRes.getReason().contains("other contracts"),
                "Unexpected multi-fail reason: " + multiFailRes.getReason());
        assertTrue(multiFailRes.getReason().contains("Tier 2 skipped: ATM Call $500 (bid $0.00 < min $0.01)"),
                "Unexpected Tier 2 fail reason: " + multiFailRes.getReason());

        // Direct validateContract checks
        assertEquals(calculator.validateContract(null, 500.0, true, 0.05, 0.15), "missing contract");
        OptionChainResponse.OptionData inverted = new OptionChainResponse.OptionData();
        inverted.setStrikePrice(500.0);
        inverted.setBid(2.0);
        inverted.setAsk(1.5);
        assertEquals(calculator.validateContract(inverted, 500.0, true, 0.05, 0.15),
                "inverted quote (bid $2.00 > ask $1.50)");
    }

    @Test
    public void testCircuitBreaker_AllTiersFail() {
        ExpectedMoveCalculator defaultCalc = new ExpectedMoveCalculator();
        assertNotNull(defaultCalc);

        ExpectedMoveResult nullChainRes = calculator.calculateExpectedMoveWaterfall(null, "2026-12-26", null, null, null);
        assertFalse(nullChainRes.isValid());
        assertEquals(nullChainRes.getTierUsed(), ExpectedMoveTier.CIRCUIT_BREAKER);
        assertEquals(nullChainRes.getTierUsed().getShortLabel(), "Unavailable");

        OptionChainResponse zeroSpotChain = StrategyTestUtils.createMockChain("BAD", 0.0);
        ExpectedMoveResult zeroSpotRes = calculator.calculateExpectedMoveWaterfall(zeroSpotChain, "2026-12-26", null, null, null);
        assertFalse(zeroSpotRes.isValid());

        // Expired option (after 16:00 ET on 0DTE)
        ZonedDateTime postClose = ZonedDateTime.of(2026, 11, 26, 16, 30, 0, 0, MARKET_ZONE);
        ExpectedMoveCalculator postCloseCalc = new ExpectedMoveCalculator(Clock.fixed(postClose.toInstant(), MARKET_ZONE));
        OptionChainResponse expiredChain = StrategyTestUtils.createMockChain("SPY", 500.0);
        StrategyTestUtils.addOption(expiredChain, "2026-11-26", 0, 500.0, 1.0, 1.1, 0.5, false);
        ExpectedMoveResult expiredRes = postCloseCalc.calculateExpectedMoveWaterfall(expiredChain, "2026-11-26", null, 20.0, null);
        assertFalse(expiredRes.isValid());

        // Wide quotes + no saved IV + corrupted chain IV (-999.0) -> Circuit breaker
        OptionChainResponse noIvChain = StrategyTestUtils.createMockChain("ILLIQ", 100.0);
        noIvChain.setVolatility(-999.0);
        StrategyTestUtils.addOption(noIvChain, "2026-12-26", 30, 100.0, 0.0, 5.0, 0.5, false);
        StrategyTestUtils.addOption(noIvChain, "2026-12-26", 30, 100.0, 0.0, 5.0, -0.5, true);
        ExpectedMoveResult cbRes = calculator.calculateExpectedMoveWaterfall(noIvChain, "2026-12-26", null, null, null);
        assertFalse(cbRes.isValid());
        assertEquals(cbRes.getTierUsed(), ExpectedMoveTier.CIRCUIT_BREAKER);
    }

    @Test
    public void testSpotPriceResolutionAndMidpointTieBreaker() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 100.0);

        // Live tight bid/ask quote wins over chain underlyingPrice
        Quote liveQuote = new Quote();
        liveQuote.setBidPrice(102.40);
        liveQuote.setAskPrice(102.60);
        QuoteData quoteData = new QuoteData();
        quoteData.setQuote(liveQuote);
        assertEquals(calculator.resolveSpotPrice(chain, quoteData), 102.50, 0.001);

        // Wide stub after-hours equity quote (> 5% spread) falls back to mark/lastPrice
        Quote wideQuote = new Quote();
        wideQuote.setBidPrice(90.0);
        wideQuote.setAskPrice(110.0);
        wideQuote.setMark(102.0);
        QuoteData wideQuoteData = new QuoteData();
        wideQuoteData.setQuote(wideQuote);
        assertEquals(calculator.resolveSpotPrice(chain, wideQuoteData), 102.0, 0.001);

        // Underlying map fallback on OptionChainResponse
        OptionChainResponse mapChain = StrategyTestUtils.createMockChain("MSFT", 0.0);
        mapChain.setUnderlying(Map.of("bid", 400.0, "ask", 400.20));
        assertEquals(calculator.resolveSpotPrice(mapChain, null), 400.10, 0.001);

        // Tie-breaker: spot = 102.50 sits halfway between 100 and 105; 105 has tighter spread
        String expiry = "2026-12-26";
        StrategyTestUtils.addOption(chain, expiry, 30, 100.0, 4.00, 4.40, 0.55, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 100.0, 1.50, 1.90, -0.45, true); // combined spread = 0.80
        StrategyTestUtils.addOption(chain, expiry, 30, 105.0, 1.90, 2.00, 0.45, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 105.0, 4.40, 4.50, -0.55, true); // combined spread = 0.20

        List<StrikeRow> rows = calculator.buildStrikeRows(chain, expiry);
        assertEquals(rows.size(), 2);
        assertEquals(calculator.findAtmIndex(rows, 102.50), 1, "Tighter spread strike 105.0 should win midpoint tie");
    }

    @Test
    public void testIntrinsicParityAndNormalizeIvGuardrails() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("TSLA", 250.0);
        StrategyTestUtils.addOption(chain, "2026-12-26", 30, 200.0, 40.0, 41.0, 0.90, false); // intrinsic = 50, mid = 40.5 -> fails parity!
        List<StrikeRow> rows = calculator.buildStrikeRows(chain, "2026-12-26");
        assertFalse(calculator.isValidContract(rows.get(0).call(), 250.0, true, 0.01, 0.25));

        assertNull(ExpectedMoveCalculator.normalizeIvToDecimal(null));
        assertNull(ExpectedMoveCalculator.normalizeIvToDecimal(-999.0));
        assertNull(ExpectedMoveCalculator.normalizeIvToDecimal(0.0));
        assertNull(ExpectedMoveCalculator.normalizeIvToDecimal(Double.NaN));
        assertNull(ExpectedMoveCalculator.normalizeIvToDecimal(2000.0));
        assertEquals(ExpectedMoveCalculator.normalizeIvToDecimal(4.2), 0.042, 0.0001);
        assertEquals(ExpectedMoveCalculator.normalizeIvToDecimal(35.0), 0.35, 0.0001);
    }

    private static OptionChainResponse buildLiquidFiveStrikeChain(
            String symbol, double spot, String expiry, int dte, double step) {
        OptionChainResponse chain = StrategyTestUtils.createMockChain(symbol, spot);
        chain.setVolatility(20.0);

        // Strike -2*step
        StrategyTestUtils.addOption(chain, expiry, dte, spot - 2 * step, 14.90, 15.10, 0.80, false);
        StrategyTestUtils.addOption(chain, expiry, dte, spot - 2 * step, 5.90, 6.10, -0.20, true); // put mid = 6.0

        // Strike -1*step
        StrategyTestUtils.addOption(chain, expiry, dte, spot - step, 11.90, 12.10, 0.65, false);
        StrategyTestUtils.addOption(chain, expiry, dte, spot - step, 7.90, 8.10, -0.35, true); // put mid = 8.0

        // ATM Strike
        StrategyTestUtils.addOption(chain, expiry, dte, spot, 9.90, 10.10, 0.50, false); // call mid = 10.0
        StrategyTestUtils.addOption(chain, expiry, dte, spot, 9.90, 10.10, -0.50, true); // put mid = 10.0

        // Strike +1*step
        StrategyTestUtils.addOption(chain, expiry, dte, spot + step, 7.40, 7.60, 0.35, false); // call mid = 7.5
        StrategyTestUtils.addOption(chain, expiry, dte, spot + step, 12.40, 12.60, -0.65, true);

        // Strike +2*step
        StrategyTestUtils.addOption(chain, expiry, dte, spot + 2 * step, 5.40, 5.60, 0.20, false); // call mid = 5.5
        StrategyTestUtils.addOption(chain, expiry, dte, spot + 2 * step, 15.90, 16.10, -0.80, true);

        return chain;
    }

    private static void addSymmetricStrike(
            OptionChainResponse chain, String expiry, int dte, double strike, double callMid, double putMid) {
        StrategyTestUtils.addOption(chain, expiry, dte, strike, callMid - 0.05, callMid + 0.05, 0.50, false);
        StrategyTestUtils.addOption(chain, expiry, dte, strike, putMid - 0.05, putMid + 0.05, -0.50, true);
    }
}
