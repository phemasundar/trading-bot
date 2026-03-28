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
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("portfolio", List.of("AAPL", "MSFT"));

        List<OptionsConfig> configs = StrategiesConfigLoader.load("test-strategies-config.json", securitiesMap);

        assertNotNull(configs);
        assertEquals(configs.get(0).getAlias(), "Bullish Puts");
        assertTrue(configs.get(0).getSecurities().contains("AAPL"));
    }

    @Test
    public void testLoad_NotFound() {
        List<OptionsConfig> configs = StrategiesConfigLoader.load("non-existent.json", new HashMap<>());
        assertTrue(configs.isEmpty());
    }

    @Test
    public void testLoad_MalformedJson() {
        List<OptionsConfig> configs = StrategiesConfigLoader.load("malformed-strategies-config.json", new HashMap<>());
        // It catches exceptions and returns whatever it could parse, or empty if root fails.
        // In our case, the root parses but strategy entry might fail.
        assertNotNull(configs);
    }

    @Test
    public void testLoadScreeners_Success() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("top100.yaml", List.of("GOOG", "TSLA"));
        
        List<com.hemasundar.technical.ScreenerConfig> screeners = StrategiesConfigLoader
                .loadScreeners("test-strategies-config.json", securitiesMap);
        
        assertNotNull(screeners);
        assertEquals(screeners.get(0).getAlias(), "Bullish Screener");
        assertEquals(screeners.get(0).getSecurities().size(), 2);
    }
}
