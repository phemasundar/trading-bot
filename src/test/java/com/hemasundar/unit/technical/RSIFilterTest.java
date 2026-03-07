package com.hemasundar.unit.technical;

import com.hemasundar.technical.RSIFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.testng.annotations.Test;

import java.time.ZonedDateTime;

import static org.testng.Assert.*;

public class RSIFilterTest {

    @Test
    public void testRSIDefaults() {
        RSIFilter filter = RSIFilter.builder().build();
        assertEquals(filter.getPeriod(), 14);
        assertEquals(filter.getOversoldThreshold(), 30.0);
        assertEquals(filter.getOverboughtThreshold(), 70.0);
    }

    @Test
    public void testRSIIndicators() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add 20 bars of increasing prices to get RSI > 70
        for (int i = 0; i < 20; i++) {
            series.addBar(now.plusDays(i), 100 + i, 101 + i, 99 + i, 100.5 + i, 1000);
        }

        RSIFilter filter = RSIFilter.builder().period(14).build();

        double currentRsi = filter.getCurrentRSI(series);
        assertTrue(currentRsi > 70.0,
                "RSI should be overbought with constantly increasing prices. Actual: " + currentRsi);
        assertTrue(filter.isOverbought(series));
        assertFalse(filter.isOversold(series));
        assertTrue(filter.evaluate(series)); // evaluate returns true if either overbought or oversold
    }

    @Test
    public void testRSIOversold() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Add 20 bars of decreasing prices to get RSI < 30
        for (int i = 0; i < 20; i++) {
            series.addBar(now.plusDays(i), 100 - i, 101 - i, 99 - i, 99.5 - i, 1000);
        }

        RSIFilter filter = RSIFilter.builder().period(14).build();

        double currentRsi = filter.getCurrentRSI(series);
        assertTrue(currentRsi < 30.0,
                "RSI should be oversold with constantly decreasing prices. Actual: " + currentRsi);
        assertTrue(filter.isOversold(series));
        assertFalse(filter.isOverbought(series));
    }

    @Test
    public void testRSIPreviousValue() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        for (int i = 0; i < 5; i++) {
            double price = (i == 4) ? 95.0 : 100.0 + i; // Drop on the last bar
            series.addBar(now.plusDays(i), price, price + 1, price - 1, price, 1000);
        }

        RSIFilter filter = RSIFilter.builder().build();
        double currentRsi = filter.getCurrentRSI(series);
        double previousRsi = filter.getPreviousRSI(series);

        assertNotEquals(currentRsi, previousRsi);
    }

    @Test
    public void testRSIPreviousValueSingleBar() {
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        series.addBar(ZonedDateTime.now(), 100, 101, 99, 100, 1000);

        RSIFilter filter = RSIFilter.builder().build();
        // Should return current RSI if only 1 bar exists (index 0)
        assertEquals(filter.getPreviousRSI(series), filter.getCurrentRSI(series));
    }

    @Test
    public void testBullishCrossover() {
        // Bullish crossover: previous < 50, current >= 50
        BarSeries series = new BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();

        // Force a sequence that crosses 50
        // (This is hard with real math, so we just check the logic if we can)
        // Simplified: use a very small period to make it volatile
        RSIFilter customFilter = RSIFilter.builder().period(2).oversoldThreshold(50.0).build();

        series.addBar(now.plusDays(1), 100, 105, 95, 90, 1000); // Down
        series.addBar(now.plusDays(2), 90, 95, 85, 80, 1000); // Down
        series.addBar(now.plusDays(3), 80, 110, 80, 105, 1000); // Sharp Up

        // We don't necessarily care about the exact RSI value in a unit test of the
        // WRAPPER
        // as much as ensuring it calls the underlying indicators correctly.
        // But since we can't easily mock the ta4j indicators inside the filter, we rely
        // on the math.

        double prev = customFilter.getPreviousRSI(series);
        double curr = customFilter.getCurrentRSI(series);

        boolean expected = (prev < 50.0 && curr >= 50.0);
        assertEquals(customFilter.isBullishCrossover(series), expected);
    }

    @Test
    public void testGetFilterName() {
        RSIFilter filter = RSIFilter.builder().period(14).oversoldThreshold(30).overboughtThreshold(70).build();
        String name = filter.getFilterName();
        assertTrue(name.contains("RSI(14)"));
        assertTrue(name.contains("30.0"));
        assertTrue(name.contains("70.0"));
    }
}
