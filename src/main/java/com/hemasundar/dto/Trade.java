package com.hemasundar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hemasundar.options.models.LongCallLeap;
import com.hemasundar.options.models.TradeSetup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single trade opportunity found by a strategy.
 * Contains common fields and per-leg details for strategy-specific display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {

    /**
     * Stock symbol (e.g., "AAPL")
     */
    private String symbol;

    /**
     * Current underlying stock price
     */
    private double underlyingPrice;

    /**
     * Option expiration date (YYYY-MM-DD)
     */
    private String expiryDate;

    /**
     * Days to expiration
     */
    private int dte;

    /**
     * Individual legs of the trade (BUY/SELL actions with strikes, deltas,
     * premiums).
     * Number of legs varies by strategy type.
     */
    private List<TradeLegDTO> legs;

    /**
     * Net credit received (positive) or debit paid (negative)
     */
    private double netCredit;

    /**
     * Maximum potential loss
     */
    private double maxLoss;

    /**
     * Return on risk percentage
     */
    private double returnOnRisk;

    /**
     * Breakeven price for the underlying
     */
    private double breakEvenPrice;

    /**
     * Breakeven as percentage from current price
     */
    private double breakEvenPercent;

    /**
     * Upper breakeven price (for strategies like BWB, Iron Condor)
     */
    private double upperBreakEvenPrice;

    /**
     * Upper breakeven as percentage from current price
     */
    private double upperBreakEvenPercent;

    /**
     * Full trade details text (matching Telegram format) for expandable view.
     */
    private String tradeDetails;

    /**
     * Converts a TradeSetup (strategy output) to a Trade DTO.
     * Shared by StrategyExecutionService (Vaadin) and SampleTestNG (TestNG).
     *
     * @param setup  the TradeSetup from strategy execution
     * @param symbol stock symbol to assign
     * @return Trade DTO ready for persistence/display
     */
    public static Trade fromTradeSetup(TradeSetup setup, String symbol) {
        List<TradeLegDTO> legDTOs = setup.getLegs().stream()
                .map(leg -> TradeLegDTO.builder()
                        .action(leg.getAction())
                        .optionType(leg.getOptionType())
                        .strike(leg.getStrike())
                        .delta(leg.getDelta())
                        .premium(leg.getPremium())
                        .build())
                .toList();

        StringBuilder details = new StringBuilder();
        for (TradeLegDTO leg : legDTOs) {
            details.append(leg.getAction()).append(" ")
                    .append(String.format("%.0f", leg.getStrike())).append(" ")
                    .append(leg.getOptionType())
                    .append(" (δ ").append(String.format("%.2f", leg.getDelta())).append(")")
                    .append(" → $").append(String.format("%.2f", leg.getPremium()))
                    .append("\n");
        }

        double netAmount = setup.getNetCredit();
        if (netAmount >= 0) {
            details.append("Credit: $").append(String.format("%.0f", netAmount));
        } else {
            details.append("Debit: $").append(String.format("%.0f", -netAmount));
        }
        details.append(" | Max Loss: $").append(String.format("%.0f", setup.getMaxLoss()));

        double ror = setup.getReturnOnRisk();
        if (ror > 0) {
            details.append(" | RoR: ").append(String.format("%.2f", ror)).append("%");
        }

        details.append("\nBE: $").append(String.format("%.2f", setup.getBreakEvenPrice()))
                .append(" (").append(String.format("%.2f", setup.getBreakEvenPercentage())).append("%)");

        double upperBE = setup.getUpperBreakEvenPrice();
        if (upperBE > 0 && Math.abs(upperBE - setup.getBreakEvenPrice()) > 0.01) {
            details.append(" | Upper BE: $").append(String.format("%.2f", upperBE))
                    .append(" (").append(String.format("%.2f", setup.getUpperBreakEvenPercentage()))
                    .append("%)");
        }

        if (setup instanceof LongCallLeap leap) {
            details.append(" [CAGR: ").append(String.format("%.2f", leap.calculateBreakevenCAGR())).append("%]");
            double costOpt = leap.getFinalCostOfOption();
            double costStock = leap.getFinalCostOfBuying();
            double diffPct = costStock > 0 ? ((costStock - costOpt) / costStock) * 100 : 0;
            details.append("\nCost (Opt/Stock): $").append(String.format("%.2f", costOpt))
                    .append(" / $").append(String.format("%.2f", costStock))
                    .append(" (").append(String.format("%.1f", diffPct)).append("% cheaper)");
        } else if (setup instanceof com.hemasundar.options.models.ZebraTrade zebra) {
            details.append("\nNet Extrinsic Value: $").append(String.format("%.2f", zebra.getNetExtrinsicValue()));
        }

        return Trade.builder()
                .symbol(symbol)
                .underlyingPrice(setup.getCurrentPrice())
                .expiryDate(setup.getExpiryDate())
                .dte(setup.getDaysToExpiration())
                .legs(legDTOs)
                .netCredit(setup.getNetCredit())
                .maxLoss(setup.getMaxLoss())
                .returnOnRisk(setup.getReturnOnRisk())
                .breakEvenPrice(setup.getBreakEvenPrice())
                .breakEvenPercent(setup.getBreakEvenPercentage())
                .upperBreakEvenPrice(setup.getUpperBreakEvenPrice())
                .upperBreakEvenPercent(setup.getUpperBreakEvenPercentage())
                .tradeDetails(details.toString())
                .build();
    }
}
