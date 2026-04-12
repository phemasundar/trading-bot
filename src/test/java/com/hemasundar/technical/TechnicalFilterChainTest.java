package com.hemasundar.technical;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class TechnicalFilterChainTest {

    @Test
    public void testTechnicalFilterChain_Of() {
        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().period(14).build())
                .bollingerFilter(BollingerBandsFilter.builder().period(20).build())
                .build();
        
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .build();
        
        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);
        
        Assert.assertNotNull(chain.getRsiFilter());
        Assert.assertNotNull(chain.getBollingerFilter());
        Assert.assertEquals(chain.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(chain.getFilters().size(), 2);
    }

    @Test
    public void testTechnicalFilterChain_Builder() {
        TechnicalFilterChain chain = TechnicalFilterChain.builder()
                .withRSI(RSIFilter.builder().period(14).build())
                .rsiCondition(RSICondition.OVERBOUGHT)
                .build();
        
        Assert.assertNotNull(chain.getRsiFilter());
        Assert.assertEquals(chain.getRsiCondition(), RSICondition.OVERBOUGHT);
        Assert.assertNull(chain.getBollingerFilter());
    }

    @Test
    public void testGetFilterByType() {
        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().period(14).build())
                .build();
        TechFilterConditions conditions = TechFilterConditions.builder().build();
        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);
        
        RSIFilter filter = chain.getFilter(RSIFilter.class);
        Assert.assertNotNull(filter);
        
        VolumeFilter volumeFilter = chain.getFilter(VolumeFilter.class);
        Assert.assertNull(volumeFilter);
    }

    @Test
    public void testGetFiltersSummary() {
        TechnicalFilterChain chain = TechnicalFilterChain.builder()
                .withRSI(RSIFilter.builder().period(14).build())
                .rsiCondition(RSICondition.OVERSOLD)
                .build();
        
        String summary = chain.getFiltersSummary();
        Assert.assertTrue(summary.contains("RSI"));
        Assert.assertTrue(summary.contains("OVERSOLD"));
    }
}
