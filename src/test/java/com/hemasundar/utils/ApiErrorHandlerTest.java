package com.hemasundar.utils;

import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ApiErrorHandlerTest {

    @Mock
    private TelegramUtils telegramUtils;

    private ApiErrorHandler apiErrorHandler;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        apiErrorHandler = new ApiErrorHandler(telegramUtils);
    }

    @Test
    public void testHandle400Error() {
        apiErrorHandler.handle400Error("Test API", "AAPL", "Bad Request Body");
        
        verify(telegramUtils, times(1)).sendMessage(ArgumentMatchers.anyString());
    }
}
