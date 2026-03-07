package com.hemasundar.unit.config;

import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.options.models.OptionsConfig;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;

public class StrategiesConfigLoaderTest {

    @Test
    public void testLoad_Success() {
        // This will load from src/test/resources/strategies-config.json
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("portfolio", List.of("AAPL", "MSFT"));

        List<OptionsConfig> configs = StrategiesConfigLoader.load("test-strategies-config.json", securitiesMap);

        assertNotNull(configs);
        assertFalse(configs.isEmpty());
        // In our test resource, we have 1 strategy
        assertEquals(configs.size(), 1);
        assertEquals(configs.get(0).getAlias(), "Bullish Puts");
        assertTrue(configs.get(0).getSecurities().contains("AAPL"));
    }

    @Test
    public void testLoadScreeners_Success() {
        // The test resource (currently empty for screeners in my previous tool call)
        // will still attempt to load.
        List<com.hemasundar.technical.ScreenerConfig> screeners = StrategiesConfigLoader
                .loadScreeners("test-strategies-config.json");
        assertNotNull(screeners);
    }
}
