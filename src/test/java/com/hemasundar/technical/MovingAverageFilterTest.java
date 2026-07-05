package com.hemasundar.technical;

import com.hemasundar.technical.MovingAverageFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class MovingAverageFilterTest {

    @Test
    public void testMAProperties() {
        MovingAverageFilter ma20 = MovingAverageFilter.builder().period(20).build();
        assertEquals(ma20.getPeriod(), 20);
        assertEquals(ma20.getFilterName(), "SMA(20)");
    }

    @Test
    public void testPriceAboveAndBelowMA() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add 5 bars of 100
        for (int i = 0; i < 5; i++) {
            series.addBar(now.plusDays(i), 100, 100, 100, 100, 1000);
        }

        MovingAverageFilter ma5 = MovingAverageFilter.builder().period(5).build();

        // MA is 100, Price is 100
        assertEquals(ma5.getCurrentSMA(series), 100.0);
        assertEquals(ma5.getCurrentPrice(series), 100.0);
        assertFalse(ma5.isPriceBelowSMA(series));
        assertFalse(ma5.isPriceAboveSMA(series));

        // Add a bar at 110
        series.addBar(now.plusDays(6), 110, 110, 110, 110, 1000);
        // New MA(5) = (100+100+100+100+110)/5 = 102
        assertEquals(ma5.getCurrentSMA(series), 102.0);
        assertEquals(ma5.getCurrentPrice(series), 110.0);
        assertTrue(ma5.isPriceAboveSMA(series));
        assertFalse(ma5.isPriceBelowSMA(series));

        // Add a bar at 80
        series.addBar(now.plusDays(7), 80, 80, 80, 80, 1000);
        // New MA(5) = (100+100+100+110+80)/5 = 98
        assertEquals(ma5.getCurrentSMA(series), 98.0);
        assertEquals(ma5.getCurrentPrice(series), 80.0);
        assertTrue(ma5.isPriceBelowSMA(series));
        assertFalse(ma5.isPriceAboveSMA(series));

        // evaluate returns isPriceBelowSMA
        assertTrue(ma5.evaluate(series));
    }
}
