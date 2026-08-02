package com.hemasundar.services;

import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.technical.*;
import com.hemasundar.utils.SecuritiesResolver;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;
import java.util.function.BiConsumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TechnicalIndicatorPreCalculationServiceTest {

    @Mock
    private TechnicalScreener technicalScreener;

    @Mock
    private StrategiesConfigLoader strategiesConfigLoader;

    @Mock
    private SecuritiesResolver securitiesResolver;

    private TechnicalIndicatorPreCalculationService preCalculationService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        com.hemasundar.cache.TechnicalIndicatorCache.getInstance().clear();
        preCalculationService = new TechnicalIndicatorPreCalculationService(
                technicalScreener, strategiesConfigLoader, securitiesResolver);
    }

    @Test
    public void testBuildUniversalIndicatorsWithEmptyLists() {
        TechnicalIndicators indicators = preCalculationService.buildUniversalIndicators(null, null);
        Assert.assertNotNull(indicators);
        Assert.assertNotNull(indicators.getMaFilters());
        Assert.assertNotNull(indicators.getEmaFilters());
    }

    @Test
    public void testBuildUniversalIndicatorsWithScreenersAndStrategies() {
        TechnicalIndicators techIndicators1 = TechnicalIndicators.builder()
                .rsiFilter(RSIFilter.builder().period(14).build())
                .bollingerFilter(BollingerBandsFilter.builder().period(20).build())
                .atrFilter(AverageTrueRangeFilter.builder().period(14).build())
                .volumeFilter(VolumeFilter.builder().minVolume(100000L).build())
                .build();

        TechnicalFilterChain chain1 = TechnicalFilterChain.of(techIndicators1, TechFilterConditions.builder().build());

        ScreenerConfig screener = ScreenerConfig.builder()
                .screenerType(ScreenerType.RSI_OVERSOLD)
                .filterChain(chain1)
                .build();

        TechnicalIndicators techIndicators2 = TechnicalIndicators.builder()
                .maFilters(Map.of(200, MovingAverageFilter.builder().period(200).build()))
                .emaFilters(Map.of(21, ExponentialMovingAverageFilter.builder().period(21).build()))
                .build();

        TechnicalFilterChain chain2 = TechnicalFilterChain.of(techIndicators2, TechFilterConditions.builder().build());

        OptionsConfig strategy = OptionsConfig.builder()
                .technicalFilterChain(chain2)
                .build();

        TechnicalIndicators result = preCalculationService.buildUniversalIndicators(
                List.of(screener), List.of(strategy));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getMaFilters().get(200));
        Assert.assertNotNull(result.getEmaFilters().get(21));
        Assert.assertNotNull(result.getRsiFilter());
        Assert.assertNotNull(result.getBollingerFilter());
    }

    @Test
    public void testBuildUniversalConditions() {
        MathExpression expr1 = MathExpression.builder()
                .leftVariable("RSI")
                .operator(RelationalOperator.LESS_THAN)
                .rightVariable("30")
                .build();

        MathExpression expr2 = MathExpression.builder()
                .leftVariable("PRICE")
                .operator(RelationalOperator.GREATER_THAN)
                .rightVariable("SMA200")
                .build();

        TechnicalFilterChain chain1 = TechnicalFilterChain.of(
                TechnicalIndicators.builder().build(),
                TechFilterConditions.builder().filterExpressions(List.of(expr1)).build()
        );

        ScreenerConfig screener = ScreenerConfig.builder()
                .screenerType(ScreenerType.RSI_OVERSOLD)
                .filterChain(chain1)
                .build();

        TechnicalFilterChain chain2 = TechnicalFilterChain.of(
                TechnicalIndicators.builder().build(),
                TechFilterConditions.builder().filterExpressions(List.of(expr2)).build()
        );

        OptionsConfig strategy = OptionsConfig.builder()
                .technicalFilterChain(chain2)
                .build();

        TechFilterConditions conditions = preCalculationService.buildUniversalConditions(
                List.of(screener), List.of(strategy));

        Assert.assertNotNull(conditions);
        Assert.assertEquals(conditions.getFilterExpressions().size(), 2);
    }

    @Test
    public void testPreCalculateAllWithNullOrEmptySymbols() {
        preCalculationService.preCalculateAll(null, null);
        preCalculationService.preCalculateAll(Collections.emptyList(), null);
        verifyNoInteractions(technicalScreener);
    }

    @Test
    public void testPreCalculateAllWithSymbols() throws Exception {
        when(securitiesResolver.loadSecuritiesMaps()).thenReturn(Collections.emptyMap());
        when(strategiesConfigLoader.loadScreeners(any(), any())).thenReturn(Collections.emptyList());
        when(strategiesConfigLoader.load(any(), any())).thenReturn(Collections.emptyList());

        TechnicalScreener.ScreeningResult screeningResult = TechnicalScreener.ScreeningResult.builder()
                .symbol("AAPL")
                .currentPrice(150.0)
                .build();

        when(technicalScreener.analyzeStock(eq("AAPL"), any(), any())).thenReturn(screeningResult);

        preCalculationService.preCalculateAll(List.of("AAPL"), null);

        verify(technicalScreener, times(1)).analyzeStock(eq("AAPL"), any(), any());
    }

    @Test
    public void testPreCalculateAllHandlingException() throws Exception {
        when(securitiesResolver.loadSecuritiesMaps()).thenReturn(Collections.emptyMap());
        when(strategiesConfigLoader.loadScreeners(any(), any())).thenReturn(Collections.emptyList());
        when(strategiesConfigLoader.load(any(), any())).thenReturn(Collections.emptyList());

        when(technicalScreener.analyzeStock(eq("FAIL_SYM"), any(), any()))
                .thenThrow(new RuntimeException("API error"));

        BiConsumer<String, String> alertCallback = mock(BiConsumer.class);

        preCalculationService.preCalculateAll(List.of("FAIL_SYM"), alertCallback);

        verify(alertCallback, times(1)).accept(contains("FAIL_SYM"), contains("API error"));
    }
}
