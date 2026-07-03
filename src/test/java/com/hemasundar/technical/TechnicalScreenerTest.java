package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.technical.*;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.cache.PriceHistoryCache;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.*;

public class TechnicalScreenerTest {

    private TechnicalScreener technicalScreener;
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    private SchwabApiExecutor schwabApiExecutor;

    @BeforeMethod
    public void setUp() {
        thinkOrSwinAPIs = Mockito.mock(ThinkOrSwinAPIs.class);
        schwabApiExecutor = Mockito.mock(SchwabApiExecutor.class);
        Mockito.when(schwabApiExecutor.executeParallel(Mockito.anyList(), Mockito.any(), Mockito.any())).thenAnswer(inv -> {
            List<String> symbols = inv.getArgument(0);
            java.util.function.Function<String, Object> func = inv.getArgument(1);
            List<Object> res = new ArrayList<>();
            for (String s : symbols) {
                res.add(func.apply(s));
            }
            return res;
        });
        technicalScreener = new TechnicalScreener(thinkOrSwinAPIs, schwabApiExecutor);
        PriceHistoryCache.getInstance().clear();
    }

    @Test
    public void testAnalyzeStockNullHistory() {
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(null);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();
        TechnicalScreener.ScreeningResult result = technicalScreener.analyzeStock("AAPL", indicators);

        assertNull(result);
    }

    @Test
    public void testAnalyzeStockEmptyHistory() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(new ArrayList<>());
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();
        TechnicalScreener.ScreeningResult result = technicalScreener.analyzeStock("TSLA", indicators);

        assertNull(result);
    }

    @Test
    public void testAnalyzeStockSuccess() {
        PriceHistoryResponse response = createMockResponse(100.0, 10);
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .bollingerFilter(BollingerBandsFilter.builder().build())
                .maFilters(new java.util.HashMap<>(java.util.Map.of(
                        20, MovingAverageFilter.builder().period(20).build()
                )))
                .build();

        TechnicalScreener.ScreeningResult result = technicalScreener.analyzeStock("MSFT", indicators);

        assertNotNull(result);
        assertEquals(result.getSymbol(), "MSFT");
        assertTrue(result.getCurrentPrice() > 0);
        assertTrue(result.getRsi() >= 0);
    }

    @Test
    public void testScreenStocksCriteriaMet() {
        PriceHistoryResponse response = createMockResponse(50.0, 30);
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .build();

        TechFilterConditions conditions = TechFilterConditions.builder()
                .minVolume(500L)
                .build();

        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);

        List<TechnicalScreener.ScreeningResult> results = technicalScreener.screenStocks(Arrays.asList("GOOGL"), chain, null);

        assertNotNull(results);
        assertEquals(results.size(), 1, "Should find 1 stock as flat price meets volume 500 requirement");
        assertEquals(results.get(0).getSymbol(), "GOOGL");
    }

    @Test
    public void testScreenStocksCriteriaNotMet() {
        PriceHistoryResponse response = createMockResponse(100.0, 30);
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();

        // Require massive volume
        TechFilterConditions conditions = TechFilterConditions.builder()
                .minVolume(10_000_000L)
                .build();

        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);

        List<TechnicalScreener.ScreeningResult> results = technicalScreener.screenStocks(Arrays.asList("NVDA"), chain, null);

        assertNotNull(results);
        assertEquals(results.size(), 0, "Should find 0 stocks as volume 1000 < 10M");
    }

    @Test
    public void testAnalyzeStock_AllIndicators() {
        // Create 201 candles for MA200
        PriceHistoryResponse response = createMockResponse(100.0, 201);
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .bollingerFilter(BollingerBandsFilter.builder().build())
                .maFilters(new java.util.HashMap<>(java.util.Map.of(
                        20, MovingAverageFilter.builder().period(20).build(),
                        50, MovingAverageFilter.builder().period(50).build(),
                        100, MovingAverageFilter.builder().period(100).build(),
                        200, MovingAverageFilter.builder().period(200).build()
                )))
                .volumeFilter(VolumeFilter.builder().build())
                .build();

        TechnicalScreener.ScreeningResult result = technicalScreener.analyzeStock("TEST", indicators);
        
        assertNotNull(result);
        assertEquals(result.getSymbol(), "TEST");
        assertTrue(result.getRsi() > 0);
        assertTrue(result.getMaValues().get(200) > 0);
    }

    @Test
    public void testMeetsAllCriteria_ConditionCoverage() {
        // We will exercise meetsAllCriteria through evaluate indirectly
        PriceHistoryResponse response = createMockResponse(100.0, 201);
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        // Scenario 1: RSI condition coverage
        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .build();
        
        TechFilterConditions conditions = TechFilterConditions.builder()
                .rsiCondition(RSICondition.OVERSOLD)
                .build();
        
        // This will call analyzeStock which builds result, then meetsAllCriteria
        List<TechnicalScreener.ScreeningResult> results = 
                technicalScreener.screenStocks(List.of("T"), TechnicalFilterChain.of(indicators, conditions), null);
        assertNotNull(results);

        // Scenario 2: Require Price Below MA200
        conditions = TechFilterConditions.builder()
                .priceConditions(java.util.List.of(
                        new com.hemasundar.config.StrategiesConfig.PriceCondition() {{
                            setPeriod(200);
                            setPosition(com.hemasundar.config.StrategiesConfig.Position.BELOW);
                        }}
                ))
                .build();
        results = technicalScreener.screenStocks(List.of("T"), TechnicalFilterChain.of(indicators, conditions), null);
        assertNotNull(results);
    }

    @Test
    public void testScreeningResultBuilderAndProperties() {
        TechnicalScreener.ScreeningResult result = TechnicalScreener.ScreeningResult.builder()
                .symbol("AAPL")
                .currentPrice(150.0)
                .previousRsi(45.0)
                .rsi(35.0)
                .rsiOversold(false)
                .volume(1000000L)
                .bollingerUpper(160.0)
                .bollingerMiddle(150.0)
                .bollingerLower(140.0)
                .priceTouchingLowerBand(false)
                .maValues(new java.util.HashMap<>(java.util.Map.of(
                        20, 155.0,
                        50, 145.0,
                        100, 140.0,
                        200, 130.0
                )))
                .build();

        assertEquals(result.getSymbol(), "AAPL");
        assertEquals(result.getCurrentPrice(), 150.0);
        assertEquals(result.getRsi(), 35.0);
        assertEquals(result.getVolume(), 1000000L);
        assertEquals(result.getBollingerUpper(), 160.0);
        assertEquals(result.getMaValues().get(20), 155.0);
        
        // Exercise toString to cover field access for all fields
        String toString = result.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("AAPL"));
        assertTrue(toString.contains("150.00"));
        assertTrue(toString.contains("Bollinger Bands"));
    }

    @Test
    public void testScreeningResultFormattedSummary() {
        TechnicalScreener.ScreeningResult result = TechnicalScreener.ScreeningResult.builder()
                .symbol("AAPL")
                .currentPrice(150.0)
                .previousRsi(45.0)
                .rsi(35.0)
                .rsiOversold(true)
                .volume(1500000L)
                .bollingerUpper(160.0)
                .bollingerMiddle(150.0)
                .bollingerLower(140.0)
                .priceTouchingLowerBand(true)
                .maValues(new java.util.HashMap<>(java.util.Map.of(
                        20, 155.0,
                        50, 145.0,
                        100, 140.0,
                        200, 130.0
                )))
                .dropType("INTRADAY")
                .dropPercent(5.0)
                .referencePrice(158.0)
                .build();

        String summary = result.getFormattedSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Price: $150.00"));
        assertTrue(summary.contains("Drop: 5.00%"));
        assertTrue(summary.contains("Ref Price: $158.00"));
        assertTrue(summary.contains("Volume: 1.50M"));
        assertTrue(summary.contains("OVERSOLD"));
        assertTrue(summary.contains("Touching Lower"));
        assertTrue(summary.contains("MA20"));
        assertTrue(summary.contains("MA200"));

        // Test with crossover, touch upper band, other volumes, etc.
        result.setRsiOversold(false);
        result.setRsiBullishCrossover(true);
        result.setPriceTouchingLowerBand(false);
        result.setPriceTouchingUpperBand(true);
        result.setVolume(500L);
        summary = result.getFormattedSummary();
        assertTrue(summary.contains("Volume: 500"));
        assertTrue(summary.contains("CROSSOVER"));
        assertTrue(summary.contains("Touching Upper"));

        // Test with bearish crossover, overbought, volume in K
        result.setRsiBullishCrossover(false);
        result.setRsiBearishCrossover(true);
        result.setRsiOverbought(true);
        result.setPriceTouchingUpperBand(false);
        result.setVolume(1500L);
        summary = result.getFormattedSummary();
        System.out.println("DEBUG FORMATTED SUMMARY:\n" + summary);
        assertTrue(summary.contains("Volume: 1.50K"));
        assertTrue(summary.contains("CROSSOVER"));
        assertTrue(summary.contains("Within bands"));
    }

    private PriceHistoryResponse createMockResponse(double price, int candleCount) {
        PriceHistoryResponse response = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < candleCount; i++) {
            PriceHistoryResponse.CandleData c = new PriceHistoryResponse.CandleData();
            c.setDatetime(now - (candleCount - i) * 86400000L);
            c.setOpen(price + i);
            c.setHigh(price + i + 1);
            c.setLow(price + i - 1);
            c.setClose(price + i);
            c.setVolume(1000L);
            candles.add(c);
        }
        response.setCandles(candles);
        return response;
    }
}
