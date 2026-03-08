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

    private PriceHistoryResponse createMockResponse(double price, int candleCount) {
        PriceHistoryResponse response = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < candleCount; i++) {
            PriceHistoryResponse.CandleData c = new PriceHistoryResponse.CandleData();
            c.setDatetime(now - (candleCount - i) * 86400000L);
            c.setOpen(price);
            c.setHigh(price + 1);
            c.setLow(price - 1);
            c.setClose(price);
            c.setVolume(1000);
            candles.add(c);
        }
        response.setCandles(candles);
        return response;
    }
}
