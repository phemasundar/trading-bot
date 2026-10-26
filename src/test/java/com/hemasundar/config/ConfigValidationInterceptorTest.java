package com.hemasundar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.dto.AlertMessages;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ConfigValidationInterceptorTest {

    @Mock
    private ApplicationConfigValidator configValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigValidationInterceptor interceptor;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        interceptor = new ConfigValidationInterceptor(configValidator, objectMapper);
    }

    @Test
    public void testPreHandle_Execute_Valid() throws Exception {
        when(configValidator.validateConfig()).thenReturn(Collections.emptyList());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/execute");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals(response.getStatus(), 200);
    }

    @Test
    public void testPreHandle_Execute_Invalid() throws Exception {
        when(configValidator.validateConfig()).thenReturn(List.of(AlertMessages.CONFIG_MISSING_IV_RECORDS));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/execute/custom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(response.getStatus(), 400);
        assertTrue(response.getContentAsString().contains(AlertMessages.CONFIG_MISSING_IV_RECORDS));
    }

    @Test
    public void testPreHandle_EarningsCalendar_Valid() throws Exception {
        when(configValidator.validateEarningsCache()).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/earnings-calendar");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals(response.getStatus(), 200);
    }

    @Test
    public void testPreHandle_EarningsCalendar_Invalid() throws Exception {
        when(configValidator.validateEarningsCache()).thenReturn(Optional.of(AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/earnings-calendar");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(response.getStatus(), 400);
        assertTrue(response.getContentAsString().contains(AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY));
    }

    @Test
    public void testPreHandle_IVRank_Valid() throws Exception {
        when(configValidator.validateIVData()).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/iv-rank");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals(response.getStatus(), 200);
    }

    @Test
    public void testPreHandle_IVRank_Invalid() throws Exception {
        when(configValidator.validateIVData()).thenReturn(Optional.of(AlertMessages.CONFIG_MISSING_IV_RECORDS));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/iv-rank");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(response.getStatus(), 400);
        assertTrue(response.getContentAsString().contains(AlertMessages.CONFIG_MISSING_IV_RECORDS));
    }

    @Test
    public void testPreHandle_OtherPath_SkipsValidation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(configValidator, never()).validateConfig();
        verify(configValidator, never()).validateEarningsCache();
        verify(configValidator, never()).validateIVData();
    }
}
