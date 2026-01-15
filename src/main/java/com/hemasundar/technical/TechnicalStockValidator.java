package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
import lombok.extern.log4j.Log4j2;
import org.ta4j.core.BarSeries;

/**
 * Validates stock symbols against technical conditions.
 */
@Log4j2
public class TechnicalStockValidator {

    public static boolean validate(String symbol, TechnicalFilterChain filterChain) {
        // 1. Check Volume First
        if (!checkVolumeCondition(symbol, filterChain)) {
            return false;
        }

        // 2. Fetch Price History
        PriceHistoryResponse priceHistory = ThinkOrSwinAPIs.getYearlyPriceHistory(symbol, 1);
        BarSeries series = TechnicalIndicatorUtils.buildBarSeries(symbol, priceHistory);

        if (series.getBarCount() == 0) {
            log.warn("[{}] No price history available", symbol);
            return false;
        }

        // 3. Check Technical Indicators
        return checkTechnicalIndicators(symbol, series, filterChain);
    }

    private static boolean checkVolumeCondition(String symbol, TechnicalFilterChain filterChain) {
        Long minVolume = filterChain.getMinVolume();
        if (minVolume == null || minVolume <= 0) {
            return true; // No volume requirement
        }

        try {
            QuotesResponse.QuoteData quoteData = ThinkOrSwinAPIs.getQuote(symbol);
            long currentVolume = quoteData.getQuote().getTotalVolume();
            if (currentVolume < minVolume) {
                log.debug("[{}] Volume: {:,} - BELOW threshold ({:,}). Skipping.",
                        symbol, currentVolume, minVolume);
                return false;
            }
            log.debug("[{}] Volume: {:,} - OK", symbol, currentVolume);
            return true;
        } catch (Exception e) {
            log.warn("[{}] Failed to fetch quote for volume check: {}", symbol, e.getMessage());
            return true; // Allow through if we can't check
        }
    }

    private static boolean checkTechnicalIndicators(String symbol, BarSeries series, TechnicalFilterChain filterChain) {
        RSIFilter rsiFilter = filterChain.getRsiFilter();
        BollingerBandsFilter bbFilter = filterChain.getBollingerFilter();

        if (rsiFilter == null || bbFilter == null) {
            log.error("RSI or Bollinger Bands filter not configured in filter chain");
            return false;
        }

        RSICondition rsiCondition = filterChain.getRsiCondition();
        BollingerCondition bbCondition = filterChain.getBollingerCondition();

        if (rsiCondition == null || bbCondition == null) {
            log.error("RSI or Bollinger condition not set in filter chain");
            return false;
        }

        double currentRSI = rsiFilter.getCurrentRSI(series);

        // Evaluate conditions
        boolean rsiConditionMet = rsiCondition.evaluate(rsiFilter, series);
        boolean bbConditionMet = bbCondition.evaluate(bbFilter, series);

        log.debug("[{}] RSI: {:.2f} (Condition: {} -> {}) | BB Condition: {} -> {}",
                symbol, currentRSI, rsiCondition, rsiConditionMet, bbCondition, bbConditionMet);

        if (!rsiConditionMet || !bbConditionMet) {
            return false;
        }

        log.info("[{}] {} + {} conditions met!", symbol, rsiCondition, bbCondition);
        return true;
    }
}
