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

    @Test
    public void testIsSimilarDifferentStrikesStillSimilar() {
        TradeLegDTO morningSell = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).strike(150.0).build();
        TradeLegDTO morningBuy = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).strike(145.0).build();
        Trade trade150 = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").legs(List.of(morningSell, morningBuy)).build();

        TradeLegDTO afternoonSell = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).strike(175.0).build();
        TradeLegDTO afternoonBuy = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).strike(170.0).build();
        Trade trade175 = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").legs(List.of(afternoonSell, afternoonBuy)).build();

        // History lookup matches all historical trades with same symbol, same expiry date, and same structural legs regardless of strike prices
        assertTrue(similarityCondition.isSimilar(trade150, trade175));
    }

    @Test
    public void testIsSimilarExpiryMismatch() {
        TradeLegDTO sellPut = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).strike(150.0).build();
        TradeLegDTO buyPut = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).strike(145.0).build();
        Trade tradeSept = Trade.builder().symbol("AAPL").expiryDate("2026-09-18").legs(List.of(sellPut, buyPut)).build();
        Trade tradeOct = Trade.builder().symbol("AAPL").expiryDate("2026-10-16").legs(List.of(sellPut, buyPut)).build();

        assertFalse(similarityCondition.isSimilar(tradeSept, tradeOct));
    }
}
