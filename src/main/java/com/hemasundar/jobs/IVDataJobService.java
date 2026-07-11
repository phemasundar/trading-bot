package com.hemasundar.jobs;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.properties.SupabaseConfig;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.utils.TelegramUtils;
import com.hemasundar.utils.WikipediaSecuritiesFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@Log4j2
@RequiredArgsConstructor
public class IVDataJobService {

    private final Optional<SupabaseService> supabaseService;

    private final SupabaseConfig supabaseConfig;

    private final ThinkOrSwinAPIs thinkOrSwinAPIs;

    private final TelegramUtils telegramUtils;

    private final IVDataCollector ivDataCollector;

    private final SchwabApiExecutor schwabApiExecutor;

    private final WikipediaSecuritiesFetcher wikipediaFetcher;

    private Set<String> allSecurities;

    private int successCount = 0;
    private int failCount = 0;
    private int skipCount = 0;
    private int totalCount = 0;
    private List<String> failedSymbols = new ArrayList<>();
    private List<String> skippedSymbols = new ArrayList<>();

    public void runIVDataCollection() {
        log.info("=".repeat(80));
        log.info("IV DATA COLLECTION - SETUP");
        log.info("=".repeat(80));

        if (supabaseService.isPresent()) {
            log.info("✓ Supabase service via Spring Bean active");
        } else {
            log.error("✗ Supabase bean not available");
            return;
        }

        allSecurities = loadAllSecurities();
        log.info("Loaded {} unique securities", allSecurities.size());

        executeCollection();
        sendTelegramSummary();
    }

    private void executeCollection() {
        totalCount = allSecurities.size();
        successCount = 0;
        failCount = 0;
        skipCount = 0;
        failedSymbols = new ArrayList<>();
        skippedSymbols = new ArrayList<>();

        List<String> symbolList = new ArrayList<>(allSecurities);
        List<IVDataPoint> results = schwabApiExecutor.executeParallel(
                symbolList,
                symbol -> ivDataCollector.collectIVDataPoint(symbol)
        );

        for (int i = 0; i < symbolList.size(); i++) {
            String symbol = symbolList.get(i);
            IVDataPoint dataPoint = results.get(i);

            if (dataPoint != null && dataPoint.isNoOptions()) {
                log.info("[{}] ⏭ Skipped - no options available", symbol);
                skipCount++;
                skippedSymbols.add(symbol);
            } else if (dataPoint != null) {
                if (supabaseService.isPresent()) {
                    try {
                        supabaseService.get().upsertIVData(dataPoint);
                        log.info("[{}] ✓ Saved to Supabase", symbol);
                    } catch (Exception e) {
                        log.error("[{}] Error saving to Supabase: {}", symbol, e.getMessage());
                    }
                }
                successCount++;
            } else {
                failCount++;
                failedSymbols.add(symbol);
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

        if (skipCount > 0) {
            message.append("├ Skipped (no options): <code>").append(skipCount).append("</code>\n");
        }

        if (failCount > 0) {
            message.append("└ Failed: <code>").append(failCount).append("</code>\n");
            message.append("\n❌ <b>Failed Symbols:</b>\n");
            message.append("<code>").append(String.join(", ", failedSymbols)).append("</code>\n");
        } else {
            message.append("└ Failed: <code>0</code> 🎉\n");
        }

        if (skipCount > 0) {
            message.append("\n⏭ <b>Skipped Symbols (no options):</b>\n");
            message.append("<code>").append(String.join(", ", skippedSymbols)).append("</code>\n");
        }

        message.append("\n📅 Date: <code>").append(java.time.LocalDate.now()).append("</code>");
        message.append("\n🕐 Time: <code>")
                .append(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")))
                .append("</code>");

        telegramUtils.sendMessage(message.toString());
    }

    Set<String> loadAllSecurities() {
        Set<String> securities = new LinkedHashSet<>();
        
        try {
            log.info("Fetching SPY securities for IV Data Collection...");
            securities.addAll(wikipediaFetcher.fetch("SPY"));
        } catch (Exception e) {
            log.error("Error loading SPY securities: {}", e.getMessage());
        }

        try {
            log.info("Fetching QQQ securities for IV Data Collection...");
            securities.addAll(wikipediaFetcher.fetch("QQQ"));
        } catch (Exception e) {
            log.error("Error loading QQQ securities: {}", e.getMessage());
        }

        File securitiesDir = FilePaths.securitiesDirectory.toFile();

        if (!securitiesDir.exists() || !securitiesDir.isDirectory())
            return securities;

        File[] yamlFiles = securitiesDir.listFiles((dir, name) -> name.endsWith(".yaml"));
        if (yamlFiles == null)
            return securities;

        Yaml yaml = new Yaml();
        for (File file : yamlFiles) {
            try (InputStream inputStream = new FileInputStream(file)) {
                Map<String, List<String>> yamlData = yaml.load(inputStream);
                if (yamlData != null && yamlData.containsKey("securities")) {
                    List<String> fileSecurities = yamlData.get("securities");
                    if (fileSecurities != null)
                        securities.addAll(fileSecurities);
                }
            } catch (Exception e) {
                log.error("Error loading securities from {}: {}", file.getName(), e.getMessage());
            }
        }
        return securities;
    }
}
