package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
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

    @BeforeMethod
    public void setUp() {
        thinkOrSwinAPIs = Mockito.mock(ThinkOrSwinAPIs.class);
        priceDropScreener = new PriceDropScreener(thinkOrSwinAPIs);
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
                List.of("AAPL"), 3.0, 0);
        
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
                List.of("MSFT"), 3.0, 0);
        
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
                List.of("TSLA"), 15.0);
        
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
        
        Mockito.when(thinkOrSwinAPIs.getPriceHistory(
                anyString(), anyString(), anyInt(), anyString(), anyInt(), any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(history);
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("NVDA"), 5.0, 5);
        
        assertEquals(results.size(), 1);
        assertEquals(results.get(0).getDropPercent(), 10.0, 0.01);
        assertEquals(results.get(0).getDropType(), "5D");
    }

    @Test
    public void testScreenPriceDrop_ApiErrorGraceful() {
        Mockito.when(thinkOrSwinAPIs.getQuotes(anyList())).thenThrow(new RuntimeException("API Down"));
        
        List<TechnicalScreener.ScreeningResult> results = priceDropScreener.screenPriceDrop(
                List.of("AAPL"), 3.0, 0);
        
        assertEquals(results.size(), 0);
    }
}
