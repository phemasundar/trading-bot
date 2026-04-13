package com.hemasundar.utils;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import com.hemasundar.technical.TechnicalScreener;
import org.testng.annotations.Test;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;

import com.hemasundar.config.properties.TelegramConfig;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import com.hemasundar.technical.TechnicalScreener;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

public class TelegramUtilsTest {

    @Mock
    private TelegramConfig telegramConfig;

    private TelegramUtils telegramUtils;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        telegramUtils = new TelegramUtils(telegramConfig);
        
        // Default to disabled to avoid external calls by default
        when(telegramConfig.getEnabled()).thenReturn(false);
    }

    @Test
    public void testSendMessage_Disabled() {
        when(telegramConfig.getEnabled()).thenReturn(false);
        assertTrue(telegramUtils.sendMessage("Test message"));
    }

    @Test
    public void testSendMessage_MissingToken() {
        when(telegramConfig.getEnabled()).thenReturn(true);
        when(telegramConfig.getBotToken()).thenReturn(null);
        when(telegramConfig.getChatId()).thenReturn("chatId");

        assertFalse(telegramUtils.sendMessage("Test message"));
    }

    @Test
    public void testSendTradeAlerts_Success() {
        Trade trade = Trade.builder()
                .symbol("AAPL")
                .underlyingPrice(150.0)
                .expiryDate("2026-01-02")
                .dte(30)
                .legs(List.of(
                        TradeLegDTO.builder().action("SELL").optionType("PUT").strike(150.0).delta(0.50).premium(5.00).build(),
                        TradeLegDTO.builder().action("BUY").optionType("PUT").strike(145.0).delta(0.30).premium(2.00).build()
                ))
                .netCredit(300.0)
                .maxLoss(200.0)
                .breakEvenPrice(147.0)
                .breakEvenPercent(2.0)
                .tradeDetails("SELL 150 PUT (δ 0.50) → $5.00\nBUY 145 PUT (δ 0.30) → $2.00\nCredit: $300 | Max Loss: $200\nBE: $147.00 (2.00%)")
                .build();

        StrategyResult result = StrategyResult.builder()
                .strategyId("Put Credit Spread")
                .strategyName("Put Credit Spread")
                .tradesFound(1)
                .trades(List.of(trade))
                .build();

        // This should not throw exception even if Telegram is disabled
        telegramUtils.sendTradeAlerts(result);
    }

    @Test
    public void testSendTechnicalScreenerAlert_Success() {
        TechnicalScreener.ScreeningResult result = TechnicalScreener.ScreeningResult.builder()
                .symbol("AAPL")
                .currentPrice(150.0)
                .rsi(25.0)
                .previousRsi(35.0)
                .volume(1500000L)
                .bollingerLower(145.0)
                .bollingerUpper(160.0)
                .priceTouchingLowerBand(true)
                .priceBelowMA200(true)
                .build();

        telegramUtils.sendTechnicalScreenerAlert("Oversold Screener", List.of(result));
    }

    @Test
    public void testFormatScreeningResult_VariousFlags() {
        TechnicalScreener.ScreeningResult result = TechnicalScreener.ScreeningResult.builder()
                .symbol("TSLA")
                .currentPrice(200.0)
                .rsi(85.0)
                .rsiOverbought(true)
                .priceTouchingUpperBand(true)
                .bollingerUpper(200.0)
                .bollingerLower(180.0)
                .volume(5000000L)
                .priceBelowMA20(false)
                .priceBelowMA50(false)
                .priceBelowMA100(false)
                .priceBelowMA200(false)
                .build();

        telegramUtils.sendTechnicalScreenerAlert("Overbought Screener", List.of(result));
    }

    @Test
    public void testSendMessage_SplitMessage() {
        try (org.mockito.MockedStatic<io.restassured.RestAssured> mockedRestAssured = org.mockito.Mockito
                .mockStatic(io.restassured.RestAssured.class)) {
            
            when(telegramConfig.getEnabled()).thenReturn(true);
            when(telegramConfig.getBotToken()).thenReturn("token");
            when(telegramConfig.getChatId()).thenReturn("chatId");

            io.restassured.specification.RequestSpecification requestSpec = org.mockito.Mockito.mock(io.restassured.specification.RequestSpecification.class);
            io.restassured.response.Response response = org.mockito.Mockito.mock(io.restassured.response.Response.class);
            mockedRestAssured.when(io.restassured.RestAssured::given).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.contentType(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.body(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.when()).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.post(org.mockito.ArgumentMatchers.anyString())).thenReturn(response);
            org.mockito.Mockito.when(response.statusCode()).thenReturn(200);

            StringBuilder longMsg = new StringBuilder();
            for (int i = 0; i < 600; i++) {
                longMsg.append("Line ").append(i).append(" with some extra text to make it longer and cross the limit faster\n");
            }
            
            assertTrue(telegramUtils.sendMessage(longMsg.toString()));
        }
    }

    @Test
    public void testSendTradeAlerts_LongCallLeap() {
        Trade trade = Trade.builder()
                .symbol("AAPL")
                .underlyingPrice(180.0)
                .expiryDate("2026-06-18")
                .dte(500)
                .legs(List.of(
                        TradeLegDTO.builder().action("BUY").optionType("CALL").strike(150.0).delta(0.80).premium(40.00).build()
                ))
                .netCredit(-40.0)
                .maxLoss(4000.0)
                .breakEvenPrice(190.0)
                .breakEvenPercent(5.5)
                .tradeDetails("BUY 150 CALL (δ 0.80) → $40.00\nDebit: $40 | Max Loss: $4000\nBE: $190.00 (5.50%)\nCost (Opt/Stock): $4000.00 / $18000.00 (77.8% cheaper)")
                .build();

        StrategyResult result = StrategyResult.builder()
                .strategyId("Long Call LEAP")
                .strategyName("Long Call LEAP")
                .tradesFound(1)
                .trades(List.of(trade))
                .build();

        telegramUtils.sendTradeAlerts(result);
    }

    @Test
    public void testSendMessage_SplitMessage_NoNewlines() {
        try (org.mockito.MockedStatic<io.restassured.RestAssured> mockedRestAssured = org.mockito.Mockito
                .mockStatic(io.restassured.RestAssured.class)) {
            
            when(telegramConfig.getEnabled()).thenReturn(true);
            when(telegramConfig.getBotToken()).thenReturn("token");
            when(telegramConfig.getChatId()).thenReturn("chatId");

            io.restassured.specification.RequestSpecification requestSpec = org.mockito.Mockito.mock(io.restassured.specification.RequestSpecification.class);
            io.restassured.response.Response response = org.mockito.Mockito.mock(io.restassured.response.Response.class);
            mockedRestAssured.when(io.restassured.RestAssured::given).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.contentType(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.body(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.when()).thenReturn(requestSpec);
            org.mockito.Mockito.when(requestSpec.post(org.mockito.ArgumentMatchers.anyString())).thenReturn(response);
            org.mockito.Mockito.when(response.statusCode()).thenReturn(200);

            // Create a very long string without newlines or spaces
            StringBuilder longMsg = new StringBuilder();
            for (int i = 0; i < 5000; i++) {
                longMsg.append("a");
            }
            
            assertTrue(telegramUtils.sendMessage(longMsg.toString()));
        }
    }

    @Test
    public void testSendTradeAlerts_NullResult() {
        telegramUtils.sendTradeAlerts(null);
    }

    @Test
    public void testSendTradeAlerts_EmptyTrades() {
        StrategyResult result = StrategyResult.builder()
                .strategyName("Strategy")
                .trades(Collections.emptyList())
                .build();
        telegramUtils.sendTradeAlerts(result);
    }

    @Test
    public void testSendTradeAlerts_NullTrades() {
        StrategyResult result = StrategyResult.builder()
                .strategyName("Strategy")
                .trades(null)
                .build();
        telegramUtils.sendTradeAlerts(result);
    }

    @Test
    public void testSendTechnicalScreenerAlert_NullResults() {
        telegramUtils.sendTechnicalScreenerAlert("Screener", null);
    }

    @Test
    public void testSendTechnicalScreenerAlert_EmptyResults() {
        telegramUtils.sendTechnicalScreenerAlert("Screener", Collections.emptyList());
    }

    @Test
    public void testEscapeHtml() {
        String input = "<b>Hello</b> & World <script>";
        String escaped = TelegramUtils.escapeHtml(input);
        assertTrue(escaped.contains("<b>"));
        assertTrue(escaped.contains("</b>"));
        assertTrue(escaped.contains("&amp;"));
        assertTrue(escaped.contains("&lt;script&gt;"));
    }

    @Test
    public void testSplitMessage() {
        String shortMsg = "Short message";
        List<String> parts = TelegramUtils.splitMessage(shortMsg, 100);
        assertEquals(parts.size(), 1);
        assertEquals(parts.get(0), shortMsg);

        // Test null
        List<String> nullParts = TelegramUtils.splitMessage(null, 100);
        assertEquals(nullParts.size(), 1);
        assertEquals(nullParts.get(0), "");
    }

    @Test
    public void testGetFormattedSummary_Oversold() {
        TechnicalScreener.ScreeningResult result = TechnicalScreener.ScreeningResult.builder()
                .symbol("AAPL")
                .currentPrice(150.0)
                .rsi(25.0)
                .previousRsi(35.0)
                .volume(1500000L)
                .rsiOversold(true)
                .bollingerLower(145.0)
                .bollingerUpper(160.0)
                .priceTouchingLowerBand(true)
                .priceBelowMA200(true)
                .build();

        String summary = result.getFormattedSummary();
        assertTrue(summary.contains("Price: $150.00"));
        assertTrue(summary.contains("OVERSOLD"));
        assertTrue(summary.contains("Touching Lower"));
        assertTrue(summary.contains("MA200"));
    }
}
