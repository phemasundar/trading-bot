package com.hemasundar.services.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hemasundar.pojos.IVDataPoint;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Repository for handling IV data operations with Supabase.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class IVDataRepository {
    private static final String IV_DATA_PATH = "/rest/v1/iv_data";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SupabaseClient client;

    /**
     * Upserts (inserts or updates) IV data point to Supabase.
     * Uses PostgreSQL's ON CONFLICT to update if entry exists for same symbol and date.
     *
     * @param dataPoint IV data point to upsert
     * @throws IOException if API call fails after retries
     */
    public void upsertIVData(IVDataPoint dataPoint) throws IOException {
        String symbol = dataPoint.getSymbol();
        String date = dataPoint.getCurrentDate().format(DATE_FORMATTER);
        String jsonPayload = buildJsonPayload(dataPoint);
        String url = client.getUrl(IV_DATA_PATH + "?on_conflict=symbol,date");

        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                Response response = client.request()
                        .header("Prefer", "resolution=merge-duplicates") // UPSERT behavior
                        .body(jsonPayload)
                        .post(url);

                int statusCode = response.getStatusCode();

                if (statusCode == 200 || statusCode == 201) {
                    log.info("[{}] Successfully upserted IV data for {} - PUT: {}%, CALL: {}%",
                            symbol, date, dataPoint.getAtmPutIV(), dataPoint.getAtmCallIV());
                    return; // Success
                } else if (statusCode == 429 && retryCount < maxRetries) {
                    retryCount++;
                    long waitTime = (long) Math.pow(2, retryCount) * 1000;
                    log.warn("[{}] Rate limit exceeded (429), retry {}/{} after {}ms",
                            symbol, retryCount, maxRetries, waitTime);
                    Thread.sleep(waitTime);
                } else {
                    String errorBody = response.getBody().asString();
                    throw new IOException(String.format(
                            "[%s] Supabase API error: %d - %s. Body: %s",
                            symbol, statusCode, response.getStatusLine(), errorBody));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for retry", e);
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException("Failed to upsert data: " + e.getMessage(), e);
            }
        }

        throw new IOException(String.format("[%s] Failed to upsert data after %d retries", symbol, maxRetries));
    }

    private String buildJsonPayload(IVDataPoint dataPoint) {
        return String.format(
                "{\"symbol\":\"%s\",\"date\":\"%s\",\"strike\":%s,\"dte\":%d,\"expiry_date\":\"%s\",\"put_iv\":%s,\"call_iv\":%s,\"underlying_price\":%s}",
                dataPoint.getSymbol(),
                dataPoint.getCurrentDate().format(DATE_FORMATTER),
                dataPoint.getStrike(),
                dataPoint.getDte(),
                dataPoint.getExpiryDate(),
                dataPoint.getAtmPutIV(),
                dataPoint.getAtmCallIV(),
                dataPoint.getUnderlyingPrice());
    }

    /**
     * Computes the IV Rank for a symbol using up to 1 year of historical data from Supabase.
     *
     * <p>IV Rank = (current_iv - min_iv) / (max_iv - min_iv) * 100
     * where {@code current_iv} is the average of the most recent row's put_iv and call_iv.
     *
     * <p>Returns {@code null} (fail-open) when fewer than 2 records exist for the symbol,
     * meaning the filter should be skipped for that symbol.
     *
     * @param symbol stock ticker
     * @return IV Rank in range [0, 100], or {@code null} if data is insufficient
     * @throws IOException if the Supabase API call fails
     */
    public Double getIVRank(String symbol) throws IOException {
        String oneYearAgo = LocalDate.now().minusYears(1).format(DATE_FORMATTER);
        // Fetch symbol, date, put_iv, call_iv for the last year, newest first
        String url = client.getUrl(
                IV_DATA_PATH
                + "?select=date,put_iv,call_iv"
                + "&symbol=eq." + symbol
                + "&date=gte." + oneYearAgo
                + "&order=date.desc"
        );

        Response response = client.request().get(url);
        if (response.getStatusCode() != 200) {
            throw new IOException(String.format(
                    "[%s] IV Rank query failed: %d - %s",
                    symbol, response.getStatusCode(), response.getStatusLine()));
        }

        List<Map<String, Object>> rows;
        try {
            rows = client.getObjectMapper().readValue(
                    response.getBody().asString(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IOException("[" + symbol + "] Failed to parse IV data response: " + e.getMessage(), e);
        }

        if (rows == null || rows.size() < 2) {
            log.debug("[{}] Insufficient IV data for rank calculation ({} records) — skipping filter",
                    symbol, rows == null ? 0 : rows.size());
            return null; // fail-open
        }

        // Current IV = average of latest row's put_iv and call_iv
        Map<String, Object> latest = rows.get(0);
        double currentIV = toAvgIV(latest, symbol);

        // Find min and max average IV across the full window
        double minIV = currentIV;
        double maxIV = currentIV;
        for (Map<String, Object> row : rows) {
            double avg = toAvgIV(row, symbol);
            if (avg < minIV) minIV = avg;
            if (avg > maxIV) maxIV = avg;
        }

        if (maxIV == minIV) {
            // All values identical — IV Rank is 0 by convention (no range)
            log.debug("[{}] IV range is zero (all values = {}), returning IV Rank = 0", symbol, currentIV);
            return 0.0;
        }

        double ivRank = (currentIV - minIV) / (maxIV - minIV) * 100.0;
        log.debug("[{}] IV Rank = {:.1f}% (current={}, min={}, max={}, {} records)",
                symbol, ivRank, currentIV, minIV, maxIV, rows.size());
        return ivRank;
    }

    /**
     * Extracts the average IV from a row map (average of put_iv and call_iv).
     * Falls back to whichever field is non-null; returns 0.0 if both are null.
     */
    private double toAvgIV(Map<String, Object> row, String symbol) {
        Object putIVObj  = row.get("put_iv");
        Object callIVObj = row.get("call_iv");
        double putIV  = putIVObj  != null ? ((Number) putIVObj).doubleValue()  : 0.0;
        double callIV = callIVObj != null ? ((Number) callIVObj).doubleValue() : 0.0;
        if (putIVObj == null && callIVObj == null) {
            log.warn("[{}] Row has null put_iv and call_iv, treating avg IV as 0", symbol);
            return 0.0;
        }
        if (putIVObj == null)  return callIV;
        if (callIVObj == null) return putIV;
        return (putIV + callIV) / 2.0;
    }

    /**
     * Returns a map of IV statistics for a symbol over the past 1 year:
     * {@code minIV}, {@code maxIV}, {@code currentIV}, and {@code recordCount}.
     *
     * <p>Returns {@code null} when fewer than 2 records exist (fail-open).
     *
     * @param symbol stock ticker
     * @return map of stats, or {@code null} if data is insufficient
     * @throws IOException if the Supabase API call fails
     */
    public Map<String, Object> getIVStats(String symbol) throws IOException {
        String oneYearAgo = LocalDate.now().minusYears(1).format(DATE_FORMATTER);
        String url = client.getUrl(
                IV_DATA_PATH
                + "?select=date,put_iv,call_iv"
                + "&symbol=eq." + symbol
                + "&date=gte." + oneYearAgo
                + "&order=date.desc"
        );

        Response response = client.request().get(url);
        if (response.getStatusCode() != 200) {
            throw new IOException(String.format(
                    "[%s] IV stats query failed: %d - %s",
                    symbol, response.getStatusCode(), response.getStatusLine()));
        }

        List<Map<String, Object>> rows;
        try {
            rows = client.getObjectMapper().readValue(
                    response.getBody().asString(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IOException("[" + symbol + "] Failed to parse IV stats response: " + e.getMessage(), e);
        }

        if (rows == null || rows.size() < 2) {
            return null;
        }

        double currentIV = toAvgIV(rows.get(0), symbol);
        double minIV = currentIV;
        double maxIV = currentIV;
        for (Map<String, Object> row : rows) {
            double avg = toAvgIV(row, symbol);
            if (avg < minIV) minIV = avg;
            if (avg > maxIV) maxIV = avg;
        }

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("currentIV",   Math.round(currentIV * 100.0) / 100.0);
        stats.put("minIV",       Math.round(minIV     * 100.0) / 100.0);
        stats.put("maxIV",       Math.round(maxIV     * 100.0) / 100.0);
        stats.put("recordCount", rows.size());
        return stats;
    }
}
