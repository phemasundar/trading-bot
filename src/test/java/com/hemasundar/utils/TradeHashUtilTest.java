package com.hemasundar.utils;

import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

public class TradeHashUtilTest {

    @Test
    public void testGenerateTradeHashSimilarTradesSameDayDeduplication() {
        TradeLegDTO leg1Morning = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).strike(150.0).build();
        TradeLegDTO leg2Morning = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).strike(145.0).build();
        Trade morningTrade = Trade.builder()
                .symbol("AAPL")
                .expiryDate("2026-09-18")
                .legs(List.of(leg1Morning, leg2Morning))
                .build();

        TradeLegDTO leg1Afternoon = TradeLegDTO.builder().action("SELL").optionType("PUT").quantity(1).strike(152.5).build();
        TradeLegDTO leg2Afternoon = TradeLegDTO.builder().action("BUY").optionType("PUT").quantity(1).strike(147.5).build();
        Trade afternoonTrade = Trade.builder()
                .symbol("AAPL")
                .expiryDate("2026-09-18")
                .legs(List.of(leg1Afternoon, leg2Afternoon))
                .build();

        long morningTime = 1786541400000L;
        long afternoonTime = 1786559400000L;

        String hashMorning = TradeHashUtil.generateTradeHash("put_credit_spread", morningTrade, morningTime);
        String hashAfternoon = TradeHashUtil.generateTradeHash("put_credit_spread", afternoonTrade, afternoonTime);

        assertNotNull(hashMorning);
        assertEquals(hashMorning.length(), 64);
        assertEquals(hashMorning, hashAfternoon);
    }

    @Test
    public void testGenerateTradeHashDifferentDays() {
        Trade trade = Trade.builder().symbol("MSFT").expiryDate("2026-10-16").build();

        String hashDay1 = TradeHashUtil.generateTradeHash("iron_condor", trade, "2026-08-13");
        String hashDay2 = TradeHashUtil.generateTradeHash("iron_condor", trade, "2026-08-14");

        assertNotEquals(hashDay1, hashDay2);
    }

    @Test
    public void testGenerateTradeHashNullTrade() {
        String hash1 = TradeHashUtil.generateTradeHash("strangle", null, "2026-08-13");
        String hash2 = TradeHashUtil.generateTradeHash("strangle", null, 0L);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1.length(), 64);
    }

    @Test
    public void testGenerateTradeHashNullFieldsAndLegs() {
        Trade tradeNullFields = Trade.builder()
                .symbol(null)
                .expiryDate(null)
                .legs(null)
                .build();

        String hash1 = TradeHashUtil.generateTradeHash("strangle", tradeNullFields, "2026-08-13");
        assertNotNull(hash1);

        List<TradeLegDTO> legsWithNulls = new ArrayList<>();
        legsWithNulls.add(null);
        legsWithNulls.add(TradeLegDTO.builder().action(null).optionType(null).quantity(1).build());

        Trade tradeLegsWithNulls = Trade.builder()
                .symbol("QQQ")
                .expiryDate("2026-11-20")
                .legs(legsWithNulls)
                .build();

        String hash2 = TradeHashUtil.generateTradeHash("strangle", tradeLegsWithNulls, "2026-08-13");
        assertNotNull(hash2);

        Trade tradeEmptyLegs = Trade.builder()
                .symbol("SPY")
                .expiryDate("2026-12-18")
                .legs(Collections.emptyList())
                .build();
        String hash3 = TradeHashUtil.generateTradeHash("strangle", tradeEmptyLegs, "2026-08-13");
        assertNotNull(hash3);
    }
}
