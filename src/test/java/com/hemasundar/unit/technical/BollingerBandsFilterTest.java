package com.hemasundar.unit.technical;

import com.hemasundar.technical.BollingerBandsFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class BollingerBandsFilterTest {

    @Test
    public void testBollingerDefaults() {
        BollingerBandsFilter filter = BollingerBandsFilter.builder().build();
        assertEquals(filter.getPeriod(), 20);
        assertEquals(filter.getStandardDeviations(), 2.0);
    }

    @Test
    public void testBollingerBandsFlatPrice() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add 30 bars of constant price 100
        for (int i = 0; i < 30; i++) {
            series.addBar(now.plusDays(i), 100, 100, 100, 100, 1000);
        }

        BollingerBandsFilter filter = BollingerBandsFilter.builder().period(20).standardDeviations(2.0).build();

        // With flat price, std dev is 0, so all bands are at 100
        assertEquals(filter.getMiddleBand(series), 100.0, 0.001);
        assertEquals(filter.getUpperBand(series), 100.0, 0.001);
        assertEquals(filter.getLowerBand(series), 100.0, 0.001);

        // Touching should be true at 100
        assertTrue(filter.isPriceTouchingUpperBand(series));
        assertTrue(filter.isPriceTouchingLowerBand(series));
        assertTrue(filter.evaluate(series));
    }

    @Test
    public void testBollingerBandsVolatilePrice() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // 20 bars of 100
        for (int i = 0; i < 20; i++) {
            series.addBar(now.plusDays(i), 100, 105, 95, 100, 1000);
        }

        // Add one bar far above
        series.addBar(now.plusDays(21), 150, 160, 140, 150, 1000);

        BollingerBandsFilter filter = BollingerBandsFilter.builder().period(20).build();

        assertTrue(filter.getUpperBand(series) > 100.0);
        assertTrue(filter.getLowerBand(series) < 100.0);
        assertTrue(filter.isPriceTouchingUpperBand(series));
        assertFalse(filter.isPriceTouchingLowerBand(series));
    }

    @Test
    public void testGetFilterName() {
        BollingerBandsFilter filter = BollingerBandsFilter.builder().period(20).standardDeviations(2.5).build();
        String name = filter.getFilterName();
        assertTrue(name.contains("Bollinger Bands(20, 2.5 SD)"));
    }
}
