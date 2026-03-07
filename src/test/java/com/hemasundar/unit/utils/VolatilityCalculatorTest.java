package com.hemasundar.unit.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.VolatilityCalculator;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

public class VolatilityCalculatorTest {

    @Test
    public void testCalculateAnnualizedVolatility_Success() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setSymbol("TEST");
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();

        // Simulating some price movement
        candles.add(createCandle(100.0));
        candles.add(createCandle(101.0));
        candles.add(createCandle(99.0));
        candles.add(createCandle(102.0));
        candles.add(createCandle(98.0));

        response.setCandles(candles);

        Double vol = VolatilityCalculator.calculateAnnualizedVolatility(response);
        assertNotNull(vol);
        assertTrue(vol > 0);
    }

    @Test
    public void testCalculateAnnualizedVolatility_InsufficientData() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        candles.add(createCandle(100.0));
        response.setCandles(candles);

        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(null));
    }

    @Test
    public void testCalculateAnnualizedVolatility_InvalidPrice() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        candles.add(createCandle(100.0));
        candles.add(createCandle(0.0));
        response.setCandles(candles);

        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
    }

    private PriceHistoryResponse.CandleData createCandle(double close) {
        PriceHistoryResponse.CandleData candle = new PriceHistoryResponse.CandleData();
        candle.setClose(close);
        return candle;
    }

    @Test
    public void testCalculateAnnualizedVolatility_NullCandles() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(null);
        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
    }

    @Test
    public void testCalculateAnnualizedVolatility_SingleCandle() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setSymbol("AAPL");
        response.setCandles(List.of(createCandle(150.0)));
        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
    }

    @Test
    public void testCalculateAnnualizedVolatility_InvalidCurrentPrice() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setSymbol("AAPL");
        response.setCandles(List.of(createCandle(150.0), createCandle(-1.0)));
        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
    }

    @Test
    public void testCalculateAnnualizedVolatility_InvalidPreviousPrice() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setSymbol("AAPL");
        response.setCandles(List.of(createCandle(0.0), createCandle(150.0)));
        assertNull(VolatilityCalculator.calculateAnnualizedVolatility(response));
    }

    @Test
    public void testCalculateAnnualizedVolatility_ZeroVolatility() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setSymbol("AAPL");
        response.setCandles(List.of(createCandle(100.0), createCandle(100.0), createCandle(100.0)));

        Double volatility = VolatilityCalculator.calculateAnnualizedVolatility(response);
        assertNotNull(volatility);
        assertEquals(volatility, 0.0, 0.0001);
    }
}
