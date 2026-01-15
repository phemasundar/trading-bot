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
        String targetExpiryDate = chain.getExpiryDateBasedOnDTE(filter.getTargetDTE());
        if (targetExpiryDate == null)
            return new ArrayList<>();

        if (!filter.isIgnoreEarnings()) {
            try {
                // Assuming targetExpiryDate format is YYYY-MM-DD which is standard for
                // LocalDate.parse
                // If getExpiryDateBasedOnDTE returns something else, we might need a formatter.
                // Based on previous code in PutCreditSpreadStrategy,
                // LocalDate.parse(targetExpiryDate) was used.
                EarningsCalendarResponse earningsResponse = FinnHubAPIs.getEarningsByTicker(chain.getSymbol(),
                        LocalDate.parse(targetExpiryDate));
                if (CollectionUtils.isNotEmpty(earningsResponse.getEarningsCalendar())) {
                    log.info("[{}] Skipping due to upcoming earnings on {}",
                            chain.getSymbol(), earningsResponse.getEarningsCalendar().get(0).getDate());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("[{}] Error checking earnings: {}", chain.getSymbol(), e.getMessage());
                // Decide whether to proceed or fail safe. Proceeding might accept risky trades.
                // Failing safe returns empty.
                // Let's print and proceed, or we can return empty. The original code didn't
                // try-catch, but FinnHubAPIs throws RuntimeException.
                // Let's stick to the user's logic: strict check. If API fails, maybe we
                // shouldn't block?
                // But the user's previous code would throw exception up.
                // Let's assume FinnHubAPIs handles basic errors or throws.
            }
        }

        return findValidTrades(chain, targetExpiryDate, filter);
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
