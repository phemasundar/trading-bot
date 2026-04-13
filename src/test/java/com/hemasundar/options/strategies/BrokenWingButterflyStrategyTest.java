package com.hemasundar.options.strategies;

import com.hemasundar.options.models.BrokenWingButterflyFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.utils.StrategyTestUtils;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import org.testng.annotations.BeforeMethod;

public class BrokenWingButterflyStrategyTest {
    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    private BrokenWingButterflyStrategy strategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new BrokenWingButterflyStrategy(StrategyType.BULLISH_BROKEN_WING_BUTTERFLY, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
    }

    @Test
    public void testFindValidTrades_Success() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // BWB Call candidate: 140/150/155
        // Leg 1 (Long 140): Ask 12.0
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 11.90, 12.00, 0.80, false);
        // Leg 2 (Short 150) x2: Bid 5.0
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 150.0, 5.00, 5.10, 0.50, false);
        // Leg 3 (Long 155): Ask 2.5
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 2.40, 2.50, 0.20, false);

        // Lower Wing = (150-140)*100 = 1000
        // Upper Wing = (155-150)*100 = 500
        // Total Debit = (12.00 + 2.50 - 2*5.00)*100 = (14.50 - 10.00)*100 = 450
        // Default Debit Filter: 450 < 1200 / 2 (600) -> PASS
        // Wing Ratio Filter: 500 < 2 * 1000 -> PASS

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true);
        filter.setMaxTotalDebit(500.0);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        // Total Debit = -Net Credit for a debit spread
        assertEquals(-trade.getNetCredit(), 450.0);
    }

    @Test
    public void testRejectByWingWidthRatio() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // 140/145/160 -> Lower Wing = 5, Upper Wing = 15. 15 > 2*5.
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 11.90, 12.00, 0.80, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 5.00, 5.10, 0.50, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 160.0, 0.10, 0.20, 0.05, false);

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());
    }

    @Test
    public void testRejectByDefaultDebitFilter() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Leg 1 Cost: 10.0 -> Limit is 5.0
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 9.90, 10.00, 0.80, false);
        // Short Legs: 2.0 x 2 = 4.0
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 150.0, 2.00, 2.10, 0.50, false);
        // Leg 3: 1.5
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 1.40, 1.50, 0.20, false);

        // Total Debit = (10.0 + 1.5 - 4.0) * 100 = 7.5 * 100 = 750
        // 750 > 1000 / 2 (500) -> REJECT

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());
    }

    @Test
    public void testFilterByPriceVsMaxDebitRatio() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 100.0);

        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 90.0, 12.00, 12.10, 0.80, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 100.0, 5.00, 5.10, 0.50, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 105.0, 2.00, 2.10, 0.20, false);

        // Debit = (12.10 + 2.10 - 10.00) * 100 = 4.20 * 100 = 420.
        // Price = 100.
        // Ratio = 2.0 -> Max Debit = 200. REJECT 420.
        // Ratio = 5.0 -> Max Debit = 500. PASS 420.

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true);
        filter.setPriceVsMaxDebitRatio(2.0);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());

        filter.setPriceVsMaxDebitRatio(5.0);
        trades = strategy.findTrades(chain, filter);
        assertFalse(trades.isEmpty());
    }

    @Test
    public void testFilterByUpperBreakevenDelta() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // 140/150/155
        // Leg 1: 140, Leg 2: 150, Leg 3: 155
        // Debit: 4.5. Lower Wing: 10.
        // Upper BE = Short Strike (150) + (Lower Wing (10) - Net Debit (4.5)) = 150 + 5.5 = 155.5
        // Nearest strike to 155.5 is 155.
        
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 12.00, 12.10, 0.80, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 150.0, 5.00, 5.10, 0.50, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 2.40, 2.50, 0.35, false); // Delta 0.35

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true);
        filter.setMaxUpperBreakevenDelta(0.20); // 0.35 > 0.20 REJECT

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());

        filter.setMaxUpperBreakevenDelta(0.40); // 0.35 < 0.40 PASS
        trades = strategy.findTrades(chain, filter);
        assertFalse(trades.isEmpty());
    }
}
