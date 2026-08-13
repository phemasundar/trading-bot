package com.hemasundar.events;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.services.supabase.TradeHistoryRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async event listener for decoupling historical trade recording from core strategy execution.
 */
@Log4j2
@Component
public class TradeHistoryEventListener {

    private final TradeHistoryRepository tradeHistoryRepository;

    @Value("${trading.history.enabled:true}")
    private boolean historyEnabled;

    public TradeHistoryEventListener(TradeHistoryRepository tradeHistoryRepository) {
        this.tradeHistoryRepository = tradeHistoryRepository;
    }

    /**
     * Handles completion of strategy execution events asynchronously.
     *
     * @param event Strategy execution completion event
     */
    @Async
    @EventListener
    public void handleStrategyExecutionCompleted(StrategyExecutionCompletedEvent event) {
        if (!historyEnabled) {
            log.debug("Trade history persistence is disabled by configuration");
            return;
        }

        if (event == null || event.isCustomExecution() || event.getResult() == null) {
            return;
        }

        StrategyResult result = event.getResult();
        if (result.getTrades() == null || result.getTrades().isEmpty()) {
            return;
        }

        try {
            tradeHistoryRepository.saveHistoricalTrades(
                    result.getTrades(),
                    result.getStrategyId(),
                    result.getStrategyName(),
                    result.getExecutionTimeMs()
            );
        } catch (Exception e) {
            log.warn("Failed to record historical trades asynchronously for strategy '{}': {}",
                    result.getStrategyId(), e.getMessage());
        }
    }
}
