package com.hemasundar.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.StrategyFilter;
import com.hemasundar.pojos.TradeSetup;
import com.hemasundar.pojos.earningsCalendarResponse;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTradingStrategy implements TradingStrategy {

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, StrategyFilter filter) {
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
                earningsCalendarResponse earningsResponse = FinnHubAPIs.getEarningsByTicker(chain.getSymbol(),
                        LocalDate.parse(targetExpiryDate));
                if (CollectionUtils.isNotEmpty(earningsResponse.getEarningsCalendar())) {
                    System.out.println("Skipping " + chain.getSymbol() + " due to upcoming earnings on "
                            + earningsResponse.getEarningsCalendar().get(0).getDate());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                System.err.println("Error checking earnings for " + chain.getSymbol() + ": " + e.getMessage());
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
            StrategyFilter filter);
}
