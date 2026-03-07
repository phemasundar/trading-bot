package com.hemasundar.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.options.models.*;
import java.util.*;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class VerifyMappingTest {
    @Test
    public void testStrategyMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Sample request map from UI for Long Call LEAP
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("targetDTE", 45);
        filterMap.put("maxCAGRForBreakEven", 12.5);
        filterMap.put("minCostSavingsPercent", 15.0);

        Map<String, Object> longCall = new HashMap<>();
        longCall.put("minDelta", 0.70);
        longCall.put("maxDelta", 0.85);
        filterMap.put("longCall", longCall);

        List<String> relaxation = Arrays.asList("maxCAGRForBreakEven", "maxOptionPricePercent");
        filterMap.put("relaxationPriority", relaxation);

        // Convert to LongCallLeapFilter
        LongCallLeapFilter filter = mapper.convertValue(filterMap, LongCallLeapFilter.class);

        assertEquals(filter.getTargetDTE(), 45);
        assertEquals(filter.getMaxCAGRForBreakEven(), 12.5);
        assertEquals(filter.getMinCostSavingsPercent(), 15.0);
        assertEquals(filter.getLongCall().getMinDelta(), 0.70);
        assertEquals(filter.getRelaxationPriority(), relaxation);

        // Sample for Iron Condor
        Map<String, Object> icMap = new HashMap<>();
        icMap.put("minTotalCredit", 150.0);
        Map<String, Object> putShort = new HashMap<>();
        putShort.put("maxDelta", 0.30);
        icMap.put("putShortLeg", putShort);

        IronCondorFilter icFilter = mapper.convertValue(icMap, IronCondorFilter.class);
        assertEquals(icFilter.getMinTotalCredit(), 150.0);
        assertEquals(icFilter.getPutShortLeg().getMaxDelta(), 0.30);
    }
}
