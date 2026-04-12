package com.hemasundar.services;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.StrategiesConfigLoader;
import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.pojos.Securities;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.SecuritiesResolver;
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

    @Mock
    private SecuritiesResolver securitiesResolver;

    @Mock
    private com.hemasundar.utils.VolatilityCalculator volatilityCalculator;

    @Mock
    private StrategiesConfigLoader strategiesConfigLoader;

    @Mock
    private TechnicalScreener technicalScreener;

    @InjectMocks
    private StrategyExecutionService strategyExecutionService;

    @Mock
    private ThinkOrSwinAPIs thinkOrSwinAPIs;

    @Mock
    private FinnHubAPIs finnHubAPIs;

    @Mock
    private TelegramUtils telegramUtils;

    private MockedStatic<FilePaths> mockedFilePaths;
    private MockedStatic<JavaUtils> mockedJavaUtils;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Explicit initialization to ensure all mocks are correctly injected into final fields
        strategyExecutionService = new StrategyExecutionService(
                supabaseService,
                securitiesResolver,
                thinkOrSwinAPIs,
                finnHubAPIs,
                telegramUtils,
                technicalScreener,
                volatilityCalculator,
                strategiesConfigLoader
        );

        mockedFilePaths = Mockito.mockStatic(FilePaths.class);
        mockedJavaUtils = Mockito.mockStatic(JavaUtils.class);

        // Global dummy API response
        when(thinkOrSwinAPIs.getOptionChain(anyString()))
                .thenReturn(new OptionChainResponse());

        // Global dummy securities response
        mockedFilePaths.when(() -> FilePaths.readResource(anyString())).thenReturn("securities: []");
        mockedJavaUtils.when(() -> JavaUtils.convertYamlToPojo(anyString(), eq(Securities.class)))
                .thenReturn(new Securities(Collections.emptyList()));
                
        try {
            when(securitiesResolver.loadSecuritiesMaps()).thenReturn(new HashMap<>());
        } catch (IOException e) {
            // ignore
        }
    }

    @AfterMethod
    public void tearDown() {
        if (mockedFilePaths != null) mockedFilePaths.close();
        if (mockedJavaUtils != null) mockedJavaUtils.close();
    }

    @Test
    public void testGetEnabledStrategies() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        when(strategiesConfigLoader.load(anyString(), anyMap()))
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
        when(strategy.getStrategyType()).thenReturn(com.hemasundar.options.strategies.StrategyType.PUT_CREDIT_SPREAD);
        com.hemasundar.options.models.TradeSetup setupSuccess = mock(com.hemasundar.options.models.TradeSetup.class);
        when(setupSuccess.getLegs()).thenReturn(Collections.emptyList());
        when(setupSuccess.getNetCredit()).thenReturn(100.0);
        when(setupSuccess.getMaxLoss()).thenReturn(500.0);
        when(setupSuccess.getReturnOnRisk()).thenReturn(1.0);
        when(setupSuccess.getBreakEvenPrice()).thenReturn(145.0);
        when(setupSuccess.getBreakEvenPercentage()).thenReturn(3.0);
        when(strategy.findTrades(any(), any()))
                .thenReturn(List.of(setupSuccess));
        when(config.getStrategy()).thenReturn(strategy);

        when(strategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(config));

        strategyExecutionService.startGlobalExecution("Test");
        ExecutionResult result = strategyExecutionService.executeStrategies(Set.of(0));

        assertNotNull(result);
        assertEquals(result.getResults().size(), 1);
        verify(supabaseService).saveExecutionResult(any());
    }

    @Test
    public void testExecuteStrategiesNoSelection() throws IOException {
        when(strategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(Collections.emptyList());

        strategyExecutionService.startGlobalExecution("Test");
        ExecutionResult result = strategyExecutionService.executeStrategies(Collections.emptySet());

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
        when(strategy1.getStrategyType()).thenReturn(com.hemasundar.options.strategies.StrategyType.PUT_CREDIT_SPREAD);
        com.hemasundar.options.models.TradeSetup setup1 = mock(com.hemasundar.options.models.TradeSetup.class);
        when(setup1.getLegs()).thenReturn(Collections.emptyList());
        when(setup1.getNetCredit()).thenReturn(100.0);
        when(setup1.getMaxLoss()).thenReturn(500.0);
        when(setup1.getReturnOnRisk()).thenReturn(20.0);
        when(setup1.getBreakEvenPrice()).thenReturn(145.0);
        when(setup1.getBreakEvenPercentage()).thenReturn(3.0);
        when(strategy1.findTrades(any(), any()))
                .thenReturn(List.of(setup1));

        OptionsConfig config2 = mock(OptionsConfig.class);
        when(config2.getName()).thenReturn("Strategy 2");
        when(config2.getSecurities()).thenReturn(Collections.emptyList());
        AbstractTradingStrategy strategy2 = mock(AbstractTradingStrategy.class);
        when(config2.getStrategy()).thenReturn(strategy2);
        when(strategy2.getStrategyName()).thenReturn("S2");
        when(strategy2.getStrategyType()).thenReturn(com.hemasundar.options.strategies.StrategyType.PUT_CREDIT_SPREAD);

        when(strategiesConfigLoader.load(anyString(), anyMap()))
                .thenReturn(List.of(config1, config2));

        Set<Integer> indices = new LinkedHashSet<>(List.of(0, 1));
        strategyExecutionService.startGlobalExecution("Test");
        ExecutionResult result = strategyExecutionService.executeStrategies(indices);

        // Strategy 1 should finish, but Strategy 2 should be skipped due to
        // cancellation
        assertEquals(result.getResults().size(), 1, "Should have cancelled after first strategy");
        assertTrue(strategyExecutionService.isCancellationRequested());
    }



    @Test
    public void testExecuteCustomStrategy() throws IOException {
        OptionsConfig config = mock(OptionsConfig.class);
        when(config.getName()).thenReturn("Custom");
        when(config.getSecurities()).thenReturn(List.of("AAPL"));
        AbstractTradingStrategy strategy = mock(AbstractTradingStrategy.class);
        when(config.getStrategy()).thenReturn(strategy);
        when(strategy.getStrategyName()).thenReturn("Custom");
        when(strategy.getStrategyType()).thenReturn(com.hemasundar.options.strategies.StrategyType.PUT_CREDIT_SPREAD);
        com.hemasundar.options.models.TradeSetup setupCustom = mock(com.hemasundar.options.models.TradeSetup.class);
        when(setupCustom.getLegs()).thenReturn(Collections.emptyList());
        when(setupCustom.getNetCredit()).thenReturn(100.0);
        when(setupCustom.getMaxLoss()).thenReturn(500.0);
        when(setupCustom.getReturnOnRisk()).thenReturn(1.0);
        when(setupCustom.getBreakEvenPrice()).thenReturn(145.0);
        when(setupCustom.getBreakEvenPercentage()).thenReturn(3.0);
        when(strategy.findTrades(any(), any()))
                .thenReturn(List.of(setupCustom));

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
        when(strategy.getStrategyType()).thenReturn(com.hemasundar.options.strategies.StrategyType.PUT_CREDIT_SPREAD);
        com.hemasundar.options.models.TradeSetup setupTech = mock(com.hemasundar.options.models.TradeSetup.class);
        when(setupTech.getLegs()).thenReturn(Collections.emptyList());
        when(setupTech.getNetCredit()).thenReturn(100.0);
        when(setupTech.getMaxLoss()).thenReturn(500.0);
        when(setupTech.getReturnOnRisk()).thenReturn(1.0);
        when(setupTech.getBreakEvenPrice()).thenReturn(145.0);
        when(setupTech.getBreakEvenPercentage()).thenReturn(3.0);
        when(strategy.findTrades(any(), any()))
                .thenReturn(List.of(setupTech));

        TechnicalScreener.ScreeningResult res = mock(TechnicalScreener.ScreeningResult.class);
        when(res.getSymbol()).thenReturn("AAPL");
        when(technicalScreener.screenStocks(anyList(), any()))
                .thenReturn(List.of(res));

        ExecutionResult result = strategyExecutionService.executeCustomStrategy(config);

        assertNotNull(result);
        verify(technicalScreener).screenStocks(eq(List.of("AAPL", "MSFT")), any());
    }

    @Test
    public void testExecutionStateGetters() {
        strategyExecutionService.startGlobalExecution("Testing Task");
        assertTrue(strategyExecutionService.isExecutionRunning());
        assertTrue(strategyExecutionService.getExecutionStartTimeMs() > 0);
        assertEquals(strategyExecutionService.getCurrentExecutionTask(), "Testing Task");

        strategyExecutionService.setCurrentExecutionTask("New Task");
        assertEquals(strategyExecutionService.getCurrentExecutionTask(), "New Task");

        strategyExecutionService.finishGlobalExecution();
        assertFalse(strategyExecutionService.isExecutionRunning());
        assertEquals(strategyExecutionService.getCurrentExecutionTask(), "");
    }

    @Test
    public void testCancelExecution() {
        assertFalse(strategyExecutionService.isCancellationRequested());
        strategyExecutionService.startGlobalExecution("Test");
        strategyExecutionService.cancelExecution();
        assertTrue(strategyExecutionService.isCancellationRequested());
        
        strategyExecutionService.finishGlobalExecution();
        assertFalse(strategyExecutionService.isCancellationRequested());
    }

    @Test
    public void testResultRetrievalMethods() throws IOException {
        when(supabaseService.getRecentCustomExecutions(anyInt())).thenReturn(Collections.emptyList());
        List<?> customResults = strategyExecutionService.getRecentCustomExecutions(5);
        assertNotNull(customResults);
        verify(supabaseService).getRecentCustomExecutions(5);

        when(supabaseService.getLatestExecutionResult()).thenReturn(Optional.empty());
        Optional<ExecutionResult> latestExec = strategyExecutionService.getLatestExecutionResult();
        assertFalse(latestExec.isPresent());
        verify(supabaseService).getLatestExecutionResult();

        when(supabaseService.getAllLatestStrategyResults()).thenReturn(Collections.emptyList());
        List<?> allResults = strategyExecutionService.getAllLatestStrategyResults();
        assertNotNull(allResults);
        verify(supabaseService).getAllLatestStrategyResults();
    }


}
