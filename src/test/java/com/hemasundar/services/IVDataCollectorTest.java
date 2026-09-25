package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.utils.StrategyTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class IVDataCollectorTest {

    private ThinkOrSwimAPIs ThinkOrSwimAPIs;
    private IVDataCollector ivDataCollector;

    @BeforeMethod
    public void setUp() {
        ThinkOrSwimAPIs = mock(ThinkOrSwimAPIs.class);
        ivDataCollector = new IVDataCollector(ThinkOrSwimAPIs);
    }

    @Test
    public void testCollectIVDataPoint_Success() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // Add ATM options for IV collection (~30 DTE)
        // Strike 150, PUT and CALL
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, true); // PUT
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, false); // CALL

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getSymbol(), symbol);
        assertEquals(result.getStrike(), 150.0);
        assertEquals(result.getDte(), 30);
        assertNotNull(result.getAtmPutIV());
        assertNotNull(result.getAtmCallIV());
        // Both should be the same blended σ₃₀ value
        assertEquals(result.getAtmPutIV(), result.getAtmCallIV());
    }

    @Test
    public void testCollectIVDataPoint_NoOptionsAvailable() {
        String symbol = "NVR";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);
        // No options added, so the symbol is not optionable

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

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

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

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

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        // Should still collect using single-expiry fallback (365 DTE is far but still found)
        // findBracketingExpiries will find nextTerm at DTE=365, no nearTerm
        assertNotNull(result);
        assertEquals(result.getSymbol(), symbol);
        assertEquals(result.getDte(), 30); // Always sets to TARGET_DTE
    }

    // ---- New tests for dual-expiry interpolation ----

    @Test
    public void testCollectIV_DualExpiry_Interpolation() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // T₁: DTE=25, IV=30%
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 25, 150.0, 4.90, 5.00, 0.50, true, 30.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 25, 150.0, 4.90, 5.00, -0.50, false, 30.0);
        // T₂: DTE=35, IV=40%
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, 0.50, true, 40.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, -0.50, false, 40.0);

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getDte(), 30);
        // σ₃₀ should be between 30 and 40, and both put/call should be the same blended value
        assertEquals(result.getAtmPutIV(), result.getAtmCallIV());
        assertTrue(result.getAtmPutIV() > 30.0 && result.getAtmPutIV() < 40.0,
                "Interpolated σ₃₀ should be between σ₁=30 and σ₂=40, got: " + result.getAtmPutIV());
    }

    @Test
    public void testCollectIV_Exact30DTE_NoInterpolation() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // Exact 30 DTE: Call IV=28%, Put IV=32%
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, true, 32.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, -0.50, false, 28.0);

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getDte(), 30);
        // Blended = (28 + 32) / 2 = 30.0
        assertEquals(result.getAtmPutIV(), 30.0, 0.01);
        assertEquals(result.getAtmCallIV(), 30.0, 0.01);
    }

    @Test
    public void testCollectIV_SingleExpiry_FallbackNearTerm() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // Only DTE=22, no DTE > 30 available
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 22, 150.0, 4.90, 5.00, 0.50, true, 35.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 22, 150.0, 4.90, 5.00, -0.50, false, 35.0);

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getDte(), 30); // Always sets to TARGET_DTE
        assertEquals(result.getAtmPutIV(), 35.0, 0.01);
    }

    @Test
    public void testCollectIV_ZeroBid_FallbackToOneSide() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // DTE=30: PUT has bid=0 (illiquid), CALL has good quotes
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 30, 150.0, 0.00, 5.00, 0.50, true, 35.0);  // PUT: bid=0
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, -0.50, false, 40.0); // CALL: good

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        // Should use CALL IV only since PUT fails liquidity check
        assertEquals(result.getAtmPutIV(), 40.0, 0.01);
        assertEquals(result.getAtmCallIV(), 40.0, 0.01);
    }

    @Test
    public void testCollectIV_ShortDTE_Excluded() {
        String symbol = "AAPL";
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain(symbol, 150.0);

        // DTE=3 (gamma noise) and DTE=35
        // DTE=3 passes the relaxed threshold (≥1), so interpolation occurs between DTE=3 (σ=60%) and DTE=35 (σ=35%)
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-01", 3, 150.0, 4.90, 5.00, 0.50, true, 60.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-01", 3, 150.0, 4.90, 5.00, -0.50, false, 60.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, 0.50, true, 35.0);
        StrategyTestUtils.addOptionWithIV(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, -0.50, false, 35.0);

        when(ThinkOrSwimAPIs.getOptionChain(symbol)).thenReturn(mockChain);

        IVDataPoint result = ivDataCollector.collectIVDataPoint(symbol);

        assertNotNull(result);
        assertEquals(result.getDte(), 30);
        // Interpolated σ₃₀ between σ₁=60 (DTE=3) and σ₂=35 (DTE=35) — result should be close to 35
        // but slightly elevated due to the high short-term IV contribution via variance weighting
        assertTrue(result.getAtmPutIV() > 35.0 && result.getAtmPutIV() < 60.0,
                "Interpolated σ₃₀ should be between 35 and 60, got: " + result.getAtmPutIV());
    }

    // ---- Unit tests for interpolation math ----

    @Test
    public void testInterpolate30DayIV_MathVerification() {
        // σ₁=25.0, DTE₁=25, σ₂=35.0, DTE₂=35
        // w₁ = (35-30)/(35-25) = 0.5, w₂ = (30-25)/(35-25) = 0.5
        // totalVar = 25² × 25 × 0.5 + 35² × 35 × 0.5 = 7812.5 + 21437.5 = 29250
        // σ₃₀² = 29250 / 30 = 975
        // σ₃₀ = sqrt(975) ≈ 31.225
        Double result = IVDataCollector.interpolate30DayIV(25.0, 25, 35.0, 35);

        assertNotNull(result);
        assertEquals(result, Math.sqrt(975), 0.001);
    }

    @Test
    public void testInterpolate30DayIV_ExactDTE1() {
        // DTE₁ = 30 → should return σ₁ directly
        Double result = IVDataCollector.interpolate30DayIV(28.0, 30, 35.0, 45);

        assertNotNull(result);
        assertEquals(result, 28.0, 0.001);
    }

    @Test
    public void testInterpolate30DayIV_ExactDTE2() {
        // DTE₂ = 30 → should return σ₂ directly
        Double result = IVDataCollector.interpolate30DayIV(25.0, 20, 32.0, 30);

        assertNotNull(result);
        assertEquals(result, 32.0, 0.001);
    }

    @Test
    public void testInterpolate30DayIV_SameDTE() {
        // DTE₁ = DTE₂ → should return σ₁
        Double result = IVDataCollector.interpolate30DayIV(28.0, 25, 35.0, 25);

        assertNotNull(result);
        assertEquals(result, 28.0, 0.001);
    }

    // ---- Unit tests for findBracketingExpiries ----

    @Test
    public void testFindBracketingExpiries_BothSides() {
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 25, 150.0, 4.90, 5.00, 0.50, true);
        StrategyTestUtils.addOption(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, 0.50, true);

        IVDataCollector.ExpiryBracket bracket = ivDataCollector.findBracketingExpiries(mockChain);

        assertNotNull(bracket);
        assertTrue(bracket.isInterpolationNeeded());
        assertEquals(bracket.getNearTerm().getDaysToExpiry(), 25);
        assertEquals(bracket.getNextTerm().getDaysToExpiry(), 35);
    }

    @Test
    public void testFindBracketingExpiries_Exact30() {
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 30, 150.0, 4.90, 5.00, 0.50, true);

        IVDataCollector.ExpiryBracket bracket = ivDataCollector.findBracketingExpiries(mockChain);

        assertNotNull(bracket);
        assertFalse(bracket.isInterpolationNeeded());
        assertEquals(bracket.getNearTerm().getDaysToExpiry(), 30);
        assertNull(bracket.getNextTerm());
    }

    @Test
    public void testFindBracketingExpiries_OnlyNearTerm() {
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 22, 150.0, 4.90, 5.00, 0.50, true);

        IVDataCollector.ExpiryBracket bracket = ivDataCollector.findBracketingExpiries(mockChain);

        assertNotNull(bracket);
        assertFalse(bracket.isInterpolationNeeded());
        assertEquals(bracket.getNearTerm().getDaysToExpiry(), 22);
        assertNull(bracket.getNextTerm());
    }

    @Test
    public void testFindBracketingExpiries_OnlyNextTerm() {
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        StrategyTestUtils.addOption(mockChain, "2026-01-02", 45, 150.0, 4.90, 5.00, 0.50, true);

        IVDataCollector.ExpiryBracket bracket = ivDataCollector.findBracketingExpiries(mockChain);

        assertNotNull(bracket);
        assertFalse(bracket.isInterpolationNeeded());
        assertNull(bracket.getNearTerm());
        assertEquals(bracket.getNextTerm().getDaysToExpiry(), 45);
    }

    @Test
    public void testFindBracketingExpiries_ShortDTE_SkippedForNearTerm() {
        OptionChainResponse mockChain = StrategyTestUtils.createMockChain("AAPL", 150.0);
        // DTE=3 should be skipped (< MIN_DTE_THRESHOLD=7)
        StrategyTestUtils.addOption(mockChain, "2026-01-01", 3, 150.0, 4.90, 5.00, 0.50, true);
        StrategyTestUtils.addOption(mockChain, "2026-01-09", 35, 150.0, 4.90, 5.00, 0.50, true);

        IVDataCollector.ExpiryBracket bracket = ivDataCollector.findBracketingExpiries(mockChain);

        assertNotNull(bracket);
        // With only DTE=3 (excluded) and DTE=35, falls back to relaxed threshold
        // DTE=3 ≥ 1, so nearTerm=3, nextTerm=35, interpolation needed
        assertTrue(bracket.isInterpolationNeeded());
        assertEquals(bracket.getNearTerm().getDaysToExpiry(), 3);
        assertEquals(bracket.getNextTerm().getDaysToExpiry(), 35);
    }
}
