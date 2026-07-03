package com.hemasundar.config;

import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

public class StrategiesConfigTest {

    @Test
    public void testStrategiesConfigPOJO() {
        StrategiesConfig config = new StrategiesConfig();
        
        List<StrategiesConfig.StrategyEntry> strategies = new ArrayList<>();
        StrategiesConfig.StrategyEntry strategy = new StrategiesConfig.StrategyEntry();
        strategy.setAlias("Test Strategy");
        strategy.setEnabled(true);
        strategies.add(strategy);
        
        config.setOptionsStrategies(strategies);
        
        List<StrategiesConfig.ScreenerEntry> screeners = new ArrayList<>();
        StrategiesConfig.ScreenerEntry screener = new StrategiesConfig.ScreenerEntry();
        screener.setAlias("Test Screener");
        screener.setEnabled(true);
        screener.setScreenerType(com.hemasundar.technical.ScreenerType.RSI_BB_BULLISH_CROSSOVER);
        screeners.add(screener);
        
        config.setTechnicalScreeners(screeners);
        
        assertEquals(config.getOptionsStrategies().size(), 1);
        assertEquals(config.getTechnicalScreeners().size(), 1);
        assertEquals(config.getOptionsStrategies().get(0).getAlias(), "Test Strategy");
        assertEquals(config.getTechnicalScreeners().get(0).getAlias(), "Test Screener");
        
        // Test getEnabledScreeners
        List<StrategiesConfig.ScreenerEntry> enabledScreeners = config.getEnabledScreeners();
        assertEquals(enabledScreeners.size(), 1);
        assertEquals(enabledScreeners.get(0).getScreenerType(), com.hemasundar.technical.ScreenerType.RSI_BB_BULLISH_CROSSOVER);
    }

    @Test
    public void testScreenerEntryAndTechnicalFilters() {
        StrategiesConfig.ScreenerEntry entry = new StrategiesConfig.ScreenerEntry();
        entry.setAlias("Alias");
        entry.setScreenerType(com.hemasundar.technical.ScreenerType.RSI_OVERSOLD);
        entry.setEnabled(true);
        entry.setSecurities("AAPL");

        // technicalFilters is now a Map<String, Object> matching the JSON structure
        java.util.Map<String, Object> filters = new java.util.HashMap<>();
        java.util.Map<String, Object> rsiEntry = new java.util.HashMap<>();
        rsiEntry.put("config", "default");
        rsiEntry.put("condition", "OVERSOLD");
        filters.put("RSI", rsiEntry);
        entry.setTechnicalFilters(filters);

        assertEquals(entry.getAlias(), "Alias");
        assertNotNull(entry.getTechnicalFilters());
        assertTrue(entry.getTechnicalFilters().containsKey("RSI"));
    }

    @Test
    public void testStrategyEntryHasTechnicalFilters() {
        StrategiesConfig.StrategyEntry strategy = new StrategiesConfig.StrategyEntry();
        assertFalse(strategy.hasTechnicalFilter(), "No technicalFilters set yet");

        strategy.setTechnicalFilters("oversold");
        assertTrue(strategy.hasTechnicalFilter(), "String preset reference should register as having a filter");

        strategy.setTechnicalFilters(new java.util.HashMap<>());
        assertTrue(strategy.hasTechnicalFilter(), "Empty map still registers as having a filter");
    }

    @Test
    public void testRSIConfigParamsDefaults() {
        StrategiesConfig.RSIConfigParams cfg = new StrategiesConfig.RSIConfigParams();
        assertEquals(cfg.getPeriod(), 14);
        assertEquals(cfg.getOversoldThreshold(), 30.0);
        assertEquals(cfg.getOverboughtThreshold(), 70.0);
    }

    @Test
    public void testBollingerConfigParamsDefaults() {
        StrategiesConfig.BollingerConfigParams cfg = new StrategiesConfig.BollingerConfigParams();
        assertEquals(cfg.getBollingerPeriod(), 20);
        assertEquals(cfg.getBollingerStdDev(), 2.0);
    }

    @Test
    public void testTechnicalIndicatorConfigsAsTypedMap() {
        StrategiesConfig config = new StrategiesConfig();
        java.util.Map<String, Object> namedConfigs = new java.util.HashMap<>();
        
        // Build the "default" object wrapping both configs
        java.util.Map<String, Object> defaultObject = new java.util.HashMap<>();
        
        java.util.Map<String, Object> rsiParams = new java.util.HashMap<>();
        rsiParams.put("period", 14);
        rsiParams.put("oversoldThreshold", 30.0);
        defaultObject.put("RSI", rsiParams);
        
        java.util.Map<String, Object> bbParams = new java.util.HashMap<>();
        bbParams.put("bollingerPeriod", 20);
        bbParams.put("bollingerStdDev", 2.0);
        defaultObject.put("BOLLINGER_BAND", bbParams);
        
        namedConfigs.put("default", defaultObject);
        
        config.setTechnicalIndicatorConfigs(namedConfigs);
        assertEquals(config.getTechnicalIndicatorConfigs().size(), 1);
        assertTrue(config.getTechnicalIndicatorConfigs().containsKey("default"));
    }
}
