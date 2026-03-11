package com.hemasundar.dto;

import com.hemasundar.technical.TechnicalScreener;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class ScreenerExecutionResultTest {

    @Test
    public void testScreenerExecutionResultPOJO() {
        List<TechnicalScreener.ScreeningResult> results = new ArrayList<>();
        Instant now = Instant.now();
        
        ScreenerExecutionResult result = ScreenerExecutionResult.builder()
                .screenerId("test-id")
                .screenerName("Test Screener")
                .executionTimeMs(1234L)
                .resultsFound(5)
                .results(results)
                .updatedAt(now)
                .build();

        assertEquals(result.getScreenerId(), "test-id");
        assertEquals(result.getScreenerName(), "Test Screener");
        assertEquals(result.getExecutionTimeMs(), 1234L);
        assertEquals(result.getResultsFound(), 5);
        assertEquals(result.getResults(), results);
        assertEquals(result.getUpdatedAt(), now);

        // Test setters
        result.setScreenerId("new-id");
        assertEquals(result.getScreenerId(), "new-id");

        // Test toString
        assertNotNull(result.toString());
        
        // Test Equals/HashCode
        ScreenerExecutionResult sameResult = ScreenerExecutionResult.builder()
                .screenerId("new-id")
                .screenerName("Test Screener")
                .executionTimeMs(1234L)
                .resultsFound(5)
                .results(results)
                .updatedAt(now)
                .build();
        
        assertEquals(result, sameResult);
        assertEquals(result.hashCode(), sameResult.hashCode());
    }
}
