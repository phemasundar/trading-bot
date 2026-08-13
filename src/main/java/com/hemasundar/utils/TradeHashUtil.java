package com.hemasundar.utils;

import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for generating deterministic SHA-256 trade hashes for historical trade deduplication per day.
 * Uses structural similarity criteria (matching Strategy, Ticker, Expiry, Leg Actions, Option Types, and Quantities).
 */
public class TradeHashUtil {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private TradeHashUtil() {
        // Utility class
    }

    /**
     * Generates a deterministic SHA-256 hex string for a trade opportunity based on structural similarity per day.
     *
     * @param strategyId      Strategy ID
     * @param trade           Trade DTO
     * @param executionTimeMs Execution timestamp in milliseconds
     * @return SHA-256 hash string unique per structural trade setup per calendar day
     */
    public static String generateTradeHash(String strategyId, Trade trade, long executionTimeMs) {
        long time = executionTimeMs > 0 ? executionTimeMs : System.currentTimeMillis();
        String dateStr = Instant.ofEpochMilli(time).atZone(MARKET_ZONE).toLocalDate().toString();
        return generateTradeHash(strategyId, trade, dateStr);
    }

    /**
     * Generates a deterministic SHA-256 hex string for a trade opportunity given a specific date string (YYYY-MM-DD).
     * Hashes on structural trade characteristics (Strategy ID, Symbol, Expiry, Leg Actions/Types/Quantities, and Date)
     * matching the similarity condition logic.
     *
     * @param strategyId Strategy ID
     * @param trade      Trade DTO
     * @param dateStr    Execution date string (YYYY-MM-DD)
     * @return SHA-256 hash string
     */
    public static String generateTradeHash(String strategyId, Trade trade, String dateStr) {
        String date = dateStr != null ? dateStr : "";
        if (trade == null) {
            return DigestUtils.sha256Hex(strategyId + ":" + date);
        }

        String symbol = trade.getSymbol() != null ? trade.getSymbol().toUpperCase() : "";
        String expiry = trade.getExpiryDate() != null ? trade.getExpiryDate() : "";

        String legsSummary = "";
        if (trade.getLegs() != null && !trade.getLegs().isEmpty()) {
            legsSummary = trade.getLegs().stream()
                    .map(TradeHashUtil::formatLegStructural)
                    .collect(Collectors.joining("|"));
        }

        String raw = String.format("%s:%s:%s:%s:%s", strategyId, symbol, expiry, legsSummary, date);
        return DigestUtils.sha256Hex(raw);
    }

    private static String formatLegStructural(TradeLegDTO leg) {
        if (leg == null) return "";
        return String.format("%s_%s_%d",
                leg.getAction() != null ? leg.getAction().toUpperCase() : "",
                leg.getOptionType() != null ? leg.getOptionType().toUpperCase() : "",
                leg.getQuantity());
    }
}
