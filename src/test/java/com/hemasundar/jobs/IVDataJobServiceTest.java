package com.hemasundar.jobs;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.config.properties.SupabaseConfig;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.SchwabApiExecutor;
import com.hemasundar.utils.TelegramUtils;
import com.hemasundar.utils.WikipediaSecuritiesFetcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class IVDataJobServiceTest {

    @Mock
    private SupabaseService supabaseService;

    @Mock
    private SupabaseConfig supabaseConfig;

    @Mock
    private ThinkOrSwimAPIs ThinkOrSwimAPIs;

    @Mock
    private TelegramUtils telegramUtils;

    @Mock
    private IVDataCollector ivDataCollector;

    @Mock
    private SchwabApiExecutor schwabApiExecutor;

    @Mock
    private WikipediaSecuritiesFetcher wikipediaFetcher;

    private IVDataJobService ivDataJobService;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ivDataJobService = new IVDataJobService(
                Optional.of(supabaseService),
                supabaseConfig,
                ThinkOrSwimAPIs,
                telegramUtils,
                ivDataCollector,
                schwabApiExecutor,
                wikipediaFetcher
        );
    }

    @Test
    public void testRunIVDataCollection_Success() throws Exception {

        // Mock data point collection
        IVDataPoint dataPoint = new IVDataPoint();
        dataPoint.setSymbol("AAPL");
        dataPoint.setAtmPutIV(25.0);
        dataPoint.setAtmCallIV(25.0);
        when(ivDataCollector.collectIVDataPoint("AAPL")).thenReturn(dataPoint);

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> symbols = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return symbols.stream().map(func).toList();
        });

        // Spy on service to mock loadAllSecurities to avoid loading 128 symbols and sleeping
        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(Collections.singleton("AAPL")).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();

        // Verify data was saved
        verify(supabaseService, times(1)).upsertIVData(dataPoint);

        // Verify summary was sent
        verify(telegramUtils, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    public void testRunIVDataCollection_SkipsNoOptionsSymbols() throws Exception {

        // AAPL succeeds, NVR has no options
        IVDataPoint aaplDataPoint = new IVDataPoint();
        aaplDataPoint.setSymbol("AAPL");
        aaplDataPoint.setAtmPutIV(25.0);
        aaplDataPoint.setAtmCallIV(25.0);

        IVDataPoint nvrDataPoint = new IVDataPoint();
        nvrDataPoint.setSymbol("NVR");
        nvrDataPoint.setNoOptions(true);

        when(ivDataCollector.collectIVDataPoint("AAPL")).thenReturn(aaplDataPoint);
        when(ivDataCollector.collectIVDataPoint("NVR")).thenReturn(nvrDataPoint);

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> symbols = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return symbols.stream().map(func).toList();
        });

        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(Set.of("AAPL", "NVR")).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();

        // Only AAPL should be persisted
        verify(supabaseService, times(1)).upsertIVData(aaplDataPoint);
        verify(supabaseService, never()).upsertIVData(nvrDataPoint);

        // Telegram summary should mention the skipped symbol
        verify(telegramUtils, atLeastOnce()).sendMessage(argThat(msg ->
                msg.contains("Skipped (no options)") && msg.contains("NVR")));
    }

    @Test
    public void testRunIVDataCollection_ToleratesOutlierFailuresWhenSuccessRateHigh() throws Exception {
        // 9 symbols succeed, 1 fails -> 90% success rate, should not throw
        Set<String> symbols = new java.util.LinkedHashSet<>();
        for (int i = 1; i <= 9; i++) {
            String sym = "SYM" + i;
            symbols.add(sym);
            IVDataPoint dp = new IVDataPoint();
            dp.setSymbol(sym);
            dp.setAtmPutIV(20.0);
            dp.setAtmCallIV(20.0);
            when(ivDataCollector.collectIVDataPoint(sym)).thenReturn(dp);
        }
        symbols.add("FAIL_SYM");
        when(ivDataCollector.collectIVDataPoint("FAIL_SYM")).thenReturn(null);

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> syms = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return syms.stream().map(func).toList();
        });

        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(symbols).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();

        verify(supabaseService, times(9)).upsertIVData(any());
        verify(telegramUtils, atLeastOnce()).sendMessage(argThat(msg ->
                msg.contains("FAIL_SYM")));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRunIVDataCollection_FailsWhenSuccessRateBelowThreshold() throws Exception {
        // 1 symbol succeeds, 5 fail -> < 90% success rate, should throw IllegalStateException
        Set<String> symbols = new java.util.LinkedHashSet<>();
        IVDataPoint dp = new IVDataPoint();
        dp.setSymbol("SYM1");
        dp.setAtmPutIV(20.0);
        when(ivDataCollector.collectIVDataPoint("SYM1")).thenReturn(dp);
        symbols.add("SYM1");

        for (int i = 2; i <= 6; i++) {
            String sym = "FAIL" + i;
            symbols.add(sym);
            when(ivDataCollector.collectIVDataPoint(sym)).thenReturn(null);
        }

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> syms = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return syms.stream().map(func).toList();
        });

        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(symbols).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRunIVDataCollection_FailsWhenAllFail() throws Exception {
        when(ivDataCollector.collectIVDataPoint("FAIL")).thenReturn(null);

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> syms = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return syms.stream().map(func).toList();
        });

        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(Collections.singleton("FAIL")).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();
    }

    @Test
    public void testRunIVDataCollection_RetrySucceeds() throws Exception {
        IVDataPoint recovered = new IVDataPoint();
        recovered.setSymbol("CTVA");
        recovered.setAtmPutIV(30.0);
        recovered.setAtmCallIV(30.0);

        // First call fails, second call succeeds
        when(ivDataCollector.collectIVDataPoint("CTVA"))
                .thenReturn(null)
                .thenReturn(recovered);

        when(schwabApiExecutor.executeParallel(anyList(), any())).thenAnswer(invocation -> {
            java.util.List<String> syms = invocation.getArgument(0);
            java.util.function.Function<String, IVDataPoint> func = invocation.getArgument(1);
            return syms.stream().map(func).toList();
        });

        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(Collections.singleton("CTVA")).when(spyService).loadAllSecurities();

        spyService.runIVDataCollection();

        verify(supabaseService, times(1)).upsertIVData(recovered);
    }
}
