package com.hemasundar.unit.options.strategies;

import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.IronCondorStrategy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class IronCondorStrategyTest {

    private TestableIronCondorStrategy strategy;

    class TestableIronCondorStrategy extends IronCondorStrategy {
        @Override
        public List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
                OptionsStrategyFilter filter) {
            return super.findValidTrades(chain, expiryDate, filter);
        }
    }

    @BeforeMethod
    public void setUp() {
        // We can test findValidTrades by providing a synthetic option chain
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
        filter.setMaxBreakEvenPercentage(5.0);
        filter.setMaxNetExtrinsicValueToPricePercentage(20.0);
        filter.setMinNetExtrinsicValueToPricePercentage(0.0);
        filter.setMaxTotalCredit(100.0);
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
    public void testFindValidTrades_CreditSpreadFilter() {
        // Create synthetic OptionChainResponse with underlying price 100
        OptionChainResponse chain = createOptionChain(100.0);

        // Legacy format using CreditSpreadFilter directly for shared leg filter
        CreditSpreadFilter filter = new CreditSpreadFilter();
        filter.setTargetDTE(30);
        filter.setMaxLossLimit(1000.0);
        filter.setMinReturnOnRisk(0);
        filter.setMaxBreakEvenPercentage(5.0);
        filter.setMaxNetExtrinsicValueToPricePercentage(20.0);
        filter.setMinNetExtrinsicValueToPricePercentage(0.0);
        filter.setMaxTotalCredit(100.0);
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
        putLeg.setMinDelta(0.6);
        putLeg.setMaxDelta(0.9); // Deep ITM put -> strike > 100
        filter.setPutShortLeg(putLeg);
        LegFilter callLeg = new LegFilter();
        callLeg.setMinDelta(0.6);
        callLeg.setMaxDelta(0.9); // Deep ITM call -> strike < 100
        filter.setCallShortLeg(callLeg);

        List<TradeSetup> trades = strategy.findValidTrades(chain, "2024-04-19", filter);
        assertTrue(trades.isEmpty(), "Should skip overlapping strikes");
    }

    private OptionChainResponse createOptionChain(double currentPrice) {
        OptionChainResponse response = new OptionChainResponse();
        response.setStatus("SUCCESS");
        response.setUnderlyingPrice(currentPrice);

        Map<OptionChainResponse.ExpirationDateKey, Map<String, List<OptionChainResponse.OptionData>>> putMap = new HashMap<>();
        Map<OptionChainResponse.ExpirationDateKey, Map<String, List<OptionChainResponse.OptionData>>> callMap = new HashMap<>();

        Map<String, List<OptionChainResponse.OptionData>> putStrikes = new HashMap<>();
        Map<String, List<OptionChainResponse.OptionData>> callStrikes = new HashMap<>();

        // Generate puts below and above current price
        for (double strike = 80; strike <= 120; strike += 5) {
            String strikeStr = String.valueOf(strike);

            List<OptionChainResponse.OptionData> putList = new ArrayList<>();
            putList.add(createOption(strike, "PUT", currentPrice));
            putStrikes.put(strikeStr, putList);

            List<OptionChainResponse.OptionData> callList = new ArrayList<>();
            callList.add(createOption(strike, "CALL", currentPrice));
            callStrikes.put(strikeStr, callList);
        }

        OptionChainResponse.ExpirationDateKey expKey = new OptionChainResponse.ExpirationDateKey("2024-04-19:30");
        putMap.put(expKey, putStrikes);
        callMap.put(expKey, callStrikes);

        response.setPutExpDateMap(putMap);
        response.setCallExpDateMap(callMap);

        return response;
    }

    private OptionChainResponse.OptionData createOption(double strike, String type, double currentPrice) {
        OptionChainResponse.OptionData opt = new OptionChainResponse.OptionData();
        opt.setStrikePrice(strike);
        opt.setPutCall(type);
        opt.setMark(1.5); // Flat mark for simplicity
        opt.setBid(1.4);
        opt.setAsk(1.6);
        opt.setOpenInterest(100);
        opt.setTotalVolume(50);
        opt.setDaysToExpiration(30);

        // Simplistic delta estimation
        if ("PUT".equals(type)) {
            // Puts: lower strike -> lower absolute delta
            double delta = Math.max(0.01, Math.min(0.99, (strike - (currentPrice - 30)) / 60.0));
            opt.setDelta(-delta);
        } else {
            // Calls: higher strike -> lower delta
            double delta = Math.max(0.01, Math.min(0.99, ((currentPrice + 30) - strike) / 60.0));
            opt.setDelta(delta);
        }

        return opt;
    }
}
