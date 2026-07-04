package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
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

public class ShortPutStrategyTest {
    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    private ShortPutStrategy strategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new ShortPutStrategy(StrategyType.SHORT_PUT, finnHubAPIs, thinkOrSwinAPIs, java.util.Optional.empty());
    }

    @Test
    public void testFindValidTrades_Success() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short put (strike 145)
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 2.50, 2.60, -0.30, true);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMinDTE(20);
        filter.setMaxDTE(40);
        filter.setMinTotalCredit(100.0);

        LegFilter shortLeg = new LegFilter();
        shortLeg.setMinDelta(0.20);
        shortLeg.setMaxDelta(0.40);
        filter.setShortLeg(shortLeg);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        assertEquals(trade.getNetCredit(), 250.0, 0.01);
        assertEquals(trade.getMaxLoss(), (145.0 - 2.50) * 100, 0.01);
    }

    @Test
    public void testFindValidTrades_NoPuts() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(0);
        filter.setMinDTE(20);
        filter.setMaxDTE(40);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testFindValidTrades_FilterOutByDelta() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 2.50, 2.60, -0.50, true);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        LegFilter shortLeg = new LegFilter();
        shortLeg.setMinDelta(0.20);
        shortLeg.setMaxDelta(0.40);
        filter.setShortLeg(shortLeg);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }
}
