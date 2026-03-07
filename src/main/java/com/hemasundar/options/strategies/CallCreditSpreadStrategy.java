package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.CallCreditSpread;
import com.hemasundar.options.model.TradeSetup;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Call Credit Spread strategy.
 */
public class CallCreditSpreadStrategy extends AbstractTradingStrategy<CallCreditSpread, OptionsStrategyFilter> {

    public List<CallCreditSpread> findTrades(List<CallCreditSpread> candidates, OptionsStrategyFilter filter) {
        return candidates.stream()
                .filter(commonMaxLossFilter(filter, CallCreditSpread::maxLoss))
                .filter(commonMinReturnOnRiskFilter(filter, CallCreditSpread::netCredit, CallCreditSpread::maxLoss))
                .filter(commonMaxTotalCreditFilter(filter, CallCreditSpread::netCredit))
                .filter(commonMinTotalCreditFilter(filter, CallCreditSpread::netCredit))
                .collect(Collectors.toList());
    }
}
