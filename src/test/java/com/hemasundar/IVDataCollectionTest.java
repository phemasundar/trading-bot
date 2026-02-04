package com.hemasundar;

import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.GoogleSheetsService;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.FilePaths;
import lombok.extern.log4j.Log4j2;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TestNG test class for collecting and storing IV data to Google Sheets.
 * Runs daily to track implied volatility for IV rank calculations.
 */
@Log4j2
public class IVDataCollectionTest {

    private GoogleSheetsService sheetsService;
    private SupabaseService supabaseService;
    private Set<String> allSecurities;

    // Database enable flags
    private boolean googleSheetsEnabled;
    private boolean supabaseEnabled;

    @BeforeClass
    public void setup() throws IOException, GeneralSecurityException {
        log.info("=".repeat(80));
        log.info("IV DATA COLLECTION - SETUP");
        log.info("=".repeat(80));

        // Load database configuration
        loadDatabaseConfiguration();

        // Initialize databases based on configuration
        if (googleSheetsEnabled) {
            String spreadsheetId = loadSpreadsheetId();
            sheetsService = new GoogleSheetsService(spreadsheetId);
            log.info("✓ Google Sheets service initialized");
        } else {
            log.info("✗ Google Sheets disabled");
        }

        if (supabaseEnabled) {
            initializeSupabase();
            log.info("✓ Supabase service initialized");
        } else {
            log.info("✗ Supabase disabled");
        }

        // Validate at least one database is enabled
        if (!googleSheetsEnabled && !supabaseEnabled) {
            throw new IllegalStateException(
                    "At least one database must be enabled. " +
                            "Set 'google_sheets_enabled=true' or 'supabase_enabled=true' in test.properties");
        }

        // Load all securities from securities folder
        allSecurities = loadAllSecurities();
        log.info("Loaded {} unique securities from securities folder", allSecurities.size());
        log.info("Active databases: {}{}",
                googleSheetsEnabled ? "Google Sheets " : "",
                supabaseEnabled ? "Supabase" : "");
    }

    /**
     * Loads database enable/disable configuration from test.properties or
     * environment variables.
     * For CI/CD: test.properties may not exist, so we check environment variables.
     */
    private void loadDatabaseConfiguration() throws IOException {
        // First check environment variables (for CI/CD)
        String sheetsEnabledEnv = System.getenv("GOOGLE_SHEETS_ENABLED");
        String supabaseEnabledEnv = System.getenv("SUPABASE_ENABLED");

        if (sheetsEnabledEnv != null || supabaseEnabledEnv != null) {
            // Use environment variables
            googleSheetsEnabled = Boolean.parseBoolean(
                    sheetsEnabledEnv != null ? sheetsEnabledEnv : "true");
            supabaseEnabled = Boolean.parseBoolean(
                    supabaseEnabledEnv != null ? supabaseEnabledEnv : "false");
            log.info("Database configuration loaded from environment: Google Sheets={}, Supabase={}",
                    googleSheetsEnabled, supabaseEnabled);
            return;
        }

        // Fall back to test.properties (for local development)
        try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
            java.util.Properties prop = new java.util.Properties();
            prop.load(input);

            googleSheetsEnabled = Boolean.parseBoolean(
                    prop.getProperty("google_sheets_enabled", "true"));
            supabaseEnabled = Boolean.parseBoolean(
                    prop.getProperty("supabase_enabled", "false"));

            log.info("Database configuration loaded from test.properties: Google Sheets={}, Supabase={}",
                    googleSheetsEnabled, supabaseEnabled);
        } catch (IOException e) {
            // If file doesn't exist (CI/CD), use defaults
            log.warn("test.properties not found, using defaults: Google Sheets=true, Supabase=false");
            googleSheetsEnabled = true;
            supabaseEnabled = false;
        }
    }

    /**
     * Loads Google Sheets spreadsheet ID from configuration.
     * Priority: 1) Environment variable, 2) test.properties file
     */
    private String loadSpreadsheetId() throws IOException {
        // First, try environment variable (for GitHub Actions)
        String spreadsheetId = System.getenv("GOOGLE_SPREADSHEET_ID");

        if (spreadsheetId != null && !spreadsheetId.isEmpty()) {
            log.info("Using spreadsheet ID from environment variable");
            return spreadsheetId;
        }

        // Fall back to test.properties (may not exist in CI/CD)
        if (FilePaths.testConfig.toFile().exists()) {
            try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
                java.util.Properties prop = new java.util.Properties();
                prop.load(input);
                spreadsheetId = prop.getProperty("google_sheets_spreadsheet_id");

                if (spreadsheetId != null && !spreadsheetId.isEmpty() &&
                        !spreadsheetId.equals("YOUR_SPREADSHEET_ID_HERE")) {
                    log.info("Using spreadsheet ID from test.properties");
                    return spreadsheetId;
                }
            } catch (IOException e) {
                log.warn("Failed to read test.properties: {}", e.getMessage());
            }
        }

        throw new IllegalStateException(
                "Google Sheets spreadsheet ID not configured. " +
                        "Set GOOGLE_SPREADSHEET_ID environment variable or 'google_sheets_spreadsheet_id' in test.properties");

    }

    /**
     * Initializes Supabase service from configuration.
     * Priority: 1) Environment variables, 2) test.properties file
     */
    private void initializeSupabase() throws IOException {
        String supabaseUrl = System.getenv("SUPABASE_URL");
        String supabaseKey = System.getenv("SUPABASE_ANON_KEY");

        // Fall back to test.properties (if file exists)
        if ((supabaseUrl == null || supabaseUrl.isEmpty() ||
                supabaseKey == null || supabaseKey.isEmpty()) &&
                FilePaths.testConfig.toFile().exists()) {
            try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
                java.util.Properties prop = new java.util.Properties();
                prop.load(input);

                if (supabaseUrl == null || supabaseUrl.isEmpty()) {
                    supabaseUrl = prop.getProperty("supabase_url");
                }
                if (supabaseKey == null || supabaseKey.isEmpty()) {
                    supabaseKey = prop.getProperty("supabase_anon_key");
                }
            } catch (IOException e) {
                log.warn("Failed to read test.properties: {}", e.getMessage());
            }
        }

        // Validate configuration
        if (supabaseUrl == null || supabaseUrl.isEmpty() ||
                supabaseUrl.equals("YOUR_SUPABASE_PROJECT_URL")) {
            throw new IllegalStateException(
                    "Supabase URL not configured. " +
                            "Set SUPABASE_URL environment variable or 'supabase_url' in test.properties");
        }

        if (supabaseKey == null || supabaseKey.isEmpty() ||
                supabaseKey.equals("YOUR_SUPABASE_PUBLISHABLE_KEY")) {
            throw new IllegalStateException(
                    "Supabase API key not configured. " +
                            "Set SUPABASE_ANON_KEY environment variable or 'supabase_anon_key' in test.properties");
        }

        supabaseService = new SupabaseService(supabaseUrl, supabaseKey);

        // Test connection
        if (!supabaseService.testConnection()) {
            throw new IOException("Failed to connect to Supabase. Check URL and API key.");
        }
    }

    @Test(description = "Collect and store IV data for all securities")
    public void testCollectIVData() {
        log.info("=".repeat(80));
        log.info("COLLECTING IV DATA FOR {} SYMBOLS", allSecurities.size());
        log.info("=".repeat(80));

        int successCount = 0;
        int failCount = 0;

        for (String symbol : allSecurities) {
            try {
                // Collect IV data
                IVDataPoint dataPoint = IVDataCollector.collectIVDataPoint(symbol);

                if (dataPoint != null) {
                    // Store to enabled databases
                    if (googleSheetsEnabled && sheetsService != null) {
                        sheetsService.appendIVData(dataPoint);
                        log.debug("[{}] Saved to Google Sheets", symbol);
                    }

                    if (supabaseEnabled && supabaseService != null) {
                        supabaseService.upsertIVData(dataPoint);
                        log.debug("[{}] Saved to Supabase", symbol);
                    }

                    successCount++;
                    log.info("[{}] Successfully collected and stored IV data", symbol);
                } else {
                    failCount++;
                    log.warn("[{}] Failed to collect IV data", symbol);
                }

                // Delay to avoid API rate limits
                // Google Sheets: 60 write requests per minute
                // Supabase: ~100 requests per second (generous)
                // 1.5 seconds per symbol = max 40 symbols/minute (safe for both)
                Thread.sleep(1500);

            } catch (Exception e) {
                failCount++;
                log.error("[{}] Error during IV data collection: {}", symbol, e.getMessage(), e);
            }
        }

        log.info("=".repeat(80));
        log.info("IV DATA COLLECTION COMPLETE");
        log.info("Success: {}, Failed: {}, Total: {}", successCount, failCount, allSecurities.size());
        log.info("=".repeat(80));

        // Send Telegram notification with summary
        sendTelegramSummary(successCount, failCount, allSecurities.size());

        // Reorder sheets to match YAML order (Google Sheets only)
        if (googleSheetsEnabled && sheetsService != null) {
            try {
                sheetsService.reorderSheets(new ArrayList<>(allSecurities));
                log.info("Successfully reordered sheets to match YAML securities order");
            } catch (IOException e) {
                log.error("Failed to reorder sheets: {}", e.getMessage());
                // Don't fail test for reordering - data is already saved
            }
        }

        // Fail test if no data was collected (indicates authentication or other
        // critical failure)
        if (successCount == 0 && !allSecurities.isEmpty()) {
            throw new AssertionError("IV data collection failed - no symbols processed successfully. " +
                    "Check authentication and Google Sheets access.");
        }
    }

    /**
     * Sends a Telegram notification with IV collection summary.
     * 
     * @param successCount Number of successfully processed symbols
     * @param failCount    Number of failed symbols
     * @param totalCount   Total number of symbols
     */
    private void sendTelegramSummary(int successCount, int failCount, int totalCount) {
        StringBuilder message = new StringBuilder();
        message.append("📊 <b>IV Data Collection Complete</b>\n\n");

        // Success rate emoji
        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
        String statusEmoji = successRate >= 90 ? "✅" : successRate >= 70 ? "⚠️" : "❌";

        message.append(statusEmoji).append(" <b>Summary:</b>\n");
        message.append("├ Total Symbols: <code>").append(totalCount).append("</code>\n");
        message.append("├ Successful: <code>").append(successCount).append("</code>");

        if (totalCount > 0) {
            message.append(" (<code>").append(String.format("%.1f%%", successRate)).append("</code>)");
        }
        message.append("\n");

        if (failCount > 0) {
            message.append("└ Failed: <code>").append(failCount).append("</code>\n");
        } else {
            message.append("└ Failed: <code>0</code> 🎉\n");
        }

        // Database information
        message.append("\n💾 <b>Databases:</b>\n");
        if (googleSheetsEnabled) {
            message.append("├ ✅ Google Sheets\n");
        }
        if (supabaseEnabled) {
            message.append(googleSheetsEnabled ? "└" : "├").append(" ✅ Supabase\n");
        }
        if (!googleSheetsEnabled && !supabaseEnabled) {
            message.append("└ ❌ None\n");
        }

        message.append("\n📅 Date: <code>").append(java.time.LocalDate.now()).append("</code>");
        message.append("\n🕐 Time: <code>").append(java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</code>");

        com.hemasundar.utils.TelegramUtils.sendMessage(message.toString());
    }

    /**
     * Loads all securities from all YAML files in the securities folder.
     * Preserves the order of securities as they appear in the YAML files.
     *
     * @return Set of unique security symbols in order
     */
    private Set<String> loadAllSecurities() {
        Set<String> securities = new LinkedHashSet<>(); // Preserves insertion order
        File securitiesDir = FilePaths.securitiesDirectory.toFile();

        if (!securitiesDir.exists() || !securitiesDir.isDirectory()) {
            log.error("Securities directory not found: {}", FilePaths.securitiesDirectory);
            return securities;
        }

        File[] yamlFiles = securitiesDir.listFiles((dir, name) -> name.endsWith(".yaml"));
        if (yamlFiles == null || yamlFiles.length == 0) {
            log.warn("No YAML files found in securities directory");
            return securities;
        }

        Yaml yaml = new Yaml();
        for (File file : yamlFiles) {
            try (InputStream inputStream = new FileInputStream(file)) {
                // Load as Map since YAML has "securities:" key
                Map<String, List<String>> yamlData = yaml.load(inputStream);
                if (yamlData != null && yamlData.containsKey("securities")) {
                    List<String> fileSecurities = yamlData.get("securities");
                    if (fileSecurities != null) {
                        securities.addAll(fileSecurities);
                        log.info("Loaded {} securities from {}", fileSecurities.size(), file.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error loading securities from {}: {}", file.getName(), e.getMessage());
            }
        }

        if (securities.isEmpty()) {
            throw new IllegalStateException("No securities loaded from securities folder. Check YAML files.");
        }

        return securities;
    }
}
