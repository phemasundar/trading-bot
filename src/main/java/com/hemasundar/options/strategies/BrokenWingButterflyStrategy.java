package com.hemasundar.options.strategies;

import com.hemasundar.options.models.BrokenWingButterfly;
import com.hemasundar.options.models.BrokenWingButterflyFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.OptionData;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.utils.VolatilityCalculator;

@Log4j2
public class BrokenWingButterflyStrategy extends AbstractTradingStrategy {

    public BrokenWingButterflyStrategy(StrategyType strategyType,
                                      FinnHubAPIs finnHubAPIs,
                                      ThinkOrSwinAPIs thinkOrSwinAPIs,
                                      VolatilityCalculator volatilityCalculator) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        log.trace("[BWB] Starting findValidTrades for expiry: {}", expiryDate);

        Map<String, List<OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty()) {
            log.trace("[BWB] No CALL options found for expiry: {}", expiryDate);
            return new ArrayList<>();
        }

        log.trace("[BWB] Found {} strike prices with CALL options", callMap.size());

        // Extract leg filters
        LegFilter leg1Filter = null, leg2Filter = null, leg3Filter = null;
        if (filter instanceof BrokenWingButterflyFilter bwbFilter) {
            leg1Filter = bwbFilter.getLeg1Long();
            leg2Filter = bwbFilter.getLeg2Short();
            leg3Filter = bwbFilter.getLeg3Long();
            log.trace("[BWB] Filter config - MaxLossLimit: {}, MaxTotalDebit: {}",
                    filter.getMaxLossLimit(), filter.getMaxTotalDebit());
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        log.trace("[BWB] Current price: {}, Available strikes: {}", chain.getUnderlyingPrice(), sortedStrikes);

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();
        FilterLogStore filterLog = FilterLogStore.getInstance();

        List<BWBCandidate> candidates = generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        filterLog.logFilter(strategyName, symbol, "Generated Candidates (expiry " + expiryDate + ")", candidates.size(), candidates.size());

        List<BWBCandidate> afterDelta = candidates.stream().filter(deltaFilter(leg1Filter, leg2Filter, leg3Filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Delta Filter", candidates.size(), afterDelta.size());

        List<BWBCandidate> afterDefaultDebit = afterDelta.stream().filter(defaultDebitFilter()).toList();
        filterLog.logFilter(strategyName, symbol, "Default Debit Filter", afterDelta.size(), afterDefaultDebit.size());

        List<BWBCandidate> afterWingWidth = afterDefaultDebit.stream().filter(wingWidthRatioFilter()).toList();
        filterLog.logFilter(strategyName, symbol, "Wing Width Ratio Filter", afterDefaultDebit.size(), afterWingWidth.size());

        List<BWBCandidate> afterDebitVsPrice = afterWingWidth.stream().filter(debitVsPriceFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Debit vs Price Filter", afterWingWidth.size(), afterDebitVsPrice.size());

        List<BWBCandidate> afterDebit = afterDebitVsPrice.stream().filter(debitFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Debit Limit Filter", afterDebitVsPrice.size(), afterDebit.size());

        List<BWBCandidate> afterCredit = afterDebit.stream().filter(creditFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Credit Filter", afterDebit.size(), afterCredit.size());

        List<BWBCandidate> afterMaxLoss = afterCredit.stream().filter(maxLossFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Max Loss Filter", afterCredit.size(), afterMaxLoss.size());

        List<BWBCandidate> afterRor = afterMaxLoss.stream()
                .filter(commonMinReturnOnRiskFilter(filter, BWBCandidate::maxProfit, BWBCandidate::maxLoss)).toList();
        filterLog.logFilter(strategyName, symbol, "Min Return on Risk Filter", afterMaxLoss.size(), afterRor.size());

        List<BWBCandidate> afterUpperBE = afterRor.stream()
                .filter(upperBreakevenFilter(filter, callMap, sortedStrikes)).toList();
        filterLog.logFilter(strategyName, symbol, "Upper Breakeven Filter", afterRor.size(), afterUpperBE.size());

        List<TradeSetup> mapped = afterUpperBE.stream().map(this::buildTradeSetup).toList();

        List<TradeSetup> afterMaxExtrinsic = mapped.stream().filter(commonMaxNetExtrinsicValueToPricePercentageFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Max Extrinsic Value Filter", mapped.size(), afterMaxExtrinsic.size());

        List<TradeSetup> afterMinExtrinsic = afterMaxExtrinsic.stream().filter(commonMinNetExtrinsicValueToPricePercentageFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Min Extrinsic Value Filter", afterMaxExtrinsic.size(), afterMinExtrinsic.size());

        List<TradeSetup> result = afterMinExtrinsic.stream()
                .filter(trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .toList();
        filterLog.logFilter(strategyName, symbol, "Break-Even Filter", afterMinExtrinsic.size(), result.size());

        log.debug("[BWB] Found {} valid trades", result.size());
        return result;
    }

    /**
     * Generates all valid 3-leg combinations as a stream of BWBCandidate records.
     */
    private Stream<BWBCandidate> generateCandidates(Map<String, List<OptionData>> callMap,
            List<Double> strikes, double currentPrice) {
        return IntStream.range(0, strikes.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, strikes.size()).boxed()
                        .flatMap(j -> IntStream.range(j + 1, strikes.size()).boxed()
                                .map(k -> createCandidate(callMap, strikes, i, j, k, currentPrice))))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Creates a BWBCandidate if all legs have valid option data.
     */
    private Optional<BWBCandidate> createCandidate(Map<String, List<OptionData>> callMap,
            List<Double> strikes, int i, int j, int k, double currentPrice) {
        OptionData leg1 = getOption(callMap, strikes.get(i));
        OptionData leg2 = getOption(callMap, strikes.get(j));
        OptionData leg3 = getOption(callMap, strikes.get(k));

        if (leg1 == null || leg2 == null || leg3 == null) {
            return Optional.empty();
        }
        return Optional.of(new BWBCandidate(leg1, leg2, leg3, currentPrice));
    }

    private OptionData getOption(Map<String, List<OptionData>> map, Double strike) {
        List<OptionData> options = map.get(String.valueOf(strike));
        return CollectionUtils.isEmpty(options) ? null : options.get(0);
    }

    // ========== FILTER PREDICATES ==========

    /**
     * Combined filter for all three legs using comprehensive LegFilter validation.
     * Now validates ALL filter fields: delta, premium, volume, open interest.
     */
    private Predicate<BWBCandidate> deltaFilter(LegFilter leg1Filter, LegFilter leg2Filter, LegFilter leg3Filter) {
        return candidate -> {
            // Comprehensive validation - checks ALL filter fields
            if (!LegFilter.passes(leg1Filter, candidate.leg1())) {
                return false;
            }
            if (!LegFilter.passes(leg2Filter, candidate.leg2())) {
                return false;
            }
            if (!LegFilter.passes(leg3Filter, candidate.leg3())) {
                return false;
            }
            return true;
        };
    }

    /**
     * Mandatory requirement: Debit limit should be less than the price of Leg 1
     * (Long Call).
     * This ensures we aren't paying more for the spread than the naked long call.
     */
    private Predicate<BWBCandidate> defaultDebitFilter() {
        return candidate -> {
            double leg1Cost = candidate.leg1().getAsk() * 100;
            // Allow small buffer or strict inequality? User said "debit amount should be
            // less than".
            if (candidate.totalDebit() >= leg1Cost / 2) {
                log.trace("[BWB] Rejected by Default Debit validation: Debit {} >= Leg1 Cost {}",
                        String.format("%.2f", candidate.totalDebit()), String.format("%.2f", leg1Cost));
                return false;
            }
            return true;
        };
    }

    /**
     * Mandatory requirement: Upper Wing (Leg 2 to Leg 3) should not be more than 2x
     * the width of the Lower Wing (Leg 1 to Leg 2).
     */
    private Predicate<BWBCandidate> wingWidthRatioFilter() {
        return candidate -> {
            // Upper Wing = Leg 3 Strike - Leg 2 Strike
            // Lower Wing = Leg 2 Strike - Leg 1 Strike
            double upperWing = candidate.upperWingWidth();
            double lowerWing = candidate.lowerWingWidth();

            if (upperWing > (2 * lowerWing)) {
                log.trace("[BWB] Rejected by Wing Width Ratio: Upper ({}) > 2x Lower ({})",
                        String.format("%.2f", upperWing), String.format("%.2f", lowerWing));
                return false;
            }
            return true;
        };
    }

    /**
     * Optional requirement: The total debit should be less than the underlying
     * price.
     * This ensures the cost of the trade is not excessive relative to the stock
     * price.
     * Only applied if validateDebitVsPrice is true in the filter config.
     */
    /**
     * Optional requirement: The total debit should be less than the underlying
     * price * ratio.
     * This ensures the cost of the trade is not excessive relative to the stock
     * price.
     * Only applied if priceVsMaxDebitRatio is set in the filter config.
     */
    private Predicate<BWBCandidate> debitVsPriceFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            if (filter.getPriceVsMaxDebitRatio() == null) {
                return true;
            }

            double ratio = filter.getPriceVsMaxDebitRatio();
            // Note: totalDebit() is the full contract debit (e.g. $50).
            // currentPrice() is the price per share (e.g. $100).
            // The requirement is Total Debit < Underlying Price * Ratio.
            double totalDebit = candidate.totalDebit();
            double underlyingPrice = candidate.currentPrice();
            double maxAllowedDebit = underlyingPrice * ratio;

            if (totalDebit >= maxAllowedDebit) {
                log.trace("[BWB] Rejected by Price vs Debit Ratio: Debit {} >= Max Allowed ({}) [Price {} * Ratio {}]",
                        String.format("%.2f", totalDebit), String.format("%.2f", maxAllowedDebit),
                        String.format("%.2f", underlyingPrice), ratio);
                return false;
            }
            return true;
        };
    }

    /**
     * Max total debit filter using OptionsStrategyFilter helper.
     */
    private Predicate<BWBCandidate> debitFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            if (!filter.passesDebitLimit(candidate.totalDebit())) {
                log.debug("[BWB] Rejected by Debit Limit: {} > Max={}",
                        String.format("%.2f", candidate.totalDebit()), filter.getMaxTotalDebit());
                return false;
            }
            return true;
        };
    }

    private Predicate<BWBCandidate> creditFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            // Net Credit is -TotalDebit
            double netCredit = -candidate.totalDebit();

            // Check max credit limit (if set)
            if (!filter.passesCreditLimit(netCredit)) {
                log.debug("[BWB] Rejected by Max Credit Limit: {} > Max={}",
                        String.format("%.2f", netCredit), filter.getMaxTotalCredit());
                return false;
            }

            // Check min credit limit (if set)
            if (!filter.passesMinCredit(netCredit)) {
                log.debug("[BWB] Rejected by Min Credit Limit: {} < Min={}",
                        String.format("%.2f", netCredit), filter.getMinTotalCredit());
                return false;
            }

            return true;
        };
    }

    /**
     * Max loss limit filter using OptionsStrategyFilter helper.
     */
    private Predicate<BWBCandidate> maxLossFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            if (!filter.passesMaxLoss(candidate.maxLoss())) {
                log.trace("[BWB] Combo {} REJECTED - MaxLoss ${} > Limit ${}",
                        candidate.strikeCombo(), String.format("%.2f", candidate.maxLoss()),
                        filter.getMaxLossLimit());
                return false;
            }
            return true;
        };
    }

    // ========== TRADE BUILDER ==========

    /**
     * Filters candidates based on the delta of the option at the Upper Breakeven
     * Price.
     * Upper BE = Short Strike + (Lower Wing - Net Debit)
     * Checks if delta of nearest strike <= maxUpperBreakevenDelta
     */
    private Predicate<BWBCandidate> upperBreakevenFilter(OptionsStrategyFilter filter,
            Map<String, List<OptionData>> callMap,
            List<Double> sortedStrikes) {
        if (filter.getMaxUpperBreakevenDelta() == null) {
            return c -> true;
        }

        double maxDelta = filter.getMaxUpperBreakevenDelta();

        return candidate -> {
            double upperBreakeven = candidate.upperBreakevenPrice();

            // Find nearest strike
            Double nearestStrike = null;
            double minDiff = Double.MAX_VALUE;

            for (Double strike : sortedStrikes) {
                double diff = Math.abs(strike - upperBreakeven);
                if (diff < minDiff) {
                    minDiff = diff;
                    nearestStrike = strike;
                }
            }

            if (nearestStrike == null)
                return false;

            OptionData optionAtBE = getOption(callMap, nearestStrike);
            if (optionAtBE == null) {
                return false;
            }

            // Validate delta (use absolute value to be safe, though calls are positive)
            double currentDelta = Math.abs(optionAtBE.getAbsDelta());
            // Note: OptionData has getAbsDelta() helper, use it if available or
            // Math.abs(getDelta())
            // Let's use getAbsDelta() directly if available from OptionData, otherwise
            // abs(getDelta)
            // OptionData in OptionChainResponse has getAbsDelta() per previous steps.

            if (currentDelta > maxDelta) {
                log.trace("[BWB] Rejected by Upper BE Delta: BE=${}, NearestStrike=${}, Delta={} > Max={}",
                        String.format("%.2f", upperBreakeven), nearestStrike,
                        String.format("%.2f", currentDelta), maxDelta);
                return false;
            }

            return true;
        };
    }

    // ========== TRADE BUILDER ==========

    /**
     * Converts a valid candidate to a BrokenWingButterfly trade setup.
     */
    private TradeSetup buildTradeSetup(BWBCandidate c) {
        log.trace("[BWB] Combo {} ACCEPTED - Debit: ${}, MaxLoss: ${}, RoR: {}%",
                c.strikeCombo(), String.format("%.2f", c.totalDebit()),
                String.format("%.2f", c.maxLoss()), String.format("%.1f", c.returnOnRisk()));

        return BrokenWingButterfly.builder()
                .leg1LongCall(c.leg1())
                .leg2ShortCalls(c.leg2())
                .leg3LongCall(c.leg3())
                .lowerWingWidth(c.lowerWingWidth())
                .upperWingWidth(c.upperWingWidth())
                .totalDebit(c.totalDebit())
                .maxLossUpside(c.maxLossUpside())
                .maxLossDownside(c.maxLossDownside())
                .maxLoss(c.maxLoss())
                .returnOnRisk(c.returnOnRisk())
                .currentPrice(c.currentPrice())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .upperBreakEvenPrice(c.upperBreakevenPrice())
                .upperBreakEvenPercentage(c.upperBreakevenPercentage())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    /**
     * Immutable record holding a BWB trade candidate with all derived calculations.
     */
    private record BWBCandidate(OptionData leg1, OptionData leg2, OptionData leg3, double currentPrice) {

        double lowerWingWidth() {
            return (leg2.getStrikePrice() - leg1.getStrikePrice()) * 100;
        }

        double upperWingWidth() {
            return (leg3.getStrikePrice() - leg2.getStrikePrice()) * 100;
        }

        double totalDebit() {
            return (leg1.getAsk() + leg3.getAsk() - (leg2.getBid() * 2)) * 100;
        }

        double maxLossUpside() {
            return (upperWingWidth() - lowerWingWidth()) + totalDebit();
        }

        double maxLossDownside() {
            return totalDebit();
        }

        double maxLoss() {
            return Math.max(maxLossUpside(), maxLossDownside());
        }

        double maxProfit() {
            return lowerWingWidth() - totalDebit();
        }

        double returnOnRisk() {
            return (maxProfit() > 0 && maxLoss() > 0) ? (maxProfit() / maxLoss()) * 100 : 0;
        }

        double breakEvenPrice() {
            return leg1.getStrikePrice() + (totalDebit() / 100);
        }

        double breakEvenPercentage() {
            return ((breakEvenPrice() - currentPrice) / currentPrice) * 100;
        }

        double upperBreakevenPrice() {
            return leg2.getStrikePrice() + ((lowerWingWidth() - totalDebit()) / 100.0);
        }

        double upperBreakevenPercentage() {
            return ((upperBreakevenPrice() - currentPrice) / currentPrice) * 100;
        }

        String strikeCombo() {
            return String.format("%.0f/%.0f/%.0f",
                    leg1.getStrikePrice(), leg2.getStrikePrice(), leg3.getStrikePrice());
        }
    }
}
