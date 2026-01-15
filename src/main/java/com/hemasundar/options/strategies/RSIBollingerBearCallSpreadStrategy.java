package com.hemasundar.options.strategies;

import lombok.extern.log4j.Log4j2;

/**
 * RSI & Bollinger Bands Bear Call Spread Strategy.
 * 
 * Entry Conditions (BEARISH signal - Overbought):
 * - RSI (14-day) > 70 (Overbought)
 * - Price is touching or piercing the Upper Bollinger Band (2 SD)
 * 
 * Trade Setup:
 * - Sell: Call option at ~30 Delta (above current price)
 * - Buy: Call option at ~15-20 Delta (further above current price)
 * - DTE: 30 days
 */
@Log4j2
public class RSIBollingerBearCallSpreadStrategy extends CallCreditSpreadStrategy {

    @Override
    public String getStrategyName() {
        return "RSI Bollinger Bear Call Spread Strategy";
    }
}
