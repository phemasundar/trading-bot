package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.dto.Trade;
import com.hemasundar.options.expectedmove.ExpectedMoveCalculator;
import com.hemasundar.options.expectedmove.ExpectedMoveResult;
import com.hemasundar.options.expectedmove.ExpectedMoveTier;
import com.hemasundar.options.models.CallCreditSpread;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.LongCallLeap;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionFilterValueResolver;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.ZebraTrade;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.FilterParser;
import com.hemasundar.utils.StrategyTestUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ExpectedMoveFilterIntegrationTest {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwimAPIs thinkOrSwimAPIs;
    @Mock
    private SupabaseService supabaseService;

    private Clock fixedClock;
    private ExpectedMoveCalculator fixedCalculator;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        ZonedDateTime fixedNow = ZonedDateTime.of(2026, 11, 26, 10, 0, 0, 0, MARKET_ZONE);
        fixedClock = Clock.fixed(fixedNow.toInstant(), MARKET_ZONE);
        fixedCalculator = new ExpectedMoveCalculator(fixedClock);
    }

    @Test
    public void testPutCreditSpread_ExpectedMoveBreakevenFilterAndTradeDtoFormatting() {
        String expiry = "2026-12-26";
        // Spot = 500, ATM Straddle = 20.0 -> Tier 2 EM = 0.85 * 20 = 17.0 -> LOWER_EXPECTED_MOVE_PRICE = 483.0, UPPER_EXPECTED_MOVE_PRICE = 517.0
        OptionChainResponse chain = StrategyTestUtils.createMockChain("SPY", 500.0);
        StrategyTestUtils.addOption(chain, expiry, 30, 500.0, 9.90, 10.10, 0.50, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 500.0, 9.90, 10.10, -0.50, true);
        // Safe PCS: Sell 480 Put @ 2.50, Buy 475 Put @ 1.50 -> Credit = 1.00, Lower BE = 479.00 <= 483.00 (PASS)
        StrategyTestUtils.addOption(chain, expiry, 30, 480.0, 2.50, 2.60, -0.18, true);
        StrategyTestUtils.addOption(chain, expiry, 30, 475.0, 1.40, 1.50, -0.12, true);
        // Aggressive PCS: Sell 490 Put @ 4.50, Buy 485 Put @ 3.00 -> Credit = 1.50, Lower BE = 488.50 > 483.00 (FAIL)
        StrategyTestUtils.addOption(chain, expiry, 30, 490.0, 4.50, 4.60, -0.30, true);
        StrategyTestUtils.addOption(chain, expiry, 30, 485.0, 2.90, 3.00, -0.22, true);

        PutCreditSpreadStrategy pcsStrategy = new PutCreditSpreadStrategy(
                StrategyType.PUT_CREDIT_SPREAD, finnHubAPIs, thinkOrSwimAPIs, Optional.of(supabaseService));
        pcsStrategy.setExpectedMoveCalculator(fixedCalculator);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setConditions(List.of("LOWER_BREAKEVEN_PRICE <= LOWER_EXPECTED_MOVE_PRICE"));
        FilterParser.initFilterExpressions(filter);

        List<TradeSetup> trades = pcsStrategy.findTrades(chain, filter);
        assertFalse(trades.isEmpty());
        for (TradeSetup setup : trades) {
            assertTrue(setup.getBreakEvenPrice() <= 483.0,
                    "Surviving PCS breakeven " + setup.getBreakEvenPrice() + " must be <= LOWER_EXPECTED_MOVE_PRICE (483.0)");
            assertNotNull(setup.getExpectedMove());
            assertTrue(setup.getExpectedMove().isValid());
        }

        Trade dto = Trade.fromTradeSetup(trades.get(0), "SPY");
        assertEquals(dto.getExpectedMoveLowerPrice(), 483.0, 0.01);
        assertEquals(dto.getExpectedMoveUpperPrice(), 517.0, 0.01);
        assertEquals(dto.getExpectedMoveTier(), "Tier 2");
        assertNotNull(dto.getExpectedMoveReason());
        assertTrue(dto.getExpectedMoveReason().contains("Tier 2"));
        assertTrue(dto.getTradeDetails().contains("EM (1σ, Tier 2): $483.00 – $517.00"));
        assertTrue(dto.getTradeDetails().contains("EM tier selection notes: " + dto.getExpectedMoveReason()));
    }

    @Test
    public void testCallCreditSpreadAndIronCondor_ExpectedMoveFilteringAndMemoization() {
        String expiry = "2026-12-26";
        // Spot = 500, Tier 2 EM = 17.0 -> LOWER_EXPECTED_MOVE_PRICE = 483.0, UPPER_EXPECTED_MOVE_PRICE = 517.0
        OptionChainResponse chain = StrategyTestUtils.createMockChain("SPY", 500.0);
        StrategyTestUtils.addOption(chain, expiry, 30, 500.0, 9.90, 10.10, 0.50, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 500.0, 9.90, 10.10, -0.50, true);

        // Put wing: Sell 482 Put @ 2.50, Buy 477 Put @ 1.00 -> Put credit = $150
        StrategyTestUtils.addOption(chain, expiry, 30, 482.0, 2.50, 2.60, -0.15, true);
        StrategyTestUtils.addOption(chain, expiry, 30, 477.0, 0.90, 1.00, -0.10, true);

        // Call wing (Safe): Sell 518 Call @ 2.50, Buy 523 Call @ 1.00 -> Call credit = $150 (CCS BE = 519.50 >= 517.0)
        StrategyTestUtils.addOption(chain, expiry, 30, 518.0, 2.50, 2.60, 0.15, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 523.0, 0.90, 1.00, 0.10, false);

        // Call wing (Unsafe for CCS): Sell 510 Call @ 4.00, Buy 515 Call @ 2.50 -> Call credit = $150 (CCS BE = 511.50 < 517.0)
        StrategyTestUtils.addOption(chain, expiry, 30, 510.0, 4.00, 4.10, 0.30, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 515.0, 2.40, 2.50, 0.22, false);

        AtomicInteger emCalculations = new AtomicInteger(0);
        ExpectedMoveCalculator countingCalculator = new ExpectedMoveCalculator(fixedClock) {
            @Override
            public ExpectedMoveResult calculateExpectedMoveWaterfall(
                    OptionChainResponse c, String exp, com.hemasundar.pojos.QuotesResponse.QuoteData q,
                    Double savedIv, Double earningsDays) {
                emCalculations.incrementAndGet();
                return super.calculateExpectedMoveWaterfall(c, exp, q, savedIv, earningsDays);
            }
        };

        PutCreditSpreadStrategy pcsStrategy = new PutCreditSpreadStrategy(
                StrategyType.PUT_CREDIT_SPREAD, finnHubAPIs, thinkOrSwimAPIs, Optional.empty());
        CallCreditSpreadStrategy ccsStrategy = new CallCreditSpreadStrategy(
                StrategyType.CALL_CREDIT_SPREAD, finnHubAPIs, thinkOrSwimAPIs, Optional.empty());
        IronCondorStrategy icStrategy = new IronCondorStrategy(
                StrategyType.IRON_CONDOR, finnHubAPIs, thinkOrSwimAPIs, Optional.empty(), pcsStrategy, ccsStrategy);

        pcsStrategy.setExpectedMoveCalculator(countingCalculator);
        ccsStrategy.setExpectedMoveCalculator(countingCalculator);
        icStrategy.setExpectedMoveCalculator(countingCalculator);

        CreditSpreadFilter ccsFilter = new CreditSpreadFilter();
        ccsFilter.setTargetDTE(30);
        ccsFilter.setConditions(List.of("UPPER_BREAKEVEN_PRICE >= UPPER_EXPECTED_MOVE_PRICE"));
        FilterParser.initFilterExpressions(ccsFilter);

        List<TradeSetup> ccsTrades = ccsStrategy.findTrades(chain, ccsFilter);
        assertFalse(ccsTrades.isEmpty());
        for (TradeSetup t : ccsTrades) {
            assertTrue(t.getEffectiveUpperBreakEvenPrice() >= 517.0);
            assertNull(t.getEffectiveLowerBreakEvenPrice());
        }

        IronCondorFilter icFilter = new IronCondorFilter();
        icFilter.setTargetDTE(30);
        icFilter.setConditions(List.of(
                "MAX_LOSS <= 1000",
                "LOWER_BREAKEVEN_PRICE <= LOWER_EXPECTED_MOVE_PRICE",
                "UPPER_BREAKEVEN_PRICE >= UPPER_EXPECTED_MOVE_PRICE"));
        FilterParser.initFilterExpressions(icFilter);

        List<TradeSetup> icTrades = icStrategy.findTrades(chain, icFilter);
        assertFalse(icTrades.isEmpty());
        for (TradeSetup t : icTrades) {
            assertTrue(t.getEffectiveLowerBreakEvenPrice() <= 483.0);
            assertTrue(t.getEffectiveUpperBreakEvenPrice() >= 517.0);
        }

        // Verify memoization: Expected Move for (SPY, 2026-12-26) was computed ONLY ONCE across both CCS and Iron Condor!
        assertEquals(emCalculations.get(), 1, "Expected Move must be memoized once per (chain, expiry)");
    }

    @Test
    public void testCrossScopeDottedLegVsExpectedMoveAndValueResolver() {
        String expiry = "2026-12-26";
        OptionChainResponse chain = StrategyTestUtils.createMockChain("QQQ", 400.0);
        // ATM Straddle = 10 + 10 = 20 -> Tier 2 EM = 17.0 -> LOWER_EXPECTED_MOVE_PRICE = 383.0
        StrategyTestUtils.addOption(chain, expiry, 30, 400.0, 9.90, 10.10, 0.50, false);
        StrategyTestUtils.addOption(chain, expiry, 30, 400.0, 9.90, 10.10, -0.50, true);
        StrategyTestUtils.addOption(chain, expiry, 30, 380.0, 2.00, 2.10, -0.15, true);
        StrategyTestUtils.addOption(chain, expiry, 30, 390.0, 4.00, 4.10, -0.28, true);

        ShortPutStrategy shortPutStrategy = new ShortPutStrategy(
                StrategyType.SHORT_PUT, finnHubAPIs, thinkOrSwimAPIs, Optional.empty());
        shortPutStrategy.setExpectedMoveCalculator(fixedCalculator);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setConditions(List.of("SHORT_LEG.STRIKE_PRICE <= LOWER_EXPECTED_MOVE_PRICE"));
        FilterParser.initFilterExpressions(filter);

        // Ensure SHORT_LEG.STRIKE_PRICE <= LOWER_EXPECTED_MOVE_PRICE was NOT routed to shortLeg (since LOWER_EXPECTED_MOVE_PRICE is trade/expiry scope)
        assertNull(filter.getShortLeg());

        List<TradeSetup> trades = shortPutStrategy.findTrades(chain, filter);
        assertEquals(trades.size(), 1);
        assertEquals(trades.get(0).getLegs().get(0).getStrike(), 380.0, 0.01);

        // Verify all *_EXPECTED_MOVE* variables in OptionFilterValueResolver + Lognormal Trade DTO formatting
        ExpectedMoveResult lognormalEm = ExpectedMoveResult.builder()
                .valid(true)
                .spotPrice(100.0)
                .upsideMove(35.0)
                .downsideMove(26.0)
                .upperBound(135.0)
                .lowerBound(74.0)
                .upsideMovePct(35.0)
                .downsideMovePct(26.0)
                .lognormal(true)
                .tierUsed(ExpectedMoveTier.LEAPS_LOGNORMAL)
                .reason("LEAPS")
                .build();

        OptionChainResponse.OptionData leapOpt = new OptionChainResponse.OptionData();
        leapOpt.setStrikePrice(80.0);
        leapOpt.setAsk(30.0);
        leapOpt.setMark(29.5);
        leapOpt.setDelta(0.75);
        leapOpt.setExpirationDate("2028-01-21");
        leapOpt.setDaysToExpiration(420);

        LongCallLeap leap = LongCallLeap.builder()
                .longCall(leapOpt)
                .currentPrice(100.0)
                .optionPrice(30.0)
                .breakEvenPrice(110.0)
                .breakEvenPercentage(10.0)
                .netCredit(-3000.0)
                .maxLoss(3000.0)
                .expectedMove(lognormalEm)
                .build();

        assertNull(leap.getEffectiveLowerBreakEvenPrice());
        assertEquals(leap.getEffectiveUpperBreakEvenPrice(), 110.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "UPPER_EXPECTED_MOVE_PRICE"), 135.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "LOWER_EXPECTED_MOVE_PRICE"), 74.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "UPSIDE_EXPECTED_MOVE_DOLLARS"), 35.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "DOWNSIDE_EXPECTED_MOVE_DOLLARS"), 26.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "UPSIDE_EXPECTED_MOVE_PCT"), 35.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "DOWNSIDE_EXPECTED_MOVE_PCT"), 26.0, 0.01);
        assertEquals(OptionFilterValueResolver.resolveTradeValue(leap, "UPPER_BREAKEVEN_PRICE"), 110.0, 0.01);

        ZebraTrade zebra = ZebraTrade.builder().breakEvenPrice(104.0).build();
        assertNull(zebra.getEffectiveLowerBreakEvenPrice());
        assertEquals(zebra.getEffectiveUpperBreakEvenPrice(), 104.0, 0.01);

        CallCreditSpread ccs = CallCreditSpread.builder().breakEvenPrice(112.0).build();
        assertNull(ccs.getEffectiveLowerBreakEvenPrice());
        assertEquals(ccs.getEffectiveUpperBreakEvenPrice(), 112.0, 0.01);

        Trade leapDto = Trade.fromTradeSetup(leap, "AAPL");
        assertEquals(leapDto.getExpectedMoveReason(), "LEAPS");
        assertTrue(leapDto.getTradeDetails().contains("EM (1σ, LEAPS): $74.00 – $135.00 (-$26.00 / +$35.00"));
        assertTrue(leapDto.getTradeDetails().contains("EM tier selection notes: LEAPS"));
    }
}
