package com.hemasundar.config;

import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.technical.ScreenerType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RuntimeConfigTest {

    @Test
    public void testLoad_Success() throws IOException {
        String json = "{\"strategies\":{\"PUT_CREDIT_SPREAD\":{\"enabled\":false}},\"screeners\":{\"PRICE_DROP\":{\"enabled\":true}}}";
        Path tempFile = Files.createTempFile("runtime-config-test", ".json");
        Files.writeString(tempFile, json);

        try {
            RuntimeConfig config = RuntimeConfig.load(tempFile);
            Assert.assertFalse(config.isStrategyEnabled(StrategyType.PUT_CREDIT_SPREAD));
            Assert.assertTrue(config.isScreenerEnabled(ScreenerType.PRICE_DROP));
            // Default enabled
            Assert.assertTrue(config.isStrategyEnabled(StrategyType.BULLISH_ZEBRA));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testLoad_FileNotFound() {
        Path nonExistent = Path.of("non-existent-file.json");
        RuntimeConfig config = RuntimeConfig.load(nonExistent);
        
        Assert.assertNotNull(config);
        // All should be enabled by default
        Assert.assertTrue(config.isStrategyEnabled(StrategyType.PUT_CREDIT_SPREAD));
        Assert.assertTrue(config.isScreenerEnabled(ScreenerType.PRICE_DROP));
    }

    @Test
    public void testLoad_MalformedJson() throws IOException {
        String json = "{\"strategies\":{\"invalid\": }}";
        Path tempFile = Files.createTempFile("runtime-config-error", ".json");
        Files.writeString(tempFile, json);

        try {
            RuntimeConfig config = RuntimeConfig.load(tempFile);
            Assert.assertNotNull(config);
            // Fallback to empty config (default enabled)
            Assert.assertTrue(config.isStrategyEnabled(StrategyType.PUT_CREDIT_SPREAD));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testSettingsAccessors() {
        RuntimeConfig config = new RuntimeConfig();
        RuntimeSettings settings = new RuntimeSettings();
        settings.setEnabled(false);
        config.getStrategies().put(StrategyType.BULLISH_ZEBRA.name(), settings);
        
        Assert.assertEquals(config.getStrategySettings(StrategyType.BULLISH_ZEBRA), settings);
        Assert.assertFalse(config.isStrategyEnabled(StrategyType.BULLISH_ZEBRA));
        
        RuntimeSettings screenerSettings = new RuntimeSettings();
        screenerSettings.setEnabled(true);
        config.getScreeners().put(ScreenerType.RSI_OVERSOLD.name(), screenerSettings);
        Assert.assertEquals(config.getScreenerSettings(ScreenerType.RSI_OVERSOLD), screenerSettings);
        Assert.assertTrue(config.isScreenerEnabled(ScreenerType.RSI_OVERSOLD));
    }
}
