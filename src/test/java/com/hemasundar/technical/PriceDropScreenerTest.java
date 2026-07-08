package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.cache.PriceHistoryCache;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.*;

public class PriceDropScreenerTest {

    private PriceDropScreener priceDropScreener;
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
    private SchwabApiExecutor schwabApiExecutor;

    @BeforeMethod
    public void setUp() {
        thinkOrSwinAPIs = Mockito.mock(ThinkOrSwinAPIs.class);
        schwabApiExecutor = Mockito.mock(SchwabApiExecutor.class);
        Mockito.when(schwabApiExecutor.executeParallel(anyList(), any(), any())).thenAnswer(inv -> {
            List<String> symbols = inv.getArgument(0);
            java.util.function.Function<String, Object> func = inv.getArgument(1);
            List<Object> res = new ArrayList<>();
            for (String s : symbols) {
                res.add(func.apply(s));
            }
            return res;
        });
        priceDropScreener = new PriceDropScreener(thinkOrSwinAPIs, schwabApiExecutor);
        PriceHistoryCache.getInstance().clear();
    }

    @Test
    public void testScreenIntradayDrop_Match() {
        Map<String, QuotesResponse.QuoteData> quotes = new HashMap<>();
        
        QuotesResponse.Quote quote = new QuotesResponse.Quote();
        quote.setLastPrice(95.0);
        quote.setClosePrice(100.0);
        quote.setNetPercentChange(-5.0);
        quote.setTotalVolume(1000000L);
        
        QuotesResponse.QuoteData data = new QuotesResponse.QuoteData();
        data.setQuote(quote);
        quotes.put("AAPL", data);
        
        Mockito.when(thinkOrSwinAPIs.getQuotes(anyList())).thenReturn(quotes);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("AAPL"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 3.0)), 0, null);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getSymbol(), "AAPL");
        assertEquals(results.get(0).getDropPercent(), 5.0, 0.01);
        assertEquals(results.get(0).getDropType(), "INTRADAY");
    }

    @Test
    public void testScreenIntradayDrop_NoMatch() {
        Map<String, QuotesResponse.QuoteData> quotes = new HashMap<>();
        
        QuotesResponse.Quote quote = new QuotesResponse.Quote();
        quote.setLastPrice(99.0);
        quote.setClosePrice(100.0);
        quote.setNetPercentChange(-1.0);
        
        QuotesResponse.QuoteData data = new QuotesResponse.QuoteData();
        data.setQuote(quote);
        quotes.put("MSFT", data);
        
        Mockito.when(thinkOrSwinAPIs.getQuotes(anyList())).thenReturn(quotes);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("MSFT"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 3.0)), 0, null);
        
        assertEquals(results.size(), 0);
    }

    @Test
    public void testScreen52WeekHighDrop_Match() {
        Map<String, QuotesResponse.QuoteData> quotes = new HashMap<>();
        
        QuotesResponse.Quote quote = new QuotesResponse.Quote();
        quote.setLastPrice(80.0);
        quote.setFiftyTwoWeekHigh(100.0);
        quote.setTotalVolume(500000L);
        
        QuotesResponse.QuoteData data = new QuotesResponse.QuoteData();
        data.setQuote(quote);
        quotes.put("TSLA", data);
        
        Mockito.when(thinkOrSwinAPIs.getQuotes(anyList())).thenReturn(quotes);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screen52WeekHighDrop(
                List.of("TSLA"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 15.0)), null);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getDropPercent(), 20.0, 0.01);
        assertEquals(results.get(0).getDropType(), "52W_HIGH");
    }

    @Test
    public void testScreenMultiDayDrop_Match() {
        PriceHistoryResponse history = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        
        // 5 days ago: Close 100
        PriceHistoryResponse.CandleData old = new PriceHistoryResponse.CandleData();
        old.setClose(100.0);
        candles.add(old);
        
        // Intermediary candles
        for (int i = 0; i < 4; i++) candles.add(new PriceHistoryResponse.CandleData());
        
        // Today: Close 90
        PriceHistoryResponse.CandleData today = new PriceHistoryResponse.CandleData();
        today.setClose(90.0);
        today.setVolume(2000000L);
        candles.add(today);
        
        history.setCandles(candles);
        
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(history);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("NVDA"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 5.0)), 5, null);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getDropPercent(), 10.0, 0.01);
        assertEquals(results.get(0).getDropType(), "5D");
    }

    @Test
    public void testScreenPriceDrop_ApiErrorGraceful() {
        Mockito.when(thinkOrSwinAPIs.getQuotes(anyList())).thenThrow(new RuntimeException("API Down"));
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("AAPL"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 3.0)), 0, null);
        
        assertEquals(results.size(), 0);
    }

    @Test
    public void testScreenMultiDayDrop_1Month_Match() {
        PriceHistoryResponse history = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        
        // 21 days ago: Close 100
        PriceHistoryResponse.CandleData old = new PriceHistoryResponse.CandleData();
        old.setClose(100.0);
        candles.add(old);
        
        // Intermediary candles
        for (int i = 0; i < 20; i++) candles.add(new PriceHistoryResponse.CandleData());
        
        // Today: Close 88
        PriceHistoryResponse.CandleData today = new PriceHistoryResponse.CandleData();
        today.setClose(88.0);
        today.setVolume(2000000L);
        candles.add(today);
        
        history.setCandles(candles);
        
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(history);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("AAPL"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 10.0)), 21, null);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getDropPercent(), 12.0, 0.01);
        assertEquals(results.get(0).getDropType(), "1M");
    }

    @Test
    public void testScreenMultiDayDrop_3Month_Match() {
        PriceHistoryResponse history = new PriceHistoryResponse();
        List<PriceHistoryResponse.CandleData> candles = new ArrayList<>();
        
        // 63 days ago: Close 100
        PriceHistoryResponse.CandleData old = new PriceHistoryResponse.CandleData();
        old.setClose(100.0);
        candles.add(old);
        
        // Intermediary candles
        for (int i = 0; i < 62; i++) candles.add(new PriceHistoryResponse.CandleData());
        
        // Today: Close 80
        PriceHistoryResponse.CandleData today = new PriceHistoryResponse.CandleData();
        today.setClose(80.0);
        today.setVolume(2000000L);
        candles.add(today);
        
        history.setCandles(candles);
        
        Mockito.when(thinkOrSwinAPIs.getYearlyPriceHistory(anyString(), anyInt()))
                .thenReturn(history);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("MSFT"), List.of(new com.hemasundar.technical.NumericRule(com.hemasundar.technical.RelationalOperator.GREATER_THAN_OR_EQUAL, 15.0)), 63, null);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getDropPercent(), 20.0, 0.01);
        assertEquals(results.get(0).getDropType(), "3M");
    }
}
