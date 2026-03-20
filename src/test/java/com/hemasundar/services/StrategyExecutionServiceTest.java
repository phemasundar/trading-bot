package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.pojos.Securities;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.TelegramUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class StrategyExecutionServiceTest {

    @Mock
    private SupabaseService supabaseService;

    @InjectMocks
    private StrategyExecutionService strategyExecutionService;

    private MockedStatic<StrategiesConfigLoader> mockedLoader;
    private MockedStatic<TechnicalScreener> mockedScreener;
    private MockedStatic<TelegramUtils> mockedTelegram;
    private MockedStatic<FilePaths> mockedFilePaths;
    private MockedStatic<JavaUtils> mockedJavaUtils;
    private MockedStatic<ThinkOrSwinAPIs> mockedApis;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedLoader = Mockito.mockStatic(StrategiesConfigLoader.class);
        mockedScreener = Mockito.mockStatic(TechnicalScreener.class);
        mockedTelegram = Mockito.mockStatic(TelegramUtils.class);
        mockedFilePaths = Mockito.mockStatic(FilePaths.class);
        mockedJavaUtils = Mockito.mockStatic(JavaUtils.class);
        mockedApis = Mockito.mockStatic(ThinkOrSwinAPIs.class);

        // Global dummy API response
        mockedApis.when(() -> ThinkOrSwinAPIs.getOptionChain(anyString()))
                .thenReturn(new OptionChainResponse());

        // Global dummy securities response
        mockedFilePaths.when(() -> FilePaths.readResource(anyString())).thenReturn("securities: []");
        mockedJavaUtils.when(() -> JavaUtils.convertYamlToPojo(anyString(), eq(Securities.class)))
                .thenReturn(new Securities(Collections.emptyList()));
    }

    @AfterMethod
    public void tearDown() {
        mockedLoader.close();
        mockedScreener.close();
        mockedTelegram.close();
        mockedFilePaths.close();
        mockedJavaUtils.close();
        mockedApis.close();
    }

    @Test
    public void testGetEnabledStrategies() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        mockedLoader.when(() -> StrategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(config));

        List<OptionsConfig> strategies = strategyExecutionService.getEnabledStrategies();
        assertNotNull(strategies);
        assertEquals(strategies.size(), 1);
    }

    @Test
    public void testExecuteStrategiesSuccess() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        when(config.getName()).thenReturn("Test Strategy");
        when(config.getSecurities()).thenReturn(List.of("AAPL"));
        AbstractTradingStrategy strategy = mock(AbstractTradingStrategy.class);
        when(strategy.getStrategyName()).thenReturn("Test Strategy");
        when(config.getStrategy()).thenReturn(strategy);

        mockedLoader.when(() -> StrategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(config));

        ExecutionResult result = strategyExecutionService.executeStrategies(Set.of(0), null);

        assertNotNull(result);
        assertEquals(result.getResults().size(), 1);
        verify(supabaseService).saveExecutionResult(any());
    }

    @Test
    public void testExecuteStrategiesNoSelection() throws IOException {
        mockedLoader.when(() -> StrategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(Collections.emptyList());

        ExecutionResult result = strategyExecutionService.executeStrategies(Collections.emptySet(), null);

        assertNotNull(result);
        assertEquals(result.getResults().size(), 0);
        assertEquals(result.getTotalTradesFound(), 0);
    }

    @Test
    public void testExecuteStrategiesCancellation() throws IOException {
        OptionsConfig config1 = mock(OptionsConfig.class);
        when(config1.getName()).thenReturn("Strategy 1");
        // Trigger cancellation during execution of first strategy via getSecurities()
        when(config1.getSecurities()).thenAnswer(inv -> {
            strategyExecutionService.cancelExecution();
            return Collections.emptyList();
        });
        AbstractTradingStrategy strategy1 = mock(AbstractTradingStrategy.class);
        when(config1.getStrategy()).thenReturn(strategy1);
        when(strategy1.getStrategyName()).thenReturn("S1");

        OptionsConfig config2 = mock(OptionsConfig.class);
        when(config2.getName()).thenReturn("Strategy 2");
        when(config2.getSecurities()).thenReturn(Collections.emptyList());
        AbstractTradingStrategy strategy2 = mock(AbstractTradingStrategy.class);
        when(config2.getStrategy()).thenReturn(strategy2);
        when(strategy2.getStrategyName()).thenReturn("S2");

        mockedLoader.when(() -> StrategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(config1, config2));

        Set<Integer> indices = new LinkedHashSet<>(List.of(0, 1));
        ExecutionResult result = strategyExecutionService.executeStrategies(indices, null);

        // Strategy 1 should finish, but Strategy 2 should be skipped due to
        // cancellation
        assertEquals(result.getResults().size(), 1, "Should have cancelled after first strategy");
        assertTrue(strategyExecutionService.isCancellationRequested());
    }

    @Test
    public void testLoadSecuritiesMaps() throws IOException {
        mockedFilePaths.when(() -> FilePaths.readResource(anyString())).thenReturn("securities: [AAPL, MSFT]");
        mockedJavaUtils.when(() -> JavaUtils.convertYamlToPojo(anyString(), eq(Securities.class)))
                .thenReturn(new Securities(List.of("AAPL", "MSFT")));

        Map<String, List<String>> maps = strategyExecutionService.loadSecuritiesMaps();
        assertNotNull(maps);
        assertTrue(maps.containsKey("portfolio"));
        assertEquals(maps.get("portfolio").size(), 2);
    }

    @Test
    public void testExecuteCustomStrategy() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        when(config.getName()).thenReturn("Custom");
        when(config.getSecurities()).thenReturn(List.of("AAPL"));
        AbstractTradingStrategy strategy = mock(AbstractTradingStrategy.class);
        when(config.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyName()).thenReturn("Custom");

        ExecutionResult result = strategyExecutionService.executeCustomStrategy(config);

        assertNotNull(result);
        assertEquals(result.getResults().size(), 1);
        verify(supabaseService).saveCustomExecutionResult(any(), anyList());
    }

    @Test
    public void testTechnicalScreeningIntegration() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        when(config.getName()).thenReturn("Tech Strategy");
        when(config.getSecurities()).thenReturn(List.of("AAPL", "MSFT"));
        when(config.hasTechnicalFilter()).thenReturn(true);

        AbstractTradingStrategy strategy = mock(AbstractTradingStrategy.class);
        when(config.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyName()).thenReturn("Tech");

        TechnicalScreener.ScreeningResult res = mock(TechnicalScreener.ScreeningResult.class);
        when(res.getSymbol()).thenReturn("AAPL");
        mockedScreener.when(() -> TechnicalScreener.screenStocks(anyList(), any()))
                .thenReturn(List.of(res));

        ExecutionResult result = strategyExecutionService.executeCustomStrategy(config);

        assertNotNull(result);
        mockedScreener.verify(() -> TechnicalScreener.screenStocks(eq(List.of("AAPL", "MSFT")), any()));
    }

    @Test
    public void testExecuteStrategies_WithTechnicalScreeners() throws IOException {
        // Mock at least one options strategy to avoid early return
        OptionsConfig dummyConfig = mock(OptionsConfig.class);
        when(dummyConfig.getName()).thenReturn("Dummy");
        when(dummyConfig.getStrategy()).thenReturn(mock(AbstractTradingStrategy.class));

        mockedLoader.when(() -> StrategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(dummyConfig));
        
        // Mock a screener
        com.hemasundar.technical.ScreenerConfig screenerConfig = mock(com.hemasundar.technical.ScreenerConfig.class);
        when(screenerConfig.getName()).thenReturn("Test Screener");
        when(screenerConfig.getSecurities()).thenReturn(List.of("AAPL"));
        when(screenerConfig.getScreenerType()).thenReturn(com.hemasundar.technical.ScreenerType.RSI_BB_BULLISH_CROSSOVER);
        when(screenerConfig.getConditions()).thenReturn(mock(com.hemasundar.technical.TechFilterConditions.class));
        
        mockedLoader.when(() -> StrategiesConfigLoader.loadScreeners(anyMap()))
                .thenReturn(List.of(screenerConfig));
        
        TechnicalScreener.ScreeningResult screenRes = mock(TechnicalScreener.ScreeningResult.class);
        when(screenRes.getSymbol()).thenReturn("AAPL");
        mockedScreener.when(() -> TechnicalScreener.screenStocks(anyList(), any()))
                .thenReturn(List.of(screenRes));

        ExecutionResult result = strategyExecutionService.executeStrategies(java.util.Set.of(0), java.util.Set.of(0));
        
        assertNotNull(result);
        mockedScreener.verify(() -> TechnicalScreener.screenStocks(anyList(), any()), atLeastOnce());
        verify(supabaseService).saveScreenerResult(any());
    }
}
