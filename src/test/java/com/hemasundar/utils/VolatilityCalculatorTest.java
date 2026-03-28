package com.hemasundar.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class VolatilityCalculatorTest {

    @Test
    public void testCalculateAnnualizedVolatility_Success() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        priceHistory.setSymbol("AAPL");
        
        PriceHistoryResponse.CandleData c1 = new PriceHistoryResponse.CandleData();
        c1.setOpen(150.0);
        c1.setHigh(150.0);
        c1.setLow(150.0);
        c1.setClose(150.0);
        c1.setVolume(100L);
        c1.setDatetime(0L);
        
        PriceHistoryResponse.CandleData c2 = new PriceHistoryResponse.CandleData();
        c2.setOpen(155.0);
        c2.setHigh(155.0);
        c2.setLow(155.0);
        c2.setClose(155.0);
        c2.setVolume(100L);
        c2.setDatetime(0L);
        
        priceHistory.setCandles(java.util.Arrays.asList(c1, c2));

        Double result = VolatilityCalculator.calculateAnnualizedVolatility(priceHistory);
        assertNotNull(result);
        assertTrue(result >= 0, "Volatility should be non-negative");
    }

    @Test
    public void testCalculateAnnualizedVolatility_NullHistory() {
        Double result = VolatilityCalculator.calculateAnnualizedVolatility(null);
        assertNull(result);
    }

    @Test
    public void testCalculateAnnualizedVolatility_EmptyCandles() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        priceHistory.setCandles(java.util.Arrays.asList());
        Double result = VolatilityCalculator.calculateAnnualizedVolatility(priceHistory);
        assertNull(result);
    }

    @Test
    public void testCalculateAnnualizedVolatility_InsufficientData() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        PriceHistoryResponse.CandleData c1 = new PriceHistoryResponse.CandleData();
        c1.setOpen(150.0);
        c1.setHigh(150.0);
        c1.setLow(150.0);
        c1.setClose(150.0);
        c1.setVolume(100L);
        c1.setDatetime(0L);
        
        priceHistory.setCandles(java.util.Arrays.asList(c1));
        Double result = VolatilityCalculator.calculateAnnualizedVolatility(priceHistory);
        assertNull(result);
    }

    @Test
    public void testCalculateAnnualizedVolatility_InvalidPrice() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        
        PriceHistoryResponse.CandleData c1 = new PriceHistoryResponse.CandleData();
        c1.setOpen(150.0);
        c1.setHigh(150.0);
        c1.setLow(150.0);
        c1.setClose(150.0);
        c1.setVolume(100L);
        c1.setDatetime(0L);
        
        PriceHistoryResponse.CandleData c2 = new PriceHistoryResponse.CandleData();
        c2.setOpen(0.0);
        c2.setHigh(0.0);
        c2.setLow(0.0);
        c2.setClose(0.0);
        c2.setVolume(100L);
        c2.setDatetime(0L);
        
        priceHistory.setCandles(java.util.Arrays.asList(c1, c2));
        Double result = VolatilityCalculator.calculateAnnualizedVolatility(priceHistory);
        assertNull(result);
    }
}
