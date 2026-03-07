package com.hemasundar.unit.strategies;

import com.hemasundar.options.models.LongCallLeapFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.LongCallLeapStrategy;
import com.hemasundar.unit.StrategyTestUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class LongCallLeapStrategyTest {

    @Test
    public void testFindValidTrades_Success() {
        LongCallLeapStrategy strategy = new LongCallLeapStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Long Call: 120 Strike (ITM), Price: 35.00
        StrategyTestUtils.addOption(chain, "2026-06-19", 400, 120.0, 34.00, 35.00, 0.85, false);

        LongCallLeapFilter filter = new LongCallLeapFilter();
        filter.setTargetDTE(400);
        filter.setMinDTE(365);
        filter.setMaxDTE(730);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        assertEquals(trade.getNetCredit(), -3500.0, 0.01);
    }
}
