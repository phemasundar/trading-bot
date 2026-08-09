package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.cache.IVRankCache;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.services.EarningsDataResolver;
import com.hemasundar.technical.MathExpressionEvaluator;
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

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractTradingStrategy implements TradingStrategy {

    @Getter
    private final StrategyType strategyType;

    protected final FinnHubAPIs finnHubAPIs;
    protected final ThinkOrSwimAPIs ThinkOrSwimAPIs;

    /**
     * Optional Supabase service for IV Rank lookups.
     * Absent when Supabase is disabled — IV Rank filter is skipped (fail-open).
     */
    protected final Optional<SupabaseService> supabaseService;

    @Override
    public List<TradeSetup> findTrades(OptionChainResponse chain, OptionsStrategyFilter filter) {
        String strategyName = getStrategyName(filter);
        String symbol = chain.getSymbol();

        // ── Track C: Fire IV Rank ──
        CompletableFuture<Double> ivRankFuture = CompletableFuture
                .supplyAsync(() -> resolveIVRank(symbol));

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
            // Apply new Earnings Filters
            if (CollectionUtils.isNotEmpty(filter.getEarningsFilterExpressions())) {
                java.util.Map<String, Double> earningsVariables = EarningsDataResolver.resolve(symbol, expiryDate, finnHubAPIs);
                boolean passesEarnings = MathExpressionEvaluator.evaluateAll(filter.getEarningsFilterExpressions(), earningsVariables::get);
                
                FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.EARNINGS_FILTER.displayName(), 1, passesEarnings ? 1 : 0);
                
                if (!passesEarnings) {
                    log.info("[{}] Skipping expiry {} due to earnings filter condition mismatch", symbol, expiryDate);
                    continue;
                }
            } else {
                FilterLogStore.getInstance().logFilter(strategyName, symbol, expiryDate, FilterStage.EARNINGS_FILTER.displayName(), 1, 1);
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
     * Uses the unique strategyId if available in the filter, otherwise falls back to StrategyType display name.
     */
    public String getStrategyName(OptionsStrategyFilter filter) {
        if (filter != null && org.apache.commons.lang3.StringUtils.isNotBlank(filter.getStrategyId())) {
            return filter.getStrategyId();
        }
        return getStrategyName();
    }

    /**
     * Returns the generic display name for this strategy (based on StrategyType).
     * Used as a fallback and by external callers that don't have the filter context.
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
     * Common filter for minReturnOnRiskCAGR (annualized Return-on-Risk).
     * Returns a predicate that checks if the annualized RoR meets the minimum
     * CAGR threshold configured in the filter.
     *
     * <p>CAGR formula: {@code ((profit / maxLoss + 1)^(365.0 / dte) - 1) * 100}
     *
     * @param filter           The strategy filter containing minReturnOnRiskCAGR
     * @param profitExtractor  Function to extract net profit/credit from candidate
     * @param maxLossExtractor Function to extract maxLoss from candidate
     * @param dteExtractor     Function to extract days-to-expiration from candidate
     * @return Predicate that validates annualized RoR against configured minimum
     */
    protected <T> java.util.function.Predicate<T> commonMinReturnOnRiskCAGRFilter(
            OptionsStrategyFilter filter,
            java.util.function.Function<T, Double> profitExtractor,
            java.util.function.Function<T, Double> maxLossExtractor,
            java.util.function.Function<T, Integer> dteExtractor) {
        return candidate -> {
            double profit = profitExtractor.apply(candidate);
            double maxLoss = maxLossExtractor.apply(candidate);
            int dte = dteExtractor.apply(candidate);
            return filter.passesMinReturnOnRiskCAGR(profit, maxLoss, dte);
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
                tradeSetup.getAnnualizedNetExtrinsicValueToCapitalPercentage());
    }

    /**
     * Common filter for minNetExtrinsicValueToPricePercentage.
     * Returns a predicate that checks if the net extrinsic value relative to the
     * stock price is at least the min limit.
     */
    protected java.util.function.Predicate<TradeSetup> commonMinNetExtrinsicValueToPricePercentageFilter(
            OptionsStrategyFilter filter) {
        return tradeSetup -> filter.passesMinNetExtrinsicValueToPricePercentage(
                tradeSetup.getAnnualizedNetExtrinsicValueToCapitalPercentage());
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
            Double rank = supabaseService.get().getIVRank(symbol);
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
