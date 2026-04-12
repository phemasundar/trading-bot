package com.hemasundar.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilePaths {



    // Securities directory as a filesystem Path — only used by IVDataCollectionTest
    // for directory scanning
    // (only ever runs locally where the source tree is present on disk)
    public static final Path securitiesDirectory = Path.of("src/main/resources/securities");
    public static final Path testConfig = Path.of("src/test/resources/test.properties");

    // Classpath resource names — bundled inside the JAR via src/main/resources/
    // These work both in local IDE runs and inside Docker (via classpath, not
    // filesystem)
    public static final String portfolioSecurities = "securities/1_portfolio.yaml";
    public static final String top100Securities = "securities/top100.yaml";
    public static final String bullishSecurities = "securities/3_bullish.yaml";
    public static final String trackingSecurities = "securities/2_tracking.yaml";
    public static final String securities2026 = "securities/4_2026.yaml";

    public static final String strategiesConfig = "strategies-config.json";

    /**
     * Reads a classpath resource and returns its content as a String.
     * Works both when running from the IDE and from a production JAR (Docker/Cloud
     * Run).
     *
     * @param resourcePath resource path relative to the classpath root (e.g.
     *                     "securities/1_portfolio.yaml")
     * @return file content as String
     * @throws IOException if the resource is not found or cannot be read
     */
    public static String readResource(String resourcePath) throws IOException {
        try (InputStream is = FilePaths.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
