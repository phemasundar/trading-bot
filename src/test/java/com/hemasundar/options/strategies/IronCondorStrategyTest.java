package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondor;
import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.TradeSetup;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class IronCondorStrategyTest {

    private IronCondorStrategy strategy;

    @BeforeMethod
    public void setUp() {
        strategy = new TestableIronCondorStrategy();
    }

    @Test
    public void testFindValidTrades_IronCondorFilter() {
        // Create synthetic OptionChainResponse with underlying price 100
        OptionChainResponse chain = createOptionChain(100.0);

        // Setup filter
        IronCondorFilter filter = new IronCondorFilter();
        filter.setTargetDTE(30);
        filter.setMaxLossLimit(1000.0);
        filter.setMinReturnOnRisk(0);
        filter.setMaxBreakEvenPercentage(null);
        filter.setMaxNetExtrinsicValueToPricePercentage(null);
        filter.setMinNetExtrinsicValueToPricePercentage(null);
        filter.setMaxTotalCredit(null);
        filter.setMinTotalCredit(0.1);

        LegFilter shortPutFilter = new LegFilter();
        shortPutFilter.setMaxDelta(0.4);
        shortPutFilter.setMinDelta(0.1);
        filter.setPutShortLeg(shortPutFilter);

        LegFilter shortCallFilter = new LegFilter();
        shortCallFilter.setMaxDelta(0.4);
        shortCallFilter.setMinDelta(0.1);
        filter.setCallShortLeg(shortCallFilter);

        List<TradeSetup> trades = strategy.findValidTrades(chain, "2024-04-19", filter);
        System.out.println("IronCondor trades found: " + (trades != null ? trades.size() : "null"));
        // Based on our synthetic chain, we expect to find valid Iron Condors
        assertNotNull(trades);
        assertFalse(trades.isEmpty(), "Should find valid Iron Condors");

        for (TradeSetup trade : trades) {
            assertTrue(trade instanceof IronCondor);
            IronCondor ic = (IronCondor) trade;
            assertNotNull(ic.getPutLeg());
            assertNotNull(ic.getCallLeg());
            assertTrue(ic.getMaxLoss() <= 1000.0);
        }
    }

    @Test
    public void testFindValidTrades_NoCallLegs() {
        OptionChainResponse chain = createOptionChain(100.0);
        // Clear call map to simulate no call spreads found
        chain.getCallExpDateMap().clear();

        IronCondorFilter filter = new IronCondorFilter();
        List<TradeSetup> trades = strategy.findValidTrades(chain, "2024-04-19", filter);

        assertNotNull(trades);
        assertTrue(trades.isEmpty(), "Should find zero trades when one leg is empty");
    }

    @Test
    public void testFindValidTrades_CreditSpreadFilter() {
        OptionChainResponse chain = createOptionChain(100.0);

        // Legacy format using CreditSpreadFilter directly for shared leg filter
        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMaxLossLimit(1000.0);
        filter.setMinReturnOnRisk(0);
        filter.setMaxBreakEvenPercentage(null);
        filter.setMaxNetExtrinsicValueToPricePercentage(null);
        filter.setMinNetExtrinsicValueToPricePercentage(null);
        filter.setMaxTotalCredit(null);
        filter.setMinTotalCredit(0.1);

        LegFilter shortLegFilter = new LegFilter();
        shortLegFilter.setMaxDelta(0.4);
        shortLegFilter.setMinDelta(0.1);
        filter.setShortLeg(shortLegFilter);

        List<TradeSetup> trades = strategy.findValidTrades(chain, "2024-04-19", filter);

        assertNotNull(trades);
        assertFalse(trades.isEmpty(), "Should find valid Iron Condors using CreditSpreadFilter");
    }

    @Test
    public void testFindValidTrades_OverlapStrikes() {
        // Provide a chain where put and call don't have enough room and overlap
        OptionChainResponse chain = createOptionChain(100.0);

        IronCondorFilter filter = new IronCondorFilter();
        filter.setTargetDTE(30);
        // Force the deltas such that short put strike >= short call strike (e.g.
        // inverted)
        LegFilter putLeg = new LegFilter();
        putLeg.setMinDelta(0.8); // High delta for deep ITM put -> high strike
        filter.setPutShortLeg(putLeg);

        LegFilter callLeg = new LegFilter();
        callLeg.setMinDelta(0.8); // High delta for deep ITM call -> low strike
        filter.setCallShortLeg(callLeg);

        List<TradeSetup> trades = strategy.findValidTrades(chain, "2024-04-19", filter);

        // Since all combinations will overlap (Put strike > Call strike), no condors
        // should be formed
        assertTrue(trades.isEmpty(), "Should filter out overlapping wings");
    }

    // Helper to create synthetic chain
    private OptionChainResponse createOptionChain(double price) {
        OptionChainResponse response = new OptionChainResponse();
        response.setSymbol("SYMBOL");
        response.setUnderlyingPrice(price);

        OptionChainResponse.ExpirationDateKey key = new OptionChainResponse.ExpirationDateKey("2024-04-19", 30);
        Map<String, List<OptionChainResponse.OptionData>> puts = new HashMap<>();
        Map<String, List<OptionChainResponse.OptionData>> calls = new HashMap<>();

        // Put Strikes: 80, 85, 90
        puts.put("80.0", List.of(createOption(OptionType.PUT, 80.0, 0.15, 0.40, true)));
        puts.put("85.0", List.of(createOption(OptionType.PUT, 85.0, 0.25, 0.80, true)));
        puts.put("90.0", List.of(createOption(OptionType.PUT, 90.0, 0.35, 1.50, true)));

        // Call Strikes: 110, 115, 120
        calls.put("110.0", List.of(createOption(OptionType.CALL, 110.0, 0.30, 1.20, false)));
        calls.put("115.0", List.of(createOption(OptionType.CALL, 115.0, 0.20, 0.70, false)));
        calls.put("120.0", List.of(createOption(OptionType.CALL, 120.0, 0.10, 0.30, false)));

        response.setPutExpDateMap(new HashMap<>(Map.of(key, puts)));
        response.setCallExpDateMap(new HashMap<>(Map.of(key, calls)));

        return response;
    }

    private OptionChainResponse.OptionData createOption(OptionType type, double strike, double delta, double mark,
            boolean isPut) {
        OptionChainResponse.OptionData data = new OptionChainResponse.OptionData();
        data.setStrikePrice(strike);
        data.setDelta(isPut ? -delta : delta);
        data.setMark(mark);
        data.setBid(mark - 0.05);
        data.setAsk(mark + 0.05);
        data.setOpenInterest(100);
        data.setTotalVolume(50);
        data.setPutCall(type.toString());
        data.setExpirationDate("2024-04-19");
        data.setDaysToExpiration(30);
        data.setExtrinsicValue(mark); // Simple assumption for testing
        data.setIntrinsicValue(0.0);
        return data;
    }

    // Since findValidTrades calls sub-strategies, we use a test subclass that
    // stubbs out sub-strategy behavior or provides real ones
    private static class TestableIronCondorStrategy extends IronCondorStrategy {
        public TestableIronCondorStrategy() {
            super();
        }
    }
}
