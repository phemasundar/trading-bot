package com.hemasundar.options.models;

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
    default double getAnulizedNetExtrinsicValueToCapitalPercentage() {
        if (getMaxLoss() <= 0 || getDaysToExpiration() <= 0)
            return 0;
        return (getNetExtrinsicValue() / getMaxLoss()) * (365.0 / getDaysToExpiration());
    }

    default double getUpperBreakEvenPrice() {
        return 0;
    }

    default double getUpperBreakEvenPercentage() {
        return 0;
    }
}
