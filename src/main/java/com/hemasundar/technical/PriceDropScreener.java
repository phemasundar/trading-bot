package com.hemasundar.technical;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.pojos.QuotesResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@lombok.RequiredArgsConstructor
public class PriceDropScreener {

    private final ThinkOrSwinAPIs thinkOrSwinAPIs;

    /**
     * Screens stocks for price drops over a given number of trading days.
     * When lookbackDays is 0, uses intraday (daily) percent change from Quotes API.
     * When lookbackDays > 0, uses Price History API to compute multi-day change.
     *
     * @param symbols        List of stock symbols to screen
     * @param minDropPercent Minimum drop percentage threshold (e.g., 5.0 for 5%)
     * @param lookbackDays   Number of trading days to look back (0 = intraday)
     * @return List of ScreeningResult for stocks matching the criteria
     */
    public List<TechnicalScreener.ScreeningResult> screenPriceDrop(
            List<String> symbols, double minDropPercent, int lookbackDays) {

        if (lookbackDays == 0) {
            return screenIntradayDrop(symbols, minDropPercent);
        } else {
            return screenMultiDayDrop(symbols, minDropPercent, lookbackDays);
        }
    }

    /**
     * Screens stocks for drops from their 52-week high.
     *
     * @param symbols        List of stock symbols to screen
     * @param minDropPercent Minimum drop percentage from 52-week high (e.g., 20.0 for 20%)
     * @return List of ScreeningResult for stocks matching the criteria
     */
    public List<TechnicalScreener.ScreeningResult> screen52WeekHighDrop(
            List<String> symbols, double minDropPercent) {

        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();

        log.info("Screening {} symbols for >= {}% drop from 52-week high", symbols.size(), minDropPercent);

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

                    if (dropPct >= minDropPercent) {
                        results.add(buildResult(symbol, currentPrice, quote.getTotalVolume(),
                                dropPct, high52w, "52W_HIGH"));
                        log.info("[{}] Down {}% from 52W high (${} -> ${})",
                                symbol, String.format("%.2f", dropPct),
                                String.format("%.2f", high52w), String.format("%.2f", currentPrice));
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching quotes for batch starting at {}: {}", i, e.getMessage());
            }
        }

        log.info("52-Week High Drop screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Screens for intraday price drops using Quotes API netPercentChange.
     */
    private List<TechnicalScreener.ScreeningResult> screenIntradayDrop(
            List<String> symbols, double minDropPercent) {

        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();

        log.info("Screening {} symbols for >= {}% intraday drop", symbols.size(), minDropPercent);

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
                    if (percentChange <= -minDropPercent) {
                        double dropPct = Math.abs(percentChange);
                        results.add(buildResult(symbol, currentPrice, quote.getTotalVolume(),
                                dropPct, closePrice, "INTRADAY"));
                        log.info("[{}] Down {}% intraday (${} -> ${})",
                                symbol, String.format("%.2f", dropPct),
                                String.format("%.2f", closePrice), String.format("%.2f", currentPrice));
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching quotes for batch starting at {}: {}", i, e.getMessage());
            }
        }

        log.info("Intraday drop screening complete. Found {} stocks matching criteria.", results.size());
        return results;
    }

    /**
     * Screens for multi-day price drops using Price History API.
     */
    private List<TechnicalScreener.ScreeningResult> screenMultiDayDrop(
            List<String> symbols, double minDropPercent, int lookbackDays) {

        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();

        log.info("Screening {} symbols for >= {}% drop over {} days", symbols.size(), minDropPercent, lookbackDays);

        for (String symbol : symbols) {
            try {
                // Fetch enough daily data to cover the lookback period
                PriceHistoryResponse history = thinkOrSwinAPIs.getPriceHistory(
                        symbol, "month", 1, "daily", 1, null, null, false, false);

                if (history == null || history.getCandles() == null || history.getCandles().isEmpty()) {
                    continue;
                }

                List<PriceHistoryResponse.CandleData> candles = history.getCandles();
                int totalBars = candles.size();

                if (totalBars < lookbackDays + 1) {
                    log.debug("[{}] Not enough price history ({} bars, need {})", symbol, totalBars, lookbackDays + 1);
                    continue;
                }

                // Current price is the last candle's close
                PriceHistoryResponse.CandleData currentCandle = candles.get(totalBars - 1);
                double currentPrice = currentCandle.getClose();
                long volume = currentCandle.getVolume();

                // Reference price is the close from N trading days ago
                PriceHistoryResponse.CandleData referenceCandle = candles.get(totalBars - 1 - lookbackDays);
                double referencePrice = referenceCandle.getClose();

                if (referencePrice <= 0) {
                    continue;
                }

                double dropPct = ((referencePrice - currentPrice) / referencePrice) * 100.0;

                if (dropPct >= minDropPercent) {
                    results.add(buildResult(symbol, currentPrice, volume,
                            dropPct, referencePrice, lookbackDays + "D"));
                    log.info("[{}] Down {}% over {} days (${} -> ${})",
                            symbol, String.format("%.2f", dropPct), lookbackDays,
                            String.format("%.2f", referencePrice), String.format("%.2f", currentPrice));
                }
            } catch (Exception e) {
                log.warn("[{}] Error analyzing multi-day drop: {}", symbol, e.getMessage());
            }
        }

        log.info("{}-Day drop screening complete. Found {} stocks matching criteria.", lookbackDays, results.size());
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
