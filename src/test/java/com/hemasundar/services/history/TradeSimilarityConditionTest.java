package com.hemasundar.services.history;

import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class TradeSimilarityConditionTest {

    private TradeSimilarityCondition similarityCondition;

    @BeforeMethod
    public void setUp() {
        similarityCondition = new TradeSimilarityCondition();
    }

    @Test
    public void testIsSimilarNullInputs() {
        assertFalse(similarityCondition.isSimilar(null, null));
        assertFalse(similarityCondition.isSimilar(Trade.builder().symbol("AAPL").build(), null));
        assertFalse(similarityCondition.isSimilar(null, Trade.builder().symbol("AAPL").build()));
    }

    @Test
    public void testIsSimilarSymbolChecks() {
        Trade t1 = Trade.builder().symbol(null).build();
        Trade t2 = Trade.builder().symbol("AAPL").build();
        assertFalse(similarityCondition.isSimilar(t1, t2));

        Trade t3 = Trade.builder().symbol("AAPL").build();
        Trade t4 = Trade.builder().symbol("MSFT").build();
        assertFalse(similarityCondition.isSimilar(t3, t4));

        Trade t5 = Trade.builder().symbol("aapl").legs(List.of()).build();
        Trade t6 = Trade.builder().symbol("AAPL").legs(List.of()).build();
        assertTrue(similarityCondition.isSimilar(t5, t6));
    }

    @Test
    public void testIsSimilarLegCountAndNullLegs() {
        TradeLegDTO leg1 = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).build();

        Trade t1 = Trade.builder().symbol("AAPL").legs(null).build();
        Trade t2 = Trade.builder().symbol("AAPL").legs(List.of(leg1)).build();
        assertFalse(similarityCondition.isSimilar(t1, t2));
        assertFalse(similarityCondition.isSimilar(t2, t1));

        Trade t3 = Trade.builder().symbol("AAPL").legs(List.of(leg1)).build();
        Trade t4 = Trade.builder().symbol("AAPL").legs(List.of(leg1, leg1)).build();
        assertFalse(similarityCondition.isSimilar(t3, t4));

        List<TradeLegDTO> legs1 = new ArrayList<>();
        legs1.add(null);
        List<TradeLegDTO> legs2 = new ArrayList<>();
        legs2.add(leg1);

        Trade t5 = Trade.builder().symbol("AAPL").legs(legs1).build();
        Trade t6 = Trade.builder().symbol("AAPL").legs(legs2).build();
        assertFalse(similarityCondition.isSimilar(t5, t6));
    }

    @Test
    public void testIsSimilarLegMismatches() {
        TradeLegDTO sellPut1 = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).build();
        TradeLegDTO buyPut1 = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).build();
        TradeLegDTO sellCall1 = TradeLegDTO.builder().action("SELL").optionType("CALL").quantity(1).build();
        TradeLegDTO sellPut2 = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(2).build();
        TradeLegDTO nullActionLeg = TradeLegDTO.builder().action(null).optionType("PUT").quantity(1).build();

        Trade base = Trade.builder().symbol("AAPL").legs(List.of(sellPut1)).build();

        assertFalse(similarityCondition.isSimilar(base, Trade.builder().symbol("AAPL").legs(List.of(buyPut1)).build()));
        assertFalse(similarityCondition.isSimilar(base, Trade.builder().symbol("AAPL").legs(List.of(sellCall1)).build()));
        assertFalse(similarityCondition.isSimilar(base, Trade.builder().symbol("AAPL").legs(List.of(sellPut2)).build()));
        assertFalse(similarityCondition.isSimilar(base, Trade.builder().symbol("AAPL").legs(List.of(nullActionLeg)).build()));

        Trade nullLegs1 = Trade.builder().symbol("AAPL").legs(List.of(nullActionLeg)).build();
        Trade nullLegs2 = Trade.builder().symbol("AAPL").legs(List.of(nullActionLeg)).build();
        assertTrue(similarityCondition.isSimilar(nullLegs1, nullLegs2));
    }
}
