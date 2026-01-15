package com.hemasundar.options.strategies;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;

import java.util.List;

public interface TradingStrategy {
    List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter);
}
