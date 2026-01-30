package com.hemasundar.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilePaths {
    public static final Path testConfig = Path.of("src/test/resources/test.properties");

    public static final Path portfolioSecurities = Path.of("src/test/resources/securities/portfolio.yaml");
    public static final Path top100Securities = Path.of("src/test/resources/securities/top100.yaml");
    public static final Path bullishSecurities = Path.of("src/test/resources/securities/bullish.yaml");
    public static final Path trackingSecurities = Path.of("src/test/resources/securities/tracking.yaml");
    public static final Path securities2026 = Path.of("src/test/resources/securities/2026.yaml");

    public static final Path strategiesConfig = Path.of("src/test/resources/strategies-config.json");
}
