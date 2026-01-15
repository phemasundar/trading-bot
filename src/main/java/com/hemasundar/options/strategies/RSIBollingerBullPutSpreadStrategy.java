package com.hemasundar.options.strategies;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import lombok.extern.log4j.Log4j2;

/**
 * RSI & Bollinger Bands Bull Put Spread Strategy.
 * 
 * Entry Conditions (BULLISH signal - Oversold):
 * - RSI (14-day) < 30 (Oversold)
 * - Price is touching or piercing the Lower Bollinger Band (2 SD)
 * 
 * Trade Setup:
 * - Sell: Put option at ~30 Delta (below current price)
 * - Buy: Put option at ~15-20 Delta (further below current price)
 * - DTE: 30 days
 */
@Log4j2
public class RSIBollingerBullPutSpreadStrategy extends PutCreditSpreadStrategy {

    @Override
    public String getStrategyName() {
        return "RSI Bollinger Bull Put Spread Strategy";
    }
}
