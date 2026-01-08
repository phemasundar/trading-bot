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
 */
public record TestConfig(
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("app_key") String appKey,
        @JsonProperty("pp_secret") String ppSecret,
        @JsonProperty("finnhub_api_key") String finnhubApiKey,
        @JsonProperty("fmp_api_key") String fmpApiKey,
        @JsonProperty("telegram_bot_token") String telegramBotToken,
        @JsonProperty("telegram_chat_id") String telegramChatId,
        @JsonProperty("db.timeout") Integer timeout) {
    /**
     * Lazy-loaded singleton holder.
     * The JVM guarantees that 'INSTANCE' is initialized only once,
     * when Holder is first accessed, ensuring only one IO read.
     */
    private static final class Holder {
        private static final TestConfig INSTANCE = load();

        private static TestConfig load() {
            // Jackson 3 Builder pattern
            JavaPropsMapper mapper = JavaPropsMapper.builder().build();

            // USE Files.newInputStream for absolute filesystem paths
            try (InputStream is = Files.newInputStream(FilePaths.testConfig)) {
                TestConfig testConfig = mapper.readValue(is, TestConfig.class);
                return testConfig;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load from filesystem: " + FilePaths.testConfig, e);
            }
        }
    }

    public static TestConfig getInstance() {
        return Holder.INSTANCE;
    }
}
