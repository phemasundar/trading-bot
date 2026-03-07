package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.PutCreditSpread;
import com.hemasundar.options.model.TradeSetup;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Put Credit Spread strategy.
 */
public class PutCreditSpreadStrategy extends AbstractTradingStrategy<PutCreditSpread, OptionsStrategyFilter> {

    public List<PutCreditSpread> findTrades(List<PutCreditSpread> candidates, OptionsStrategyFilter filter) {
        return candidates.stream()
                .filter(commonMaxLossFilter(filter, PutCreditSpread::maxLoss))
                .filter(commonMinReturnOnRiskFilter(filter, PutCreditSpread::netCredit, PutCreditSpread::maxLoss))
                .filter(commonMaxTotalCreditFilter(filter, PutCreditSpread::netCredit))
                .filter(commonMinTotalCreditFilter(filter, PutCreditSpread::netCredit))
                .collect(Collectors.toList());
    }
}
