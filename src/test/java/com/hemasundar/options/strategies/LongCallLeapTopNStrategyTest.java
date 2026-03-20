package com.hemasundar.options.strategies;

import com.hemasundar.options.models.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class LongCallLeapTopNStrategyTest {

    private LongCallLeapTopNStrategy strategy;
    private OptionChainResponse mockChain;

    @BeforeMethod
    public void setUp() {
        strategy = new LongCallLeapTopNStrategy();
        
        mockChain = new OptionChainResponse();
        mockChain.setSymbol("AAPL");
        mockChain.setUnderlyingPrice(150.0);
        
        Map<OptionChainResponse.ExpirationDateKey, Map<String, List<OptionChainResponse.OptionData>>> callExpDateMap = new HashMap<>();
        OptionChainResponse.ExpirationDateKey key = new OptionChainResponse.ExpirationDateKey("2026-01-02", 365);
        
        Map<String, List<OptionChainResponse.OptionData>> strikeMap = new HashMap<>();
        
        // ITM Call
        OptionChainResponse.OptionData itmCall = new OptionChainResponse.OptionData();
        itmCall.setStrikePrice(140.0);
        itmCall.setMark(20.0);
        itmCall.setAsk(20.0); // Crucial for filtration
        itmCall.setDelta(0.7);
        itmCall.setExpirationDate("2026-01-02");
        itmCall.setDaysToExpiration(365);
        
        // ATM Call
        OptionChainResponse.OptionData atmCall = new OptionChainResponse.OptionData();
        atmCall.setStrikePrice(150.0);
        atmCall.setMark(10.0);
        atmCall.setAsk(10.0); // Crucial for filtration
        atmCall.setDelta(0.5);
        atmCall.setExpirationDate("2026-01-02");
        atmCall.setDaysToExpiration(365);

        strikeMap.put("140.0", List.of(itmCall));
        strikeMap.put("150.0", List.of(atmCall));
        callExpDateMap.put(key, strikeMap);
        
        mockChain.setCallExpDateMap(callExpDateMap);
        mockChain.setPutExpDateMap(new HashMap<>()); // Avoid NPE
    }

    @Test
    public void testFindTrades_StrictSuffice() {
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(1)
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertEquals(trades.size(), 1);
        assertTrue(trades.get(0) instanceof LongCallLeap);
    }

    @Test
    public void testFindTrades_WithRelaxation() {
        // High minCostSavings making strict return 0
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(2)
                .minCostSavingsPercent(99.0) // impossible
                .relaxationPriority(Arrays.asList("minCostSavingsPercent"))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        // Should find 2 trades after relaxation (which nulls minCostSavings)
        assertEquals(trades.size(), 2);
    }

    @Test
    public void testFindTrades_NoRelaxationConfigured() {
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(2)
                .minCostSavingsPercent(99.0) // impossible
                .relaxationPriority(null) // No relaxation
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertEquals(trades.size(), 0);
    }

    @Test
    public void testCustomSortPriority() {
        LongCallLeapFilter filter = LongCallLeapFilter.builder()
                .minDTE(300)
                .maxDTE(500)
                .topTradesCount(10)
                .sortPriority(Arrays.asList("optionPricePercent", "breakevenCAGR"))
                .build();

        List<TradeSetup> trades = strategy.findTrades(mockChain, filter);
        assertNotNull(trades);
        assertTrue(trades.size() > 0);
    }
}
