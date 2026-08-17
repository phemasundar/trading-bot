package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.dto.Trade;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class TradeHistoryRepositoryTest {

    @Mock
    private SupabaseClient supabaseClient;

    @Mock
    private RequestSpecification requestSpecification;

    @Mock
    private Response response;

    private TradeHistoryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(supabaseClient.getObjectMapper()).thenReturn(objectMapper);
        when(supabaseClient.request()).thenReturn(requestSpecification);
        when(requestSpecification.header(anyString(), anyString())).thenReturn(requestSpecification);
        when(requestSpecification.body(anyString())).thenReturn(requestSpecification);

        repository = new TradeHistoryRepository(supabaseClient);
    }

    @Test
    public void testSaveHistoricalTradesNullOrEmpty() throws Exception {
        repository.saveHistoricalTrades(null, "strat1", "Strat 1", 100L);
        repository.saveHistoricalTrades(Collections.emptyList(), "strat1", "Strat 1", 100L);
        verifyNoInteractions(requestSpecification);

        List<Trade> tradesWithNull = new ArrayList<>();
        tradesWithNull.add(null);
        tradesWithNull.add(Trade.builder().symbol(null).build());

        repository.saveHistoricalTrades(tradesWithNull, "strat1", "Strat 1", 100L);
        verifyNoInteractions(requestSpecification);
    }

    @Test
    public void testSaveHistoricalTradesSuccess() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?on_conflict=trade_hash");
        when(requestSpecification.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        Trade trade = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").build();
        repository.saveHistoricalTrades(List.of(trade), "put_credit_spread", null, 100L);

        verify(requestSpecification, times(1)).header("Prefer", "resolution=merge-duplicates");
        verify(requestSpecification, times(1)).post("http://localhost/rest/v1/historical_trades?on_conflict=trade_hash");
    }

    @Test
    public void testSaveHistoricalTradesDeduplicatesWithinSameBatch() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?on_conflict=trade_hash");
        when(requestSpecification.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        Trade trade1 = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").underlyingPrice(150.0).returnOnRisk(10.0).build();
        Trade trade2 = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").underlyingPrice(155.0).returnOnRisk(12.5).build(); // Duplicate on same day with updated price

        repository.saveHistoricalTrades(List.of(trade1, trade2), "put_credit_spread", "Put Credit Spread", 100L);

        verify(requestSpecification, times(1)).header("Prefer", "resolution=merge-duplicates");
        verify(requestSpecification, times(1)).body(bodyCaptor.capture());
        verify(requestSpecification, times(1)).post("http://localhost/rest/v1/historical_trades?on_conflict=trade_hash");

        String payload = bodyCaptor.getValue();
        assertTrue(payload.contains("\"underlyingPrice\":155.0"));
        assertTrue(payload.contains("\"returnOnRisk\":12.5"));
        assertTrue(payload.contains("\"created_at\""));
    }

    @Test(expectedExceptions = IOException.class)
    public void testSaveHistoricalTradesHttpError() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?on_conflict=trade_hash");
        when(requestSpecification.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Internal Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("DB error");

        Trade trade = Trade.builder().symbol("AAPL").build();
        repository.saveHistoricalTrades(List.of(trade), "put_credit_spread", "Put Credit Spread", 100L);
    }

    @Test
    public void testFindHistoricalTradesBySymbolAndStrategyNullOrBlankSymbol() throws Exception {
        List<Trade> result1 = repository.findHistoricalTradesBySymbolAndStrategy(null, "strat1", 10);
        List<Trade> result2 = repository.findHistoricalTradesBySymbolAndStrategy("", "strat1", 10);
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
    }

    @Test
    public void testFindHistoricalTradesBySymbolAndStrategySuccess() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?symbol=eq.AAPL");
        when(requestSpecification.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        String jsonResponse = "[{\"execution_time_ms\": 1723651200000, \"trade_data\": {\"symbol\": \"AAPL\", \"underlyingPrice\": 150.0}}]";
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn(jsonResponse);

        List<Trade> trades = repository.findHistoricalTradesBySymbolAndStrategy("AAPL", "put_credit_spread", 20);
        assertNotNull(trades);
        assertEquals(trades.size(), 1);
        assertEquals(trades.get(0).getSymbol(), "AAPL");
        assertNotNull(trades.get(0).getFoundDate());
    }

    @Test
    public void testFindHistoricalTradesBySymbolAndStrategyEmptyResult() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?symbol=eq.AAPL");
        when(requestSpecification.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("[]");

        List<Trade> trades = repository.findHistoricalTradesBySymbolAndStrategy("AAPL", null, 0);
        assertTrue(trades.isEmpty());
    }

    @Test(expectedExceptions = IOException.class)
    public void testFindHistoricalTradesBySymbolAndStrategyError() throws Exception {
        when(supabaseClient.getUrl(anyString())).thenReturn("http://localhost/rest/v1/historical_trades?symbol=eq.AAPL");
        when(requestSpecification.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(500);
        when(response.getStatusLine()).thenReturn("Server Error");
        when(response.getBody()).thenReturn(mock(io.restassured.response.ResponseBody.class));
        when(response.getBody().asString()).thenReturn("Error");

        repository.findHistoricalTradesBySymbolAndStrategy("AAPL", "put_credit_spread", 10);
    }
}
