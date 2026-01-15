package com.hemasundar.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Centralized handler for API errors.
 * For 400 errors: logs warning and sends Telegram notification.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiErrorHandler {

    /**
     * Handles 400 response errors by logging and sending Telegram notification.
     * This allows execution to continue (caller should return null/empty).
     *
     * @param apiName   Name of the API that failed (e.g., "Quote API")
     * @param symbol    The stock symbol (e.g., "AAPL")
     * @param errorBody The error response body from the API
     */
    public static void handle400Error(String apiName, String symbol, String errorBody) {
        String message = String.format("⚠️ API 400 Error\n<b>API:</b> %s\n<b>Symbol:</b> %s\n<b>Error:</b> %s",
                apiName, symbol, errorBody);

        log.warn("[{}] {} failed with 400: {}", symbol, apiName, errorBody);
        TelegramUtils.sendMessage(message);
    }
}
