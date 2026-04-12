package com.hemasundar.config;

import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.options.models.OptionsConfig;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.options.strategies.PutCreditSpreadStrategy;
import com.hemasundar.options.strategies.CallCreditSpreadStrategy;
import org.testng.annotations.BeforeMethod;

public class StrategiesConfigLoaderTest {
    private StrategiesConfigLoader configLoader;
    
    @Mock
    private PutCreditSpreadStrategy putStrategy;
    
    @Mock
    private CallCreditSpreadStrategy callStrategy;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(putStrategy.getStrategyType()).thenReturn(StrategyType.PUT_CREDIT_SPREAD);
        when(callStrategy.getStrategyType()).thenReturn(StrategyType.CALL_CREDIT_SPREAD);
        
        configLoader = new StrategiesConfigLoader(Arrays.asList(putStrategy, callStrategy));
        configLoader.init(); // Initialize strategyMap
    }

    @Test
    public void testLoad_Success() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("portfolio", List.of("AAPL", "MSFT"));

        List<OptionsConfig> configs = configLoader.load("test-strategies-config.json", securitiesMap);

        assertNotNull(configs);
        assertEquals(configs.get(0).getAlias(), "Bullish Puts");
        assertTrue(configs.get(0).getSecurities().contains("AAPL"));
    }

    @Test
    public void testLoad_NotFound() {
        List<OptionsConfig> configs = configLoader.load("non-existent.json", new HashMap<>());
        assertTrue(configs.isEmpty());
    }

    @Test
    public void testLoad_MalformedJson() {
        List<OptionsConfig> configs = configLoader.load("malformed-strategies-config.json", new HashMap<>());
        assertNotNull(configs);
    }

    @Test
    public void testLoadScreeners_Success() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("top100.yaml", List.of("GOOG", "TSLA"));
        
        List<com.hemasundar.technical.ScreenerConfig> screeners = configLoader
                .loadScreeners("test-strategies-config.json", securitiesMap);
        
        assertNotNull(screeners);
        assertEquals(screeners.get(0).getAlias(), "Bullish Screener");
        assertEquals(screeners.get(0).getSecurities().size(), 2);
    }
}
