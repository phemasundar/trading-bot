package com.hemasundar.unit.strategies;

import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.IronCondorStrategy;
import com.hemasundar.unit.StrategyTestUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class IronCondorStrategyTest {

    @Test
    public void testFindValidTrades_Success() {
        IronCondorStrategy strategy = new IronCondorStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Put Spread: 145/140
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 2.50, 2.60, 0.30, true);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 1.00, 1.10, 0.15, true);

        // Call Spread: 155/160
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 2.00, 2.10, 0.30, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 160.0, 0.50, 0.60, 0.15, false);

        IronCondorFilter filter = new IronCondorFilter();
        filter.setTargetDTE(30);
        filter.setMinTotalCredit(200.0); // (1.40 + 1.40) * 100 = 280.0

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        assertEquals(trade.getNetCredit(), 280.0, 0.01);
        assertEquals(trade.getMaxLoss(), 220.0, 0.01); // (max width 500) - 280 = 220
    }
}
