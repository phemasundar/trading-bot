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
    public void testScreenerEntryAndConditions() {
        StrategiesConfig.ScreenerEntry entry = new StrategiesConfig.ScreenerEntry();
        entry.setAlias("Alias");
        entry.setScreenerType(com.hemasundar.technical.ScreenerType.RSI_OVERSOLD);
        entry.setEnabled(true);
        entry.setSecurities("AAPL");
        
        StrategiesConfig.ScreenerConditionsConfig conditions = new StrategiesConfig.ScreenerConditionsConfig();
        conditions.setRsiCondition(com.hemasundar.technical.RSICondition.OVERBOUGHT);
        conditions.setMinVolume(5000L);
        entry.setConditions(conditions);
        
        assertEquals(entry.getAlias(), "Alias");
        assertEquals(entry.getConditions().getRsiCondition(), com.hemasundar.technical.RSICondition.OVERBOUGHT);
        assertEquals(entry.getConditions().getMinVolume(), Long.valueOf(5000L));
    }
}
