package com.hemasundar.utils;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

public class ApiErrorHandlerTest {

    @Test
    public void testHandle400Error() {
        try (MockedStatic<TelegramUtils> mockedTelegram = Mockito.mockStatic(TelegramUtils.class)) {
            ApiErrorHandler.handle400Error("Test API", "AAPL", "Bad Request Body");
            
            mockedTelegram.verify(() -> TelegramUtils.sendMessage(anyString()), times(1));
        }
    }
}
