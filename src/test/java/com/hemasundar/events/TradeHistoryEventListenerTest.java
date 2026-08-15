package com.hemasundar.events;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.services.supabase.TradeHistoryRepository;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TradeHistoryEventListenerTest {

    @Mock
    private TradeHistoryRepository tradeHistoryRepository;

    @Mock
    private com.hemasundar.services.StrategyExecutionService strategyExecutionService;

    private TradeHistoryEventListener listener;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new TradeHistoryEventListener(tradeHistoryRepository, strategyExecutionService);
        ReflectionTestUtils.setField(listener, "historyEnabled", true);
    }

    @Test
    public void testHandleStrategyExecutionCompletedDisabledHistory() {
        ReflectionTestUtils.setField(listener, "historyEnabled", false);
        listener.handleStrategyExecutionCompleted(new StrategyExecutionCompletedEvent(null, false));
        verifyNoInteractions(tradeHistoryRepository);
    }

    @Test
    public void testHandleStrategyExecutionCompletedNullAndCustomEvent() {
        listener.handleStrategyExecutionCompleted(null);
        listener.handleStrategyExecutionCompleted(new StrategyExecutionCompletedEvent(null, true));

        StrategyResult result = StrategyResult.builder().trades(List.of(Trade.builder().symbol("AAPL").build())).build();
        listener.handleStrategyExecutionCompleted(new StrategyExecutionCompletedEvent(result, true));

        verifyNoInteractions(tradeHistoryRepository);
    }

    @Test
    public void testHandleStrategyExecutionCompletedNullOrEmptyTrades() {
        StrategyResult resultNullTrades = StrategyResult.builder().trades(null).build();
        listener.handleStrategyExecutionCompleted(new StrategyExecutionCompletedEvent(resultNullTrades, false));

        StrategyResult resultEmptyTrades = StrategyResult.builder().trades(Collections.emptyList()).build();
        listener.handleStrategyExecutionCompleted(new StrategyExecutionCompletedEvent(resultEmptyTrades, false));

        verifyNoInteractions(tradeHistoryRepository);
    }

    @Test
    public void testHandleStrategyExecutionCompletedSavesTrades() throws Exception {
        Trade trade = Trade.builder().symbol("AAPL").build();
        StrategyResult result = StrategyResult.builder()
                .strategyId("put_credit_spread")
                .strategyName("Put Credit Spread")
                .executionTimeMs(100L)
                .trades(List.of(trade))
                .build();

        StrategyExecutionCompletedEvent event = new StrategyExecutionCompletedEvent(result, false);
        listener.handleStrategyExecutionCompleted(event);

        verify(tradeHistoryRepository, times(1)).saveHistoricalTrades(eq(List.of(trade)), eq("put_credit_spread"), eq("Put Credit Spread"), eq(100L));
    }

    @Test
    public void testHandleStrategyExecutionCompletedHandlesExceptionGracefully() throws Exception {
        doThrow(new IOException("Database down")).when(tradeHistoryRepository)
                .saveHistoricalTrades(anyList(), anyString(), anyString(), anyLong());

        Trade trade = Trade.builder().symbol("AAPL").build();
        StrategyResult result = StrategyResult.builder()
                .strategyId("put_credit_spread")
                .strategyName("Put Credit Spread")
                .executionTimeMs(100L)
                .trades(List.of(trade))
                .build();

        StrategyExecutionCompletedEvent event = new StrategyExecutionCompletedEvent(result, false);

        listener.handleStrategyExecutionCompleted(event);

        verify(strategyExecutionService, times(1)).addAlert(
                eq(com.hemasundar.dto.ExecutionAlert.Severity.ERROR),
                eq(com.hemasundar.dto.AlertMessages.SRC_SUPABASE),
                contains("Save historical trades failed")
        );
    }
}
