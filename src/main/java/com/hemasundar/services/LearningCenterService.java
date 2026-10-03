package com.hemasundar.services;

import com.hemasundar.dto.StrategyDescriptionDto;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service for loading, parsing, and caching strategy guides for the Learning Center.
 */
@Log4j2
@Service
public class LearningCenterService {

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    /**
     * Retrieves all discovered option strategies parsed from markdown descriptions.
     * Excludes non-option screeners (like ATR screener) from the primary option strategies list.
     *
     * @return sorted list of strategy description DTOs
     */
    public List<StrategyDescriptionDto> getOptionStrategies() {
        List<StrategyDescriptionDto> all = loadAllDescriptions();
        return all.stream()
                .filter(s -> !"Screener".equalsIgnoreCase(s.getCategory()))
                .sorted(Comparator.comparing(StrategyDescriptionDto::getName))
                .toList();
    }

    /**
     * Retrieves a strategy description by its ID (filename without .md).
     *
     * @param id strategy identifier
     * @return optional containing the strategy description if found
     */
    public Optional<StrategyDescriptionDto> getStrategyById(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        String cleanId = id.trim().toLowerCase();
        if (cleanId.endsWith(".md")) {
            cleanId = cleanId.substring(0, cleanId.length() - 3);
        }
        final String targetId = cleanId;
        return loadAllDescriptions().stream()
                .filter(s -> s.getId().equalsIgnoreCase(targetId))
                .findFirst();
    }

    /**
     * Discovers and parses all markdown files in static/descriptions/.
     *
     * @return list of parsed strategy descriptions
     */
    public List<StrategyDescriptionDto> loadAllDescriptions() {
        List<StrategyDescriptionDto> results = new ArrayList<>();
        Map<String, String> contentsByFilename = loadMarkdownContents();

        for (Map.Entry<String, String> entry : contentsByFilename.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();
            StrategyDescriptionDto dto = parseMarkdown(filename, content);
            if (dto != null) {
                results.add(dto);
            }
        }
        return results;
    }

    private Map<String, String> loadMarkdownContents() {
        Map<String, String> contents = new LinkedHashMap<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath*:static/descriptions/*.md");
            if (resources != null && resources.length > 0) {
                for (Resource r : resources) {
                    String filename = r.getFilename();
                    if (filename != null && !contents.containsKey(filename)) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            contents.put(filename, sb.toString());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to discover descriptions via pattern resolver: {}", e.getMessage());
        }

        // Fallback to local filesystem path if running in IDE/development
        Path localDir = Path.of("src/main/resources/static/descriptions");
        if (Files.exists(localDir)) {
            try (Stream<Path> stream = Files.list(localDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".md")).forEach(p -> {
                    String filename = p.getFileName().toString();
                    if (!contents.containsKey(filename)) {
                        try {
                            contents.put(filename, Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            log.warn("Failed to read local markdown file {}: {}", filename, e.getMessage());
                        }
                    }
                });
            } catch (IOException e) {
                log.warn("Failed to list local descriptions folder: {}", e.getMessage());
            }
        }

        return contents;
    }

    private StrategyDescriptionDto parseMarkdown(String filename, String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }

        String id = filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
        String name = id;
        Map<String, String> greeks = new LinkedHashMap<>();
        String summary = "";
        String bias = "Neutral";
        String category = "Options Strategy";

        String[] lines = content.split("\r?\n");
        boolean inGreeksTable = false;
        StringBuilder summaryBuilder = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("# ") && name.equals(id)) {
                name = line.substring(2).trim();
                continue;
            }

            if (line.startsWith("### Strategy Option Greeks")) {
                inGreeksTable = true;
                continue;
            }

            if (inGreeksTable) {
                if (line.startsWith("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        String greekRaw = stripMarkdown(parts[1]).trim();
                        String polarityRaw = stripMarkdown(parts[2]).trim();

                        if (greekRaw.toLowerCase().contains("delta")) {
                            greeks.put("Delta", polarityRaw);
                        } else if (greekRaw.toLowerCase().contains("gamma")) {
                            greeks.put("Gamma", polarityRaw);
                        } else if (greekRaw.toLowerCase().contains("theta")) {
                            greeks.put("Theta", polarityRaw);
                        } else if (greekRaw.toLowerCase().contains("vega")) {
                            greeks.put("Vega", polarityRaw);
                        }
                    }
                } else if (line.isEmpty() && !greeks.isEmpty()) {
                    inGreeksTable = false;
                }
            } else if (!inGreeksTable && !greeks.isEmpty() && StringUtils.isBlank(summary)) {
                if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith("|") && !line.startsWith("*")) {
                    summaryBuilder.append(stripMarkdown(line)).append(" ");
                    if (summaryBuilder.length() > 60) {
                        summary = summaryBuilder.toString().trim();
                    }
                }
            }
        }

        if (StringUtils.isBlank(summary)) {
            summary = summaryBuilder.toString().trim();
        }

        // If Greeks table was absent (e.g. screener)
        if (greeks.isEmpty()) {
            if (id.contains("screener")) {
                category = "Screener";
                bias = "Technical";
            }
            // Extract first non-header, non-empty paragraph
            for (String rawLine : lines) {
                String line = stripMarkdown(rawLine.trim());
                if (!line.isEmpty() && !rawLine.trim().startsWith("#") && StringUtils.isBlank(summary)) {
                    summary = line;
                    break;
                }
            }
        } else {
            String deltaPolarity = greeks.getOrDefault("Delta", "Neutral").toLowerCase();
            if (deltaPolarity.contains("positive")) {
                bias = "Bullish";
            } else if (deltaPolarity.contains("negative")) {
                bias = "Bearish";
            } else {
                bias = "Neutral";
            }
        }

        return StrategyDescriptionDto.builder()
                .id(id)
                .name(name)
                .filename(filename)
                .category(category)
                .bias(bias)
                .summary(summary)
                .greeks(greeks)
                .build();
    }

    private String stripMarkdown(String text) {
        if (text == null) return "";
        return text.replace("**", "").replace("*", "").replace("`", "").trim();
    }
}
