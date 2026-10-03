package com.hemasundar.services;

import com.hemasundar.dto.StrategyDescriptionDto;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class LearningCenterServiceTest {

    private LearningCenterService service;

    @BeforeMethod
    public void setup() {
        service = new LearningCenterService();
    }

    @Test
    public void testGetOptionStrategies_ReturnsStrategies() {
        List<StrategyDescriptionDto> strategies = service.getOptionStrategies();
        Assert.assertNotNull(strategies);
        Assert.assertFalse(strategies.isEmpty(), "Should discover option strategies from static/descriptions");

        // Verify Iron Condor exists and is parsed correctly
        Optional<StrategyDescriptionDto> ironCondorOpt = strategies.stream()
                .filter(s -> "iron_condor".equalsIgnoreCase(s.getId()))
                .findFirst();

        Assert.assertTrue(ironCondorOpt.isPresent(), "Iron Condor strategy should be present");
        StrategyDescriptionDto ic = ironCondorOpt.get();
        Assert.assertEquals(ic.getName(), "Iron Condor");
        Assert.assertEquals(ic.getFilename(), "iron_condor.md");
        Assert.assertEquals(ic.getBias(), "Neutral");
        Assert.assertNotNull(ic.getGreeks());
        Assert.assertEquals(ic.getGreeks().get("Delta"), "Neutral");
        Assert.assertEquals(ic.getGreeks().get("Theta"), "Positive");
        Assert.assertFalse(ic.getSummary().isEmpty());
    }

    @Test
    public void testGetOptionStrategies_ExcludesNonOptionScreeners() {
        List<StrategyDescriptionDto> strategies = service.getOptionStrategies();
        boolean hasAtrScreener = strategies.stream().anyMatch(s -> "atr_screener".equalsIgnoreCase(s.getId()));
        Assert.assertFalse(hasAtrScreener, "Option strategies list should exclude technical screeners");
    }

    @Test
    public void testGetStrategyById_Success() {
        Optional<StrategyDescriptionDto> res = service.getStrategyById("put_credit_spread");
        Assert.assertTrue(res.isPresent());
        StrategyDescriptionDto pcs = res.get();
        Assert.assertEquals(pcs.getBias(), "Bullish");
        Assert.assertEquals(pcs.getGreeks().get("Delta"), "Positive");

        // Also test with .md extension
        Optional<StrategyDescriptionDto> withExt = service.getStrategyById("put_credit_spread.md");
        Assert.assertTrue(withExt.isPresent());
        Assert.assertEquals(withExt.get().getId(), "put_credit_spread");
    }

    @Test
    public void testGetStrategyById_NotFoundOrBlank() {
        Assert.assertTrue(service.getStrategyById(null).isEmpty());
        Assert.assertTrue(service.getStrategyById("   ").isEmpty());
        Assert.assertTrue(service.getStrategyById("non_existent_strategy_xyz").isEmpty());
    }

    @Test
    public void testLoadAllDescriptions_IncludesScreener() {
        List<StrategyDescriptionDto> all = service.loadAllDescriptions();
        Assert.assertNotNull(all);
        boolean hasScreener = all.stream().anyMatch(s -> "atr_screener".equalsIgnoreCase(s.getId()));
        Assert.assertTrue(hasScreener, "All descriptions should include atr_screener");
    }
}
