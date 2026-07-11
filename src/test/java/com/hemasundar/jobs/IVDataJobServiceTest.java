package com.hemasundar.jobs;

import com.hemasundar.apis.ThinkOrSwinAPIs;
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
    private ThinkOrSwinAPIs thinkOrSwinAPIs;

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
                thinkOrSwinAPIs,
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


}
