package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.Zebra;
import com.hemasundar.options.model.TradeSetup;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ZEBRA strategy.
 */
public class ZebraStrategy extends AbstractTradingStrategy<Zebra, OptionsStrategyFilter> {

    public List<Zebra> findTrades(List<Zebra> candidates, OptionsStrategyFilter filter) {
        return candidates.stream()
                .filter(commonMaxLossFilter(filter, Zebra::maxLoss))
                .filter(commonMaxTotalDebitFilter(filter, Zebra::netDebit))
                .collect(Collectors.toList());
    }
}
