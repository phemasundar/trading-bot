package com.hemasundar.utils;

import com.hemasundar.pojos.TestConfig;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.technical.TechnicalScreener;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.List;

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
     * Sends a text message to the configured Telegram chat.
     *
     * @param message The message to send
     * @return true if message was sent successfully, false otherwise
     */
    public static boolean sendMessage(String message) {
        String botToken = TestConfig.getInstance().telegramBotToken();
        String chatId = TestConfig.getInstance().telegramChatId();

        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Telegram not configured. Set telegram_bot_token and telegram_chat_id in test.properties");
            return false;
        }

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
        sb.append("  📈 RSI: ").append(String.format("%.2f", result.getRsi()));
        if (result.isRsiBullishCrossover()) {
            sb.append(" ⬆️ CROSSOVER");
        } else if (result.isRsiOversold()) {
            sb.append(" 🔴 OVERSOLD");
        }
        sb.append("\n");
        sb.append("  📉 BB Lower: $").append(String.format("%.2f", result.getBollingerLower()));
        if (result.isPriceTouchingLowerBand()) {
            sb.append(" ✓");
        }
        sb.append("\n");
        sb.append("  📊 MA20: $").append(String.format("%.2f", result.getMa20()));
        if (result.isPriceBelowMA20()) {
            sb.append(" (below)");
        }
        sb.append("\n");
        sb.append("  📊 MA50: $").append(String.format("%.2f", result.getMa50()));
        if (result.isPriceBelowMA50()) {
            sb.append(" (below)");
        }
        sb.append("\n");
        return sb.toString();
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
