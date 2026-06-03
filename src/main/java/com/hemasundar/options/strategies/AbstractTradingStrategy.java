package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.cache.IVRankCache;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.VolatilityCalculator;
import org.apache.commons.collections4.CollectionUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.hemasundar.utils.PerformanceLogger;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractTradingStrategy implements TradingStrategy {

    @Getter
    private final StrategyType strategyType;

    protected final FinnHubAPIs finnHubAPIs;
    protected final ThinkOrSwinAPIs thinkOrSwinAPIs;
    protected final VolatilityCalculator volatilityCalculator;

    /**
     * Optional Supabase service for IV Rank lookups.
     * Absent when Supabase is disabled — IV Rank filter is skipped (fail-open).
     */
    protected final Optional<SupabaseService> supabaseService;

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();

        // ── Track C: Fire HV + IV Rank in parallel ──
        // Both are independent network I/O calls (~270-330ms each).
        // Starting them concurrently saves one round-trip per symbol.
        CompletableFuture<Boolean> hvFuture = CompletableFuture
                .supplyAsync(() -> checkHistoricalVolatility(symbol, filter));
        CompletableFuture<Double> ivRankFuture = CompletableFuture
                .supplyAsync(() -> resolveIVRank(symbol));

        // ── Volatility Filter ──
        boolean passesHV = hvFuture.join();
        if (!passesHV) {
            ivRankFuture.cancel(true); // best-effort; IV rank result is not needed
            FilterLogStore.getInstance().logFilter(strategyName, symbol, "Historical Volatility", 1, 0);
            return Collections.emptyList();
        }
        FilterLogStore.getInstance().logFilter(strategyName, symbol, "Historical Volatility", 1, 1);

        // ── IV Rank Filter ──
        Double ivRank = ivRankFuture.join();
        if (!filter.passesIVRank(ivRank)) {
            log.info("[{}] IV Rank {:.1f}% outside configured bounds [min={}, max={}], skipping symbol",
                    symbol, ivRank, filter.getMinIVRank(), filter.getMaxIVRank());
            FilterLogStore.getInstance().logFilter(strategyName, symbol, FilterStage.IV_RANK_FILTER.displayName(), 1, 0);
            return Collections.emptyList();
        }
        if (filter.getMinIVRank() != null || filter.getMaxIVRank() != null) {
            FilterLogStore.getInstance().logFilter(strategyName, symbol, FilterStage.IV_RANK_FILTER.displayName(), 1, 1);
        }

        int targetDTE = filter.getTargetDTE() != null ? filter.getTargetDTE() : 0;
        int minDTE = filter.getMinDTE() != null ? filter.getMinDTE() : 0;
        int maxDTE = filter.getMaxDTE() != null ? filter.getMaxDTE() : Integer.MAX_VALUE;

        List<String> expiryDates = chain.getExpiryDatesInRange(targetDTE, minDTE, maxDTE);

        // ── DTE Filter ──
        int totalExpiries = 0;
        if (chain.getCallExpDateMap() != null) totalExpiries = chain.getCallExpDateMap().size();
        else if (chain.getPutExpDateMap() != null) totalExpiries = chain.getPutExpDateMap().size();
        FilterLogStore.getInstance().logFilter(strategyName, symbol, "DTE Filter", totalExpiries, expiryDates.size());

        if (expiryDates.isEmpty()) {
            log.debug("[{}] No expiry dates found in range [{}-{}]",
                    symbol, minDTE, maxDTE);
            return new ArrayList<>();
        }

        log.info("[{}] Processing {} expiry dates: {}", symbol, expiryDates.size(), expiryDates);

        List<TradeSetup> allTrades = new ArrayList<>();

        for (String expiryDate : expiryDates) {
            // Check earnings for this expiry if not ignored
            if (!filter.isIgnoreEarnings()) {
                try {
                    EarningsCalendarResponse earningsResponse = finnHubAPIs.getEarningsByTicker(
                            symbol, LocalDate.parse(expiryDate));
                    if (CollectionUtils.isNotEmpty(earningsResponse.getEarningsCalendar())) {
                        log.info("[{}] Skipping expiry {} due to upcoming earnings on {}",
                                symbol, expiryDate,
                                earningsResponse.getEarningsCalendar().get(0).getDate());
                        continue; // Skip this expiry, try next
                    }
                } catch (Exception e) {
                    log.error("[{}] Error checking earnings for {}: {}",
                            symbol, expiryDate, e.getMessage());
                }
            }

            // Find trades for this expiry
            List<TradeSetup> trades = findValidTrades(chain, expiryDate, filter);
            log.info("[{}] Found {} trades for expiry {}", symbol, trades.size(), expiryDate);
            allTrades.addAll(trades);
        }

        log.info("[{}] Total trades found: {}", symbol, allTrades.size());
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

    // ========== COMMON FILTER HELPERS ==========

    /**
     * Common filter for maxLossLimit.
     * Returns a predicate that checks if maxLoss <= maxLossLimit (if configured).
     * 
     * @param filter           The strategy filter containing maxLossLimit
     *                         configuration
     * @param maxLossExtractor Function to extract maxLoss from candidate
     * @return Predicate that validates maxLoss against configured limit
     */
    protected <T> java.util.function.Predicate<T> commonMaxLossFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> maxLossExtractor) {
        return candidate -> {
            double maxLoss = maxLossExtractor.apply(candidate);
            return filter.passesMaxLoss(maxLoss);
        };
    }

    /**
     * Common filter for minReturnOnRisk (typically for credit strategies).
     * Returns a predicate that checks if return on risk meets minimum threshold.
     * 
     * @param filter           The strategy filter containing minReturnOnRisk
     *                         configuration
     * @param profitExtractor  Function to extract profit/credit from candidate
     * @param maxLossExtractor Function to extract maxLoss from candidate
     * @return Predicate that validates return on risk against configured minimum
     */
    protected <T> java.util.function.Predicate<T> commonMinReturnOnRiskFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> profitExtractor,
            java.util.function.Function<T, Double> maxLossExtractor) {
        return candidate -> {
            double profit = profitExtractor.apply(candidate);
            double maxLoss = maxLossExtractor.apply(candidate);
            return filter.passesMinReturnOnRisk(profit, maxLoss);
        };
    }

    /**
     * Common filter for maxNetExtrinsicValueToPricePercentage.
     * Returns a predicate that checks if the net extrinsic value relative to the
     * stock price is within the max limit.
     */
    protected java.util.function.Predicate<TradeSetup> commonMaxNetExtrinsicValueToPricePercentageFilter(
            OptionsStrategyFilter filter) {
        return tradeSetup -> filter.passesMaxNetExtrinsicValueToPricePercentage(
                tradeSetup.getAnulizedNetExtrinsicValueToCapitalPercentage());
    }

    /**
     * Common filter for minNetExtrinsicValueToPricePercentage.
     * Returns a predicate that checks if the net extrinsic value relative to the
     * stock price is at least the min limit.
     */
    protected java.util.function.Predicate<TradeSetup> commonMinNetExtrinsicValueToPricePercentageFilter(
            OptionsStrategyFilter filter) {
        return tradeSetup -> filter.passesMinNetExtrinsicValueToPricePercentage(
                tradeSetup.getAnulizedNetExtrinsicValueToCapitalPercentage());
    }

    /**
     * Common filter for maxTotalDebit.
     */
    protected <T> java.util.function.Predicate<T> commonMaxTotalDebitFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> debitExtractor) {
        return candidate -> filter.passesDebitLimit(debitExtractor.apply(candidate));
    }

    /**
     * Common filter for maxTotalCredit.
     */
    protected <T> java.util.function.Predicate<T> commonMaxTotalCreditFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> creditExtractor) {
        return candidate -> filter.passesCreditLimit(creditExtractor.apply(candidate));
    }

    /**
     * Common filter for minTotalCredit.
     */
    protected <T> java.util.function.Predicate<T> commonMinTotalCreditFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> creditExtractor) {
        return candidate -> filter.passesMinCredit(creditExtractor.apply(candidate));
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
                // Point 5: time the price history API call for HV calculation
                long t0 = System.currentTimeMillis();
                PriceHistoryResponse priceHistory = thinkOrSwinAPIs.getYearlyPriceHistory(symbol, 1);
                PerformanceLogger.log("getYearlyPriceHistory (HV)", symbol, System.currentTimeMillis() - t0);
                historicalVolatility = volatilityCalculator.calculateAnnualizedVolatility(priceHistory);

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

    /**
     * Resolves the IV Rank for a symbol, using the per-execution {@link IVRankCache}.
     *
     * <p>If the value has already been fetched this run it is returned from cache.
     * If Supabase is disabled, or if an error occurs, {@code null} is returned (fail-open).
     *
     * @param symbol stock ticker
     * @return IV Rank in [0, 100], or {@code null} if unavailable
     */
    protected Double resolveIVRank(String symbol) {
        IVRankCache cache = IVRankCache.getInstance();
        if (cache.isCached(symbol)) {
            return cache.get(symbol).orElse(null);
        }
        if (supabaseService.isEmpty()) {
            cache.put(symbol, null);
            return null;
        }
        try {
            // Point 6: time the Supabase IV Rank lookup
            long t0 = System.currentTimeMillis();
            Double rank = supabaseService.get().getIVRank(symbol);
            PerformanceLogger.log("resolveIVRank (Supabase)", symbol, System.currentTimeMillis() - t0);
            cache.put(symbol, rank);
            if (rank != null) {
                log.debug("[{}] Fetched IV Rank: {:.1f}%", symbol, rank);
            } else {
                log.debug("[{}] IV Rank unavailable (insufficient data)", symbol);
            }
            return rank;
        } catch (Exception e) {
            log.error("[{}] Error fetching IV Rank: {}, allowing trade (fail-open)", symbol, e.getMessage());
            cache.put(symbol, null);
            return null;
        }
    }
}
