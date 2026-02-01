package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.VolatilityCalculator;
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
        // Check historical volatility before processing
        if (!checkHistoricalVolatility(chain.getSymbol(), filter)) {
            return new ArrayList<>(); // Skip symbol if volatility doesn't meet threshold
        }

        List<String> expiryDates = chain.getExpiryDatesInRange(filter.getTargetDTE(), filter.getMinDTE(),
                filter.getMaxDTE());
        if (expiryDates.isEmpty()) {
            log.debug("[{}] No expiry dates found in range [{}-{}]",
                    chain.getSymbol(), filter.getMinDTE(), filter.getMaxDTE());
            return new ArrayList<>();
        }

        log.info("[{}] Processing {} expiry dates: {}", chain.getSymbol(), expiryDates.size(), expiryDates);

        List<TradeSetup> allTrades = new ArrayList<>();

        for (String expiryDate : expiryDates) {
            // Check earnings for this expiry if not ignored
            if (!filter.isIgnoreEarnings()) {
                try {
                    EarningsCalendarResponse earningsResponse = FinnHubAPIs.getEarningsByTicker(
                            chain.getSymbol(), LocalDate.parse(expiryDate));
                    if (CollectionUtils.isNotEmpty(earningsResponse.getEarningsCalendar())) {
                        log.info("[{}] Skipping expiry {} due to upcoming earnings on {}",
                                chain.getSymbol(), expiryDate,
                                earningsResponse.getEarningsCalendar().get(0).getDate());
                        continue; // Skip this expiry, try next
                    }
                } catch (Exception e) {
                    log.error("[{}] Error checking earnings for {}: {}",
                            chain.getSymbol(), expiryDate, e.getMessage());
                }
            }

            // Find trades for this expiry
            List<TradeSetup> trades = findValidTrades(chain, expiryDate, filter);
            log.info("[{}] Found {} trades for expiry {}", chain.getSymbol(), trades.size(), expiryDate);
            allTrades.addAll(trades);
        }

        log.info("[{}] Total trades found: {}", chain.getSymbol(), allTrades.size());
        return allTrades;
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

    /**
     * Checks if the symbol's historical volatility meets the minimum threshold.
     * Uses cache to avoid redundant API calls and calculations.
     *
     * @param symbol Stock symbol
     * @param filter Strategy filter containing minHistoricalVolatility
     * @return true if volatility check passes or is not configured, false otherwise
     */
    protected boolean checkHistoricalVolatility(String symbol, OptionsStrategyFilter filter) {
        // If no minimum volatility is set, pass all symbols
        if (filter.getMinHistoricalVolatility() == null) {
            return true;
        }

        try {
            // Check cache first
            PriceHistoryCache cache = PriceHistoryCache.getInstance();
            PriceHistoryCache.HistoricalData cachedData = cache.get(symbol);

            Double historicalVolatility;
            if (cachedData != null) {
                // Use cached volatility
                historicalVolatility = cachedData.getAnnualizedHistoricalVolatility();
                log.debug("[{}] Using cached historical volatility: {}%", symbol, historicalVolatility);
            } else {
                // Fetch price history and calculate volatility
                log.debug("[{}] Fetching price history to calculate historical volatility", symbol);
                PriceHistoryResponse priceHistory = ThinkOrSwinAPIs.getYearlyPriceHistory(symbol, 1);
                historicalVolatility = VolatilityCalculator.calculateAnnualizedVolatility(priceHistory);

                // Cache the result
                PriceHistoryCache.HistoricalData data = new PriceHistoryCache.HistoricalData(
                        priceHistory, historicalVolatility);
                cache.put(symbol, data);
                log.debug("[{}] Calculated and cached historical volatility: {}%",
                        symbol, historicalVolatility);
            }

            // Check if volatility meets threshold
            if (historicalVolatility == null) {
                log.warn("[{}] Could not calculate historical volatility, allowing trade", symbol);
                return true; // Fail-open: allow trade if calculation fails
            }

            boolean passes = historicalVolatility >= filter.getMinHistoricalVolatility();
            if (!passes) {
                log.info("[{}] Historical volatility {}% is below minimum {}%, skipping symbol",
                        symbol, historicalVolatility, filter.getMinHistoricalVolatility());
            }
            return passes;

        } catch (Exception e) {
            log.error("[{}] Error checking historical volatility: {}, allowing trade", symbol, e.getMessage());
            return true; // Fail-open: allow trade on error
        }
    }
}
