package com.hemasundar.utils;

import com.hemasundar.pojos.Securities;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads static securities lists from YAML files in the {@code securities/} classpath folder.
 *
 * <p>Dynamic index constituents (SPY = S&P 500, QQQ = Nasdaq-100) are <b>not</b> loaded here.
 * They are resolved lazily by {@link StrategiesConfigLoader} via {@link WikipediaSecuritiesFetcher}
 * only when a strategy that actually references those keywords is about to execute.
 */
@Log4j2
@Component
public class SecuritiesResolver {

    /**
     * Loads all predefined static securities maps from YAML classpath resources.
     *
     * <p>Keys: {@code portfolio}, {@code top100}, {@code bullish}, {@code 2026}, {@code tracking}.
     * Dynamic keys (SPY, QQQ) are resolved lazily via {@link WikipediaSecuritiesFetcher}.
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

    private List<String> loadSecurities(String resourcePath) throws IOException {
        String yaml = FilePaths.readResource(resourcePath);
        Securities securities = JavaUtils.convertYamlToPojo(yaml, Securities.class);
        log.info("Loading securities from: {} - Found {} symbols", resourcePath, securities.securities().size());
        return securities.securities();
    }
}
