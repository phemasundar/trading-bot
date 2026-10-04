package com.hemasundar.services;

import com.hemasundar.dto.OptionGreekDto;
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

    @Test
    public void testGetOptionGreeks_ReturnsGreeks() {
        List<OptionGreekDto> greeks = service.getOptionGreeks();
        Assert.assertNotNull(greeks);
        Assert.assertEquals(greeks.size(), 8, "Should return 8 Option Greek guides");

        // Verify Delta
        OptionGreekDto delta = greeks.stream().filter(g -> "delta".equals(g.getId())).findFirst().orElse(null);
        Assert.assertNotNull(delta);
        Assert.assertEquals(delta.getName(), "Delta");
        Assert.assertEquals(delta.getSymbol(), "Δ");
        Assert.assertEquals(delta.getOrder(), "First-Order");
        Assert.assertEquals(delta.getDerivative(), "∂V / ∂S");
        Assert.assertEquals(delta.getFilename(), "greeks/delta.md");
        Assert.assertFalse(delta.getSummary().isEmpty());

        // Verify Gamma
        OptionGreekDto gamma = greeks.stream().filter(g -> "gamma".equals(g.getId())).findFirst().orElse(null);
        Assert.assertNotNull(gamma);
        Assert.assertEquals(gamma.getName(), "Gamma");
        Assert.assertEquals(gamma.getSymbol(), "Γ");
        Assert.assertEquals(gamma.getOrder(), "Second-Order");
        Assert.assertEquals(gamma.getDerivative(), "∂²V / ∂S²");

        // Verify Second-Order Greeks presence
        Assert.assertTrue(greeks.stream().anyMatch(g -> "vanna".equals(g.getId())));
        Assert.assertTrue(greeks.stream().anyMatch(g -> "charm".equals(g.getId())));
        Assert.assertTrue(greeks.stream().anyMatch(g -> "volga".equals(g.getId())));
    }

    @Test
    public void testGetGreekById_Success() {
        Optional<OptionGreekDto> deltaOpt = service.getGreekById("delta");
        Assert.assertTrue(deltaOpt.isPresent());
        Assert.assertEquals(deltaOpt.get().getName(), "Delta");
        Assert.assertEquals(deltaOpt.get().getOrder(), "First-Order");

        // Test with .md suffix and greeks/ prefix
        Optional<OptionGreekDto> withSuffix = service.getGreekById("gamma.md");
        Assert.assertTrue(withSuffix.isPresent());
        Assert.assertEquals(withSuffix.get().getId(), "gamma");

        Optional<OptionGreekDto> withPrefix = service.getGreekById("greeks/vanna.md");
        Assert.assertTrue(withPrefix.isPresent());
        Assert.assertEquals(withPrefix.get().getId(), "vanna");
    }

    @Test
    public void testGetGreekById_NotFoundOrBlank() {
        Assert.assertTrue(service.getGreekById(null).isEmpty());
        Assert.assertTrue(service.getGreekById("   ").isEmpty());
        Assert.assertTrue(service.getGreekById("non_existent_greek_xyz").isEmpty());
    }
}
