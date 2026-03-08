package com.hemasundar.options.models;

import com.hemasundar.options.models.OptionsStrategyFilter;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class OptionsStrategyFilterTest {

    @Test
    public void testPassesMaxLoss() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .maxLossLimit(1000.0)
                .build();

        assertTrue(filter.passesMaxLoss(500.0));
        assertTrue(filter.passesMaxLoss(1000.0));
        assertFalse(filter.passesMaxLoss(1000.01));

        filter.setMaxLossLimit(null);
        assertTrue(filter.passesMaxLoss(5000.0));
    }

    @Test
    public void testPassesDebitLimit() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .maxTotalDebit(5.0)
                .build();

        assertTrue(filter.passesDebitLimit(2.0));
        assertTrue(filter.passesDebitLimit(5.0));
        assertFalse(filter.passesDebitLimit(5.01));
    }

    @Test
    public void testPassesCreditLimit() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .maxTotalCredit(10.0)
                .build();

        assertTrue(filter.passesCreditLimit(5.0));
        assertTrue(filter.passesCreditLimit(10.0));
        assertFalse(filter.passesCreditLimit(10.1));

        filter.setMaxTotalCredit(0.0);
        assertFalse(filter.passesCreditLimit(1.0));
    }

    @Test
    public void testPassesMinCredit() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .minTotalCredit(2.0)
                .build();

        assertTrue(filter.passesMinCredit(2.0));
        assertTrue(filter.passesMinCredit(3.0));
        assertFalse(filter.passesMinCredit(1.99));
    }

    @Test
    public void testPassesMinReturnOnRisk() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .minReturnOnRisk(10) // 10%
                .build();

        // profit = 100, maxLoss = 1000 -> 10% -> pass
        assertTrue(filter.passesMinReturnOnRisk(100.0, 1000.0));
        // profit = 99, maxLoss = 1000 -> 9.9% -> fail
        assertFalse(filter.passesMinReturnOnRisk(99.0, 1000.0));
        // maxLoss = 0 -> pass
        assertTrue(filter.passesMinReturnOnRisk(10.0, 0.0));
    }

    @Test
    public void testPassesMaxBreakEvenPercentage() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .maxBreakEvenPercentage(5.0)
                .build();

        assertTrue(filter.passesMaxBreakEvenPercentage(4.0));
        assertTrue(filter.passesMaxBreakEvenPercentage(5.0));
        assertFalse(filter.passesMaxBreakEvenPercentage(5.01));
    }
}
