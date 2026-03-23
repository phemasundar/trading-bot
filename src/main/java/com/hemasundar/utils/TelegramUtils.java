package com.hemasundar.utils;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.technical.TechnicalScreener;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for sending messages to Telegram via Bot API.
 * <p>
 * This class is a pure <b>message transport</b> layer. It does NOT contain any
 * trade-specific or screener-specific formatting logic. All formatting is done
 * upstream in the DTO layer ({@link Trade#getTradeDetails()},
 * {@link TechnicalScreener.ScreeningResult#getFormattedSummary()}).
 * <p>
 * Requires telegram_bot_token and telegram_chat_id to be configured in
 * test.properties.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TelegramUtils {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";
    /**
     * Telegram's maximum message length is 4096 characters.
     * We use a slightly smaller limit to account for HTML escaping overhead.
     */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    // ==================== Public API ====================

    /**
     * Sends a text message to the configured Telegram chat.
     * If the message exceeds Telegram's limit, it will be automatically split
     * into multiple messages at newline boundaries.
     * If telegram_enabled is false, logs the message to console instead.
     *
     * @param message The message to send
     * @return true if all message parts were sent/logged successfully, false
     *         otherwise
     */
    public static boolean sendMessage(String message) {
        Boolean telegramEnabled = TestConfig.getInstance().telegramEnabled();

        // If Telegram is disabled, just log to console
        if (telegramEnabled != null && !telegramEnabled) {
            log.info("[TELEGRAM DISABLED] Message would be sent:\n{}", message);
            return true;
        }

        String botToken = TestConfig.getInstance().telegramBotToken();
        String chatId = TestConfig.getInstance().telegramChatId();

        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Telegram not configured. Set telegram_bot_token and telegram_chat_id in test.properties");
            log.info("[TELEGRAM] Message:\n{}", message);
            return false;
        }

        // Split message if too long
        List<String> messageParts = splitMessage(message, MAX_MESSAGE_LENGTH);
        boolean allSuccess = true;

        for (int i = 0; i < messageParts.size(); i++) {
            String part = messageParts.get(i);
            // Add part indicator if message was split
            if (messageParts.size() > 1) {
                part = String.format("(%d/%d)\n%s", i + 1, messageParts.size(), part);
            }

            if (!sendSingleMessage(botToken, chatId, part)) {
                allSuccess = false;
            }

            // Small delay between messages to avoid rate limiting
            if (i < messageParts.size() - 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return allSuccess;
    }

    /**
     * Sends trade alerts for a strategy result to Telegram.
     * Uses the pre-formatted {@link Trade#getTradeDetails()} from each trade DTO,
     * wrapping it with a Telegram-specific HTML header (bold, emojis).
     *
     * @param result The strategy result containing trades with pre-formatted details
     */
    public static void sendTradeAlerts(StrategyResult result) {
        if (result == null || result.getTrades() == null || result.getTrades().isEmpty()) {
            return;
        }

        // Group trades by symbol+expiry for per-symbol messages
        // (matches previous behavior where each symbol/expiry combination was a separate message)
        var tradesByKey = new java.util.LinkedHashMap<String, List<Trade>>();
        for (Trade trade : result.getTrades()) {
            String key = trade.getSymbol() + "_" + trade.getExpiryDate();
            tradesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(trade);
        }

        for (var entry : tradesByKey.entrySet()) {
            List<Trade> trades = entry.getValue();
            Trade firstTrade = trades.get(0);

            StringBuilder message = new StringBuilder();
            message.append("<b>📊 ").append(result.getStrategyName()).append("</b>\n");
            message.append("<b>💰 ").append(firstTrade.getSymbol()).append(" @ $")
                    .append(String.format("%.2f", firstTrade.getUnderlyingPrice())).append("</b>\n");
            message.append("<b>📅 Expiry: ").append(formatExpiryDate(firstTrade.getExpiryDate()))
                    .append(" (").append(firstTrade.getDte()).append(" DTE)</b>\n");
            message.append("━━━━━━━━━━━━━━━━━━━━\n\n");

            int tradeNum = 1;
            for (Trade trade : trades) {
                message.append("<b>Trade ").append(tradeNum++).append(":</b>\n");
                message.append(trade.getTradeDetails());
                message.append("\n\n");
            }

            sendMessage(message.toString());
        }
    }

    /**
     * Sends technical screening alerts to Telegram.
     * Uses the pre-formatted {@link TechnicalScreener.ScreeningResult#getFormattedSummary()}
     * from each result, wrapping it with a Telegram-specific HTML header.
     *
     * @param screenerName Name of the screener for display in the alert
     * @param results      List of screening results to send
     */
    public static void sendTechnicalScreenerAlert(String screenerName,
            List<TechnicalScreener.ScreeningResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("<b>🔍 ").append(screenerName).append("</b>\n");
        message.append("<b>Found ").append(results.size()).append(" stocks matching criteria</b>\n");
        message.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        for (TechnicalScreener.ScreeningResult result : results) {
            message.append("<b>").append(result.getSymbol()).append("</b>\n");
            message.append(result.getFormattedSummary());
            message.append("\n");
        }

        sendMessage(message.toString());
    }

    // ==================== Internal Helpers ====================

    /**
     * Sends a single message part to Telegram.
     */
    private static boolean sendSingleMessage(String botToken, String chatId, String message) {
        try {
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body("""
                            {
                                "chat_id": "%s",
                                "text": "%s",
                                "parse_mode": "HTML"
                            }
                            """.formatted(chatId, escapeHtml(message)))
                    .when()
                    .post(TELEGRAM_API_BASE + botToken + "/sendMessage");

            if (response.statusCode() == 200) {
                log.info("Telegram message sent successfully");
                return true;
            } else {
                log.warn("Failed to send Telegram message: {} - {}", response.statusCode(), response.asString());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending Telegram message", e);
            return false;
        }
    }

    /**
     * Splits a message into multiple parts respecting the maximum length.
     * Tries to split at newline boundaries to preserve formatting.
     *
     * @param message   The message to split
     * @param maxLength Maximum length of each part
     * @return List of message parts
     */
    static List<String> splitMessage(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return List.of(message == null ? "" : message);
        }

        List<String> parts = new ArrayList<>();
        String remaining = message;

        while (!remaining.isEmpty()) {
            if (remaining.length() <= maxLength) {
                parts.add(remaining);
                break;
            }

            // Find the best split point (prefer newlines)
            int splitPoint = findSplitPoint(remaining, maxLength);
            parts.add(remaining.substring(0, splitPoint).trim());
            remaining = remaining.substring(splitPoint).trim();
        }

        return parts;
    }

    /**
     * Finds the best point to split the message.
     * Prefers splitting at double newlines (paragraph breaks), then single
     * newlines.
     */
    private static int findSplitPoint(String text, int maxLength) {
        // Look for double newline (paragraph break) first
        int doubleNewline = text.lastIndexOf("\n\n", maxLength);
        if (doubleNewline > maxLength / 2) {
            return doubleNewline + 2;
        }

        // Look for single newline
        int singleNewline = text.lastIndexOf("\n", maxLength);
        if (singleNewline > maxLength / 2) {
            return singleNewline + 1;
        }

        // Look for space
        int space = text.lastIndexOf(" ", maxLength);
        if (space > maxLength / 2) {
            return space + 1;
        }

        // No good break point found, just cut at max length
        return maxLength;
    }

    /**
     * Formats expiry date from ISO format to readable format.
     */
    private static String formatExpiryDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return "Unknown";
        }
        return isoDate.length() >= 10 ? isoDate.substring(0, 10) : isoDate;
    }

    /**
     * Escapes special HTML characters for Telegram's HTML parse mode.
     * Preserves {@code <b>} and {@code </b>} tags which Telegram supports.
     *
     * @param text The text to escape
     * @return Escaped text safe for HTML
     */
    static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<b>", "§BOLD_START§")
                .replace("</b>", "§BOLD_END§")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("§BOLD_START§", "<b>")
                .replace("§BOLD_END§", "</b>");
    }
}
