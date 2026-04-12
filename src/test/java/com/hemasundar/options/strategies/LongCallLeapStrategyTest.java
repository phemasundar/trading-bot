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
import com.hemasundar.apis.ThinkOrSwinAPIs;

public class LongCallLeapStrategyTest {

    private LongCallLeapStrategy strategy;
    private OptionChainResponse mockChain;

    @Mock
    private FinnHubAPIs finnHubAPIs;

    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;

    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new LongCallLeapStrategy(finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
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
}
