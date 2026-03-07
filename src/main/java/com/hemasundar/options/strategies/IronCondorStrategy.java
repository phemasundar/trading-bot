package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.IronCondor;
import com.hemasundar.options.model.TradeSetup;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Iron Condor strategy.
 */
public class IronCondorStrategy extends AbstractTradingStrategy<IronCondor, OptionsStrategyFilter> {

    public List<IronCondor> findTrades(List<IronCondor> candidates, OptionsStrategyFilter filter) {
        return candidates.stream()
                .filter(commonMaxLossFilter(filter, IronCondor::maxLoss))
                .filter(commonMinReturnOnRiskFilter(filter, IronCondor::netCredit, IronCondor::maxLoss))
                .filter(commonMaxTotalCreditFilter(filter, IronCondor::netCredit))
                .filter(commonMinTotalCreditFilter(filter, IronCondor::netCredit))
                .collect(Collectors.toList());
    }
}
