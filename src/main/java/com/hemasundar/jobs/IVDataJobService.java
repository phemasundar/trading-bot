package com.hemasundar.jobs;

import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.GoogleSheetsService;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.TelegramUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@Log4j2
public class IVDataJobService {

    @Autowired(required = false)
    private SupabaseService supabaseService;

    private GoogleSheetsService sheetsService;
    private Set<String> allSecurities;

    private boolean googleSheetsEnabled;
    private boolean supabaseEnabled;

    private int successCount = 0;
    private int failCount = 0;
    private int totalCount = 0;

    public void runIVDataCollection() {
        log.info("=".repeat(80));
        log.info("IV DATA COLLECTION - SETUP");
        log.info("=".repeat(80));

        try {
            // Load database configuration
            loadDatabaseConfiguration();

            if (googleSheetsEnabled) {
                String spreadsheetId = loadSpreadsheetId();
                sheetsService = new GoogleSheetsService(spreadsheetId);
                log.info("✓ Google Sheets service initialized");
            } else {
                log.info("✗ Google Sheets disabled");
            }

            if (supabaseEnabled && supabaseService != null) {
                log.info("✓ Supabase service via Spring Bean active");
            } else if (supabaseEnabled) {
                initializeSupabaseManual();
            } else {
                log.info("✗ Supabase disabled");
            }

            if (!googleSheetsEnabled && (!supabaseEnabled || supabaseService == null)) {
                log.error("At least one database must be enabled.");
                return;
            }

            allSecurities = loadAllSecurities();
            log.info("Loaded {} unique securities", allSecurities.size());

            executeCollection();
            sendTelegramSummary();

        } catch (Exception e) {
            log.error("Failed to initialize or run IV Data Collection", e);
        }
    }

    private void executeCollection() {
        totalCount = allSecurities.size();
        successCount = 0;
        failCount = 0;

        for (String symbol : allSecurities) {
            try {
                IVDataPoint dataPoint = null;
                try {
                    dataPoint = IVDataCollector.collectIVDataPoint(symbol);
                } catch (Exception e) {
                    log.warn("[{}] IV Data Collection Failed: {}", symbol, e.getMessage());
                    failCount++;
                    continue;
                }

                if (dataPoint != null) {
                    if (googleSheetsEnabled && sheetsService != null) {
                        sheetsService.appendIVData(dataPoint);
                        log.info("[{}] ✓ Saved to Google Sheets", symbol);
                    }

                    if (supabaseEnabled && supabaseService != null) {
                        supabaseService.upsertIVData(dataPoint);
                        log.info("[{}] ✓ Saved to Supabase", symbol);
                    }

                    successCount++;
                } else {
                    failCount++;
                }

                Thread.sleep(1500);

            } catch (Exception e) {
                log.error("[{}] CRITICAL ERROR: {}", symbol, e.getMessage());
                failCount++;
            }
        }

        if (googleSheetsEnabled && sheetsService != null) {
            try {
                sheetsService.reorderSheets(new ArrayList<>(allSecurities));
            } catch (IOException e) {
                log.error("Failed to reorder sheets: {}", e.getMessage());
            }
        }
    }

    private void sendTelegramSummary() {
        StringBuilder message = new StringBuilder();
        message.append("📊 <b>IV Data Collection Complete</b>\n\n");

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

        message.append("\n💾 <b>Databases:</b>\n");
        if (googleSheetsEnabled) message.append("├ ✅ Google Sheets\n");
        if (supabaseEnabled) message.append(googleSheetsEnabled ? "└" : "├").append(" ✅ Supabase\n");
        if (!googleSheetsEnabled && !supabaseEnabled) message.append("└ ❌ None\n");

        message.append("\n📅 Date: <code>").append(java.time.LocalDate.now()).append("</code>");
        message.append("\n🕐 Time: <code>").append(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</code>");

        TelegramUtils.sendMessage(message.toString());
    }

    private void loadDatabaseConfiguration() {
        String sheetsEnabledEnv = System.getenv("GOOGLE_SHEETS_ENABLED");
        String supabaseEnabledEnv = System.getenv("SUPABASE_ENABLED");

        if (sheetsEnabledEnv != null || supabaseEnabledEnv != null) {
            googleSheetsEnabled = Boolean.parseBoolean(sheetsEnabledEnv != null ? sheetsEnabledEnv : "true");
            supabaseEnabled = Boolean.parseBoolean(supabaseEnabledEnv != null ? supabaseEnabledEnv : "false");
            return;
        }

        try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
            Properties prop = new Properties();
            prop.load(input);
            googleSheetsEnabled = Boolean.parseBoolean(prop.getProperty("google_sheets_enabled", "true"));
            supabaseEnabled = Boolean.parseBoolean(prop.getProperty("supabase_enabled", "false"));
        } catch (IOException e) {
            googleSheetsEnabled = true;
            supabaseEnabled = false;
        }
    }

    private String loadSpreadsheetId() throws IOException {
        String spreadsheetId = System.getenv("GOOGLE_SPREADSHEET_ID");
        if (spreadsheetId != null && !spreadsheetId.isEmpty()) return spreadsheetId;

        if (FilePaths.testConfig.toFile().exists()) {
            try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
                Properties prop = new Properties();
                prop.load(input);
                spreadsheetId = prop.getProperty("google_sheets_spreadsheet_id");
                if (spreadsheetId != null && !spreadsheetId.isEmpty() && !spreadsheetId.equals("YOUR_SPREADSHEET_ID_HERE")) {
                    return spreadsheetId;
                }
            }
        }
        throw new IllegalStateException("Google Sheets spreadsheet ID not configured.");
    }

    private void initializeSupabaseManual() {
        try {
            String supabaseUrl = System.getenv("SUPABASE_URL");
            String supabaseKey = System.getenv("SUPABASE_SERVICE_ROLE_KEY");

            if ((supabaseUrl == null || supabaseKey == null) && FilePaths.testConfig.toFile().exists()) {
                try (InputStream input = new FileInputStream(FilePaths.testConfig.toFile())) {
                    Properties prop = new Properties();
                    prop.load(input);
                    if (supabaseUrl == null) supabaseUrl = prop.getProperty("supabase_url");
                    if (supabaseKey == null) supabaseKey = prop.getProperty("supabase_service_role_key");
                }
            }

            if (supabaseUrl != null && supabaseKey != null) {
                this.supabaseService = new SupabaseService(supabaseUrl, supabaseKey);
            }
        } catch (Exception e) {
            log.warn("Failed to manually initialize Supabase: {}", e.getMessage());
        }
    }

    private Set<String> loadAllSecurities() {
        Set<String> securities = new LinkedHashSet<>();
        File securitiesDir = FilePaths.securitiesDirectory.toFile();

        if (!securitiesDir.exists() || !securitiesDir.isDirectory()) return securities;

        File[] yamlFiles = securitiesDir.listFiles((dir, name) -> name.endsWith(".yaml"));
        if (yamlFiles == null) return securities;

        Yaml yaml = new Yaml();
        for (File file : yamlFiles) {
            try (InputStream inputStream = new FileInputStream(file)) {
                Map<String, List<String>> yamlData = yaml.load(inputStream);
                if (yamlData != null && yamlData.containsKey("securities")) {
                    List<String> fileSecurities = yamlData.get("securities");
                    if (fileSecurities != null) securities.addAll(fileSecurities);
                }
            } catch (Exception e) {
                log.error("Error loading securities from {}: {}", file.getName(), e.getMessage());
            }
        }
        return securities;
    }
}
