package com.hemasundar.technical;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class DollarVolumeFilterTest {

    @Test
    public void testDollarVolumeDefaults() {
        DollarVolumeFilter filter = DollarVolumeFilter.builder().build();
        assertEquals(filter.getMinDollarVolume(), 5_000_000.0);
    }

    @Test
    public void testEmptySeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        DollarVolumeFilter filter = DollarVolumeFilter.builder().build();
        assertEquals(filter.getCurrentDollarVolume(null), 0.0);
        assertEquals(filter.getCurrentDollarVolume(series), 0.0);
        assertFalse(filter.evaluate(series));
    }

    @Test
    public void testDollarVolumeThreshold() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();
        // Price 50, Volume 50,000 => Dollar Volume = 2,500,000 (< 5,000,000)
        series.addBar(now, 50, 50, 50, 50, 50_000);

        DollarVolumeFilter filter = DollarVolumeFilter.builder().minDollarVolume(5_000_000.0).build();
        assertEquals(filter.getCurrentDollarVolume(series), 2_500_000.0);
        assertFalse(filter.isDollarVolumeAboveThreshold(series));
        assertFalse(filter.evaluate(series));

        // Add bar: Price 100, Volume 60,000 => Dollar Volume = 6,000,000 (>= 5,000,000)
        series.addBar(now.plusMinutes(1), 100, 100, 100, 100, 60_000);
        assertEquals(filter.getCurrentDollarVolume(series), 6_000_000.0);
        assertTrue(filter.isDollarVolumeAboveThreshold(series));
        assertTrue(filter.evaluate(series));
    }

    @Test
    public void testGetFilterName() {
        DollarVolumeFilter filter = DollarVolumeFilter.builder().minDollarVolume(50_000_000.0).build();
        String name = filter.getFilterName();
        assertTrue(name.contains("Dollar Volume"));
        assertTrue(name.contains("50,000,000"));
    }
}
