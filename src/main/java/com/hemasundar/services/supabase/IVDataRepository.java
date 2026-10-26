package com.hemasundar.services.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.pojos.IVDataPoint;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for handling IV data operations with Supabase.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class IVDataRepository {
    private static final String IV_DATA_PATH = "/rest/v1/iv_data";
    private static final String IV_STATS_BULK_RPC_PATH = "/rest/v1/rpc/get_iv_stats_bulk";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int BATCH_SIZE = 500;

    private final SupabaseClient client;
    private final Map<String, Map<String, Object>> ivStatsCache = new ConcurrentHashMap<>();

    /**
     * Upserts a single IV data point to Supabase by delegating to {@link #upsertIVDataBatch(List)}.
     *
     * @param dataPoint IV data point to upsert
     * @throws IOException if API call fails
     */
    public void upsertIVData(IVDataPoint dataPoint) throws IOException {
        if (dataPoint != null) {
            upsertIVDataBatch(Collections.singletonList(dataPoint));
        }
    }

    /**
     * Upserts a batch of IV data points to Supabase in chunks of 500 records.
     * Uses PostgreSQL's ON CONFLICT to update if entry exists for same symbol and date.
     * All JSON objects contain uniform keys to satisfy PostgREST batch constraints.
     *
     * @param dataPoints list of IV data points to upsert
     * @throws IOException if API call fails after retries
     */
    public void upsertIVDataBatch(List<IVDataPoint> dataPoints) throws IOException {
        if (CollectionUtils.isEmpty(dataPoints)) {
            return;
        }

        String url = client.getUrl(IV_DATA_PATH + "?on_conflict=symbol,date");

        for (List<IVDataPoint> chunk : ListUtils.partition(dataPoints, BATCH_SIZE)) {
            ArrayNode arrayNode = client.getObjectMapper().createArrayNode();
            for (IVDataPoint dp : chunk) {
                if (dp == null || StringUtils.isBlank(dp.getSymbol())) continue;
                ObjectNode node = client.getObjectMapper().createObjectNode();
                node.put("symbol", dp.getSymbol().toUpperCase());
                node.put("date", dp.getCurrentDate().format(DATE_FORMATTER));
                node.put("strike", dp.getStrike());
                node.put("dte", dp.getDte());
                node.put("expiry_date", dp.getExpiryDate());
                node.put("put_iv", dp.getAtmPutIV());
                node.put("call_iv", dp.getAtmCallIV());
                node.put("underlying_price", dp.getUnderlyingPrice());
                arrayNode.add(node);
            }

            if (arrayNode.isEmpty()) continue;

            String jsonPayload = client.getObjectMapper().writeValueAsString(arrayNode);
            sendUpsertBatchWithRetry(url, jsonPayload, chunk);
        }
    }

    private void sendUpsertBatchWithRetry(String url, String jsonPayload, List<IVDataPoint> batch) throws IOException {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                Response response = client.request()
                        .header("Prefer", "resolution=merge-duplicates,return=minimal")
                        .body(jsonPayload)
                        .post(url);

                int statusCode = response.getStatusCode();

                if (statusCode == 200 || statusCode == 201) {
                    for (IVDataPoint dp : batch) {
                        if (dp != null && dp.getSymbol() != null) {
                            ivStatsCache.remove(dp.getSymbol().toUpperCase());
                        }
                    }
                    log.debug("Successfully upserted {} IV records to Supabase", batch.size());
                    return;
                } else if (statusCode == 429 && retryCount < maxRetries) {
                    retryCount++;
                    long waitTime = (long) Math.pow(2, retryCount) * 1000;
                    log.warn("Rate limit exceeded (429), retry {}/{} after {}ms", retryCount, maxRetries, waitTime);
                    Thread.sleep(waitTime);
                } else {
                    String errorBody = response.getBody() != null ? response.getBody().asString() : "";
                    throw new IOException(String.format("Supabase API error: %d - %s. Body: %s",
                            statusCode, response.getStatusLine(), errorBody));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for retry", e);
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException("Failed to upsert IV batch: " + e.getMessage(), e);
            }
        }

        throw new IOException(String.format("Failed to upsert IV batch of %d items after %d retries", batch.size(), maxRetries));
    }

    /**
     * Computes the IV Rank for a symbol using the PostgreSQL get_iv_stats_bulk RPC.
     *
     * @param symbol stock ticker
     * @return IV Rank in range [0, 100], or {@code null} if data is insufficient (< 20 records)
     * @throws IOException if the Supabase API call fails
     */
    public Double getIVRank(String symbol) throws IOException {
        Map<String, Object> stats = getIVStats(symbol);
        if (stats.get("ivRank") instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }

    /**
     * Computes the IV Percentile for a symbol using the PostgreSQL get_iv_stats_bulk RPC.
     *
     * @param symbol stock ticker
     * @return IV Percentile in range [0, 100], or {@code null} if data is insufficient (< 20 records)
     * @throws IOException if the Supabase API call fails
     */
    public Double getIVPercentile(String symbol) throws IOException {
        Map<String, Object> stats = getIVStats(symbol);
        if (stats.get("ivPercentile") instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }

    /**
     * Returns a map of IV statistics for a symbol over the past 1 year:
     * {@code currentIV}, {@code minIV}, {@code maxIV}, {@code ivPercentile}, {@code ivRank},
     * and {@code recordCount}.
     *
     * @param symbol stock ticker
     * @return map of stats, or empty map if data is insufficient
     * @throws IOException if the Supabase API call fails
     */
    public Map<String, Object> getIVStats(String symbol) throws IOException {
        if (StringUtils.isBlank(symbol)) {
            return Collections.emptyMap();
        }
        String symUpper = symbol.trim().toUpperCase();

        Map<String, Object> cached = ivStatsCache.get(symUpper);
        if (cached != null) {
            return cached;
        }

        Map<String, Map<String, Object>> result = getIVStatsForSymbols(Collections.singletonList(symUpper));
        return result.getOrDefault(symUpper, Collections.emptyMap());
    }

    /**
     * Clears the in-memory IV stats cache.
     */
    public void clearIVStatsCache() {
        ivStatsCache.clear();
        log.debug("IV stats cache cleared");
    }

    /**
     * Preloads IV statistics for a collection of symbols into memory using bulk RPC.
     *
     * @param symbols collection of stock tickers
     */
    public void preloadIVStats(Collection<String> symbols) {
        getIVStatsForSymbols(symbols);
    }

    /**
     * Computes IV statistics for multiple symbols in a single bulk RPC request to Supabase.
     * Results are cached in memory for subsequent single or multi-symbol lookups.
     *
     * @param symbols collection of stock tickers
     * @return map of uppercase symbol to IV stats map
     */
    public Map<String, Map<String, Object>> getIVStatsForSymbols(Collection<String> symbols) {
        if (CollectionUtils.isEmpty(symbols)) {
            return Collections.emptyMap();
        }

        List<String> uncached = symbols.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toUpperCase)
                .distinct()
                .filter(sym -> !ivStatsCache.containsKey(sym))
                .collect(Collectors.toList());

        if (!uncached.isEmpty()) {
            for (List<String> chunk : ListUtils.partition(uncached, BATCH_SIZE)) {
                fetchAndCacheBulkIVStats(chunk);
            }
        }

        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        for (String sym : symbols) {
            if (StringUtils.isNotBlank(sym)) {
                String upper = sym.trim().toUpperCase();
                Map<String, Object> stats = ivStatsCache.get(upper);
                if (stats != null && !stats.isEmpty()) {
                    results.put(upper, stats);
                }
            }
        }
        return results;
    }

    private void fetchAndCacheBulkIVStats(List<String> symbols) {
        try {
            String url = client.getUrl(IV_STATS_BULK_RPC_PATH);
            ObjectNode requestBody = client.getObjectMapper().createObjectNode();
            ArrayNode symbolsNode = requestBody.putArray("p_symbols");
            symbols.forEach(symbolsNode::add);

            String payload = client.getObjectMapper().writeValueAsString(requestBody);
            Response response = client.request()
                    .body(payload)
                    .post(url);

            if (response.getStatusCode() == 200) {
                String body = response.getBody().asString();
                JsonNode arrayNode = client.getObjectMapper().readTree(body);

                if (arrayNode.isArray()) {
                    for (JsonNode item : arrayNode) {
                        String sym = item.hasNonNull("symbol") ? item.get("symbol").asText().toUpperCase() : null;
                        if (sym == null) continue;

                        Map<String, Object> stats = new LinkedHashMap<>();
                        if (item.hasNonNull("current_iv")) stats.put("currentIV", item.get("current_iv").asDouble());
                        if (item.hasNonNull("min_iv")) stats.put("minIV", item.get("min_iv").asDouble());
                        if (item.hasNonNull("max_iv")) stats.put("maxIV", item.get("max_iv").asDouble());
                        if (item.hasNonNull("iv_percentile")) stats.put("ivPercentile", item.get("iv_percentile").asDouble());
                        if (item.hasNonNull("iv_rank")) stats.put("ivRank", item.get("iv_rank").asDouble());
                        if (item.hasNonNull("record_count")) stats.put("recordCount", item.get("record_count").asInt());

                        ivStatsCache.put(sym, stats);
                    }
                    // Negative caching: mark requested symbols that have < 20 records (absent from RPC response)
                    for (String sym : symbols) {
                        ivStatsCache.putIfAbsent(sym, Collections.emptyMap());
                    }
                    log.debug("Successfully retrieved bulk IV stats for {} symbols via RPC", arrayNode.size());
                }
            } else {
                log.error("Failed to fetch bulk IV stats from Supabase RPC: {} - {}",
                        response.getStatusCode(), response.getStatusLine());
            }
        } catch (Exception e) {
            log.error("Exception fetching bulk IV stats via RPC: {}", e.getMessage(), e);
        }
    }
}
