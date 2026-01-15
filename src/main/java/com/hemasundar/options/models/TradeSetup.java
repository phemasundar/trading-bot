package com.hemasundar.options.models;

import lombok.Data;
import lombok.experimental.SuperBuilder;

public interface TradeSetup {
    double getNetCredit();

    double getMaxLoss();

    double getReturnOnRisk();
}
