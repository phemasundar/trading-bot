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

    @Test
    public void testGettersAndBuilderAllFields() {
        RSIFilter rsi = RSIFilter.builder().period(14).build();
        BollingerBandsFilter bb = BollingerBandsFilter.builder().period(20).build();
        VolumeFilter vol = VolumeFilter.builder().minVolume(1000000L).build();
        MovingAverageFilter ma20 = MovingAverageFilter.builder().period(20).build();
        MovingAverageFilter ma50 = MovingAverageFilter.builder().period(50).build();

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(rsi)
                .bollingerFilter(bb)
                .volumeFilter(vol)
                .ma20Filter(ma20)
                .ma50Filter(ma50)
                .build();

        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .minVolume(500000L)
                .build();

        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);

        Assert.assertEquals(chain.getRsiFilter(), rsi);
        Assert.assertEquals(chain.getBollingerFilter(), bb);
        Assert.assertEquals(chain.getVolumeFilter(), vol);
        Assert.assertEquals(chain.getMa20Filter(), ma20);
        Assert.assertEquals(chain.getMa50Filter(), ma50);
        Assert.assertEquals(chain.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(chain.getBollingerCondition(), BollingerCondition.LOWER_BAND);
        Assert.assertEquals(chain.getMinVolume(), 500000L);
    }

    @Test
    public void testBuilderWithOtherFields() {
        RSIFilter rsi = RSIFilter.builder().build();
        BollingerBandsFilter bb = BollingerBandsFilter.builder().build();
        VolumeFilter vol = VolumeFilter.builder().build();

        TechnicalFilterChain chain = TechnicalFilterChain.builder()
                .withRSI(rsi)
                .withBollingerBands(bb)
                .withVolume(vol)
                .rsiCondition(RSICondition.OVERSOLD)
                .bollingerCondition(BollingerCondition.LOWER_BAND)
                .build();

        Assert.assertEquals(chain.getRsiFilter(), rsi);
        Assert.assertEquals(chain.getBollingerFilter(), bb);
        Assert.assertEquals(chain.getVolumeFilter(), vol);
        Assert.assertEquals(chain.getRsiCondition(), RSICondition.OVERSOLD);
        Assert.assertEquals(chain.getBollingerCondition(), BollingerCondition.LOWER_BAND);
    }

    @Test
    public void testNullConditions() {
        TechnicalIndicators indicators = TechnicalIndicators.builder().build();
        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, null);

        Assert.assertNull(chain.getRsiCondition());
        Assert.assertNull(chain.getBollingerCondition());
        Assert.assertNull(chain.getMinVolume());
    }
}
