package com.hemasundar.utils;

import com.hemasundar.pojos.Securities;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Shared utility for loading and parsing securities files.
 */
@Log4j2
@Component
public class SecuritiesResolver {

    /**
     * Loads all predefined securities maps from YAML files.
     */
    public Map<String, List<String>> loadSecuritiesMaps() throws IOException {
        return Map.of(
                "portfolio", loadSecurities(FilePaths.portfolioSecurities),
                "top100", loadSecurities(FilePaths.top100Securities),
                "bullish", loadSecurities(FilePaths.bullishSecurities),
                "2026", loadSecurities(FilePaths.securities2026),
                "tracking", loadSecurities(FilePaths.trackingSecurities)
        );
    }

    /**
     * Loads securities from a specific YAML file.
     */
    private List<String> loadSecurities(String resourcePath) throws IOException {
        String yaml = FilePaths.readResource(resourcePath);
        Securities securities = JavaUtils.convertYamlToPojo(yaml, Securities.class);
        log.info("Loading securities from: {} - Found {} symbols", resourcePath, securities.securities().size());
        return securities.securities();
    }
}
