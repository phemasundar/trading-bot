package com.hemasundar.services.history;

import com.hemasundar.dto.Trade;
import com.hemasundar.services.supabase.TradeHistoryRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing trade history lookups and executing dynamic similarity matching.
 */
@Log4j2
@Service
public class TradeHistoryService {

    private final TradeHistoryRepository tradeHistoryRepository;
    private final TradeSimilarityCondition similarityCondition;

    @Value("${trading.history.enabled:true}")
    private boolean historyEnabled;

    public TradeHistoryService(TradeHistoryRepository tradeHistoryRepository, TradeSimilarityCondition similarityCondition) {
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.similarityCondition = similarityCondition;
    }

    /**
     * Finds historical trades similar to a target trade.
     *
     * @param strategyId  Strategy ID
     * @param targetTrade Target trade DTO
     * @param limit       Max number of similar trades to return
     * @return List of matching historical trade DTOs
     */
    public List<Trade> findSimilarTrades(String strategyId, Trade targetTrade, int limit) {
        if (!historyEnabled) {
            log.debug("Trade history lookup requested while history is disabled");
            return Collections.emptyList();
        }

        if (targetTrade == null || targetTrade.getSymbol() == null || targetTrade.getSymbol().isBlank()) {
            return Collections.emptyList();
        }

        try {
            int fetchLimit = Math.max(limit * 3, 50);
            List<Trade> candidates = tradeHistoryRepository.findHistoricalTradesBySymbolAndStrategy(
                    targetTrade.getSymbol(), strategyId, fetchLimit);

            int maxResults = limit > 0 ? limit : 20;
            java.util.Set<String> seenTradeKeys = new java.util.HashSet<>();
            return candidates.stream()
                    .filter(candidate -> similarityCondition.isSimilar(targetTrade, candidate))
                    .filter(candidate -> {
                        String date = candidate.getFoundDate() != null ? candidate.getFoundDate() : "";
                        String key = date + ":" + com.hemasundar.utils.TradeHashUtil.generateTradeHash("", candidate, date);
                        return seenTradeKeys.add(key);
                    })
                    .limit(maxResults)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to find similar historical trades for symbol '{}': {}",
                    targetTrade.getSymbol(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
