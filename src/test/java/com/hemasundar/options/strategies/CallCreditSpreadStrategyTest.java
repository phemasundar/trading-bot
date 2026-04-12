package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
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

public class CallCreditSpreadStrategyTest {
    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    private CallCreditSpreadStrategy strategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new CallCreditSpreadStrategy(finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
    }

    @Test
    public void testFindValidTrades_Success() {
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
