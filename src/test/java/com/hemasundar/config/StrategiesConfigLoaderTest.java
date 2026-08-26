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

    @Mock
    private com.hemasundar.utils.WikipediaSecuritiesFetcher wikipediaFetcher;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(putStrategy.getStrategyType()).thenReturn(StrategyType.PUT_CREDIT_SPREAD);
        when(callStrategy.getStrategyType()).thenReturn(StrategyType.CALL_CREDIT_SPREAD);
        
        configLoader = new StrategiesConfigLoader(Arrays.asList(putStrategy, callStrategy), wikipediaFetcher);
        configLoader.init(); // Initialize strategyMap
    }

    @Test
    public void testLoad_Success() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("portfolio", List.of("AAPL", "MSFT"));

        List<OptionsConfig> configs = configLoader.load("test-strategies-config.yml", securitiesMap);

        assertNotNull(configs);
        assertEquals(configs.get(0).getAlias(), "Bullish Puts");
        assertTrue(configs.get(0).getSecurities().contains("AAPL"));
    }

    @Test
    public void testLoad_NotFound() {
        List<OptionsConfig> configs = configLoader.load("non-existent.yml", new HashMap<>());
        assertTrue(configs.isEmpty());
    }

    @Test
    public void testLoad_MalformedJson() {
        List<OptionsConfig> configs = configLoader.load("malformed-strategies-config.yml", new HashMap<>());
        assertNotNull(configs);
    }

    @Test
    public void testLoadScreeners_Success() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("top100.yaml", List.of("GOOG", "TSLA"));
        
        List<com.hemasundar.technical.ScreenerConfig> screeners = configLoader
                .loadScreeners("test-strategies-config.yml", securitiesMap);
        
        assertNotNull(screeners);
        assertEquals(screeners.get(0).getAlias(), "Bullish Screener");
        assertEquals(screeners.get(0).getSecurities().size(), 2);
    }

    @Test
    public void testLoad_GreeksFromStrategyGreeksConfig() {
        Map<String, List<String>> securitiesMap = new HashMap<>();
        securitiesMap.put("portfolio", List.of("AAPL", "MSFT"));

        List<OptionsConfig> configs = configLoader.load("test-strategies-config.yml", securitiesMap);

        assertNotNull(configs);
        OptionsConfig config = configs.get(0);
        assertNotNull(config.getGreeks());
        assertEquals(config.getGreeks().get("delta"), "positive");
        assertEquals(config.getGreeks().get("gamma"), "negative");
        assertEquals(config.getGreeks().get("theta"), "positive");
        assertEquals(config.getGreeks().get("vega"), "negative");

        assertNotNull(config.getFilter());
        assertEquals(config.getFilter().getGreeks(), config.getGreeks());
    }

    @Test
    public void testGetGreeks_AllStrategyTypesConfigured() {
        Map<String, String> pcsGreeks = configLoader.getGreeks(StrategyType.PUT_CREDIT_SPREAD);
        assertNotNull(pcsGreeks);
        assertEquals(pcsGreeks.get("delta"), "positive");
        assertEquals(pcsGreeks.get("gamma"), "negative");
        assertEquals(pcsGreeks.get("theta"), "positive");
        assertEquals(pcsGreeks.get("vega"), "negative");

        Map<String, String> ccsGreeks = configLoader.getGreeks(StrategyType.CALL_CREDIT_SPREAD);
        assertNotNull(ccsGreeks);
        assertEquals(ccsGreeks.get("delta"), "negative");

        Map<String, String> icGreeks = configLoader.getGreeks(StrategyType.IRON_CONDOR);
        assertNotNull(icGreeks);
        assertEquals(icGreeks.get("delta"), "neutral");
    }
}
