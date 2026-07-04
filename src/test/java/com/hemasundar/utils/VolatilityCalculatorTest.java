package com.hemasundar.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class VolatilityCalculatorTest {

    private VolatilityCalculator volatilityCalculator;

    @BeforeMethod
    public void setUp() {
        volatilityCalculator = new VolatilityCalculator();
    }

    @Test
    public void testCalculateHvRank_Success() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        priceHistory.setSymbol("AAPL");
        
        // We need at least 21 candles for a period of 20
        List<PriceHistoryResponse.CandleData> candles = new java.util.ArrayList<>();
        double currentPrice = 100.0;
        for (int i = 0; i < 30; i++) {
            PriceHistoryResponse.CandleData c = new PriceHistoryResponse.CandleData();
            c.setOpen(currentPrice);
            c.setHigh(currentPrice);
            c.setLow(currentPrice);
            c.setClose(currentPrice);
            c.setVolume(100L);
            c.setDatetime((long) i);
            candles.add(c);
            // small variation
            currentPrice *= 1.01;
        }
        priceHistory.setCandles(candles);

        Double result = volatilityCalculator.calculateHvRank(priceHistory, 20);
        assertNotNull(result);
        assertTrue(result >= 0.0 && result <= 100.0, "Rank should be between 0 and 100");
    }

    @Test
    public void testCalculateHvRank_NullHistory() {
        Double result = volatilityCalculator.calculateHvRank(null, 20);
        assertNull(result);
    }

    @Test
    public void testCalculateHvRank_EmptyCandles() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        priceHistory.setCandles(java.util.Arrays.asList());
        Double result = volatilityCalculator.calculateHvRank(priceHistory, 20);
        assertNull(result);
    }

    @Test
    public void testCalculateHvRank_InsufficientData() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        PriceHistoryResponse.CandleData c1 = new PriceHistoryResponse.CandleData();
        c1.setOpen(150.0);
        c1.setHigh(150.0);
        c1.setLow(150.0);
        c1.setClose(150.0);
        c1.setVolume(100L);
        c1.setDatetime(0L);
        
        priceHistory.setCandles(java.util.Arrays.asList(c1));
        // Need at least period + 1 candles
        Double result = volatilityCalculator.calculateHvRank(priceHistory, 20);
        assertNull(result);
    }

    @Test
    public void testCalculateHvRank_InvalidPrice() {
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        
        List<PriceHistoryResponse.CandleData> candles = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            PriceHistoryResponse.CandleData c = new PriceHistoryResponse.CandleData();
            c.setOpen(0.0);
            c.setHigh(0.0);
            c.setLow(0.0);
            c.setClose(0.0);
            c.setVolume(100L);
            c.setDatetime((long) i);
            candles.add(c);
        }
        
        priceHistory.setCandles(candles);
        Double result = volatilityCalculator.calculateHvRank(priceHistory, 20);
        assertNull(result);
    }
}
