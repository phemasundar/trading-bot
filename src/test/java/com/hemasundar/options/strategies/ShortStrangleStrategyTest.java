package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.ShortStrangle;
import com.hemasundar.options.models.ShortStrangleFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.utils.StrategyTestUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;

public class ShortStrangleStrategyTest {

    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;

    private ShortStrangleStrategy strategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new ShortStrangleStrategy(StrategyType.SHORT_STRANGLE, finnHubAPIs, thinkOrSwinAPIs, Optional.empty());
    }

    @Test
    public void testFindValidTrades_Success() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short put (strike 140)
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 140.0, 2.50, 2.60, -0.18, true);
        // Add a short call (strike 160)
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 160.0, 1.50, 1.60, 0.16, false);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);
        filter.setMinDTE(40);
        filter.setMaxDTE(50);
        filter.setMinTotalCredit(100.0);

        LegFilter putLeg = new LegFilter();
        putLeg.setMinDelta(0.15);
        putLeg.setMaxDelta(0.20);
        filter.setPutShortLeg(putLeg);

        LegFilter callLeg = new LegFilter();
        callLeg.setMinDelta(0.15);
        callLeg.setMaxDelta(0.20);
        filter.setCallShortLeg(callLeg);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        
        double expectedCredit = (2.50 + 1.50) * 100;
        double expectedMaxLoss = (160.0 * 100) - expectedCredit;
        
        assertEquals(trade.getNetCredit(), expectedCredit, 0.01);
        assertEquals(trade.getMaxLoss(), expectedMaxLoss, 0.01);
    }

    @Test
    public void testFindValidTrades_NoPuts() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short call (strike 160) only
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 160.0, 1.50, 1.60, 0.16, false);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testFindValidTrades_NoCalls() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short put (strike 140) only
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 140.0, 2.50, 2.60, -0.18, true);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testFindValidTrades_FilterOutByDelta() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short put with invalid delta (-0.25)
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 145.0, 3.50, 3.60, -0.25, true);
        // Add a short call with valid delta (0.16)
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 160.0, 1.50, 1.60, 0.16, false);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);
        
        LegFilter putLeg = new LegFilter();
        putLeg.setMinDelta(0.15);
        putLeg.setMaxDelta(0.20);
        filter.setPutShortLeg(putLeg);

        LegFilter callLeg = new LegFilter();
        callLeg.setMinDelta(0.15);
        callLeg.setMaxDelta(0.20);
        filter.setCallShortLeg(callLeg);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testFindValidTrades_OverlappingStrikes() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Put strike >= call strike
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 155.0, 5.00, 5.10, -0.45, true);
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 145.0, 6.00, 6.10, 0.45, false);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testFindValidTrades_MaxLossFilter() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 140.0, 2.50, 2.60, -0.18, true);
        StrategyTestUtils.addOption(chain, "2026-01-02", 45, 160.0, 1.50, 1.60, 0.16, false);

        ShortStrangleFilter filter = new ShortStrangleFilter();
        filter.setTargetDTE(45);
        // Very tight max loss limit to force failure (requires less than $1000 risk)
        filter.setMaxLossLimit(1000.0);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }
}
