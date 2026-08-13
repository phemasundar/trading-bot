package com.hemasundar.api;

import com.hemasundar.dto.Trade;
import com.hemasundar.services.history.TradeHistoryService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class TradeHistoryControllerTest {

    @Mock
    private TradeHistoryService tradeHistoryService;

    private TradeHistoryController controller;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TradeHistoryController(tradeHistoryService);
    }

    @Test
    public void testGetSimilarTradesNullRequestOrTrade() {
        ResponseEntity<?> resp1 = controller.getSimilarTrades(null);
        assertEquals(resp1.getStatusCode(), HttpStatus.BAD_REQUEST);

        TradeHistoryController.SimilarTradesRequest req2 = new TradeHistoryController.SimilarTradesRequest();
        ResponseEntity<?> resp2 = controller.getSimilarTrades(req2);
        assertEquals(resp2.getStatusCode(), HttpStatus.BAD_REQUEST);
    }

    @Test
    public void testGetSimilarTradesSuccess() {
        Trade target = Trade.builder().symbol("AAPL").build();
        Trade HistoryMatch = Trade.builder().symbol("AAPL").netCredit(150.0).build();

        TradeHistoryController.SimilarTradesRequest req = new TradeHistoryController.SimilarTradesRequest();
        req.setStrategyId("put_credit_spread");
        req.setTrade(target);
        req.setLimit(15);

        when(tradeHistoryService.findSimilarTrades(eq("put_credit_spread"), eq(target), eq(15)))
                .thenReturn(List.of(HistoryMatch));

        ResponseEntity<?> response = controller.getSimilarTrades(req);
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertTrue(response.getBody() instanceof List);

        @SuppressWarnings("unchecked")
        List<Trade> body = (List<Trade>) response.getBody();
        assertEquals(body.size(), 1);
        assertEquals(body.get(0).getNetCredit(), 150.0);
    }

    @Test
    public void testGetSimilarTradesExceptionHandling() {
        Trade target = Trade.builder().symbol("AAPL").build();
        TradeHistoryController.SimilarTradesRequest req = new TradeHistoryController.SimilarTradesRequest();
        req.setTrade(target);

        when(tradeHistoryService.findSimilarTrades(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Service Error"));

        ResponseEntity<?> response = controller.getSimilarTrades(req);
        assertEquals(response.getStatusCode(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
