package com.hemasundar.options.models;

import com.hemasundar.options.expectedmove.ExpectedMoveResult;

import java.util.List;

public interface TradeSetup {
    double getNetCredit();

    double getMaxLoss();

    double getReturnOnRisk();

    double getBreakEvenPrice();

    double getBreakEvenPercentage();

    // New methods for OO-based Telegram formatting
    String getExpiryDate();

    double getCurrentPrice();

    int getDaysToExpiration();

    List<TradeLeg> getLegs();

    // Abstract method to be implemented by Trade Models (BWBTrade, ZebraTrade,
    // etc.)
    double getNetExtrinsicValue();

    // Default calculation for the annualized net extrinsic percentage
    default double getAnnualizedNetExtrinsicValueToCapitalPercentage() {
        if (getMaxLoss() <= 0 || getDaysToExpiration() <= 0)
            return 0;
        return (getNetExtrinsicValue() / getMaxLoss()) * (365.0 / getDaysToExpiration()) * 100;
    }

    default double getUpperBreakEvenPrice() {
        return 0;
    }

    default double getUpperBreakEvenPercentage() {
        return 0;
    }

    /**
     * Upper breakeven delta (delta of option nearest to upper breakeven price).
     */
    default double getUpperBreakEvenDelta() {
        return 0;
    }

    /**
     * Strategy-aware lower breakeven price in dollars, or {@code null} for upside-only strategies.
     */
    default Double getEffectiveLowerBreakEvenPrice() {
        return getBreakEvenPrice() > 0 ? getBreakEvenPrice() : null;
    }

    /**
     * Strategy-aware upper breakeven price in dollars, or {@code null} for downside-only strategies.
     */
    default Double getEffectiveUpperBreakEvenPrice() {
        return getUpperBreakEvenPrice() > 0 ? getUpperBreakEvenPrice() : null;
    }

    /**
     * Expected Move result computed for this trade's underlying and expiration date.
     */
    default ExpectedMoveResult getExpectedMove() {
        return null;
    }

    /**
     * Attaches the computed Expected Move result to this trade setup.
     */
    default void setExpectedMove(ExpectedMoveResult expectedMove) {
    }

    // Optional metric for strategies like LEAPs
    default Double getBreakevenCAGR() {
        return null;
    }

    default Double getReturnOnRiskCAGR() {
        if (getMaxLoss() <= 0 || getDaysToExpiration() <= 0) return null;
        double rawRoR = getReturnOnRisk() / 100.0;
        return (Math.pow(1.0 + rawRoR, 365.0 / getDaysToExpiration()) - 1.0) * 100.0;
    }

    /**
     * Strategy type name (e.g., "LONG_CALL_LEAP").
     */
    default String getStrategyType() {
        return null;
    }

    /**
     * Cost savings percentage comparing option route vs buying stock.
     */
    default Double getCostSavingsPercent() {
        return null;
    }
}
