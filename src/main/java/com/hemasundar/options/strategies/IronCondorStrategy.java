package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondor;
import com.hemasundar.options.models.IronCondorFilter;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.CallCreditSpread;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.services.FilterLogStore;
import com.hemasundar.services.SupabaseService;

public class IronCondorStrategy extends AbstractTradingStrategy {
    private final PutCreditSpreadStrategy putStrategy;
    private final CallCreditSpreadStrategy callStrategy;

    public IronCondorStrategy(StrategyType strategyType,
                             FinnHubAPIs finnHubAPIs,
                             ThinkOrSwinAPIs thinkOrSwinAPIs,
                             java.util.Optional<SupabaseService> supabaseService,
                             PutCreditSpreadStrategy putStrategy,
                             CallCreditSpreadStrategy callStrategy) {
        super(strategyType, finnHubAPIs, thinkOrSwinAPIs, supabaseService);
        this.putStrategy = putStrategy;
        this.callStrategy = callStrategy;
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        // Create separate filters for put and call legs
        // Supports both new IronCondorFilter (separate legs) and legacy
        // CreditSpreadFilter (shared leg)

        LegFilter putShortLegFilter = null;
        LegFilter callShortLegFilter = null;

        if (filter instanceof IronCondorFilter ironCondorFilter) {
            // New format: use separate put and call short leg filters
            putShortLegFilter = ironCondorFilter.getPutShortLeg();
            callShortLegFilter = ironCondorFilter.getCallShortLeg();
        } else if (filter instanceof CreditSpreadFilter creditSpreadFilter) {
            // Legacy format: use same shortLeg for both
            putShortLegFilter = creditSpreadFilter.getShortLeg();
            callShortLegFilter = creditSpreadFilter.getShortLeg();
        }

        // Create put leg filter
        CreditSpreadFilter putLegFilter = CreditSpreadFilter.builder()
                .targetDTE(filter.getTargetDTE())
                .maxLossLimit(filter.getMaxLossLimit())
                .minReturnOnRisk(0) // Get all valid spreads
                .shortLeg(putShortLegFilter)
                .build();

        // Create call leg filter (may have different delta)
        CreditSpreadFilter callLegFilter = CreditSpreadFilter.builder()
                .targetDTE(filter.getTargetDTE())
                .maxLossLimit(filter.getMaxLossLimit())
                .minReturnOnRisk(0)
                .shortLeg(callShortLegFilter)
                .build();

        List<TradeSetup> putSetups = putStrategy.findValidTrades(chain, expiryDate, putLegFilter);
        List<TradeSetup> callSetups = callStrategy.findValidTrades(chain, expiryDate, callLegFilter);

        // Cast back to specific types
        List<PutCreditSpread> putSpreads = new ArrayList<>();
        for (TradeSetup setup : putSetups) {
            if (setup instanceof PutCreditSpread) {
                putSpreads.add((PutCreditSpread) setup);
            }
        }

        List<CallCreditSpread> callSpreads = new ArrayList<>();
        for (TradeSetup setup : callSetups) {
            if (setup instanceof CallCreditSpread) {
                callSpreads.add((CallCreditSpread) setup);
            }
        }

        return findValidIronCondors(chain.getSymbol(), expiryDate, putSpreads, callSpreads, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidIronCondors(String symbol, String expiryDate, List<PutCreditSpread> putSpreads,
            List<CallCreditSpread> callSpreads,
            double currentPrice,
            OptionsStrategyFilter filter) {
        
        List<IronCondor> combinations = new ArrayList<>();

        for (PutCreditSpread putSpread : putSpreads) {
            for (CallCreditSpread callSpread : callSpreads) {
                // Ensure no overlap
                if (putSpread.getShortPut().getStrikePrice() >= callSpread.getShortCall().getStrikePrice()) {
                    continue;
                }

                double totalCredit = putSpread.getNetCredit() + callSpread.getNetCredit();

                double putWidth = (putSpread.getShortPut().getStrikePrice() - putSpread.getLongPut().getStrikePrice())
                        * 100;
                double callWidth = (callSpread.getLongCall().getStrikePrice()
                        - callSpread.getShortCall().getStrikePrice()) * 100;
                double maxRisk = Math.max(putWidth, callWidth) - totalCredit;
                double returnOnRisk = maxRisk > 0 ? (totalCredit / maxRisk) * 100 : 0;

                double lowerBreakEven = putSpread.getShortPut().getStrikePrice() - (totalCredit / 100);
                double upperBreakEven = callSpread.getShortCall().getStrikePrice() + (totalCredit / 100);

                double lowerBreakEvenPercentage = ((currentPrice - lowerBreakEven) / currentPrice) * 100;
                double upperBreakEvenPercentage = ((upperBreakEven - currentPrice) / currentPrice) * 100;

                IronCondor condor = IronCondor.builder()
                        .putLeg(putSpread)
                        .callLeg(callSpread)
                        .netCredit(totalCredit)
                        .maxLoss(maxRisk)
                        .returnOnRisk(returnOnRisk)
                        .lowerBreakEven(lowerBreakEven)
                        .upperBreakEven(upperBreakEven)
                        .lowerBreakEvenPercentage(lowerBreakEvenPercentage)
                        .upperBreakEvenPercentage(upperBreakEvenPercentage)
                        .currentPrice(currentPrice)
                        .build();

                combinations.add(condor);
            }
        }
        
        FilterLogStore.getInstance().logFilter(
                getStrategyName(), symbol, expiryDate,
                FilterStage.GENERATED_CANDIDATES.displayName(),
                combinations.size(), combinations.size());

        List<TradeSetup> mapped = new ArrayList<>(combinations);

        return FilterPipeline
                .<TradeSetup>forContext(getStrategyName(), symbol, expiryDate)
                .step(FilterStage.MAX_LOSS_FILTER,          commonMaxLossFilter(filter, TradeSetup::getMaxLoss))
                .step(FilterStage.MIN_RETURN_ON_RISK_FILTER,commonMinReturnOnRiskFilter(filter, TradeSetup::getNetCredit, TradeSetup::getMaxLoss))
                .step(FilterStage.MAX_CREDIT_FILTER,        commonMaxTotalCreditFilter(filter, TradeSetup::getNetCredit))
                .step(FilterStage.MIN_CREDIT_FILTER,        commonMinTotalCreditFilter(filter, TradeSetup::getNetCredit))
                .step(FilterStage.MAX_EXTRINSIC_VALUE_FILTER, commonMaxNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.MIN_EXTRINSIC_VALUE_FILTER, commonMinNetExtrinsicValueToPricePercentageFilter(filter))
                .step(FilterStage.BREAK_EVEN_FILTER,        trade -> filter.passesMaxBreakEvenPercentage(trade.getBreakEvenPercentage()) && filter.passesMaxBreakEvenPercentage(trade.getUpperBreakEvenPercentage()))
                .run(mapped);
    }
}
