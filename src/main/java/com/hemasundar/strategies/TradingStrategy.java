package com.hemasundar.strategies;

import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.OptionsStrategyFilter;
import com.hemasundar.pojos.TradeSetup;

import java.util.List;

public interface TradingStrategy {
    List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter);
}
