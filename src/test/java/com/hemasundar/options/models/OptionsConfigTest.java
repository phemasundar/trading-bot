package com.hemasundar.options.models;

import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.options.strategies.StrategyType;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class OptionsConfigTest {

    @Test
    public void testGetStrategyIdExcludesSecuritiesFile() {
        AbstractTradingStrategy strategy = Mockito.mock(AbstractTradingStrategy.class);
        when(strategy.getStrategyType()).thenReturn(StrategyType.PUT_CREDIT_SPREAD);

        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .securitiesFile("portfolio.txt")
                .targetDTE(30)
                .build();

        OptionsConfig config = OptionsConfig.builder()
                .strategy(strategy)
                .alias("PCS")
                .termType("Short Term")
                .filter(filter)
                .build();

        // Strategy ID must be Alias + TermType without securitiesFile
        assertEquals(config.getStrategyId(), "PCS - Short Term");
        assertEquals(config.getName(), "PCS");
    }

    @Test
    public void testGetStrategyIdWithoutTermType() {
        AbstractTradingStrategy strategy = Mockito.mock(AbstractTradingStrategy.class);
        when(strategy.getStrategyType()).thenReturn(StrategyType.IRON_CONDOR);

        OptionsConfig config = OptionsConfig.builder()
                .strategy(strategy)
                .alias("Iron Condor")
                .filter(OptionsStrategyFilter.builder().securitiesFile("sp500.txt").build())
                .build();

        assertEquals(config.getStrategyId(), "Iron Condor");
    }

    @Test
    public void testGetStrategyIdFallbackToStrategyType() {
        AbstractTradingStrategy strategy = Mockito.mock(AbstractTradingStrategy.class);
        when(strategy.getStrategyType()).thenReturn(StrategyType.LONG_CALL_LEAP);

        OptionsConfig config = OptionsConfig.builder()
                .strategy(strategy)
                .termType("Extra Long Term")
                .build();

        assertEquals(config.getStrategyId(), StrategyType.LONG_CALL_LEAP.toString() + " - Extra Long Term");
    }
}
