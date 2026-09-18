package com.hemasundar.utils;

import com.hemasundar.dto.SecuritiesGroupDto;
import com.hemasundar.pojos.Securities;
import com.hemasundar.technical.SecuritiesFilterConfig;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads static and dynamically discovered securities lists from YAML files
 * in the {@code securities/} classpath folder, and loads filter configurations.
 */
@Log4j2
@Component
public class SecuritiesResolver {

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    /**
     * Loads predefined static securities maps from YAML classpath resources.
     *
     * @return mutable map from key → list of ticker symbols
     * @throws IOException if a static YAML file cannot be read
     */
    public Map<String, List<String>> loadSecuritiesMaps() throws IOException {
        Map<String, List<String>> map = new HashMap<>();
        map.put("portfolio", loadSecurities(FilePaths.portfolioSecurities));
        map.put("top100",    loadSecurities(FilePaths.top100Securities));
        map.put("bullish",   loadSecurities(FilePaths.bullishSecurities));
        map.put("2026",      loadSecurities(FilePaths.securities2026));
        map.put("tracking",  loadSecurities(FilePaths.trackingSecurities));
        return map;
    }

    /**
     * Dynamically discovers all securities YAML files under {@code securities/} classpath folder,
     * parses each file into a {@link SecuritiesGroupDto}, and returns them sorted by filename.
     *
     * @return list of discovered securities groups
     * @throws IOException if discovery fails
     */
    public List<SecuritiesGroupDto> loadSecuritiesGroups() throws IOException {
        Resource[] yamlResources = resourcePatternResolver.getResources("classpath*:securities/*.yaml");
        Resource[] ymlResources = resourcePatternResolver.getResources("classpath*:securities/*.yml");

        Map<String, Resource> resourceMap = new TreeMap<>();
        if (yamlResources != null) {
            for (Resource r : yamlResources) {
                if (r.getFilename() != null) {
                    resourceMap.put(r.getFilename(), r);
                }
            }
        }
        if (ymlResources != null) {
            for (Resource r : ymlResources) {
                if (r.getFilename() != null) {
                    resourceMap.put(r.getFilename(), r);
                }
            }
        }

        List<SecuritiesGroupDto> groups = new ArrayList<>();
        for (Map.Entry<String, Resource> entry : resourceMap.entrySet()) {
            String fileName = entry.getKey();
            Resource resource = entry.getValue();
            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Securities securities = JavaUtils.convertYamlToPojo(content, Securities.class);
                List<String> rawSymbols = securities != null && securities.securities() != null
                        ? securities.securities()
                        : Collections.emptyList();

                List<String> symbols = rawSymbols.stream()
                        .filter(StringUtils::isNotBlank)
                        .map(String::trim)
                        .map(String::toUpperCase)
                        .distinct()
                        .collect(Collectors.toList());

                String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                String displayName = formatDisplayName(baseName);

                groups.add(SecuritiesGroupDto.builder()
                        .id(baseName)
                        .fileName(fileName)
                        .displayName(displayName)
                        .symbolCount(symbols.size())
                        .symbols(symbols)
                        .build());
            } catch (Exception e) {
                log.error("Failed to load securities file {}: {}", fileName, e.getMessage());
            }
        }
        return groups;
    }

    /**
     * Formats a raw file base name (e.g. "1_portfolio", "top100") into a clean display title.
     *
     * @param baseName raw base filename without extension
     * @return clean capitalized display name
     */
    public String formatDisplayName(String baseName) {
        if (StringUtils.isBlank(baseName)) {
            return "";
        }
        String clean = baseName.replaceFirst("^\\d+[_-]", "");
        if (clean.equalsIgnoreCase("top100")) {
            return "Top 100";
        }
        String[] words = clean.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Loads the securities filter configuration from {@code securities-filters.yml}.
     *
     * @return parsed filter configuration
     * @throws IOException if the configuration file cannot be read
     */
    public SecuritiesFilterConfig loadSecuritiesFiltersConfig() throws IOException {
        String yaml = FilePaths.readResource(FilePaths.securitiesFiltersConfig);
        return JavaUtils.convertYamlToPojo(yaml, SecuritiesFilterConfig.class);
    }

    private List<String> loadSecurities(String resourcePath) throws IOException {
        String yaml = FilePaths.readResource(resourcePath);
        Securities securities = JavaUtils.convertYamlToPojo(yaml, Securities.class);
        log.info("Loading securities from: {} - Found {} symbols", resourcePath, securities.securities().size());
        return securities.securities();
    }
}
