package com.hemasundar.services.history;

import com.hemasundar.dto.Trade;
import com.hemasundar.services.supabase.TradeHistoryRepository;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class TradeHistoryServiceTest {

    @Mock
    private TradeHistoryRepository tradeHistoryRepository;

    @Mock
    private TradeSimilarityCondition similarityCondition;

    private TradeHistoryService service;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TradeHistoryService(tradeHistoryRepository, similarityCondition);
        ReflectionTestUtils.setField(service, "historyEnabled", true);
    }

    @Test
    public void testFindSimilarTradesDisabledHistory() {
        ReflectionTestUtils.setField(service, "historyEnabled", false);
        List<Trade> result = service.findSimilarTrades("strat1", Trade.builder().symbol("AAPL").build(), 10);
        assertTrue(result.isEmpty());
        verifyNoInteractions(tradeHistoryRepository);
    }

    @Test
    public void testFindSimilarTradesNullOrBlankSymbol() {
        List<Trade> res1 = service.findSimilarTrades("strat1", null, 10);
        List<Trade> res2 = service.findSimilarTrades("strat1", Trade.builder().symbol("").build(), 10);
        assertTrue(res1.isEmpty());
        assertTrue(res2.isEmpty());
        verifyNoInteractions(tradeHistoryRepository);
    }

    @Test
    public void testFindSimilarTradesSuccess() throws Exception {
        Trade target = Trade.builder().symbol("AAPL").build();
        Trade candidate1 = Trade.builder().symbol("AAPL").netCredit(200.0).build();
        Trade candidate2 = Trade.builder().symbol("AAPL").netCredit(100.0).build();

        when(tradeHistoryRepository.findHistoricalTradesBySymbolAndStrategy(eq("AAPL"), eq("strat1"), anyInt()))
                .thenReturn(List.of(candidate1, candidate2));

        when(similarityCondition.isSimilar(target, candidate1)).thenReturn(true);
        when(similarityCondition.isSimilar(target, candidate2)).thenReturn(false);

        List<Trade> matches = service.findSimilarTrades("strat1", target, 5);
        assertNotNull(matches);
        assertEquals(matches.size(), 1);
        assertEquals(matches.get(0).getNetCredit(), 200.0);
    }

    @Test
    public void testFindSimilarTradesExceptionHandling() throws Exception {
        Trade target = Trade.builder().symbol("AAPL").build();
        when(tradeHistoryRepository.findHistoricalTradesBySymbolAndStrategy(anyString(), anyString(), anyInt()))
                .thenThrow(new IOException("Database failure"));

        List<Trade> matches = service.findSimilarTrades("strat1", target, 10);
        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }
}
