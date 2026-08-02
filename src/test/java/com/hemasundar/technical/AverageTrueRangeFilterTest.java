package com.hemasundar.technical;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class AverageTrueRangeFilterTest {

    @Test
    public void testATRProperties() {
        AverageTrueRangeFilter atr14 = AverageTrueRangeFilter.builder().period(14).build();
        assertEquals(atr14.getPeriod(), 14);
        assertEquals(atr14.getFilterName(), "ATR(14)");
    }

    @Test
    public void testCalculateATR() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add bars with high/low range
        for (int i = 0; i < 5; i++) {
            series.addBar(now.plusDays(i), 100, 110, 90, 105, 1000);
        }

        AverageTrueRangeFilter atr3 = AverageTrueRangeFilter.builder().period(3).build();

        double currentATR = atr3.getCurrentATR(series);
        assertTrue(currentATR > 0);
        assertTrue(atr3.evaluate(series));
    }
}
