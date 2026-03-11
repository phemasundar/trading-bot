package com.hemasundar.config;

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
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("top100.yaml", List.of("GOOG", "TSLA"));
        
        List<com.hemasundar.technical.ScreenerConfig> screeners = StrategiesConfigLoader
                .loadScreeners("test-strategies-config.json", securitiesMap);
        
        assertNotNull(screeners);
        assertFalse(screeners.isEmpty());
        // Verify we loaded the screener from the test JSON
        assertEquals(screeners.get(0).getAlias(), "Bullish Screener");
        assertEquals(screeners.get(0).getSecurities().size(), 2);
    }
}
