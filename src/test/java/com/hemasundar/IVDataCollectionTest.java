package com.hemasundar;

import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.GoogleSheetsService;
import com.hemasundar.services.IVDataCollector;
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
    private Set<String> allSecurities;

    @BeforeClass
    public void setup() throws IOException, GeneralSecurityException {
        log.info("=".repeat(80));
        log.info("IV DATA COLLECTION - SETUP");
        log.info("=".repeat(80));

        // Load spreadsheet ID from test.properties
        String spreadsheetId = loadSpreadsheetId();

        // Initialize Google Sheets service
        sheetsService = new GoogleSheetsService(spreadsheetId);

        // Load all securities from securities folder
        allSecurities = loadAllSecurities();
        log.info("Loaded {} unique securities from securities folder", allSecurities.size());
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

        // Fall back to test.properties
        try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
            java.util.Properties prop = new java.util.Properties();
            prop.load(input);
            spreadsheetId = prop.getProperty("google_sheets_spreadsheet_id");

            if (spreadsheetId == null || spreadsheetId.isEmpty() ||
                    spreadsheetId.equals("YOUR_SPREADSHEET_ID_HERE")) {
                throw new IllegalStateException(
                        "Google Sheets spreadsheet ID not configured. " +
                                "Please set 'google_sheets_spreadsheet_id' in test.properties");
            }

            log.info("Using spreadsheet ID from test.properties");
            return spreadsheetId;

        } catch (IOException e) {
            throw new IOException("Failed to load test.properties: " + e.getMessage(), e);
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
                    // Store to Google Sheets
                    sheetsService.appendIVData(dataPoint);
                    successCount++;
                    log.info("[{}] Successfully collected and stored IV data", symbol);
                } else {
                    failCount++;
                    log.warn("[{}] Failed to collect IV data", symbol);
                }

                // Delay to avoid Google Sheets API rate limit (60 write requests per minute)
                // 1.5 seconds per symbol = max 40 symbols/minute (safe buffer)
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
    }

    /**
     * Loads all securities from all YAML files in the securities folder.
     * Preserves the order of securities as they appear in the YAML files.
     *
     * @return Set of unique security symbols in order
     */
    private Set<String> loadAllSecurities() {
        Set<String> securities = new java.util.LinkedHashSet<>(); // Preserves insertion order
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
