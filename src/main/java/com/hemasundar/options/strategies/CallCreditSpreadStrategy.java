package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CallCreditSpread;
import com.hemasundar.options.models.CreditSpreadFilter;
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
public class CallCreditSpreadStrategy extends AbstractTradingStrategy {

    public CallCreditSpreadStrategy(StrategyType strategyType,
                                   FinnHubAPIs finnHubAPIs,
                                   ThinkOrSwinAPIs thinkOrSwinAPIs,
                                   VolatilityCalculator volatilityCalculator) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, volatilityCalculator);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        Map<String, List<OptionData>> callMap = chain.getOptionDataForASpecificExpiryDate(
                OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty())
            return new ArrayList<>();

        // Extract leg filters
        LegFilter shortLegFilter = null, longLegFilter = null;
        if (filter instanceof CreditSpreadFilter csFilter) {
            shortLegFilter = csFilter.getShortLeg();
            longLegFilter = csFilter.getLongLeg();
        }

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .toList();

        String strategyName = getStrategyName();
        String symbol = chain.getSymbol();
        FilterLogStore filterLog = FilterLogStore.getInstance();

        List<CallSpreadCandidate> candidates = generateCandidates(callMap, sortedStrikes, chain.getUnderlyingPrice()).toList();
        filterLog.logFilter(strategyName, symbol, "Generated Candidates (expiry " + expiryDate + ")", candidates.size(), candidates.size());

        List<CallSpreadCandidate> afterDelta = candidates.stream().filter(deltaFilter(shortLegFilter, longLegFilter)).toList();
        filterLog.logFilter(strategyName, symbol, "Delta Filter", candidates.size(), afterDelta.size());

        List<CallSpreadCandidate> afterCredit = afterDelta.stream().filter(creditFilter()).toList();
        filterLog.logFilter(strategyName, symbol, "Positive Credit Filter", afterDelta.size(), afterCredit.size());

        List<CallSpreadCandidate> afterMaxCredit = afterCredit.stream().filter(commonMaxTotalCreditFilter(filter, CallSpreadCandidate::netCredit)).toList();
        filterLog.logFilter(strategyName, symbol, "Max Credit Filter", afterCredit.size(), afterMaxCredit.size());

        List<CallSpreadCandidate> afterMinCredit = afterMaxCredit.stream().filter(commonMinTotalCreditFilter(filter, CallSpreadCandidate::netCredit)).toList();
        filterLog.logFilter(strategyName, symbol, "Min Credit Filter", afterMaxCredit.size(), afterMinCredit.size());

        List<CallSpreadCandidate> afterMaxLoss = afterMinCredit.stream().filter(commonMaxLossFilter(filter, CallSpreadCandidate::maxLoss)).toList();
        filterLog.logFilter(strategyName, symbol, "Max Loss Filter", afterMinCredit.size(), afterMaxLoss.size());

        List<CallSpreadCandidate> afterRor = afterMaxLoss.stream()
                .filter(commonMinReturnOnRiskFilter(filter, CallSpreadCandidate::netCredit, CallSpreadCandidate::maxLoss)).toList();
        filterLog.logFilter(strategyName, symbol, "Min Return on Risk Filter", afterMaxLoss.size(), afterRor.size());

        List<TradeSetup> mapped = afterRor.stream().map(this::buildTradeSetup).toList();

        List<TradeSetup> afterMaxExtrinsic = mapped.stream().filter(commonMaxNetExtrinsicValueToPricePercentageFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Max Extrinsic Value Filter", mapped.size(), afterMaxExtrinsic.size());

        List<TradeSetup> afterMinExtrinsic = afterMaxExtrinsic.stream().filter(commonMinNetExtrinsicValueToPricePercentageFilter(filter)).toList();
        filterLog.logFilter(strategyName, symbol, "Min Extrinsic Value Filter", afterMaxExtrinsic.size(), afterMinExtrinsic.size());

        List<TradeSetup> result = afterMinExtrinsic.stream()
                .filter(trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()))
                .toList();
        filterLog.logFilter(strategyName, symbol, "Break-Even Filter", afterMinExtrinsic.size(), result.size());

        return result;
    }

    /**
     * Generates all valid 2-leg combinations as a stream of CallSpreadCandidate
     * records.
     * For Call Spreads: Short Strike (i) < Long Strike (j)
     */
    private Stream<CallSpreadCandidate> generateCandidates(Map<String, List<OptionData>> callMap,
            List<Double> strikes, double currentPrice) {
        return IntStream.range(0, strikes.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, strikes.size()).boxed()
                        .map(j -> createCandidate(callMap, strikes, i, j, currentPrice)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<CallSpreadCandidate> createCandidate(Map<String, List<OptionData>> callMap,
            List<Double> strikes, int shortIndex, int longIndex, double currentPrice) {
        double shortStrike = strikes.get(shortIndex);

        // OTM Check: For Call Credit Spread, Short Strike must be > Current Price
        // Wait, logic in original file was: if (shortStrikePrice <= currentPrice)
        // continue
        // So Short Strike MUST be > Current Price (OTM)
        if (shortStrike <= currentPrice) {
            return Optional.empty();
        }

        OptionData shortLeg = getOption(callMap, shortStrike);
        OptionData longLeg = getOption(callMap, strikes.get(longIndex));

        if (shortLeg == null || longLeg == null) {
            return Optional.empty();
        }
        return Optional.of(new CallSpreadCandidate(shortLeg, longLeg, currentPrice));
    }

    private OptionData getOption(Map<String, List<OptionData>> map, Double strike) {
        List<OptionData> options = map.get(String.valueOf(strike));
        return CollectionUtils.isEmpty(options) ? null : options.get(0);
    }

    // ========== FILTER PREDICATES ==========

    private Predicate<CallSpreadCandidate> deltaFilter(LegFilter shortLegFilter, LegFilter longLegFilter) {
        return candidate -> LegFilter.passes(shortLegFilter, candidate.shortLeg())
                && LegFilter.passes(longLegFilter, candidate.longLeg());
    }

    private Predicate<CallSpreadCandidate> creditFilter() {
        return candidate -> candidate.netCredit() > 0;
    }

    // Note: maxLossFilter and minReturnOnRiskFilter now use common helpers from
    // AbstractTradingStrategy

    // ========== TRADE BUILDER ==========

    private TradeSetup buildTradeSetup(CallSpreadCandidate c) {
        return CallCreditSpread.builder()
                .shortCall(c.shortLeg())
                .longCall(c.longLeg())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .breakEvenPrice(c.breakEvenPrice())
                .breakEvenPercentage(c.breakEvenPercentage())
                .returnOnRisk(c.returnOnRisk())
                .currentPrice(c.currentPrice())
                .build();
    }

    // ========== CANDIDATE RECORD ==========

    private record CallSpreadCandidate(OptionData shortLeg, OptionData longLeg, double currentPrice) {

        double netCredit() {
            return (shortLeg.getBid() - longLeg.getAsk()) * 100;
        }

        double strikeWidth() {
            return (longLeg.getStrikePrice() - shortLeg.getStrikePrice()) * 100;
        }

        double maxLoss() {
            return strikeWidth() - netCredit();
        }

        double returnOnRisk() {
            return (maxLoss() > 0) ? (netCredit() / maxLoss()) * 100 : 0;
        }

        double breakEvenPrice() {
            return shortLeg.getStrikePrice() + (netCredit() / 100);
        }

        double breakEvenPercentage() {
            return ((breakEvenPrice() - currentPrice) / currentPrice) * 100;
        }
    }
}
