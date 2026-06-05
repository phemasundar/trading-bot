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
    public void testPassesMinReturnOnRiskCAGR() {
        OptionsStrategyFilter filter = OptionsStrategyFilter.builder()
                .minReturnOnRiskCAGR(50) // 50%
                .build();

        // Base case: exactly 1 year (365 DTE)
        // Profit = 500, MaxLoss = 1000 -> raw return = 0.50 (50%)
        // CAGR = (1 + 0.5)^(365/365) - 1 = 0.5 (50%) -> Pass
        assertTrue(filter.passesMinReturnOnRiskCAGR(500.0, 1000.0, 365));

        // Less than 1 year (e.g. 73 days, 1/5 of a year)
        // Profit = 100, MaxLoss = 1000 -> raw return = 0.10 (10%)
        // CAGR = (1 + 0.1)^(365/73) - 1 = (1.1)^5 - 1 = 1.61051 - 1 = 0.61051 (61.05%) -> Pass
        assertTrue(filter.passesMinReturnOnRiskCAGR(100.0, 1000.0, 73));

        // Barely failing on short timeframe
        // Profit = 80, MaxLoss = 1000 -> raw return = 0.08 (8%)
        // CAGR = (1 + 0.08)^5 - 1 = 0.4693 (46.9%) -> Fail (needs 50%)
        assertFalse(filter.passesMinReturnOnRiskCAGR(80.0, 1000.0, 73));

        // Edge case: maxLoss = 0 -> pass
        assertTrue(filter.passesMinReturnOnRiskCAGR(10.0, 0.0, 30));

        // Edge case: DTE = 0 -> pass
        assertTrue(filter.passesMinReturnOnRiskCAGR(10.0, 1000.0, 0));

        // Edge case: filter disabled
        filter.setMinReturnOnRiskCAGR(null);
        assertTrue(filter.passesMinReturnOnRiskCAGR(10.0, 1000.0, 30));
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
