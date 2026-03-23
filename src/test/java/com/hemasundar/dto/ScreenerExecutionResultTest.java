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

        // Test toBuilder
        ScreenerExecutionResult updatedResult = result.toBuilder().screenerId("new-id").build();
        assertEquals(updatedResult.getScreenerId(), "new-id");

        // Test toString
        assertNotNull(updatedResult.toString());
        
        // Test Equals/HashCode
        ScreenerExecutionResult sameResult = ScreenerExecutionResult.builder()
                .screenerId("new-id")
                .screenerName("Test Screener")
                .executionTimeMs(1234L)
                .resultsFound(5)
                .results(results)
                .updatedAt(now)
                .build();
        
        assertEquals(updatedResult, sameResult);
        assertEquals(updatedResult.hashCode(), sameResult.hashCode());
    }
}
