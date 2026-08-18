package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.dto.Trade;
import com.hemasundar.utils.TradeHashUtil;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for persisting and retrieving historical strategy trade records from Supabase.
 */
@Log4j2
@Component
public class TradeHistoryRepository {
    private static final String HISTORICAL_TRADES_PATH = "/rest/v1/historical_trades";
    private static final ZoneId CDT_ZONE = ZoneId.of("America/Chicago");

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public TradeHistoryRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves or updates a collection of strategy execution trades into the historical_trades table.
     * Uses merge-duplicates on trade_hash so that the latest trade details on a given day overwrite earlier entries.
     *
     * @param trades          List of Trade objects
     * @param strategyId      Strategy ID
     * @param strategyName    Strategy name
     * @param executionTimeMs Execution time in milliseconds
     * @throws IOException if database insert fails
     */
    public void saveHistoricalTrades(List<Trade> trades, String strategyId, String strategyName, long executionTimeMs) throws IOException {
        if (trades == null || trades.isEmpty()) {
            return;
        }

        try {
            ArrayNode payloadArray = mapper.createArrayNode();
            java.util.Map<String, ObjectNode> uniqueNodesByHash = new java.util.LinkedHashMap<>();

            for (Trade trade : trades) {
                if (trade == null || trade.getSymbol() == null) continue;

                String hash = TradeHashUtil.generateTradeHash(strategyId, trade, executionTimeMs);

                ObjectNode node = mapper.createObjectNode();
                node.put("trade_hash", hash);
                node.put("strategy_id", strategyId);
                node.put("strategy_name", strategyName != null ? strategyName : strategyId);
                node.put("symbol", trade.getSymbol().toUpperCase());
                node.put("expiry_date", trade.getExpiryDate() != null ? trade.getExpiryDate() : "");
                node.put("execution_time_ms", executionTimeMs);
                node.put("created_at", ZonedDateTime.now(CDT_ZONE).toString());
                node.set("trade_data", mapper.valueToTree(trade));

                uniqueNodesByHash.put(hash, node);
            }

            for (ObjectNode node : uniqueNodesByHash.values()) {
                payloadArray.add(node);
            }

            if (payloadArray.isEmpty()) return;

            String payload = mapper.writeValueAsString(payloadArray);
            String url = client.getUrl(HISTORICAL_TRADES_PATH + "?on_conflict=trade_hash");

            Response response = client.request()
                    .header("Prefer", "resolution=merge-duplicates")
                    .body(payload)
                    .post(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200 || statusCode == 201) {
                log.info("Successfully persisted {} historical trades for strategy: {}", payloadArray.size(), strategyId);
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to save historical trades: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to save historical trades: " + e.getMessage(), e);
        }
    }

    /**
     * Finds historical trades matching the target symbol and strategy, limited to the top N results.
     *
     * @param symbol     Stock symbol
     * @param strategyId Strategy ID
     * @param limit      Maximum number of historical trades to retrieve
     * @return List of Trade objects
     * @throws IOException if query fails
     */
    public List<Trade> findHistoricalTradesBySymbolAndStrategy(String symbol, String strategyId, int limit) throws IOException {
        if (symbol == null || symbol.isBlank()) {
            return Collections.emptyList();
        }

        List<Trade> trades = fetchHistoricalTradesFromSupabase(symbol, strategyId, limit);
        if (trades.isEmpty() && strategyId != null && !strategyId.isBlank()) {
            // Fallback to symbol-only query if strategyId formatting differed so similarity condition can match candidates
            trades = fetchHistoricalTradesFromSupabase(symbol, null, limit);
        }
        return trades;
    }

    private List<Trade> fetchHistoricalTradesFromSupabase(String symbol, String strategyId, int limit) throws IOException {
        try {
            int fetchLimit = limit > 0 ? limit : 50;
            StringBuilder pathBuilder = new StringBuilder(HISTORICAL_TRADES_PATH)
                    .append("?symbol=eq.").append(java.net.URLEncoder.encode(symbol.trim().toUpperCase(), java.nio.charset.StandardCharsets.UTF_8))
                    .append("&select=*&order=created_at.desc&limit=").append(fetchLimit);

            if (strategyId != null && !strategyId.isBlank()) {
                pathBuilder.append("&strategy_id=eq.").append(java.net.URLEncoder.encode(strategyId.trim(), java.nio.charset.StandardCharsets.UTF_8));
            }

            String url = client.getUrl(pathBuilder.toString());
            Response response = client.request().get(url);

            int statusCode = response.getStatusCode();
            if (statusCode == 200) {
                String body = response.getBody().asString();
                if (body.equals("[]") || body.isEmpty()) {
                    return Collections.emptyList();
                }

                JsonNode arrayNode = mapper.readTree(body);
                List<Trade> trades = new ArrayList<>();

                for (JsonNode node : arrayNode) {
                    JsonNode tradeDataNode = node.get("trade_data");
                    if (tradeDataNode != null && !tradeDataNode.isNull()) {
                        Trade trade = mapper.treeToValue(tradeDataNode, Trade.class);
                        if (trade.getFoundDate() == null || trade.getFoundDate().isBlank() || "1969-12-31".equals(trade.getFoundDate()) || "1970-01-01".equals(trade.getFoundDate())) {
                            if (node.hasNonNull("execution_time_ms") && node.get("execution_time_ms").asLong() > 86400000L) {
                                trade.setFoundDate(Instant.ofEpochMilli(node.get("execution_time_ms").asLong())
                                        .atZone(ZoneId.of("America/New_York")).toLocalDate().toString());
                            } else if (node.hasNonNull("created_at")) {
                                String ca = node.get("created_at").asText();
                                try {
                                    trade.setFoundDate(Instant.parse(ca)
                                            .atZone(ZoneId.of("America/New_York")).toLocalDate().toString());
                                } catch (Exception e) {
                                    if (ca.length() >= 10 && !ca.startsWith("1969") && !ca.startsWith("1970") && !ca.equals("null")) {
                                        trade.setFoundDate(ca.substring(0, 10));
                                    }
                                }
                            }
                            if ("1969-12-31".equals(trade.getFoundDate()) || "1970-01-01".equals(trade.getFoundDate())) {
                                trade.setFoundDate(null);
                            }
                        }
                        trades.add(trade);
                    }
                }
                return trades;
            } else {
                String errorBody = response.getBody().asString();
                throw new IOException(String.format("Failed to retrieve historical trades: %d - %s. Body: %s",
                        statusCode, response.getStatusLine(), errorBody));
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to retrieve historical trades: " + e.getMessage(), e);
        }
    }
}
