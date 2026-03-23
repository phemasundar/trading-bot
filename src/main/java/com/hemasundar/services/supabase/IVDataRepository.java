package com.hemasundar.services.supabase;

import com.hemasundar.pojos.IVDataPoint;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Repository for handling IV data operations with Supabase.
 */
@Log4j2
public class IVDataRepository {
    private static final String IV_DATA_PATH = "/rest/v1/iv_data";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SupabaseClient client;

    public IVDataRepository(SupabaseClient client) {
        this.client = client;
    }

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
}
