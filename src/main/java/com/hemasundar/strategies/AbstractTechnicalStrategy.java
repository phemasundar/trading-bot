package com.hemasundar.strategies;

import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.OptionsStrategyFilter;
import com.hemasundar.pojos.TradeSetup;
import com.hemasundar.pojos.technicalfilters.TechnicalFilterChain;
import com.hemasundar.utils.TechnicalStockValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for strategies that rely on technical indicators.
 * Handles duplicate logic for:
 * 1. Fetching price history and building BarSeries
 * 2. Checking volume conditions
 * 3. Validating RSI and Bollinger Band conditions
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractTechnicalStrategy extends AbstractTradingStrategy {

    protected final TechnicalFilterChain filterChain;

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate, OptionsStrategyFilter filter) {
        // Delegate all technical validation to the independent validator
        if (!isValidStock(chain.getSymbol())) {
            log.debug("[{}] Technical conditions NOT met for strategy: {}", chain.getSymbol(), getStrategyName());
            return new ArrayList<>();
        }

        log.info("[{}] Technical validation passed. Looking for trades via {}...",
                chain.getSymbol(), getStrategyName());

        // 4. Delegate to subclass for specific trade logic
        return findStrategySpecificTrades(chain, expiryDate, filter);
    }

    /**
     * Checks if a stock symbol meets the technical criteria of this strategy.
     * Can be used to filter stocks without fetching option chains.
     *
     * @param symbol The stock symbol to check
     * @return true if the stock meets volume, RSI, and Bollinger Band conditions
     */
    public boolean isValidStock(String symbol) {
        return TechnicalStockValidator.validate(symbol, filterChain);
    }

    /**
     * Implemented by subclasses to find specific trades (e.g., spreads)
     * after all technical conditions have been met.
     */
    protected abstract List<TradeSetup> findStrategySpecificTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter);
}
