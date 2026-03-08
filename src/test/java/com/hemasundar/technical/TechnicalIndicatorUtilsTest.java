package com.hemasundar.technical;

import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.technical.TechnicalIndicatorUtils;
import org.ta4j.core.BarSeries;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class TechnicalIndicatorUtilsTest {

    @Test
    public void testBuildBarSeriesWithNullResponse() {
        BarSeries series = TechnicalIndicatorUtils.buildBarSeries("AAPL", null);
        assertNotNull(series);
        assertEquals(series.getName(), "AAPL");
        assertEquals(series.getBarCount(), 0);
    }

    @Test
    public void testBuildBarSeriesWithEmptyCandles() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(new ArrayList<>());

        BarSeries series = TechnicalIndicatorUtils.buildBarSeries("TSLA", response);
        assertNotNull(series);
        assertEquals(series.getName(), "TSLA");
        assertEquals(series.getBarCount(), 0);
    }

    @Test
    public void testBuildBarSeriesWithValidData() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();

        // Create 3 days of fake data
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000;

        candles.add(createCandle(now - 2 * dayMs, 100.0, 105.0, 95.0, 102.0, 1000));
        candles.add(createCandle(now - dayMs, 102.0, 108.0, 101.0, 107.0, 1500));
        candles.add(createCandle(now, 107.0, 110.0, 106.0, 109.0, 2000));

        response.setCandles(candles);

        BarSeries series = TechnicalIndicatorUtils.buildBarSeries("MSFT", response);

        assertNotNull(series);
        assertEquals(series.getName(), "MSFT");
        assertEquals(series.getBarCount(), 3);

        assertEquals(series.getBar(0).getClosePrice().doubleValue(), 102.0);
        assertEquals(series.getBar(1).getClosePrice().doubleValue(), 107.0);
        assertEquals(series.getBar(2).getClosePrice().doubleValue(), 109.0);

        assertEquals(series.getBar(2).getVolume().longValue(), 2000L);
    }

    private PriceHistoryResponse.CandleData createCandle(long timestamp, double o, double h, double l, double c,
            long v) {
        PriceHistoryResponse.CandleData candle = new PriceHistoryResponse.CandleData();
        candle.setDatetime(timestamp);
        candle.setOpen(o);
        candle.setHigh(h);
        candle.setLow(l);
        candle.setClose(c);
        candle.setVolume(v);
        return candle;
    }
}
