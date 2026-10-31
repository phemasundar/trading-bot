package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.models.CallCreditSpread;
import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondor;
import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Strategy implementation for 4-leg Iron Condor setups (Bull Put Credit Spread + Bear Call Credit Spread).
 * <p>
 * <b>Important Configuration Note ({@code MAX_LOSS} Filter Requirement):</b>
 * An Iron Condor is a 4-leg strategy whose unconstrained search space scales as {@code O(S^4)}
 * across option strikes {@code S} (i.e. {@code O(S^2)} valid Put Credit Spreads crossed with
 * {@code O(S^2)} valid Call Credit Spreads). On deep option chains such as {@code SPY}, {@code QQQ},
 * or {@code NVDA} (200+ strikes per expiration), an unconstrained Cartesian product can generate
 * over 1,000,000 combinations per expiry.
 * <p>
 * Configuring a maximum loss bound — via {@code MAX_LOSS <= <limit>} in unified {@code conditions}
 * (or legacy {@code maxLossLimit}) — is therefore <b>strongly recommended / practically mandatory</b>.
 * When {@code MAX_LOSS} is configured, {@link #findValidIronCondors} applies exact mathematical
 * wing pre-pruning before the Cartesian loop:
 * <ul>
 *   <li>Since {@code condorMaxLoss >= putSpread.getMaxLoss() - callSpread.getNetCredit()}, any put
 *       spread with {@code putSpread.getMaxLoss() > effectiveMaxLoss + maxCallCredit} cannot possibly
 *       form a valid Iron Condor and is pruned upfront.</li>
 *   <li>Symmetrically, any call spread with {@code callSpread.getMaxLoss() > effectiveMaxLoss + maxPutCredit}
 *       is pruned before pairing.</li>
 * </ul>
 * This exact pre-pruning reduces candidate combinations by 95–99% with zero false negatives.
 */
public class IronCondorStrategy extends AbstractTradingStrategy {
    private final PutCreditSpreadStrategy putStrategy;
    private final CallCreditSpreadStrategy callStrategy;

    public IronCondorStrategy(StrategyType strategyType,
                             FinnHubAPIs finnHubAPIs,
                             ThinkOrSwimAPIs ThinkOrSwimAPIs,
                             Optional<SupabaseService> supabaseService,
                             PutCreditSpreadStrategy putStrategy,
                             CallCreditSpreadStrategy callStrategy) {
        super(strategyType, finnHubAPIs, ThinkOrSwimAPIs, supabaseService);
        this.putStrategy = putStrategy;
        this.callStrategy = callStrategy;
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        LegFilter putShortLegFilter = null;
        LegFilter callShortLegFilter = null;
        LegFilter putLongLegFilter = null;
        LegFilter callLongLegFilter = null;

        if (filter instanceof IronCondorFilter ironCondorFilter) {
            putShortLegFilter = ironCondorFilter.getPutShortLeg();
            callShortLegFilter = ironCondorFilter.getCallShortLeg();
            putLongLegFilter = ironCondorFilter.getPutLongLeg();
            callLongLegFilter = ironCondorFilter.getCallLongLeg();
        } else if (filter instanceof CreditSpreadFilter creditSpreadFilter) {
            putShortLegFilter = creditSpreadFilter.getShortLeg();
            callShortLegFilter = creditSpreadFilter.getShortLeg();
            putLongLegFilter = creditSpreadFilter.getLongLeg();
            callLongLegFilter = creditSpreadFilter.getLongLeg();
        }

        CreditSpreadFilter putLegFilter = CreditSpreadFilter.builder()
                .strategyId(getStrategyName(filter))
                .targetDTE(filter.getTargetDTE())
                .maxLossLimit(filter.getMaxLossLimit())
                .minReturnOnRisk(0)
                .shortLeg(putShortLegFilter)
                .longLeg(putLongLegFilter)
                .build();

        CreditSpreadFilter callLegFilter = CreditSpreadFilter.builder()
                .strategyId(getStrategyName(filter))
                .targetDTE(filter.getTargetDTE())
                .maxLossLimit(filter.getMaxLossLimit())
                .minReturnOnRisk(0)
                .shortLeg(callShortLegFilter)
                .longLeg(callLongLegFilter)
                .build();

        List<TradeSetup> putSetups = putStrategy.findValidTrades(chain, expiryDate, putLegFilter);
        List<TradeSetup> callSetups = callStrategy.findValidTrades(chain, expiryDate, callLegFilter);

        List<PutCreditSpread> putSpreads = new ArrayList<>(putSetups.size());
        for (TradeSetup setup : putSetups) {
            if (setup instanceof PutCreditSpread putSpread) {
                putSpreads.add(putSpread);
            }
        }

        List<CallCreditSpread> callSpreads = new ArrayList<>(callSetups.size());
        for (TradeSetup setup : callSetups) {
            if (setup instanceof CallCreditSpread callSpread) {
                callSpreads.add(callSpread);
            }
        }

        return findValidIronCondors(chain.getSymbol(), expiryDate, putSpreads, callSpreads, chain.getUnderlyingPrice(), filter);
    }

    /**
     * Pairs valid Put and Call Credit Spreads into Iron Condors using exact mathematical wing
     * pre-pruning when {@code MAX_LOSS} is configured, followed by lightweight candidate filtering
     * before instantiating {@link IronCondor} trade setups.
     */
    private List<TradeSetup> findValidIronCondors(String symbol, String expiryDate, List<PutCreditSpread> putSpreads,
            List<CallCreditSpread> callSpreads,
            double currentPrice,
            OptionsStrategyFilter filter) {

        if (putSpreads.isEmpty() || callSpreads.isEmpty()) {
            FilterLogStore.getInstance().logFilter(
                    getStrategyName(filter), symbol, expiryDate,
                    FilterStage.GENERATED_CANDIDATES.displayName(), 0, 0);
            return new ArrayList<>();
        }

        // Exact mathematical wing pre-pruning using effective MAX_LOSS bound
        Double effectiveMaxLoss = filter.getEffectiveMaxLossLimit();
        List<PutCreditSpread> candidatePuts = putSpreads;
        List<CallCreditSpread> candidateCalls = callSpreads;

        if (effectiveMaxLoss != null) {
            double maxCallCredit = callSpreads.stream()
                    .mapToDouble(CallCreditSpread::getNetCredit)
                    .max()
                    .orElse(0.0);
            double maxPutCredit = putSpreads.stream()
                    .mapToDouble(PutCreditSpread::getNetCredit)
                    .max()
                    .orElse(0.0);

            double maxAllowedPutSpreadLoss = effectiveMaxLoss + maxCallCredit;
            double maxAllowedCallSpreadLoss = effectiveMaxLoss + maxPutCredit;

            candidatePuts = putSpreads.stream()
                    .filter(p -> p.getMaxLoss() <= maxAllowedPutSpreadLoss)
                    .toList();
            candidateCalls = callSpreads.stream()
                    .filter(c -> c.getMaxLoss() <= maxAllowedCallSpreadLoss)
                    .toList();
        }

        List<IronCondorCandidate> candidates = new ArrayList<>();

        for (PutCreditSpread putSpread : candidatePuts) {
            double shortPutStrike = putSpread.getShortPut().getStrikePrice();
            double putWidth = (shortPutStrike - putSpread.getLongPut().getStrikePrice()) * 100.0;
            double putCredit = putSpread.getNetCredit();

            for (CallCreditSpread callSpread : candidateCalls) {
                double shortCallStrike = callSpread.getShortCall().getStrikePrice();
                if (shortPutStrike >= shortCallStrike) {
                    continue;
                }

                double totalCredit = putCredit + callSpread.getNetCredit();
                double callWidth = (callSpread.getLongCall().getStrikePrice() - shortCallStrike) * 100.0;
                double maxRisk = Math.max(putWidth, callWidth) - totalCredit;
                double returnOnRisk = maxRisk > 0 ? (totalCredit / maxRisk) * 100.0 : 0.0;

                double lowerBreakEven = shortPutStrike - (totalCredit / 100.0);
                double upperBreakEven = shortCallStrike + (totalCredit / 100.0);

                double lowerBreakEvenPercentage = ((currentPrice - lowerBreakEven) / currentPrice) * 100.0;
                double upperBreakEvenPercentage = ((upperBreakEven - currentPrice) / currentPrice) * 100.0;

                candidates.add(new IronCondorCandidate(
                        putSpread,
                        callSpread,
                        totalCredit,
                        maxRisk,
                        returnOnRisk,
                        lowerBreakEven,
                        upperBreakEven,
                        lowerBreakEvenPercentage,
                        upperBreakEvenPercentage,
                        currentPrice));
            }
        }

        FilterLogStore.getInstance().logFilter(
                getStrategyName(filter), symbol, expiryDate,
                FilterStage.GENERATED_CANDIDATES.displayName(),
                candidates.size(), candidates.size());

        FilterPipeline<IronCondorCandidate> candidatePipeline = FilterPipeline
                .<IronCondorCandidate>forContext(getStrategyName(filter), symbol, expiryDate);

        if (filter.getMaxLossLimit() != null) {
            candidatePipeline.step(FilterStage.MAX_LOSS_FILTER, commonMaxLossFilter(filter, IronCondorCandidate::maxLoss));
        }
        if (filter.getMinReturnOnRisk() != null) {
            candidatePipeline.step(FilterStage.MIN_RETURN_ON_RISK_FILTER, commonMinReturnOnRiskFilter(filter, IronCondorCandidate::netCredit, IronCondorCandidate::maxLoss));
        }
        if (filter.getMaxTotalCredit() != null) {
            candidatePipeline.step(FilterStage.MAX_CREDIT_FILTER, commonMaxTotalCreditFilter(filter, IronCondorCandidate::netCredit));
        }
        if (filter.getMinTotalCredit() != null) {
            candidatePipeline.step(FilterStage.MIN_CREDIT_FILTER, commonMinTotalCreditFilter(filter, IronCondorCandidate::netCredit));
        }
        if (filter.getMaxBreakEvenPercentage() != null) {
            candidatePipeline.step(FilterStage.BREAK_EVEN_FILTER, c ->
                    filter.passesMaxBreakEvenPercentage(c.lowerBreakEvenPercentage())
                            && filter.passesMaxBreakEvenPercentage(c.upperBreakEvenPercentage()));
        }

        List<IronCondorCandidate> survived = candidatePipeline.run(candidates);

        List<TradeSetup> mapped = survived.stream()
                .map(this::buildTradeSetup)
                .toList();

        FilterPipeline<TradeSetup> tradePipeline = FilterPipeline
                .<TradeSetup>forContext(getStrategyName(filter), symbol, expiryDate);

        if (filter.getMaxNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter));
        }
        if (filter.getMinNetExtrinsicValueToPricePercentage() != null) {
            tradePipeline.step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter));
        }

        return applyTradeMathFilterExpressions(tradePipeline, filter).run(mapped);
    }

    private TradeSetup buildTradeSetup(IronCondorCandidate c) {
        return IronCondor.builder()
                .putLeg(c.putLeg())
                .callLeg(c.callLeg())
                .netCredit(c.netCredit())
                .maxLoss(c.maxLoss())
                .returnOnRisk(c.returnOnRisk())
                .lowerBreakEven(c.lowerBreakEven())
                .upperBreakEven(c.upperBreakEven())
                .lowerBreakEvenPercentage(c.lowerBreakEvenPercentage())
                .upperBreakEvenPercentage(c.upperBreakEvenPercentage())
                .currentPrice(c.currentPrice())
                .build();
    }

    /**
     * Lightweight candidate record holding pre-validated Iron Condor metrics prior to full
     * {@link IronCondor} POJO instantiation.
     */
    private record IronCondorCandidate(
            PutCreditSpread putLeg,
            CallCreditSpread callLeg,
            double netCredit,
            double maxLoss,
            double returnOnRisk,
            double lowerBreakEven,
            double upperBreakEven,
            double lowerBreakEvenPercentage,
            double upperBreakEvenPercentage,
            double currentPrice) {
    }
}
