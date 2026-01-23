package com.hemasundar.utils;

import com.hemasundar.pojos.TestConfig;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.technical.TechnicalScreener;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for sending messages to Telegram via Bot API.
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
    private static List<String> splitMessage(String message, int maxLength) {
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
     * Sends trade alerts for a strategy to Telegram.
     *
     * @param strategyName The name of the trading strategy
     * @param symbol       The stock symbol
     * @param trades       List of trade setups to send
     */
    public static void sendTradeAlerts(String strategyName, String symbol, List<? extends TradeSetup> trades) {
        if (trades == null || trades.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("<b>📊 ").append(strategyName).append("</b>\n");
        message.append("<b>Symbol: ").append(symbol).append("</b>\n");
        message.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        for (TradeSetup trade : trades) {
            message.append(formatTradeForTelegram(trade));
            message.append("\n");
        }

        sendMessage(message.toString());
    }

    /**
     * Sends trade alerts for a strategy to Telegram.
     * Processes a map of symbol -> trades (batch of results).
     *
     * @param strategyName The name of the trading strategy
     * @param tradesMap    Map of symbol -> trade setups
     */
    public static void sendTradeAlerts(String strategyName, Map<String, List<TradeSetup>> tradesMap) {
        if (tradesMap == null || tradesMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<TradeSetup>> entry : tradesMap.entrySet()) {
            sendTradeAlerts(strategyName, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Sends technical screening alerts to Telegram.
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
            message.append(formatScreeningResult(result));
            message.append("\n");
        }

        sendMessage(message.toString());
    }

    /**
     * Formats a single trade setup for Telegram display.
     *
     * @param trade The trade setup to format
     * @return Formatted string representation
     */
    private static String formatTradeForTelegram(TradeSetup trade) {
        // Use the toString() method which contains all trade details
        String tradeStr = trade.toString();

        // Replace curly braces and format for better readability
        tradeStr = tradeStr.replaceAll("\\{", "\n")
                .replaceAll("}", "")
                .replaceAll(", ", "\n• ");

        return tradeStr;
    }

    /**
     * Formats a screening result for Telegram display.
     */
    private static String formatScreeningResult(TechnicalScreener.ScreeningResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(result.getSymbol()).append("</b>\n");
        sb.append("  💰 Price: $").append(String.format("%.2f", result.getCurrentPrice())).append("\n");

        // Volume
        sb.append("  📊 Volume: ").append(formatVolume(result.getVolume())).append("\n");

        // RSI Section
        sb.append("  📈 RSI: ").append(String.format("%.2f", result.getRsi()));
        sb.append(" (prev: ").append(String.format("%.2f", result.getPreviousRsi())).append(")");
        if (result.isRsiBullishCrossover()) {
            sb.append(" ⬆️ CROSSOVER");
        } else if (result.isRsiBearishCrossover()) {
            sb.append(" ⬇️ CROSSOVER");
        } else if (result.isRsiOversold()) {
            sb.append(" 🔴 OVERSOLD");
        } else if (result.isRsiOverbought()) {
            sb.append(" 🟢 OVERBOUGHT");
        }
        sb.append("\n");

        // Bollinger Bands Section - Condensed
        sb.append("  📉 BB: ");
        if (result.isPriceTouchingLowerBand()) {
            sb.append("Touching Lower ($").append(String.format("%.2f", result.getBollingerLower())).append(")");
        } else if (result.isPriceTouchingUpperBand()) {
            sb.append("Touching Upper ($").append(String.format("%.2f", result.getBollingerUpper())).append(")");
        } else {
            sb.append("Within bands ($").append(String.format("%.2f", result.getBollingerLower()))
                    .append(" - $").append(String.format("%.2f", result.getBollingerUpper())).append(")");
        }
        sb.append("\n");

        // Moving Averages Section - Condensed summary
        List<String> belowMAs = new ArrayList<>();
        List<String> aboveMAs = new ArrayList<>();

        if (result.isPriceBelowMA20())
            belowMAs.add("MA20");
        else
            aboveMAs.add("MA20");
        if (result.isPriceBelowMA50())
            belowMAs.add("MA50");
        else
            aboveMAs.add("MA50");
        if (result.isPriceBelowMA100())
            belowMAs.add("MA100");
        else
            aboveMAs.add("MA100");
        if (result.isPriceBelowMA200())
            belowMAs.add("MA200");
        else
            aboveMAs.add("MA200");

        sb.append("  📊 MAs: ");
        if (!belowMAs.isEmpty()) {
            sb.append("Below ").append(String.join(", ", belowMAs));
        }
        if (!belowMAs.isEmpty() && !aboveMAs.isEmpty()) {
            sb.append(" | ");
        }
        if (!aboveMAs.isEmpty()) {
            sb.append("Above ").append(String.join(", ", aboveMAs));
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Formats volume for display (e.g., 1.5M, 500K).
     */
    private static String formatVolume(long volume) {
        if (volume >= 1_000_000) {
            return String.format("%.2fM", volume / 1_000_000.0);
        } else if (volume >= 1_000) {
            return String.format("%.2fK", volume / 1_000.0);
        }
        return String.valueOf(volume);
    }

    /**
     * Escapes special HTML characters for Telegram's HTML parse mode.
     *
     * @param text The text to escape
     * @return Escaped text safe for HTML
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<b>", "§BOLD_START§")
                .replace("</b>", "§BOLD_END§")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("§BOLD_START§", "<b>")
                .replace("§BOLD_END§", "</b>");
    }
}
