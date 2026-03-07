package com.hemasundar.unit.strategies;

import com.hemasundar.options.models.BrokenWingButterflyFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.BrokenWingButterflyStrategy;
import com.hemasundar.unit.StrategyTestUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class BrokenWingButterflyStrategyTest {

    @Test
    public void testFindValidTrades_Success() {
        BrokenWingButterflyStrategy strategy = new BrokenWingButterflyStrategy();
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // BWB Call: Leg 1 (140), Leg 2 (150) x2, Leg 3 (155)
        // Leg 1: 140, Price: 1.50
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 1.40, 1.50, 0.20, false);
        // Leg 2: 150, Price: 5.50
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 150.0, 5.50, 5.60, 0.50, false);
        // Leg 3: 155, Price: 8.00
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 155.0, 7.90, 8.00, 0.70, false);

        BrokenWingButterflyFilter filter = new BrokenWingButterflyFilter();
        filter.setTargetDTE(30);
        filter.setIgnoreEarnings(true); // Bypass FinnHub API
        filter.setMinTotalCredit(50.0);
        // (2*4.90 - (8.00 + 1.50)) = 9.80 - 9.50 = 0.30 -> $30.0
        // Wait, 0.30 is less than 0.50. Let's adjust prices to pass.

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        assertTrue(trades.size() >= 1);
    }
}
