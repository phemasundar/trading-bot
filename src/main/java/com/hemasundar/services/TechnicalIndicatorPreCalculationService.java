package com.hemasundar.services;

import com.hemasundar.cache.TechnicalIndicatorCache;
import com.hemasundar.technical.ScreenerConfig;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.technical.*;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.SecuritiesResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@Log4j2
public class TechnicalIndicatorPreCalculationService {

    private final TechnicalScreener technicalScreener;
    private final StrategiesConfigLoader strategiesConfigLoader;
    private final SecuritiesResolver securitiesResolver;
    private final Optional<SupabaseService> supabaseService;

    @Autowired
    public TechnicalIndicatorPreCalculationService(
            TechnicalScreener technicalScreener,
            StrategiesConfigLoader strategiesConfigLoader,
            SecuritiesResolver securitiesResolver,
            Optional<SupabaseService> supabaseService) {
        this.technicalScreener = technicalScreener;
        this.strategiesConfigLoader = strategiesConfigLoader;
        this.securitiesResolver = securitiesResolver;
        this.supabaseService = supabaseService;
    }

    public TechnicalIndicatorPreCalculationService(
            TechnicalScreener technicalScreener,
            StrategiesConfigLoader strategiesConfigLoader,
            SecuritiesResolver securitiesResolver) {
        this(technicalScreener, strategiesConfigLoader, securitiesResolver, Optional.empty());
    }

    /**
     * Extracts a union of all technical indicator configurations across all screeners
     * and strategies. This ensures that the pre-calculation covers every period
     * needed by any strategy.
     */
    public TechnicalIndicators buildUniversalIndicators(List<ScreenerConfig> screeners, List<OptionsConfig> strategies) {
        TechnicalIndicators.TechnicalIndicatorsBuilder builder = TechnicalIndicators.createDefaults().toBuilder();

        Map<Integer, MovingAverageFilter> maFilters = new HashMap<>();
        Map<Integer, ExponentialMovingAverageFilter> emaFilters = new HashMap<>();

        // Add defaults first
        if (TechnicalIndicators.createDefaults().getMaFilters() != null) {
            maFilters.putAll(TechnicalIndicators.createDefaults().getMaFilters());
        }
        if (TechnicalIndicators.createDefaults().getEmaFilters() != null) {
            emaFilters.putAll(TechnicalIndicators.createDefaults().getEmaFilters());
        }

        List<TechnicalFilterChain> allChains = new ArrayList<>();
        List<TechFilterConditions> allConditions = new ArrayList<>();

        if (screeners != null) {
            for (ScreenerConfig screener : screeners) {
                if (screener.getFilterChain() != null) {
                    allChains.add(screener.getFilterChain());
                    if (screener.getFilterChain().getConditions() != null) {
                        allConditions.add(screener.getFilterChain().getConditions());
                    }
                }
            }
        }

        if (strategies != null) {
            for (OptionsConfig strategy : strategies) {
                if (strategy.getTechnicalFilterChain() != null) {
                    allChains.add(strategy.getTechnicalFilterChain());
                    if (strategy.getTechnicalFilterChain().getConditions() != null) {
                        allConditions.add(strategy.getTechnicalFilterChain().getConditions());
                    }
                }
            }
        }

        for (TechnicalFilterChain chain : allChains) {
            TechnicalIndicators indicators = chain.getIndicators();
            if (indicators == null) continue;

            if (indicators.getMaFilters() != null) {
                maFilters.putAll(indicators.getMaFilters());
            }
            if (indicators.getEmaFilters() != null) {
                emaFilters.putAll(indicators.getEmaFilters());
            }

            // Keep the last encountered config for non-mapped indicators
            if (indicators.getRsiFilter() != null) {
                builder.rsiFilter(indicators.getRsiFilter());
            }
            if (indicators.getBollingerFilter() != null) {
                builder.bollingerFilter(indicators.getBollingerFilter());
            }
            if (indicators.getAtrFilter() != null) {
                builder.atrFilter(indicators.getAtrFilter());
            }
            if (indicators.getVolumeFilter() != null) {
                builder.volumeFilter(indicators.getVolumeFilter());
            }
        }

        builder.maFilters(maFilters);
        builder.emaFilters(emaFilters);

        return builder.build();
    }

    /**
     * Builds a combined conditions object to extract variable periods (HIGH, VOLUME_SMA).
     */
    public TechFilterConditions buildUniversalConditions(List<ScreenerConfig> screeners, List<OptionsConfig> strategies) {
        List<MathExpression> allExpressions = new ArrayList<>();

        if (screeners != null) {
            for (ScreenerConfig screener : screeners) {
                if (screener.getFilterChain() != null && screener.getFilterChain().getConditions() != null) {
                    if (screener.getFilterChain().getConditions().getFilterExpressions() != null) {
                        allExpressions.addAll(screener.getFilterChain().getConditions().getFilterExpressions());
                    }
                }
            }
        }

        if (strategies != null) {
            for (OptionsConfig strategy : strategies) {
                if (strategy.getTechnicalFilterChain() != null && strategy.getTechnicalFilterChain().getConditions() != null) {
                    if (strategy.getTechnicalFilterChain().getConditions().getFilterExpressions() != null) {
                        allExpressions.addAll(strategy.getTechnicalFilterChain().getConditions().getFilterExpressions());
                    }
                }
            }
        }

        return TechFilterConditions.builder()
                .filterExpressions(allExpressions)
                .build();
    }

    private TechFilterConditions augmentWithAllAvailableIndicators(TechFilterConditions conditions) {
        List<MathExpression> exprs = new ArrayList<>();
        if (conditions != null && conditions.getFilterExpressions() != null) {
            exprs.addAll(conditions.getFilterExpressions());
        }
        exprs.add(MathExpression.builder().leftVariable("VOLUME_SMA20").operator(RelationalOperator.GREATER_THAN_OR_EQUAL).rightVariable("0").build());
        exprs.add(MathExpression.builder().leftVariable("VOLUME_SMA50").operator(RelationalOperator.GREATER_THAN_OR_EQUAL).rightVariable("0").build());
        exprs.add(MathExpression.builder().leftVariable("HIGH5").operator(RelationalOperator.GREATER_THAN_OR_EQUAL).rightVariable("0").build());
        exprs.add(MathExpression.builder().leftVariable("HIGH20").operator(RelationalOperator.GREATER_THAN_OR_EQUAL).rightVariable("0").build());
        exprs.add(MathExpression.builder().leftVariable("HIGH252").operator(RelationalOperator.GREATER_THAN_OR_EQUAL).rightVariable("0").build());

        return TechFilterConditions.builder()
                .rsiCondition(conditions != null ? conditions.getRsiCondition() : null)
                .minRsi(conditions != null ? conditions.getMinRsi() : null)
                .maxRsi(conditions != null ? conditions.getMaxRsi() : null)
                .bollingerCondition(conditions != null ? conditions.getBollingerCondition() : null)
                .filterExpressions(exprs)
                .hvPeriod(20)
                .lookbackDays(conditions != null ? conditions.getLookbackDays() : null)
                .build();
    }

    /**
     * Pre-calculates indicators for all unique symbols across all strategies/screeners.
     * Calculates all available technical indicators and persists them into Supabase.
     */
    public void preCalculateAll(List<String> symbols, BiConsumer<String, String> alertCallback) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }

        log.info("[TechnicalIndicatorPreCalculationService] Starting pre-calculation for {} symbols", symbols.size());
        long t0 = System.currentTimeMillis();

        List<ScreenerConfig> screeners = new ArrayList<>();
        List<OptionsConfig> strategies = new ArrayList<>();
        try {
            Map<String, List<String>> securitiesMap = securitiesResolver.loadSecuritiesMaps();
            screeners = strategiesConfigLoader.loadScreeners(FilePaths.strategiesConfig, securitiesMap);
            strategies = strategiesConfigLoader.load(FilePaths.strategiesConfig, securitiesMap);
        } catch (Exception e) {
            log.error("Failed to load configs for indicator pre-calculation", e);
        }

        TechnicalIndicators universalIndicators = buildUniversalIndicators(screeners, strategies);
        TechFilterConditions universalConditions = augmentWithAllAvailableIndicators(
                buildUniversalConditions(screeners, strategies));

        // Fetch unique symbols that aren't already cached
        List<String> uncachedSymbols = symbols.stream()
                .distinct()
                .filter(symbol -> !TechnicalIndicatorCache.getInstance().isCached(symbol))
                .collect(Collectors.toList());

        if (uncachedSymbols.isEmpty()) {
            log.info("[TechnicalIndicatorPreCalculationService] All {} symbols already pre-calculated in memory", symbols.size());
            saveAllToSupabase(symbols);
            return;
        }

        uncachedSymbols.parallelStream().forEach(symbol -> {
            try {
                ScreeningResult result = technicalScreener.analyzeStock(symbol, universalIndicators, universalConditions);
                if (result != null) {
                    TechnicalIndicatorCache.getInstance().put(symbol, result);
                }
            } catch (Exception e) {
                log.warn("[TechnicalIndicatorPreCalculationService] Failed to calculate indicators for {}: {}", symbol, e.getMessage());
                if (alertCallback != null) {
                    alertCallback.accept("PreCalculation: " + symbol, e.getMessage());
                }
            }
        });

        long t1 = System.currentTimeMillis();
        log.info("[TechnicalIndicatorPreCalculationService] Pre-calculated {} symbols in {}ms", uncachedSymbols.size(), (t1 - t0));

        // Save all evaluated indicators into Supabase table latest_security_indicators (1 row per security)
        saveAllToSupabase(symbols);
    }

    private void saveAllToSupabase(List<String> symbols) {
        if (supabaseService == null || supabaseService.isEmpty() || symbols == null || symbols.isEmpty()) {
            return;
        }
        List<ScreeningResult> resultsToSave = symbols.stream()
                .distinct()
                .map(s -> TechnicalIndicatorCache.getInstance().get(s))
                .filter(Objects::nonNull)
                .toList();

        if (!resultsToSave.isEmpty()) {
            try {
                supabaseService.get().saveSecurityIndicators(resultsToSave);
                log.info("[TechnicalIndicatorPreCalculationService] Persisted {} security indicators to Supabase", resultsToSave.size());
            } catch (Exception e) {
                log.error("[TechnicalIndicatorPreCalculationService] Failed to persist security indicators to Supabase: {}", e.getMessage(), e);
            }
        }
    }
}
