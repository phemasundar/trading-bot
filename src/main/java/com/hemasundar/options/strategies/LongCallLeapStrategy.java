package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.LongCallLeap;
import com.hemasundar.options.model.TradeSetup;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Long Call LEAP strategy.
 */
public class LongCallLeapStrategy extends AbstractTradingStrategy<LongCallLeap, OptionsStrategyFilter> {

    public List<LongCallLeap> findTrades(List<LongCallLeap> candidates, OptionsStrategyFilter filter) {
        return candidates.stream()
                .filter(commonMaxLossFilter(filter, LongCallLeap::maxLoss))
                .filter(commonMaxTotalDebitFilter(filter, LongCallLeap::netDebit))
                .collect(Collectors.toList());
    }
}
