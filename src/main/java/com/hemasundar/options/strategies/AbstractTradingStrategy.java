package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.pojos.EarningsCache;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.pojos.EarningsCalendarResponse;
import org.apache.commons.collections4.CollectionUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public abstract class AbstractTradingStrategy implements TradingStrategy {

    @Getter
    private final StrategyType strategyType;

    protected AbstractTradingStrategy(StrategyType strategyType) {
        this.strategyType = strategyType;
    }

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        List<String> expiryDates = chain.getExpiryDatesInRange(filter.getTargetDTE(), filter.getMinDTE(),
                filter.getMaxDTE());
        if (expiryDates.isEmpty()) {
            log.debug("[{}] No expiry dates found in range [{}-{}]",
                    chain.getSymbol(), filter.getMinDTE(), filter.getMaxDTE());
            return new ArrayList<>();
        }

        log.info("[{}] Processing {} expiry dates: {}", chain.getSymbol(), expiryDates.size(), expiryDates);

        List<TradeSetup> allTrades = new ArrayList<>();

        for (String expiryDate : expiryDates) {
            // Check earnings for this expiry if not ignored
            if (!filter.isIgnoreEarnings()) {
                try {
                    EarningsCalendarResponse earningsResponse = FinnHubAPIs.getEarningsByTicker(
                            chain.getSymbol(), LocalDate.parse(expiryDate));
                    if (CollectionUtils.isNotEmpty(earningsResponse.getEarningsCalendar())) {
                        log.info("[{}] Skipping expiry {} due to upcoming earnings on {}",
                                chain.getSymbol(), expiryDate,
                                earningsResponse.getEarningsCalendar().get(0).getDate());
                        continue; // Skip this expiry, try next
                    }
                } catch (Exception e) {
                    log.error("[{}] Error checking earnings for {}: {}",
                            chain.getSymbol(), expiryDate, e.getMessage());
                }
            }

            // Find trades for this expiry
            List<TradeSetup> trades = findValidTrades(chain, expiryDate, filter);
            log.info("[{}] Found {} trades for expiry {}", chain.getSymbol(), trades.size(), expiryDate);
            allTrades.addAll(trades);
        }

        log.info("[{}] Total trades found: {}", chain.getSymbol(), allTrades.size());
        return allTrades;
    }

    protected abstract List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter);

    /**
     * Returns the display name for this strategy.
     * Derived from the StrategyType enum.
     */
    public String getStrategyName() {
        return strategyType.getDisplayName();
    }
}
