package com.hemasundar.utils;

import com.hemasundar.options.models.PutCreditSpread;
import com.hemasundar.technical.TechnicalScreener;
import com.hemasundar.utils.TelegramUtils;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class TelegramUtilsTest {

    @Test
    public void testSendMessage_Disabled() {
        // test.properties has telegram_enabled=false
        assertTrue(TelegramUtils.sendMessage("Test message"));
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
                .build();

        TelegramUtils.sendTechnicalScreenerAlert("Oversold Screener", List.of(result));
    }
}
