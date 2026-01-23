package com.hemasundar.options.strategies;

import com.hemasundar.options.models.CreditSpreadFilter;
import com.hemasundar.options.models.IronCondor;
import com.hemasundar.options.models.LegFilter;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.options.models.CallCreditSpread;

import java.util.ArrayList;
import java.util.List;

public class IronCondorStrategy extends AbstractTradingStrategy {

    private final PutCreditSpreadStrategy putStrategy = new PutCreditSpreadStrategy();
    private final CallCreditSpreadStrategy callStrategy = new CallCreditSpreadStrategy();

    public IronCondorStrategy() {
        super(StrategyType.IRON_CONDOR);
    }

    public IronCondorStrategy(StrategyType strategyType) {
        super(strategyType);
    }

    @Override
    protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
            OptionsStrategyFilter filter) {
        // Create a filter for legs with 0 min return to get all valid spreads
        // SET ignoreEarnings = true for legs to avoid redundant checks

        // Get the short leg filter from the parent filter if available
        LegFilter shortLegFilter = null;
        if (filter instanceof CreditSpreadFilter) {
            shortLegFilter = ((CreditSpreadFilter) filter).getShortLeg();
        }

        CreditSpreadFilter legFilter = CreditSpreadFilter.builder()
                .targetDTE(filter.getTargetDTE())
                .maxLossLimit(filter.getMaxLossLimit())
                .minReturnOnRisk(0) // Get all valid spreads
                .ignoreEarnings(true) // Don't check earnings again for legs
                .shortLeg(shortLegFilter)
                .build();

        List<TradeSetup> putSetups = putStrategy.findTrades(chain, legFilter);
        List<TradeSetup> callSetups = callStrategy.findTrades(chain, legFilter);

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

        return findValidIronCondors(putSpreads, callSpreads, chain.getUnderlyingPrice(), filter);
    }

    private List<TradeSetup> findValidIronCondors(List<PutCreditSpread> putSpreads,
            List<CallCreditSpread> callSpreads,
            double currentPrice,
            OptionsStrategyFilter filter) {
        List<TradeSetup> condors = new ArrayList<>();

        for (PutCreditSpread putSpread : putSpreads) {
            for (CallCreditSpread callSpread : callSpreads) {
                // Ensure no overlap
                if (putSpread.getShortPut().getStrikePrice() >= callSpread.getShortCall().getStrikePrice())
                    continue;

                double totalCredit = putSpread.getNetCredit() + callSpread.getNetCredit();

                double putWidth = (putSpread.getShortPut().getStrikePrice() - putSpread.getLongPut().getStrikePrice())
                        * 100;
                double callWidth = (callSpread.getLongCall().getStrikePrice()
                        - callSpread.getShortCall().getStrikePrice()) * 100;
                double maxRisk = Math.max(putWidth, callWidth) - totalCredit;

                if (maxRisk > filter.getMaxLossLimit())
                    continue;

                double requiredProfit = maxRisk * ((double) filter.getMinReturnOnRisk() / 100);

                if (totalCredit < requiredProfit)
                    continue;

                double returnOnRisk = (totalCredit / maxRisk) * 100;
                double lowerBreakEven = putSpread.getShortPut().getStrikePrice() - (totalCredit / 100);
                double upperBreakEven = callSpread.getShortCall().getStrikePrice() + (totalCredit / 100);

                double lowerBreakEvenPercentage = ((currentPrice - lowerBreakEven) / currentPrice) * 100;
                double upperBreakEvenPercentage = ((upperBreakEven - currentPrice) / currentPrice) * 100;

                condors.add(IronCondor.builder()
                        .putLeg(putSpread)
                        .callLeg(callSpread)
                        .netCredit(totalCredit)
                        .maxLoss(maxRisk)
                        .returnOnRisk(returnOnRisk)
                        .lowerBreakEven(lowerBreakEven)
                        .upperBreakEven(upperBreakEven)
                        .lowerBreakEvenPercentage(lowerBreakEvenPercentage)
                        .upperBreakEvenPercentage(upperBreakEvenPercentage)
                        .build());
            }
        }
        return condors;
    }
}
