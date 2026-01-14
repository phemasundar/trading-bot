package com.hemasundar.utils;

import com.hemasundar.pojos.PriceHistoryResponse;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility class for building ta4j BarSeries from price history data.
 */
public class TechnicalIndicators {

    private TechnicalIndicators() {
        // Private constructor to prevent instantiation
    }

    /**
     * Builds a BarSeries from price history response for use with ta4j indicators.
     *
     * @param symbol   The stock symbol (used as series name)
     * @param response The price history response containing OHLCV candle data
     * @return BarSeries ready for technical analysis
     */
    public static BarSeries buildBarSeries(String symbol, PriceHistoryResponse response) {
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();

        if (response == null || response.getCandles() == null || response.getCandles().isEmpty()) {
            return series;
        }

        for (PriceHistoryResponse.CandleData candle : response.getCandles()) {
            // Convert milliseconds timestamp to ZonedDateTime
            ZonedDateTime endTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(candle.getDatetime()),
                    ZoneId.of("America/New_York"));

            // Add bar using the addBar method with Duration
            series.addBar(
                    Duration.ofDays(1),
                    endTime,
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume());
        }

        return series;
    }
}
