package com.hemasundar.unit.technical;

import com.hemasundar.technical.VolumeFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class VolumeFilterTest {

    @Test
    public void testVolumeDefaults() {
        VolumeFilter filter = VolumeFilter.builder().build();
        assertEquals(filter.getMinVolume(), 100_000L);
    }

    @Test
    public void testEmptySeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        VolumeFilter filter = VolumeFilter.builder().build();
        assertEquals(filter.getCurrentVolume(series), 0L);
        assertFalse(filter.evaluate(series));
    }

    @Test
    public void testVolumeThreshold() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();
        series.addBar(now, 100, 100, 100, 100, 50_000);

        VolumeFilter filter = VolumeFilter.builder().minVolume(100_000L).build();
        assertEquals(filter.getCurrentVolume(series), 50_000L);
        assertFalse(filter.isVolumeAboveThreshold(series));
        assertFalse(filter.evaluate(series));

        // Add bar with higher volume
        series.addBar(now.plusMinutes(1), 100, 100, 100, 100, 150_000);
        assertEquals(filter.getCurrentVolume(series), 150_000L);
        assertTrue(filter.isVolumeAboveThreshold(series));
        assertTrue(filter.evaluate(series));
    }

    @Test
    public void testGetFilterName() {
        VolumeFilter filter = VolumeFilter.builder().minVolume(500_000L).build();
        String name = filter.getFilterName();
        assertTrue(name.contains("Volume"));
        assertTrue(name.contains("500,000"));
    }
}
