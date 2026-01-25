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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Log4j2
public class BrokenWingButterflyStrategy extends AbstractTradingStrategy {

    public BrokenWingButterflyStrategy() {
        super(StrategyType.BULLISH_BROKEN_WING_BUTTERFLY);
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

        // Use streams to generate candidates and filter
        List<TradeSetup> trades = generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice())
                .filter(deltaFilter(leg1Filter, leg2Filter, leg3Filter))
                .filter(debitFilter(filter))
                .filter(maxLossFilter(filter))
                .map(this::buildTradeSetup)
                .toList();

        log.debug("[BWB] Found {} valid trades", trades.size());
        return trades;
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
     * Combined delta filter for all three legs.
     */
    private Predicate<BWBCandidate> deltaFilter(LegFilter leg1Filter, LegFilter leg2Filter, LegFilter leg3Filter) {
        return candidate -> {
            // Leg 1: Must pass minDelta
            if (leg1Filter != null && !leg1Filter.passesMinDelta(candidate.leg1().getAbsDelta())) {
                log.trace("[BWB] Leg1 @ {} REJECTED - Delta {} < minDelta",
                        candidate.leg1().getStrikePrice(), candidate.leg1().getAbsDelta());
                return false;
            }
            // Leg 2: Must pass maxDelta
            if (leg2Filter != null && !leg2Filter.passesMaxDelta(candidate.leg2().getAbsDelta())) {
                log.trace("[BWB] Leg2 @ {} REJECTED - Delta {} > maxDelta",
                        candidate.leg2().getStrikePrice(), candidate.leg2().getAbsDelta());
                return false;
            }
            // Leg 3: Must pass both min and max delta
            if (leg3Filter != null && !leg3Filter.passesDeltaFilter(candidate.leg3().getAbsDelta())) {
                log.trace("[BWB] Leg3 @ {} REJECTED - Delta {} failed filter",
                        candidate.leg3().getStrikePrice(), candidate.leg3().getAbsDelta());
                return false;
            }
            return true;
        };
    }

    /**
     * Max total debit filter.
     */
    private Predicate<BWBCandidate> debitFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            if (filter.getMaxTotalDebit() > 0 && candidate.totalDebit() > filter.getMaxTotalDebit()) {
                log.trace("[BWB] Combo {} REJECTED - Debit ${} > MaxDebit ${}",
                        candidate.strikeCombo(), String.format("%.2f", candidate.totalDebit()),
                        filter.getMaxTotalDebit());
                return false;
            }
            return true;
        };
    }

    /**
     * Max loss limit filter.
     */
    private Predicate<BWBCandidate> maxLossFilter(OptionsStrategyFilter filter) {
        return candidate -> {
            if (candidate.maxLoss() > filter.getMaxLossLimit()) {
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

        String strikeCombo() {
            return String.format("%.0f/%.0f/%.0f",
                    leg1.getStrikePrice(), leg2.getStrikePrice(), leg3.getStrikePrice());
        }
    }
}
