package com.hemasundar.unit.services;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.unit.StrategyTestUtils;
import org.mockito.MockedStatic;
import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class IVDataCollectorTest {

    @Test
    public void testCollectIVDataPoint_Success() {
        try (MockedStatic<ThinkOrSwinAPIs> mockedApis = mockStatic(ThinkOrSwinAPIs.class)) {
            String symbol = "AAPL";
            OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

            // Add ATM options for IV collection (~30 DTE)
            // Strike 150, PUT and CALL
            StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, true); // PUT
            StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, false); // CALL

            mockedApis.when(() -> ThinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

            IVDataPoint result = IVDataCollector.collectIVDataPoint(symbol);

            assertNotNull(result);
            assertEquals(result.getSymbol(), symbol);
            assertEquals(result.getStrike(), 150.0);
            assertEquals(result.getDte(), 30);
            assertNotNull(result.getAtmPutIV());
            assertNotNull(result.getAtmCallIV());
        }
    }

    @Test
    public void testCollectIVDataPoint_NoExpiryInRange() {
        try (MockedStatic<ThinkOrSwinAPIs> mockedApis = mockStatic(ThinkOrSwinAPIs.class)) {
            String symbol = "AAPL";
            OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);
            // No options added, so no expiry in range

            mockedApis.when(() -> ThinkOrSwinAPIs.getOptionChain(symbol)).thenReturn(mockChain);

            IVDataPoint result = IVDataCollector.collectIVDataPoint(symbol);

            assertNull(result);
        }
    }
}
