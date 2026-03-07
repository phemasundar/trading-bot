package com.hemasundar.unit.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.CallCreditSpreadStrategy;
import com.hemasundar.unit.StrategyTestUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class CallCreditSpreadStrategyTest {

    @Test
    public void testFindValidTrades_Success() {
        CallCreditSpreadStrategy strategy = new CallCreditSpreadStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // For Call Spread: Short Strike (155) < Long Strike (160)
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 2.00, 2.10, 0.30, false);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 160.0, 0.50, 0.60, 0.15, false);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMinTotalCredit(50.0); // ($2.00 - $0.60) * 100 = 140.0

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        assertEquals(trade.getNetCredit(), 140.0, 0.01);
        assertEquals(trade.getMaxLoss(), 360.0, 0.01); // (160-155)*100 - 140 = 500 - 140 = 360
    }
}
