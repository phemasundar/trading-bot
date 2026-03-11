package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.technical.*;
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

    private MockedStatic<ThinkOrSwinAPIs> mockedApis;

    @BeforeMethod
    public void setUp() {
        mockedApis = Mockito.mockStatic(ThinkOrSwinAPIs.class);
    }

    @AfterMethod
    public void tearDown() {
        mockedApis.close();
    }

    @Test
    public void testAnalyzeStockNullHistory() {
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(null);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();
        TechnicalScreener.ScreeningResult result = TechnicalScreener.analyzeStock("AAPL", indicators);

        assertNull(result);
    }

    @Test
    public void testAnalyzeStockEmptyHistory() {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(new ArrayList<>());
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();
        TechnicalScreener.ScreeningResult result = TechnicalScreener.analyzeStock("TSLA", indicators);

        assertNull(result);
    }

    @Test
    public void testAnalyzeStockSuccess() {
        PriceHistoryResponse response = createMockResponse(100.0, 10);
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .bollingerFilter(BollingerBandsFilter.builder().build())
                .ma20Filter(MovingAverageFilter.builder().period(20).build())
                .build();

        TechnicalScreener.ScreeningResult result = TechnicalScreener.analyzeStock("MSFT", indicators);

        assertNotNull(result);
        assertEquals(result.getSymbol(), "MSFT");
        assertTrue(result.getCurrentPrice() > 0);
        assertTrue(result.getRsi() >= 0);
    }

    @Test
    public void testScreenStocksCriteriaMet() {
        PriceHistoryResponse response = createMockResponse(50.0, 30);
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .build();

        TechFilterConditions conditions = TechFilterConditions.builder()
                .minVolume(500L)
                .build();

        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);

        List<TechnicalScreener.ScreeningResult> results = TechnicalScreener.screenStocks(Arrays.asList("GOOGL"), chain);

        assertNotNull(results);
        assertEquals(results.size(), 1, "Should find 1 stock as flat price meets volume 500 requirement");
        assertEquals(results.get(0).getSymbol(), "GOOGL");
    }

    @Test
    public void testScreenStocksCriteriaNotMet() {
        PriceHistoryResponse response = createMockResponse(100.0, 30);
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder().build();

        // Require massive volume
        TechFilterConditions conditions = TechFilterConditions.builder()
                .minVolume(10_000_000L)
                .build();

        TechnicalFilterChain chain = TechnicalFilterChain.of(indicators, conditions);

        List<TechnicalScreener.ScreeningResult> results = TechnicalScreener.screenStocks(Arrays.asList("NVDA"), chain);

        assertNotNull(results);
        assertEquals(results.size(), 0, "Should find 0 stocks as volume 1000 < 10M");
    }

    @Test
    public void testAnalyzeStock_AllIndicators() {
        // Create 201 candles for MA200
        PriceHistoryResponse response = createMockResponse(100.0, 201);
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(response);

        TechnicalIndicators indicators = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().build())
                .bollingerFilter(BollingerBandsFilter.builder().build())
                .ma20Filter(MovingAverageFilter.builder().period(20).build())
                .ma50Filter(MovingAverageFilter.builder().period(50).build())
                .ma100Filter(MovingAverageFilter.builder().period(100).build())
                .ma200Filter(MovingAverageFilter.builder().period(200).build())
                .volumeFilter(VolumeFilter.builder().build())
                .build();

        TechnicalScreener.ScreeningResult result = TechnicalScreener.analyzeStock("TEST", indicators);
        
        assertNotNull(result);
        assertEquals(result.getSymbol(), "TEST");
        assertTrue(result.getRsi() > 0);
        assertTrue(result.getMa200() > 0);
    }

    @Test
    public void testMeetsAllCriteria_ConditionCoverage() {
        // We will exercise meetsAllCriteria through evaluate indirectly
        PriceHistoryResponse response = createMockResponse(100.0, 201);
        mockedApis.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
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
                TechnicalScreener.screenStocks(List.of("T"), TechnicalFilterChain.of(indicators, conditions));
        assertNotNull(results);

        // Scenario 2: Require Price Below MA200
        conditions = TechFilterConditions.builder()
                .requirePriceBelowMA200(true)
                .build();
        
        results = TechnicalScreener.screenStocks(List.of("T"), TechnicalFilterChain.of(
                        TechnicalIndicators.builder().ma200Filter(MovingAverageFilter.builder().period(200).build()).build(),
                        conditions));
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
                .ma20(155.0)
                .priceBelowMA20(true)
                .ma50(145.0)
                .priceBelowMA50(false)
                .ma100(140.0)
                .priceBelowMA100(false)
                .ma200(130.0)
                .priceBelowMA200(false)
                .build();

        assertEquals(result.getSymbol(), "AAPL");
        assertEquals(result.getCurrentPrice(), 150.0);
        assertEquals(result.getRsi(), 35.0);
        assertEquals(result.getVolume(), 1000000L);
        assertEquals(result.getBollingerUpper(), 160.0);
        assertEquals(result.getMa20(), 155.0);
        assertTrue(result.isPriceBelowMA20());
        
        // Exercise toString to cover field access for all fields
        String toString = result.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("AAPL"));
        assertTrue(toString.contains("150.00"));
        assertTrue(toString.contains("Bollinger Bands"));
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
            c.setVolume(1000);
            candles.add(c);
        }
        response.setCandles(candles);
        return response;
    }
}
