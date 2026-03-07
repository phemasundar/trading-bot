package com.hemasundar.unit.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.PutCreditSpreadStrategy;
import com.hemasundar.unit.StrategyTestUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class PutCreditSpreadStrategyTest {

    @Test
    public void testFindValidTrades_Success() {
        PutCreditSpreadStrategy strategy = new PutCreditSpreadStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Add a short put (higher strike, e.g. 145)
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 2.50, 2.60, 0.30, true);
        // Add a long put (lower strike, e.g. 140)
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 1.00, 1.10, 0.15, true);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMinDTE(20);
        filter.setMaxDTE(40);
        filter.setMinTotalCredit(100.0); // ($2.50 - $1.10) * 100 = 140.0

        LegFilter shortLeg = new LegFilter();
        shortLeg.setMinDelta(0.20);
        shortLeg.setMaxDelta(0.40);
        filter.setShortLeg(shortLeg);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertEquals(trades.size(), 1);
        TradeSetup trade = trades.get(0);
        assertEquals(trade.getNetCredit(), 140.0, 0.01);
        assertEquals(trade.getMaxLoss(), 360.0, 0.01); // (145-140)*100 - 140 = 500 - 140 = 360
    }

    @Test
    public void testFindValidTrades_FilterOutByCredit() {
        PutCreditSpreadStrategy strategy = new PutCreditSpreadStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 145.0, 2.50, 2.60, 0.30, true);
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 2.00, 2.10, 0.15, true);

        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMinTotalCredit(100.0); // ($2.50 - $2.10) * 100 = 40.0. Should fail minTotalCredit(100)

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(trades.size(), 0);
    }
}
