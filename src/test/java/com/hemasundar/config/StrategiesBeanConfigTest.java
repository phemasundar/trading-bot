package com.hemasundar.config;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
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
    private ThinkOrSwinAPIs thinkOrSwinAPIs;
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
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(pcs);
        assertEquals(pcs.getStrategyType(), StrategyType.PUT_CREDIT_SPREAD);

        PutCreditSpreadStrategy tpcs = beanConfig.techPutCreditSpreadStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(tpcs);
        assertEquals(tpcs.getStrategyType(), StrategyType.TECH_PUT_CREDIT_SPREAD);

        PutCreditSpreadStrategy blpcs = beanConfig.bullishLongPutCreditSpreadStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(blpcs);
        assertEquals(blpcs.getStrategyType(), StrategyType.BULLISH_LONG_PUT_CREDIT_SPREAD);

        CallCreditSpreadStrategy ccs = beanConfig.callCreditSpreadStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(ccs);
        assertEquals(ccs.getStrategyType(), StrategyType.CALL_CREDIT_SPREAD);

        CallCreditSpreadStrategy tccs = beanConfig.techCallCreditSpreadStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(tccs);
        assertEquals(tccs.getStrategyType(), StrategyType.TECH_CALL_CREDIT_SPREAD);

        IronCondorStrategy ic = beanConfig.ironCondorStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb, pcs, ccs);
        assertNotNull(ic);
        assertEquals(ic.getStrategyType(), StrategyType.IRON_CONDOR);

        IronCondorStrategy blic = beanConfig.bullishLongIronCondorStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb, pcs, ccs);
        assertNotNull(blic);
        assertEquals(blic.getStrategyType(), StrategyType.BULLISH_LONG_IRON_CONDOR);

        LongCallLeapStrategy leap = beanConfig.longCallLeapStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(leap);
        assertEquals(leap.getStrategyType(), StrategyType.LONG_CALL_LEAP);

        BrokenWingButterflyStrategy bwb = beanConfig.brokenWingButterflyStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(bwb);
        assertEquals(bwb.getStrategyType(), StrategyType.BULLISH_BROKEN_WING_BUTTERFLY);

        ZebraStrategy zebra = beanConfig.zebraStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(zebra);
        assertEquals(zebra.getStrategyType(), StrategyType.BULLISH_ZEBRA);

        ShortPutStrategy sp = beanConfig.shortPutStrategy(
                finnHubAPIs, thinkOrSwinAPIs, optDb);
        assertNotNull(sp);
        assertEquals(sp.getStrategyType(), StrategyType.SHORT_PUT);
    }
}
