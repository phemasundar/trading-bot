package com.hemasundar.options.strategies;

import com.hemasundar.options.models.*;
import com.hemasundar.utils.StrategyTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;

public class LongCallLeapStrategyTest {

    private LongCallLeapStrategy strategy;
    private OptionChainResponse mockChain;

    @Mock
    private FinnHubAPIs finnHubAPIs;

    @Mock
    private ThinkOrSwimAPIs ThinkOrSwimAPIs;

    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new LongCallLeapStrategy(StrategyType.LONG_CALL_LEAP, finnHubAPIs, ThinkOrSwimAPIs, java.util.Optional.empty());
        mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // ITM Call: 140 Strike, Ask 20.00
        StrategyTestUtils.addOption(mockChain, "2026-06-19", 400, 140.0, 19.50, 20.00, 0.70, false);
        // ATM Call: 150 Strike, Ask 10.00
        StrategyTestUtils.addOption(mockChain, "2026-06-19", 400, 150.0, 9.50, 10.00, 0.50, false);
    }

    @Test
    public void testFindValidTrades_Success() {
        LongCallLeapFilter filter = new LongCallLeapFilter();
        filter.setTargetDTE(400);
        filter.setMinDTE(365);
        filter.setMaxDTE(730);

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);

        // Found 2 trades (140, 150 ITM/ATM)
        assertEquals(trades.size(), 2);
        assertTrue(trades.get(0) instanceof LongCallLeap);
    }

    @Test
    public void testFindTrades_StrictLimit() {
        // Find top 1 trade
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(1)
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertEquals(trades.size(), 1);
        
        // Sorting check: High DTE/Savings priority.
        // With same expiry, it ranks by savings/price etc.
    }

    @Test
    public void testFindTrades_WithRelaxation() {
        // High minCostSavings making strict return 0 (impossible target)
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(2)
                .minCostSavingsPercent(99.0) 
                .relaxationPriority(Collections.singletonList("minCostSavingsPercent"))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        // Should find 2 trades after relaxing minCostSavings
        assertEquals(trades.size(), 2);
    }

    @Test
    public void testFindTrades_NoRelaxationConfigured() {
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(2)
                .minCostSavingsPercent(99.0)
                .relaxationPriority(null) // No relaxation
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testCustomSortPriority() {
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(10)
                .sortPriority(Arrays.asList("optionPricePercent", "breakevenCAGR"))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertTrue(trades.size() > 0);
    }

    @Test
    public void testOptionPricePctFilter_RejectsExpensiveOptions() {
        // Mock chain has AAPL at $150.0:
        // Strike 140: Ask 20.00 -> OptionPricePct = 20/150 * 100 = 13.33%
        // Strike 150: Ask 10.00 -> OptionPricePct = 10/150 * 100 = 6.67%
        // Add deep ITM strike 5 with ask 145.00 -> OptionPricePct = 145/150 * 100 = 96.67%
        StrategyTestUtils.addOption(mockChain, "2026-06-19", 400, 5.0, 144.50, 145.00, 1.00, false);

        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .filterExpressions(com.hemasundar.utils.MathExpressionParser.parseRules(
                        List.of("OPTION_PRICE_PCT <= 50")))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertEquals(trades.size(), 2);
        for (TradeSetup trade : trades) {
            LongCallLeap leap = (LongCallLeap) trade;
            assertTrue(leap.getOptionPricePercent() <= 50.0);
            assertNotEquals(leap.getLongCall().getStrikePrice(), 5.0);
        }
    }

    @Test
    public void testOptionPricePctFilter_StrictThreshold() {
        // Strike 140 is 13.33%, Strike 150 is 6.67%
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .filterExpressions(com.hemasundar.utils.MathExpressionParser.parseRules(
                        List.of("OPTION_PRICE_PCT <= 10.0")))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertEquals(trades.size(), 1);
        LongCallLeap leap = (LongCallLeap) trades.get(0);
        assertEquals(leap.getLongCall().getStrikePrice(), 150.0);
        assertEquals(leap.getOptionPrice(), 10.0, 0.001);
        assertEquals(leap.getOptionPricePercent(), (10.0 / 150.0) * 100.0, 0.001);
    }
}
