package com.hemasundar.services.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Repository for storing and retrieving calculated security technical indicators in Supabase.
 * Indicator values and their evaluation configurations are bundled into structured JSONB columns
 * in {@code latest_security_indicators}, with 1 row per security.
 */
@Log4j2
@Component
public class SecurityIndicatorsRepository {
    private static final String SECURITY_INDICATORS_PATH = "/rest/v1/latest_security_indicators";
    private static final int BATCH_SIZE = 50;

    private final SupabaseClient client;
    private final ObjectMapper mapper;

    public SecurityIndicatorsRepository(SupabaseClient client) {
        this.client = client;
        this.mapper = client.getObjectMapper();
    }

    /**
     * Saves or updates calculated technical indicators for a collection of securities.
     * Bundles configs and values into indicator-specific JSONB columns.
     * Replaces existing rows on duplicate symbols using merge-duplicates upsert.
     *
     * @param results list of calculated screening results
     * @throws IOException if the Supabase request fails
     */
    public void saveSecurityIndicators(List<ScreeningResult> results) throws IOException {
        if (CollectionUtils.isEmpty(results)) {
            return;
        }

        String url = client.getUrl(SECURITY_INDICATORS_PATH);
        String nowStr = Instant.now().toString();

        for (int i = 0; i < results.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, results.size());
            List<ScreeningResult> batch = results.subList(i, end);

            ArrayNode arrayNode = mapper.createArrayNode();
            for (ScreeningResult r : batch) {
                if (r == null || r.getSymbol() == null || r.getSymbol().isBlank()) {
                    continue;
                }
                ObjectNode node = mapper.createObjectNode();
                node.put("symbol", r.getSymbol().toUpperCase());
                if (r.getCompanyName() != null) node.put("company_name", r.getCompanyName());
                node.put("current_price", r.getCurrentPrice());
                if (r.getMarketCapB() != null) node.put("market_cap_b", r.getMarketCapB());

                // 1. RSI (Config + Values, keyed by config name)
                ObjectNode rsiContainer = mapper.createObjectNode();
                ObjectNode rsiDefault = mapper.createObjectNode();
                rsiDefault.put("period", 14);
                rsiDefault.put("oversoldThreshold", 30.0);
                rsiDefault.put("overboughtThreshold", 70.0);
                rsiDefault.put("value", r.getRsi());
                rsiDefault.put("previousValue", r.getPreviousRsi());
                rsiDefault.put("isOversold", r.isRsiOversold());
                rsiDefault.put("isOverbought", r.isRsiOverbought());
                rsiDefault.put("isBullishCrossover", r.isRsiBullishCrossover());
                rsiDefault.put("isBearishCrossover", r.isRsiBearishCrossover());
                rsiContainer.set("default", rsiDefault);
                node.set("rsi", rsiContainer);

                // 2. Bollinger Bands (Config + Values, keyed by config name)
                ObjectNode bbContainer = mapper.createObjectNode();
                ObjectNode bbDefault = mapper.createObjectNode();
                bbDefault.put("period", 20);
                bbDefault.put("stdDev", 2.0);
                bbDefault.put("lower", r.getBollingerLower());
                bbDefault.put("middle", r.getBollingerMiddle());
                bbDefault.put("upper", r.getBollingerUpper());
                bbDefault.put("priceTouchingLower", r.isPriceTouchingLowerBand());
                bbDefault.put("priceTouchingUpper", r.isPriceTouchingUpperBand());
                bbContainer.set("default", bbDefault);
                node.set("bollinger", bbContainer);

                // 3. Moving Averages (SMAs and EMAs)
                ObjectNode maContainer = mapper.createObjectNode();
                maContainer.set("sma", r.getMaValues() != null ? mapper.valueToTree(r.getMaValues()) : mapper.createObjectNode());
                maContainer.set("ema", r.getEmaValues() != null ? mapper.valueToTree(r.getEmaValues()) : mapper.createObjectNode());
                node.set("moving_averages", maContainer);

                // 4. Volume (Current volume and Volume SMAs)
                ObjectNode volContainer = mapper.createObjectNode();
                volContainer.put("currentVolume", r.getVolume());
                volContainer.set("sma", r.getVolumeMaValues() != null ? mapper.valueToTree(r.getVolumeMaValues()) : mapper.createObjectNode());
                node.set("volume", volContainer);

                // 5. Volatility (ATR and Historical Volatility Rank)
                ObjectNode volaContainer = mapper.createObjectNode();
                if (r.getAtr() != null) {
                    ObjectNode atrNode = mapper.createObjectNode();
                    atrNode.put("period", 14);
                    atrNode.put("value", r.getAtr());
                    volaContainer.set("atr", atrNode);
                }
                if (r.getHistoricalVolatilityRank() != null) {
                    ObjectNode hvNode = mapper.createObjectNode();
                    hvNode.put("period", 20);
                    hvNode.put("hvRank", r.getHistoricalVolatilityRank());
                    volaContainer.set("historicalVolatility", hvNode);
                }
                if (r.getIvPercentile() != null || r.getIvRank() != null || r.getCurrentIV() != null || r.getIvDays() != null) {
                    ObjectNode ivNode = mapper.createObjectNode();
                    if (r.getIvPercentile() != null) ivNode.put("ivPercentile", r.getIvPercentile());
                    if (r.getIvRank() != null) ivNode.put("ivRank", r.getIvRank());
                    if (r.getCurrentIV() != null) ivNode.put("currentIV", r.getCurrentIV());
                    if (r.getIvDays() != null) ivNode.put("recordCount", r.getIvDays());
                    volaContainer.set("impliedVolatility", ivNode);
                }
                node.set("volatility", volaContainer);

                // Summaries
                String summary = r.getAllTechnicalIndicatorsSummary() != null
                        ? r.getAllTechnicalIndicatorsSummary()
                        : r.getFormattedSummary();
                if (summary != null) {
                    node.put("indicators_summary", summary);
                }
                node.put("updated_at", nowStr);

                arrayNode.add(node);
            }

            if (arrayNode.isEmpty()) {
                continue;
            }

            try {
                String payload = mapper.writeValueAsString(arrayNode);
                Response response = client.request()
                        .header("Prefer", "resolution=merge-duplicates")
                        .body(payload)
                        .post(url);

                int statusCode = response.getStatusCode();
                if (statusCode != 200 && statusCode != 201) {
                    throw new IOException(String.format(
                            "Failed to save security indicators batch [%d-%d]: %d - %s. Body: %s",
                            i, end, statusCode, response.getStatusLine(), response.getBody().asString()));
                }
                log.info("Successfully saved/updated indicators for {} securities in Supabase", arrayNode.size());
            } catch (Exception e) {
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException("Failed to save security indicators batch: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves all saved security indicators from Supabase.
     */
    public List<ScreeningResult> getAllSecurityIndicators() throws IOException {
        try {
            String url = client.getUrl(SECURITY_INDICATORS_PATH + "?select=*&order=symbol.asc");
            Response response = client.request().get(url);

            if (response.getStatusCode() != 200) {
                throw new IOException(String.format(
                        "Failed to retrieve security indicators: %d - %s",
                        response.getStatusCode(), response.getBody().asString()));
            }

            String body = response.getBody().asString();
            if (body.isEmpty() || body.equals("[]")) {
                return Collections.emptyList();
            }

            JsonNode arrayNode = mapper.readTree(body);
            List<ScreeningResult> results = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                ScreeningResult res = mapJsonToScreeningResult(node);
                if (res != null) {
                    results.add(res);
                }
            }
            return results;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to fetch all security indicators: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves saved security indicators for a specified set of ticker symbols.
     */
    public Map<String, ScreeningResult> getSecurityIndicatorsForSymbols(Collection<String> symbols) throws IOException {
        if (CollectionUtils.isEmpty(symbols)) {
            return Collections.emptyMap();
        }

        try {
            String inFilter = String.join(",", symbols.stream().map(String::toUpperCase).toList());
            String url = client.getUrl(SECURITY_INDICATORS_PATH + "?symbol=in.(" + inFilter + ")&select=*");
            Response response = client.request().get(url);

            if (response.getStatusCode() != 200) {
                throw new IOException(String.format(
                        "Failed to retrieve security indicators for symbols: %d - %s",
                        response.getStatusCode(), response.getBody().asString()));
            }

            String body = response.getBody().asString();
            if (body.isEmpty() || body.equals("[]")) {
                return Collections.emptyMap();
            }

            JsonNode arrayNode = mapper.readTree(body);
            Map<String, ScreeningResult> map = new HashMap<>();
            for (JsonNode node : arrayNode) {
                ScreeningResult res = mapJsonToScreeningResult(node);
                if (res != null && res.getSymbol() != null) {
                    map.put(res.getSymbol().toUpperCase(), res);
                }
            }
            return map;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to fetch security indicators for symbols: " + e.getMessage(), e);
        }
    }

    private ScreeningResult mapJsonToScreeningResult(JsonNode node) {
        try {
            ScreeningResult.ScreeningResultBuilder builder = ScreeningResult.builder()
                    .symbol(node.path("symbol").asText(null))
                    .companyName(node.path("company_name").asText(null))
                    .currentPrice(node.path("current_price").asDouble(0.0))
                    .marketCapB(node.hasNonNull("market_cap_b") ? node.get("market_cap_b").asDouble() : null)
                    .allTechnicalIndicatorsSummary(node.path("indicators_summary").asText(null))
                    .updatedAt(node.hasNonNull("updated_at") ? node.get("updated_at").asText() : null);

            // Unpack RSI (supports multi-config, extracts default or first config)
            JsonNode rsiNode = node.path("rsi");
            if (!rsiNode.isMissingNode() && !rsiNode.isNull()) {
                JsonNode activeRsi = rsiNode.has("default") ? rsiNode.get("default") : (rsiNode.elements().hasNext() ? rsiNode.elements().next() : null);
                if (activeRsi != null) {
                    builder.rsi(activeRsi.path("value").asDouble(0.0))
                            .previousRsi(activeRsi.path("previousValue").asDouble(0.0))
                            .rsiOversold(activeRsi.path("isOversold").asBoolean(false))
                            .rsiOverbought(activeRsi.path("isOverbought").asBoolean(false))
                            .rsiBullishCrossover(activeRsi.path("isBullishCrossover").asBoolean(false))
                            .rsiBearishCrossover(activeRsi.path("isBearishCrossover").asBoolean(false));
                }
            }

            // Unpack Bollinger Bands
            JsonNode bbNode = node.path("bollinger");
            if (!bbNode.isMissingNode() && !bbNode.isNull()) {
                JsonNode activeBb = bbNode.has("default") ? bbNode.get("default") : (bbNode.elements().hasNext() ? bbNode.elements().next() : null);
                if (activeBb != null) {
                    builder.bollingerLower(activeBb.path("lower").asDouble(0.0))
                            .bollingerMiddle(activeBb.path("middle").asDouble(0.0))
                            .bollingerUpper(activeBb.path("upper").asDouble(0.0))
                            .priceTouchingLowerBand(activeBb.path("priceTouchingLower").asBoolean(false))
                            .priceTouchingUpperBand(activeBb.path("priceTouchingUpper").asBoolean(false));
                }
            }

            // Unpack Moving Averages
            JsonNode maNode = node.path("moving_averages");
            if (!maNode.isMissingNode() && !maNode.isNull()) {
                if (maNode.hasNonNull("sma")) {
                    Map<Integer, Double> smaMap = mapper.convertValue(maNode.get("sma"), new TypeReference<Map<Integer, Double>>() {});
                    builder.maValues(smaMap);
                }
                if (maNode.hasNonNull("ema")) {
                    Map<Integer, Double> emaMap = mapper.convertValue(maNode.get("ema"), new TypeReference<Map<Integer, Double>>() {});
                    builder.emaValues(emaMap);
                }
            }

            // Unpack Volume
            JsonNode volNode = node.path("volume");
            if (!volNode.isMissingNode() && !volNode.isNull()) {
                builder.volume(volNode.path("currentVolume").asLong(0L));
                if (volNode.hasNonNull("sma")) {
                    Map<Integer, Double> vmaMap = mapper.convertValue(volNode.get("sma"), new TypeReference<Map<Integer, Double>>() {});
                    builder.volumeMaValues(vmaMap);
                }
            }

            // Unpack Volatility (ATR and HV Rank)
            JsonNode volaNode = node.path("volatility");
            if (!volaNode.isMissingNode() && !volaNode.isNull()) {
                if (volaNode.path("atr").hasNonNull("value")) {
                    builder.atr(volaNode.path("atr").get("value").asDouble());
                }
                if (volaNode.path("historicalVolatility").hasNonNull("hvRank")) {
                    builder.historicalVolatilityRank(volaNode.path("historicalVolatility").get("hvRank").asDouble());
                }
                if (volaNode.path("impliedVolatility").hasNonNull("ivPercentile")) {
                    builder.ivPercentile(volaNode.path("impliedVolatility").get("ivPercentile").asDouble());
                }
                if (volaNode.path("impliedVolatility").hasNonNull("ivRank")) {
                    builder.ivRank(volaNode.path("impliedVolatility").get("ivRank").asDouble());
                }
                if (volaNode.path("impliedVolatility").hasNonNull("currentIV")) {
                    builder.currentIV(volaNode.path("impliedVolatility").get("currentIV").asDouble());
                }
                if (volaNode.path("impliedVolatility").hasNonNull("recordCount")) {
                    builder.ivDays(volaNode.path("impliedVolatility").get("recordCount").asInt());
                } else if (volaNode.path("impliedVolatility").hasNonNull("ivDays")) {
                    builder.ivDays(volaNode.path("impliedVolatility").get("ivDays").asInt());
                }
            }
            if (node.hasNonNull("iv_percentile")) {
                builder.ivPercentile(node.get("iv_percentile").asDouble());
            }
            if (node.hasNonNull("iv_rank")) {
                builder.ivRank(node.get("iv_rank").asDouble());
            }
            if (node.hasNonNull("current_iv")) {
                builder.currentIV(node.get("current_iv").asDouble());
            }
            if (node.hasNonNull("iv_days")) {
                builder.ivDays(node.get("iv_days").asInt());
            } else if (node.hasNonNull("record_count")) {
                builder.ivDays(node.get("record_count").asInt());
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to parse security indicator row: {}", e.getMessage());
            return null;
        }
    }
}
