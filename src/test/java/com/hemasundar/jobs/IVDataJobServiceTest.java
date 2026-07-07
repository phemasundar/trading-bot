package com.hemasundar.jobs;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.config.properties.SupabaseConfig;
import com.hemasundar.pojos.IVDataPoint;
import com.hemasundar.services.IVDataCollector;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.TelegramUtils;
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

    private IVDataJobService ivDataJobService;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ivDataJobService = new IVDataJobService(
                Optional.of(supabaseService),
                supabaseConfig,
                thinkOrSwinAPIs,
                telegramUtils,
                ivDataCollector
        );
    }

    @Test
    public void testRunIVDataCollection_Success() throws Exception {

        // Mock data point collection
        IVDataPoint dataPoint = new IVDataPoint();
        dataPoint.setSymbol("AAPL");
        when(ivDataCollector.collectIVDataPoint("AAPL")).thenReturn(dataPoint);

        // Spy on service to mock loadAllSecurities to avoid loading 128 symbols and sleeping
        IVDataJobService spyService = spy(ivDataJobService);
        doReturn(Collections.singleton("AAPL")).when(spyService).loadAllSecurities();
        
        spyService.runIVDataCollection();
        
        // Verify summary was sent
        verify(telegramUtils, atLeastOnce()).sendMessage(anyString());
    }


}
