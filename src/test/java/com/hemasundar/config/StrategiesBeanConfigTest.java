package com.hemasundar.config;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.strategies.*;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.utils.VolatilityCalculator;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.*;

public class StrategiesBeanConfigTest {

    @Mock
    private FinnHubAPIs finnHubAPIs;
    @Mock
    private ThinkOrSwimAPIs ThinkOrSwimAPIs;
    @Mock
    private VolatilityCalculator volatilityCalculator;
    @Mock
    private SupabaseService supabaseService;

    private StrategiesBeanConfig beanConfig;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        beanConfig = new StrategiesBeanConfig();
    }

    @Test
    public void testBeanInstantiation() {
        Optional<SupabaseService> optDb = Optional.of(supabaseService);

        PutCreditSpreadStrategy pcs = beanConfig.putCreditSpreadStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(pcs);
        assertEquals(pcs.getStrategyType(), StrategyType.PUT_CREDIT_SPREAD);

        PutCreditSpreadStrategy tpcs = beanConfig.techPutCreditSpreadStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(tpcs);
        assertEquals(tpcs.getStrategyType(), StrategyType.TECH_PUT_CREDIT_SPREAD);

        PutCreditSpreadStrategy blpcs = beanConfig.bullishLongPutCreditSpreadStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(blpcs);
        assertEquals(blpcs.getStrategyType(), StrategyType.BULLISH_LONG_PUT_CREDIT_SPREAD);

        CallCreditSpreadStrategy ccs = beanConfig.callCreditSpreadStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(ccs);
        assertEquals(ccs.getStrategyType(), StrategyType.CALL_CREDIT_SPREAD);

        CallCreditSpreadStrategy tccs = beanConfig.techCallCreditSpreadStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(tccs);
        assertEquals(tccs.getStrategyType(), StrategyType.TECH_CALL_CREDIT_SPREAD);

        IronCondorStrategy ic = beanConfig.ironCondorStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb, pcs, ccs);
        assertNotNull(ic);
        assertEquals(ic.getStrategyType(), StrategyType.IRON_CONDOR);

        IronCondorStrategy blic = beanConfig.bullishLongIronCondorStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb, pcs, ccs);
        assertNotNull(blic);
        assertEquals(blic.getStrategyType(), StrategyType.BULLISH_LONG_IRON_CONDOR);

        LongCallLeapStrategy leap = beanConfig.longCallLeapStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(leap);
        assertEquals(leap.getStrategyType(), StrategyType.LONG_CALL_LEAP);

        BrokenWingButterflyStrategy bwb = beanConfig.brokenWingButterflyStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(bwb);
        assertEquals(bwb.getStrategyType(), StrategyType.BULLISH_BROKEN_WING_BUTTERFLY);

        ZebraStrategy zebra = beanConfig.zebraStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(zebra);
        assertEquals(zebra.getStrategyType(), StrategyType.BULLISH_ZEBRA);

        ShortPutStrategy sp = beanConfig.shortPutStrategy(
                finnHubAPIs, ThinkOrSwimAPIs, optDb);
        assertNotNull(sp);
        assertEquals(sp.getStrategyType(), StrategyType.SHORT_PUT);
    }
}
