package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
import com.hemasundar.utils.SchwabApiExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Log4j2
@Component
@lombok.RequiredArgsConstructor
public class PriceDropScreener {

    private final ThinkOrSwinAPIs thinkOrSwinAPIs;
    private final SchwabApiExecutor schwabApiExecutor;

    /**
     * Screens stocks for price drops over a given number of trading days.
     * When lookbackDays is 0, uses intraday (daily) percent change from Quotes API.
     * When lookbackDays > 0, uses Price History API to compute multi-day change.
     *
     * @param symbols        List of stock symbols to screen
     * @param symbols        List of stock symbols to screen
     * @param dropRules      Rules to evaluate drop percentage against
     * @param lookbackDays   Number of trading days to look back (0 = intraday)
     * @return List of ScreeningResult for stocks matching the criteria
     */
    public List<TechnicalScreener.ScreeningResult> screenPriceDrop(
            List<String> symbols, List<com.hemasundar.technical.NumericRule> dropRules, int lookbackDays, BiConsumer<String, String> alertCallback) {

        if (lookbackDays == 0) {
            return screenIntradayDrop(symbols, dropRules, alertCallback);
        } else {
            return screenMultiDayDrop(symbols, dropRules, lookbackDays, alertCallback);
        }
    }

    /**
     * Screens stocks for drops from their 52-week high.
     *
     * @param symbols        List of stock symbols to screen
     * @param symbols        List of stock symbols to screen
     * @param dropRules      Rules to evaluate drop percentage against
     * @return List of ScreeningResult for stocks matching the criteria
     */
    public List<TechnicalScreener.ScreeningResult> screen52WeekHighDrop(
            List<String> symbols, List<com.hemasundar.technical.NumericRule> dropRules, BiConsumer<String, String> alertCallback) {

        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();

        log.info("Screening {} symbols for 52-week high drop rules", symbols.size());

        // Fetch quotes in batches of 50 (API limit consideration)
        for (int i = 0; i < symbols.size(); i += 50) {
            List<String> batch = symbols.subList(i, Math.min(i + 50, symbols.size()));
            try {
                Map<String, QuotesResponse.QuoteData> quotes = thinkOrSwinAPIs.getQuotes(batch);

                for (Map.Entry<String, QuotesResponse.QuoteData> entry : quotes.entrySet()) {
                    String symbol = entry.getKey();
                    QuotesResponse.QuoteData quoteData = entry.getValue();

                    if (quoteData == null || quoteData.getQuote() == null) {
                        continue;
                    }

                    QuotesResponse.Quote quote = quoteData.getQuote();
                    double currentPrice = quote.getLastPrice();
                    double high52w = quote.getFiftyTwoWeekHigh();

                    if (high52w <= 0 || currentPrice <= 0) {
                        continue;
                    }

                    double dropPct = ((high52w - currentPrice) / high52w) * 100.0;

                    boolean passes = true;
                    if (dropRules != null && !dropRules.isEmpty()) {
                        for (com.hemasundar.technical.NumericRule rule : dropRules) {
                            if (!rule.evaluate(dropPct)) {
                                passes = false;
                                break;
                            }
                        }
                    } else {
                        passes = false; // Need rules to pass
                    }

                    if (passes) {
                        results.add(buildResult(symbol, currentPrice, quote.getTotalVolume(),
                                dropPct, high52w, "52W_HIGH"));
                        log.info("[{}] Down {}% from 52W high (${} -> ${})",
                                symbol, String.format("%.2f", dropPct),
                                String.format("%.2f", high52w), String.format("%.2f", currentPrice));
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching quotes for batch starting at {}: {}", i, e.getMessage());
                if (alertCallback != null) {
                    alertCallback.accept("Batch " + i, e.getMessage());
                }
            }
        }

        log.info("52-Week High Drop screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Screens for intraday price drops using Quotes API netPercentChange.
     */
    private List<TechnicalScreener.ScreeningResult> screenIntradayDrop(
            List<String> symbols, List<com.hemasundar.technical.NumericRule> dropRules, BiConsumer<String, String> alertCallback) {

        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();

        log.info("Screening {} symbols for intraday drop rules", symbols.size());

        for (int i = 0; i < symbols.size(); i += 50) {
            List<String> batch = symbols.subList(i, Math.min(i + 50, symbols.size()));
            try {
                Map<String, QuotesResponse.QuoteData> quotes = thinkOrSwinAPIs.getQuotes(batch);

                for (Map.Entry<String, QuotesResponse.QuoteData> entry : quotes.entrySet()) {
                    String symbol = entry.getKey();
                    QuotesResponse.QuoteData quoteData = entry.getValue();

                    if (quoteData == null || quoteData.getQuote() == null) {
                        continue;
                    }

                    QuotesResponse.Quote quote = quoteData.getQuote();
                    double percentChange = quote.getNetPercentChange();
                    double currentPrice = quote.getLastPrice();
                    double closePrice = quote.getClosePrice();

                    // netPercentChange is negative for drops
                    double dropPct = Math.abs(percentChange);
                    
                    boolean passes = true;
                    if (dropRules != null && !dropRules.isEmpty()) {
                        for (com.hemasundar.technical.NumericRule rule : dropRules) {
                            if (!rule.evaluate(dropPct)) {
                                passes = false;
                                break;
                            }
                        }
                    } else {
                        passes = false;
                    }

                    if (passes) {
                        results.add(buildResult(symbol, currentPrice, quote.getTotalVolume(),
                                dropPct, closePrice, "INTRADAY"));
                        log.info("[{}] Down {}% intraday (${} -> ${})",
                                symbol, String.format("%.2f", dropPct),
                                String.format("%.2f", closePrice), String.format("%.2f", currentPrice));
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching quotes for batch starting at {}: {}", i, e.getMessage());
                if (alertCallback != null) {
                    alertCallback.accept("Batch " + i, e.getMessage());
                }
            }
        }

        log.info("Intraday drop screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Screens for multi-day price drops using Price History API.
     */
    private List<TechnicalScreener.ScreeningResult> screenMultiDayDrop(
            List<String> symbols, List<com.hemasundar.technical.NumericRule> dropRules, int lookbackDays, BiConsumer<String, String> alertCallback) {

        log.info("Screening {} symbols for drop rules over {} days (parallel)",
                symbols.size(), lookbackDays);

        long screenT0 = System.currentTimeMillis();

        // ── Parallel execution (Track B) ──
        List<TechnicalScreener.ScreeningResult> parallelResults = schwabApiExecutor.executeParallel(
                symbols, symbol -> {
                    PriceHistoryCache.HistoricalData cachedData = PriceHistoryCache.getInstance().getHistoricalData(symbol, thinkOrSwinAPIs);
                    PriceHistoryResponse history = cachedData != null ? cachedData.getPriceHistory() : null;

                    if (history == null || history.getCandles() == null
                            || history.getCandles().isEmpty()) {
                        return null;
                    }

                    List<PriceHistoryResponse.CandleData> candles = history.getCandles();
                    int totalBars = candles.size();

                    if (totalBars < lookbackDays + 1) {
                        log.debug("[{}] Not enough price history ({} bars, need {})",
                                symbol, totalBars, lookbackDays + 1);
                        return null;
                    }

                    PriceHistoryResponse.CandleData currentCandle = candles.get(totalBars - 1);
                    double currentPrice = currentCandle.getClose();
                    long volume = currentCandle.getVolume();

                    PriceHistoryResponse.CandleData referenceCandle = candles.get(totalBars - 1 - lookbackDays);
                    double referencePrice = referenceCandle.getClose();

                    if (referencePrice <= 0)
                        return null;

                    double dropPct = ((referencePrice - currentPrice) / referencePrice) * 100.0;

                    boolean passes = true;
                    if (dropRules != null && !dropRules.isEmpty()) {
                        for (com.hemasundar.technical.NumericRule rule : dropRules) {
                            if (!rule.evaluate(dropPct)) {
                                passes = false;
                                break;
                            }
                        }
                    } else {
                        passes = false;
                    }

                    if (passes) {
                        String dropType;
                        if (lookbackDays == 21) {
                            dropType = "1M";
                        } else if (lookbackDays == 63) {
                            dropType = "3M";
                        } else {
                            dropType = lookbackDays + "D";
                        }
                        log.info("[{}] Down {}% over {} days (${} -> ${})",
                                symbol, String.format("%.2f", dropPct), lookbackDays,
                                String.format("%.2f", referencePrice),
                                String.format("%.2f", currentPrice));
                        return buildResult(symbol, currentPrice, volume, dropPct,
                                referencePrice, dropType);
                    }
                    return null;
                }, alertCallback);

        // Collect non-null results
        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();
        for (TechnicalScreener.ScreeningResult r : parallelResults) {
            if (r != null)
                results.add(r);
        }

        log.info("Multi-day drop screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Builds a ScreeningResult with drop-specific fields populated.
     */
    private TechnicalScreener.ScreeningResult buildResult(
            String symbol, double currentPrice, long volume,
            double dropPercent, double referencePrice, String dropType) {

        return TechnicalScreener.ScreeningResult.builder()
                .symbol(symbol)
                .currentPrice(currentPrice)
                .volume(volume)
                .dropPercent(dropPercent)
                .referencePrice(referencePrice)
                .dropType(dropType)
                .build();
    }
}
