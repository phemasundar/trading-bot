package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.utils.StrategyTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class IVDataCollectorTest {

    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    private IVDataCollector ivDataCollector;

    @BeforeMethod
    public void setUp() {
        thinkOrSwinAPIs = mock(ThinkOrSwinAPIs.class);
        ivDataCollector = new IVDataCollector(thinkOrSwinAPIs);
    }

    @Test
    public void testCollectIVDataPoint_Success() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // Add ATM options for IV collection (~30 DTE)
        // Strike 150, PUT and CALL
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, true); // PUT
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, false); // CALL

        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getSymbol(), symbol);
        assertEquals(result.getStrike(), 150.0);
        assertEquals(result.getDte(), 30);
        assertNotNull(result.getAtmPutIV());
        assertNotNull(result.getAtmCallIV());
    }

    @Test
    public void testCollectIVDataPoint_NoOptionsAvailable() {
        String symbol = "NVR";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);
        // No options added, so the symbol is not optionable

        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getSymbol(), symbol);
        assertTrue(result.isNoOptions());
    }

    @Test
    public void testCollectIVDataPoint_NoOptionsAvailable_ZeroUnderlyingPrice() {
        // Mirrors the actual Schwab response for non-optionable stocks like NVR:
        // status SUCCESS, underlyingPrice 0.0, empty call/put maps.
        String symbol = "NVR";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 0.0);

        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getSymbol(), symbol);
        assertTrue(result.isNoOptions());
    }

    @Test
    public void testCollectIVDataPoint_NoExpiryInRange() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);
        // Add an option far outside the target DTE range so no valid expiry is found,
        // but the symbol does have options.
        StrategyTestUtils.addOption(mockChain, "2027-01-02", 365, 150.0, 4.90, 5.00, 0.50, true);

        when(thinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNull(result);
    }
}
