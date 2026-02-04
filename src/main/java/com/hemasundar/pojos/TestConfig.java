package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hemasundar.utils.FilePaths;
import tools.jackson.dataformat.javaprop.JavaPropsMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Immutable configuration record.
 * Fields are final, making it naturally thread-safe.
 * 
 * Configuration priority:
 * 1. Environment variables (for CI/CD pipelines)
 * 2. Properties file (for local development)
 */
public record TestConfig(
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("app_key") String appKey,
        @JsonProperty("pp_secret") String ppSecret,
        @JsonProperty("finnhub_api_key") String finnhubApiKey,
        @JsonProperty("fmp_api_key") String fmpApiKey,
        @JsonProperty("telegram_bot_token") String telegramBotToken,
        @JsonProperty("telegram_chat_id") String telegramChatId,
        @JsonProperty("telegram_enabled") Boolean telegramEnabled,
        @JsonProperty("db.timeout") Integer timeout) {

    /**
     * Lazy-loaded singleton holder.
     * The JVM guarantees that 'INSTANCE' is initialized only once,
     * when Holder is first accessed, ensuring only one IO read.
     */
    private static final class Holder {
        private static final TestConfig INSTANCE = load();

        private static TestConfig load() {
            TestConfig fileConfig = loadFromFile();

            // Override with environment variables if present
            return new TestConfig(
                    getEnvOrDefault("REFRESH_TOKEN", fileConfig.refreshToken()),
                    getEnvOrDefault("APP_KEY", fileConfig.appKey()),
                    getEnvOrDefault("PP_SECRET", fileConfig.ppSecret()),
                    getEnvOrDefault("FINNHUB_API_KEY", fileConfig.finnhubApiKey()),
                    getEnvOrDefault("FMP_API_KEY", fileConfig.fmpApiKey()),
                    getEnvOrDefault("TELEGRAM_BOT_TOKEN", fileConfig.telegramBotToken()),
                    getEnvOrDefault("TELEGRAM_CHAT_ID", fileConfig.telegramChatId()),
                    getBooleanEnvOrDefault("TELEGRAM_ENABLED", fileConfig.telegramEnabled()),
                    fileConfig.timeout());
        }

        private static TestConfig loadFromFile() {
            JavaPropsMapper mapper = JavaPropsMapper.builder().build();
            try (InputStream is = Files.newInputStream(FilePaths.testConfig)) {
                return mapper.readValue(is, TestConfig.class);
            } catch (IOException e) {
                // Return empty config if file doesn't exist (rely on env vars)
                return new TestConfig(null, null, null, null, null, null, null, null, null);
            }
        }

        private static String getEnvOrDefault(String envVar, String defaultValue) {
            String envValue = System.getenv(envVar);
            return (envValue != null && !envValue.isBlank()) ? envValue : defaultValue;
        }

        private static Boolean getBooleanEnvOrDefault(String envVar, Boolean defaultValue) {
            String envValue = System.getenv(envVar);
            if (envValue != null && !envValue.isBlank()) {
                return Boolean.parseBoolean(envValue);
            }
            return defaultValue != null ? defaultValue : true; // Default to enabled
        }
    }

    public static TestConfig getInstance() {
        return Holder.INSTANCE;
    }
}
