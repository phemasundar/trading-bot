package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.ShortStrangle;
import com.hemasundar.options.models.ShortStrangleFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Strategy implementation for a Short Strangle.
 *
 * <p>The strategy sells one OTM put option and one OTM call option per expiry.
 * It uses {@link ShortStrangleFilter} to independently filter the put and call legs.
 *
 * <p>Risk metrics:
 * <ul>
 *   <li>netCredit  = (put bid + call bid) × 100</li>
 *   <li>maxLoss    = max(putStrike × 100, callStrike × 100) - netCredit (theoretically unlimited on call side)</li>
 *   <li>lowerBreakEven = putStrike − totalPremium</li>
 *   <li>upperBreakEven = callStrike + totalPremium</li>
 *   <li>returnOnRisk = netCredit / maxLoss × 100</li>
 * </ul>
 */
@Log4j2
public class ShortStrangleStrategy extends AbstractTradingStrategy {

    public ShortStrangleStrategy(StrategyType strategyType,
                                 FinnHubAPIs finnHubAPIs,
                                 ThinkOrSwimAPIs ThinkOrSwimAPIs,
                                 Optional<SupabaseService> supabaseService) {
        super(strategyType, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
                                               OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> putMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.PUT, expiryDate);
        Map<String, List<OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (MapUtils.isEmpty(putMap) || MapUtils.isEmpty(callMap)) {
            return new ArrayList<>();
        }

        LegFilter putShortLegFilter = (filter instanceof ShortStrangleFilter strangleFilter) ? strangleFilter.getPutShortLeg() : null;
        LegFilter callShortLegFilter = (filter instanceof ShortStrangleFilter strangleFilter) ? strangleFilter.getCallShortLeg() : null;

        // Flatten all put options for this expiry into typed candidate records
        List<OptionData> putCandidates = putMap.values().stream()
                .filter(list -> !CollectionUtils.isEmpty(list))
                .map(list -> list.get(0))
                .toList();
                
        // Flatten all call options for this expiry into typed candidate records
        List<OptionData> callCandidates = callMap.values().stream()
                .filter(list -> !CollectionUtils.isEmpty(list))
                .map(list -> list.get(0))
                .toList();

        String strategyName = getStrategyName(filter);
        String symbol = chain.getSymbol();

        // ── Phase 1: Candidate-level filters (before building TradeSetup) ──────
        FilterPipeline<OptionData> putPipeline = FilterPipeline
                .<OptionData>forContext(strategyName, symbol, expiryDate);
        if (hasLegacyDelta(putShortLegFilter)) {
            putPipeline.step("Put " + FilterStage.DELTA_FILTER.displayName(), deltaFilter(putShortLegFilter));
        }
        if (hasLegacyPremium(putShortLegFilter)) {
            putPipeline.step("Put " + FilterStage.LEG_PREMIUM_FILTER.displayName(), legPremiumFilter(putShortLegFilter));
        }
        if (hasLegacyVolume(putShortLegFilter)) {
            putPipeline.step("Put " + FilterStage.VOLUME_FILTER.displayName(), volumeFilter(putShortLegFilter));
        }
        if (hasLegacyOpenInterest(putShortLegFilter)) {
            putPipeline.step("Put " + FilterStage.OPEN_INTEREST_FILTER.displayName(), openInterestFilter(putShortLegFilter));
        }
        if (hasLegacyVolatility(putShortLegFilter)) {
            putPipeline.step("Put " + FilterStage.LEG_VOLATILITY_FILTER.displayName(), volatilityFilter(putShortLegFilter));
        }
        applyLegFilterExpressions(putPipeline, putShortLegFilter, "putShortLeg", p -> p);
        List<OptionData> survivedPuts = putPipeline.run(putCandidates);
                
        FilterPipeline<OptionData> callPipeline = FilterPipeline
                .<OptionData>forContext(strategyName, symbol, expiryDate);
        if (hasLegacyDelta(callShortLegFilter)) {
            callPipeline.step("Call " + FilterStage.DELTA_FILTER.displayName(), deltaFilter(callShortLegFilter));
        }
        if (hasLegacyPremium(callShortLegFilter)) {
            callPipeline.step("Call " + FilterStage.LEG_PREMIUM_FILTER.displayName(), legPremiumFilter(callShortLegFilter));
        }
        if (hasLegacyVolume(callShortLegFilter)) {
            callPipeline.step("Call " + FilterStage.VOLUME_FILTER.displayName(), volumeFilter(callShortLegFilter));
        }
        if (hasLegacyOpenInterest(callShortLegFilter)) {
            callPipeline.step("Call " + FilterStage.OPEN_INTEREST_FILTER.displayName(), openInterestFilter(callShortLegFilter));
        }
        if (hasLegacyVolatility(callShortLegFilter)) {
            callPipeline.step("Call " + FilterStage.LEG_VOLATILITY_FILTER.displayName(), volatilityFilter(callShortLegFilter));
        }
        applyLegFilterExpressions(callPipeline, callShortLegFilter, "callShortLeg", c -> c);
        List<OptionData> survivedCalls = callPipeline.run(callCandidates);

        List<ShortStrangleCandidate> combinations = new ArrayList<>();
        double currentPrice = chain.getUnderlyingPrice();
        
        for (OptionData put : survivedPuts) {
            for (OptionData call : survivedCalls) {
                // Ensure no overlap (put strike must be < call strike for a true strangle)
                if (put.getStrikePrice() >= call.getStrikePrice()) {
                    continue;
                }
                combinations.add(new ShortStrangleCandidate(put, call, currentPrice));
            }
        }

        FilterLogStore.getInstance().logFilter(
                strategyName, symbol, expiryDate,
                FilterStage.GENERATED_CANDIDATES.displayName(),
                combinations.size(), combinations.size());
        
        FilterPipeline<ShortStrangleCandidate> combinationPipeline = FilterPipeline
                .<ShortStrangleCandidate>forContext(strategyName, symbol, expiryDate)
                .step("NET_CREDIT > 0", creditFilter());

        if (filter.getMaxTotalCredit() != null) {
            combinationPipeline.step(FilterStage.MAX_CREDIT_FILTER, commonMaxTotalCreditFilter(filter, ShortStrangleCandidate::netCredit));
        }
        if (filter.getMinTotalCredit() != null) {
            combinationPipeline.step(FilterStage.MIN_CREDIT_FILTER, commonMinTotalCreditFilter(filter, ShortStrangleCandidate::netCredit));
        }
        if (filter.getMaxLossLimit() != null) {
            combinationPipeline.step(FilterStage.MAX_LOSS_FILTER, commonMaxLossFilter(filter, ShortStrangleCandidate::maxLoss));
        }
        if (filter.getMinReturnOnRisk() != null) {
            combinationPipeline.step(FilterStage.MIN_RETURN_ON_RISK_FILTER, commonMinReturnOnRiskFilter(filter, ShortStrangleCandidate::netCredit, ShortStrangleCandidate::maxLoss));
        }
        if (filter.getMinReturnOnRiskCAGR() != null) {
            combinationPipeline.step(FilterStage.MIN_RETURN_ON_RISK_CAGR_FILTER, commonMinReturnOnRiskCAGRFilter(filter, ShortStrangleCandidate::netCredit, ShortStrangleCandidate::maxLoss, c -> c.shortPut().getDaysToExpiration()));
        }

        List<ShortStrangleCandidate> survivedCombinations = combinationPipeline.run(combinations);

        // ── Map to TradeSetup ─────────────────────────────────────────────────
        List<TradeSetup> mapped = survivedCombinations.stream().map(this::buildTradeSetup).toList();

        // ── Phase 2: TradeSetup-level filters ────────────────────────────────
        FilterPipeline<TradeSetup> tradePipeline = FilterPipeline
                .<TradeSetup>forContext(strategyName, symbol, expiryDate);

        if (filter.getMaxNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter));
        }
        if (filter.getMinNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter));
        }
        if (filter.getMaxBreakEvenPercentage() != null) {
            tradePipeline.step(FilterStage.BREAK_EVEN_FILTER, trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()) && filter.passesMaxBreakEvenPercentage(trade.getUpperBreakEvenPercentage()));
        }

        return applyTradeMathFilterExpressions(tradePipeline, filter).run(mapped);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<OptionData> deltaFilter(LegFilter legFilter) {
        return o -> LegFilter.passesDelta(legFilter, o);
    }

    private Predicate<OptionData> legPremiumFilter(LegFilter legFilter) {
        return o -> LegFilter.passesPremium(legFilter, o);
    }

    private Predicate<OptionData> volumeFilter(LegFilter legFilter) {
        return o -> LegFilter.passesVolume(legFilter, o);
    }

    private Predicate<OptionData> openInterestFilter(LegFilter legFilter) {
        return o -> LegFilter.passesOpenInterest(legFilter, o);
    }

    private Predicate<OptionData> volatilityFilter(LegFilter legFilter) {
        return o -> LegFilter.passesVolatility(legFilter, o);
    }

    private Predicate<ShortStrangleCandidate> creditFilter() {
        return c -> c.netCredit() > 0;
    }

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(ShortStrangleCandidate c) {
        return ShortStrangle.builder()
                .shortPut(c.shortPut())
                .shortCall(c.shortCall())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .currentPrice(c.currentPrice())
                .lowerBreakEven(c.lowerBreakEven())
                .upperBreakEven(c.upperBreakEven())
                .lowerBreakEvenPercentage(c.lowerBreakEvenPercentage())
                .upperBreakEvenPercentage(c.upperBreakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record ShortStrangleCandidate(OptionData shortPut, OptionData shortCall, double currentPrice) {

        /** Credit received = sell at bid × 100. */
        double netCredit() {
            return (shortPut.getBid() + shortCall.getBid()) * 100;
        }

        /**
         * Theoretical max loss is unlimited on the call side.
         * For filtering purposes, we approximate the margin requirement or notional value.
         * maxLoss = max(putStrike, callStrike) * 100 - netCredit
         */
        double maxLoss() {
            double notional = Math.max(shortPut.getStrikePrice(), shortCall.getStrikePrice()) * 100;
            return Math.max(notional - netCredit(), 0);
        }

        double returnOnRisk() {
            return maxLoss() > 0 ? (netCredit() / maxLoss()) * 100 : 0;
        }

        double lowerBreakEven() {
            return shortPut.getStrikePrice() - (shortPut.getBid() + shortCall.getBid());
        }

        double upperBreakEven() {
            return shortCall.getStrikePrice() + (shortPut.getBid() + shortCall.getBid());
        }

        double lowerBreakEvenPercentage() {
            return currentPrice > 0
                    ? ((currentPrice - lowerBreakEven()) / currentPrice) * 100
                    : 0;
        }
        
        double upperBreakEvenPercentage() {
            return currentPrice > 0
                    ? ((upperBreakEven() - currentPrice) / currentPrice) * 100
                    : 0;
        }
    }
}
