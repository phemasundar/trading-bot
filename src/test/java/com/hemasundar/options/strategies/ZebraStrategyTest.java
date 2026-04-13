package com.hemasundar.options.strategies;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.ZebraFilter;
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

public class ZebraStrategyTest {
    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    private ZebraStrategy strategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new ZebraStrategy(StrategyType.BULLISH_ZEBRA, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
    }

    @Test
    public void testFindValidTrades_Success() {
        OptionChainResponse chain = StrategyTestUtils.createMockChain("AAPL", 150.0);

        // Short Call: 150 Strike (ATM), Price: 5.00
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, false);
        // Long Call: 140 Strike (ITM), Price: 12.00 (x2)
        StrategyTestUtils.addOption(chain, "2026-01-02", 30, 140.0, 11.90, 12.00, 0.75, false);

        ZebraFilter filter = new ZebraFilter();
        filter.setTargetDTE(30);
        filter.setMaxNetExtrinsicValueToPricePercentage(2.0); // (2*longAsk - shortBid) - intrinsic?
        // Actually Zebra logic: netExtrinsic = (2*longExtrinsic - shortExtrinsic)

        List<TradeSetup> trades = strategy.findTrades(chain, filter);

        // Note: Zebra calculates extrinsic internally.
        // With 150 ATM, extrinsic is ~5.00.
        // With 140 ITM, intrinsic is 10, extrinsic is 2.00.
        // Net extrinsic = 2*2.00 - 5.00 = -1.00 (Perfect!)

        assertTrue(trades.size() >= 1);
    }
}
