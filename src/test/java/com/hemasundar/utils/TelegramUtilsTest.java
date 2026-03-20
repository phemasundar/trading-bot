package com.hemasundar.utils;

import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.technical.TechnicalScreener;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Collections;
import static org.testng.Assert.*;

public class TelegramUtilsTest {

    @Test
    public void testSendMessage_Disabled() {
        try (org.mockito.MockedStatic<com.hemasundar.pojos.TestConfig> mockedConfig = org.mockito.Mockito
                .mockStatic(com.hemasundar.pojos.TestConfig.class)) {
            com.hemasundar.pojos.TestConfig mockConf = org.mockito.Mockito.mock(com.hemasundar.pojos.TestConfig.class);
            org.mockito.Mockito.when(mockConf.telegramEnabled()).thenReturn(false);
            mockedConfig.when(com.hemasundar.pojos.TestConfig::getInstance).thenReturn(mockConf);

            assertTrue(TelegramUtils.sendMessage("Test message"));
        }
    }

    @Test
    public void testSendMessage_MissingToken() {
        try (org.mockito.MockedStatic<com.hemasundar.pojos.TestConfig> mockedConfig = org.mockito.Mockito
                .mockStatic(com.hemasundar.pojos.TestConfig.class)) {
            com.hemasundar.pojos.TestConfig mockConf = new com.hemasundar.pojos.TestConfig(
                    null, null, null, null, null, null, null, true, null);
            mockedConfig.when(com.hemasundar.pojos.TestConfig::getInstance).thenReturn(mockConf);

            assertFalse(TelegramUtils.sendMessage("Test message"));
        }
    }

    @Test
    public void testSendTradeAlerts_Success() {
        com.hemasundar.options.models.OptionChainResponse.OptionData shortPut = new com.hemasundar.options.models.OptionChainResponse.OptionData();
        shortPut.setStrikePrice(150.0);
        shortPut.setDelta(0.50);
        shortPut.setMark(5.00);
        shortPut.setExpirationDate("2026-01-02");
        shortPut.setDaysToExpiration(30);

        com.hemasundar.options.models.OptionChainResponse.OptionData longPut = new com.hemasundar.options.models.OptionChainResponse.OptionData();
        longPut.setStrikePrice(145.0);
        longPut.setDelta(0.30);
        longPut.setMark(2.00);

        PutCreditSpread trade = PutCreditSpread.builder()
                .shortPut(shortPut)
                .longPut(longPut)
                .currentPrice(150.0)
                .netCredit(300.0)
                .maxLoss(200.0)
                .breakEvenPrice(147.0)
                .breakEvenPercentage(2.0)
                .build();

        // This should not throw exception even if Telegram is disabled
        TelegramUtils.sendTradeAlerts("Put Credit Spread", "AAPL", List.of(trade));
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

        TelegramUtils.sendTechnicalScreenerAlert("Oversold Screener", List.of(result));
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

        TelegramUtils.sendTechnicalScreenerAlert("Overbought Screener", List.of(result));
    }

    @Test
    public void testSendMessage_SplitMessage() {
        try (org.mockito.MockedStatic<com.hemasundar.pojos.TestConfig> mockedConfig = org.mockito.Mockito
                .mockStatic(com.hemasundar.pojos.TestConfig.class);
             org.mockito.MockedStatic<io.restassured.RestAssured> mockedRestAssured = org.mockito.Mockito
                .mockStatic(io.restassured.RestAssured.class)) {
            
            com.hemasundar.pojos.TestConfig mockConf = org.mockito.Mockito.mock(com.hemasundar.pojos.TestConfig.class);
            org.mockito.Mockito.when(mockConf.telegramEnabled()).thenReturn(true);
            org.mockito.Mockito.when(mockConf.telegramBotToken()).thenReturn("token");
            org.mockito.Mockito.when(mockConf.telegramChatId()).thenReturn("chatId");
            mockedConfig.when(com.hemasundar.pojos.TestConfig::getInstance).thenReturn(mockConf);

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
            
            assertTrue(TelegramUtils.sendMessage(longMsg.toString()));
        }
    }

    @Test
    public void testSendTradeAlerts_LongCallLeap() {
        com.hemasundar.options.models.OptionChainResponse.OptionData op = new com.hemasundar.options.models.OptionChainResponse.OptionData();
        op.setStrikePrice(150.0);
        op.setExpirationDate("2026-06-18");
        op.setDaysToExpiration(500);
        op.setMark(40.0);
        op.setDelta(0.80);

        com.hemasundar.options.models.LongCallLeap leap = com.hemasundar.options.models.LongCallLeap.builder()
                .longCall(op)
                .currentPrice(180.0)
                .finalCostOfOption(4000.0)
                .finalCostOfBuying(18000.0)
                .netCredit(-40.0) 
                .maxLoss(4000.0)
                .breakEvenPrice(190.0)
                .breakEvenPercentage(5.5)
                .build();

        TelegramUtils.sendTradeAlerts("Long Call LEAP", "AAPL", List.of(leap));
    }

    @Test
    public void testSendMessage_SplitMessage_NoNewlines() {
        try (org.mockito.MockedStatic<com.hemasundar.pojos.TestConfig> mockedConfig = org.mockito.Mockito
                .mockStatic(com.hemasundar.pojos.TestConfig.class);
             org.mockito.MockedStatic<io.restassured.RestAssured> mockedRestAssured = org.mockito.Mockito
                .mockStatic(io.restassured.RestAssured.class)) {
            
            com.hemasundar.pojos.TestConfig mockConf = org.mockito.Mockito.mock(com.hemasundar.pojos.TestConfig.class);
            org.mockito.Mockito.when(mockConf.telegramEnabled()).thenReturn(true);
            org.mockito.Mockito.when(mockConf.telegramBotToken()).thenReturn("token");
            org.mockito.Mockito.when(mockConf.telegramChatId()).thenReturn("chatId");
            mockedConfig.when(com.hemasundar.pojos.TestConfig::getInstance).thenReturn(mockConf);

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
            
            assertTrue(TelegramUtils.sendMessage(longMsg.toString()));
        }
    }

    @Test
    public void testSendTradeAlerts_EmptyList() {
        TelegramUtils.sendTradeAlerts("Strategy", "AAPL", Collections.emptyList());
    }

    @Test
    public void testSendTradeAlerts_NullList() {
        TelegramUtils.sendTradeAlerts("Strategy", "AAPL", (List<PutCreditSpread>) null);
    }

    @Test
    public void testSendTradeAlerts_Map_Null() {
        TelegramUtils.sendTradeAlerts("Strategy", (java.util.Map<String, List<com.hemasundar.options.models.TradeSetup>>) null);
    }

    @Test
    public void testSendTechnicalScreenerAlert_NullResults() {
        TelegramUtils.sendTechnicalScreenerAlert("Screener", null);
    }

    @Test
    public void testSendTechnicalScreenerAlert_EmptyResults() {
        TelegramUtils.sendTechnicalScreenerAlert("Screener", Collections.emptyList());
    }
}
