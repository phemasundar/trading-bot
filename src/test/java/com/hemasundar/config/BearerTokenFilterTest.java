package com.hemasundar.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class BearerTokenFilterTest {

    private BearerTokenFilter filter;
    private Environment mockEnv;
    private static final String AUTH_TOKEN = "test-token";

    @BeforeMethod
    public void setUp() throws Exception {
        mockEnv = mock(Environment.class);
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"dev"});
        
        filter = new BearerTokenFilter(mockEnv);
        
        // Use reflection to set the private expectedToken from @Value
        Field tokenField = BearerTokenFilter.class.getDeclaredField("expectedToken");
        tokenField.setAccessible(true);
        tokenField.set(filter, AUTH_TOKEN);
    }

    @Test
    public void testDoFilter_ValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        request.setRequestURI("/api/strategies");
        request.addHeader("Authorization", "Bearer " + AUTH_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }

    @Test
    public void testDoFilter_InvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        request.setRequestURI("/api/strategies");
        request.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoFilter_MissingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        request.setRequestURI("/api/strategies");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoFilter_NonApiRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        request.setRequestURI("/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNotEquals(response.getStatus(), HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoFilter_Production_NoToken() throws Exception {
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"production"});
        BearerTokenFilter prodFilter = new BearerTokenFilter(mockEnv);
        // expectedToken is null by default
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        request.setRequestURI("/api/strategies");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        prodFilter.doFilter(request, response, filterChain);

        assertEquals(response.getStatus(), HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testDoFilter_Dev_NoToken() throws Exception {
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"dev"});
        BearerTokenFilter devFilter = new BearerTokenFilter(mockEnv);
        // expectedToken is null by default
        
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/strategies");
        request.setRequestURI("/api/strategies");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        devFilter.doFilter(request, response, filterChain);

        assertEquals(response.getStatus(), HttpServletResponse.SC_OK);
    }
}
