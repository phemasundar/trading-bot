package com.hemasundar.technical;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class ExponentialMovingAverageFilterTest {

    @Test
    public void testEMAProperties() {
        ExponentialMovingAverageFilter ema21 = ExponentialMovingAverageFilter.builder().period(21).build();
        assertEquals(ema21.getPeriod(), 21);
        assertEquals(ema21.getFilterName(), "EMA(21)");
    }

    @Test
    public void testPriceAboveAndBelowEMA() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add 5 bars at 100
        for (int i = 0; i < 5; i++) {
            series.addBar(now.plusDays(i), 100, 100, 100, 100, 1000);
        }

        ExponentialMovingAverageFilter ema3 = ExponentialMovingAverageFilter.builder().period(3).build();

        // Initial EMA
        assertTrue(ema3.getCurrentEMA(series) > 0);
        assertEquals(ema3.getCurrentPrice(series), 100.0);

        // Add a higher price bar
        series.addBar(now.plusDays(6), 120, 120, 120, 120, 1000);
        assertTrue(ema3.isPriceAboveEMA(series));
        assertFalse(ema3.isPriceBelowEMA(series));

        // Add a lower price bar
        series.addBar(now.plusDays(7), 50, 50, 50, 50, 1000);
        assertTrue(ema3.isPriceBelowEMA(series));
        assertFalse(ema3.isPriceAboveEMA(series));

        // Default evaluate calls isPriceBelowEMA
        assertTrue(ema3.evaluate(series));
    }
}
