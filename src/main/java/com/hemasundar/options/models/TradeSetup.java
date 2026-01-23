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
}
