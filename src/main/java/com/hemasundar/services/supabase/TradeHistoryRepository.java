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

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public TradeHistoryRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves a collection of strategy execution trades into the historical_trades table.
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
                node.set("trade_data", mapper.valueToTree(trade));

                payloadArray.add(node);
            }

            if (payloadArray.isEmpty()) return;

            String payload = mapper.writeValueAsString(payloadArray);
            String url = client.getUrl(HISTORICAL_TRADES_PATH);

            Response response = client.request()
                    .header("Prefer", "resolution=ignore-duplicates")
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

        try {
            StringBuilder urlBuilder = new StringBuilder(HISTORICAL_TRADES_PATH)
                    .append("?symbol=eq.").append(symbol.toUpperCase())
                    .append("&select=*&order=created_at.desc&limit=").append(limit > 0 ? limit : 50);

            if (strategyId != null && !strategyId.isBlank()) {
                urlBuilder.append("&strategy_id=eq.").append(strategyId);
            }

            String url = client.getUrl(urlBuilder.toString());
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
