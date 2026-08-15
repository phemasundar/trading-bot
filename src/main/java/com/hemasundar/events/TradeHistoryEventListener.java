package com.hemasundar.events;

import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.supabase.TradeHistoryRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener for historical trade recording upon strategy execution completion.
 */
@Log4j2
@Component
public class TradeHistoryEventListener {

    private final TradeHistoryRepository tradeHistoryRepository;
    private final StrategyExecutionService strategyExecutionService;

    @Value("${trading.history.enabled:true}")
    private boolean historyEnabled;

    public TradeHistoryEventListener(TradeHistoryRepository tradeHistoryRepository,
                                     @Lazy StrategyExecutionService strategyExecutionService) {
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.strategyExecutionService = strategyExecutionService;
    }

    /**
     * Handles completion of strategy execution events.
     *
     * @param event Strategy execution completion event
     */
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
            log.error("Failed to record historical trades for strategy '{}': {}",
                    result.getStrategyId(), e.getMessage());
            if (strategyExecutionService != null) {
                strategyExecutionService.addAlert(
                        ExecutionAlert.Severity.ERROR,
                        AlertMessages.SRC_SUPABASE,
                        AlertMessages.SAVE_HISTORICAL_TRADES_FAILED + ": " + (result.getStrategyName() != null ? result.getStrategyName() : result.getStrategyId()) + " (" + e.getMessage() + ")"
                );
            }
        }
    }
}
