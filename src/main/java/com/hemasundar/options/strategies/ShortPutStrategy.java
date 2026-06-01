package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.ShortPut;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.VolatilityCalculator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Strategy implementation for a single-leg Short Put (naked / cash-secured put).
 *
 * <p>The strategy sells one put option per expiry. It uses {@link CreditSpreadFilter}
 * (only the {@code shortLeg} field is relevant — the {@code longLeg} field is ignored).
 *
 * <p>Risk metrics:
 * <ul>
 *   <li>netCredit  = option bid × 100</li>
 *   <li>maxLoss    = (strikePrice − premiumPerShare) × 100</li>
 *   <li>breakEven  = strikePrice − premiumPerShare</li>
 *   <li>returnOnRisk = netCredit / maxLoss × 100</li>
 * </ul>
 */
@Log4j2
public class ShortPutStrategy extends AbstractTradingStrategy {

    public ShortPutStrategy(StrategyType strategyType,
                            FinnHubAPIs finnHubAPIs,
                            ThinkOrSwinAPIs thinkOrSwinAPIs,
                            VolatilityCalculator volatilityCalculator,
                            Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator, supabaseService);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
                                               OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);

        if (putMap == null || putMap.isEmpty()) {
            return new ArrayList<>();
        }

        // Extract short-leg filter (longLeg is not applicable for a naked put)
        LegFilter shortLegFilter = null;
        if (filter instanceof CreditSpreadFilter csFilter) {
            shortLegFilter = csFilter.getShortLeg();
        }

        // Flatten all put options for this expiry into typed candidate records
        List<ShortPutCandidate> candidates = putMap.values().stream()
                .filter(list -> !CollectionUtils.isEmpty(list))
                .map(list -> new ShortPutCandidate(list.get(0), chain.getUnderlyingPrice()))
                .toList();

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();

        FilterLogStore.getInstance().logFilter(
                strategyName, symbol, expiryDate,
                FilterStage.GENERATED_CANDIDATES.displayName(),
                candidates.size(), candidates.size());

        // ── Phase 1: Candidate-level filters (before building TradeSetup) ──────
        List<ShortPutCandidate> survived = FilterPipeline
                .<ShortPutCandidate>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.DELTA_FILTER,               deltaFilter(shortLegFilter))
                .step(FilterStage.LEG_PREMIUM_FILTER,         legPremiumFilter(shortLegFilter))
                .step(FilterStage.VOLUME_FILTER,              volumeFilter(shortLegFilter))
                .step(FilterStage.OPEN_INTEREST_FILTER,       openInterestFilter(shortLegFilter))
                .step(FilterStage.LEG_VOLATILITY_FILTER,      volatilityFilter(shortLegFilter))
                .step(FilterStage.POSITIVE_CREDIT_FILTER,     creditFilter())
                .step(FilterStage.MAX_CREDIT_FILTER,          commonMaxTotalCreditFilter(filter, ShortPutCandidate::netCredit))
                .step(FilterStage.MIN_CREDIT_FILTER,          commonMinTotalCreditFilter(filter, ShortPutCandidate::netCredit))
                .step(FilterStage.MAX_LOSS_FILTER,            commonMaxLossFilter(filter, ShortPutCandidate::maxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_FILTER,  commonMinReturnOnRiskFilter(filter, ShortPutCandidate::netCredit, ShortPutCandidate::maxLoss))
                .run(candidates);

        // ── Map to TradeSetup ─────────────────────────────────────────────────
        List<TradeSetup> mapped = survived.stream().map(this::buildTradeSetup).toList();

        // ── Phase 2: TradeSetup-level filters ────────────────────────────────
        return FilterPipeline
                .<TradeSetup>forContext(strategyName, symbol, expiryDate)
                .step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.BREAK_EVEN_FILTER,          trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .run(mapped);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<ShortPutCandidate> deltaFilter(LegFilter shortFilter) {
        return c -> LegFilter.passesDelta(shortFilter, c.shortLeg());
    }

    private Predicate<ShortPutCandidate> legPremiumFilter(LegFilter shortFilter) {
        return c -> LegFilter.passesPremium(shortFilter, c.shortLeg());
    }

    private Predicate<ShortPutCandidate> volumeFilter(LegFilter shortFilter) {
        return c -> LegFilter.passesVolume(shortFilter, c.shortLeg());
    }

    private Predicate<ShortPutCandidate> openInterestFilter(LegFilter shortFilter) {
        return c -> LegFilter.passesOpenInterest(shortFilter, c.shortLeg());
    }

    private Predicate<ShortPutCandidate> volatilityFilter(LegFilter shortFilter) {
        return c -> LegFilter.passesVolatility(shortFilter, c.shortLeg());
    }

    private Predicate<ShortPutCandidate> creditFilter() {
        return c -> c.netCredit() > 0;
    }

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(ShortPutCandidate c) {
        return ShortPut.builder()
                .shortPut(c.shortLeg())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .currentPrice(c.currentPrice())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record ShortPutCandidate(OptionData shortLeg, double currentPrice) {

        /** Credit received = sell at bid × 100 (conservative estimate). */
        double netCredit() {
            return shortLeg.getBid() * 100;
        }

        /**
         * Maximum loss if the underlying falls to zero at expiration.
         * maxLoss = (strikePrice − premiumPerShare) × 100
         */
        double maxLoss() {
            double loss = (shortLeg.getStrikePrice() - shortLeg.getBid()) * 100;
            return Math.max(loss, 0); // edge case: deep ITM where premium > strike
        }

        double returnOnRisk() {
            return maxLoss() > 0 ? (netCredit() / maxLoss()) * 100 : 0;
        }

        double breakEvenPrice() {
            return shortLeg.getStrikePrice() - shortLeg.getBid();
        }

        double breakEvenPercentage() {
            return currentPrice > 0
                    ? ((currentPrice - breakEvenPrice()) / currentPrice) * 100
                    : 0;
        }
    }
}
