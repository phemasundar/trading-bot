package com.hemasundar.technical;

import com.hemasundar.utils.JavaUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class SecuritiesFilterConfigTest {

    @Test
    public void testYamlParsing() {
        String yaml = """
                filters:
                  rsi:
                    enabled: true
                    period: 14
                    oversold: 30.0
                    overbought: 70.0
                  bollinger:
                    enabled: true
                    period: 20
                    stdDev: 2.0
                  moving_averages:
                    enabled: true
                    periods: [20, 50, 100, 200]
                  exponential_moving_averages:
                    enabled: true
                    periods: [9, 21, 50]
                  volume:
                    enabled: true
                    sma_periods: [20, 50]
                  volatility:
                    enabled: true
                    atr_period: 14
                    hv_period: 20
                """;

        SecuritiesFilterConfig config = JavaUtils.convertYamlToPojo(yaml, SecuritiesFilterConfig.class);
        assertNotNull(config);
        assertNotNull(config.getFilters());

        SecuritiesFilterConfig.FilterSettings f = config.getFilters();
        assertTrue(f.getRsi().isEnabled());
        assertEquals(f.getRsi().getPeriod(), 14);
        assertEquals(f.getRsi().getOversold(), 30.0);
        assertEquals(f.getRsi().getOverbought(), 70.0);

        assertTrue(f.getBollinger().isEnabled());
        assertEquals(f.getBollinger().getPeriod(), 20);
        assertEquals(f.getBollinger().getStdDev(), 2.0);

        assertTrue(f.getMovingAverages().isEnabled());
        assertEquals(f.getMovingAverages().getPeriods().size(), 4);
        assertEquals(f.getMovingAverages().getPeriods().get(0).intValue(), 20);

        assertTrue(f.getExponentialMovingAverages().isEnabled());
        assertEquals(f.getExponentialMovingAverages().getPeriods().size(), 3);

        assertTrue(f.getVolume().isEnabled());
        assertEquals(f.getVolume().getSmaPeriods().size(), 2);

        assertTrue(f.getVolatility().isEnabled());
        assertEquals(f.getVolatility().getAtrPeriod(), 14);
        assertEquals(f.getVolatility().getHvPeriod(), 20);
    }
}
