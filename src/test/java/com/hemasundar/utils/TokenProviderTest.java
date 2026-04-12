package com.hemasundar.utils;

import com.hemasundar.config.properties.SchwabConfig;
import com.hemasundar.pojos.RefreshToken;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class TokenProviderTest {

    private MockedStatic<RestAssured> mockedRestAssured;
    private SchwabConfig mockConfig;
    private TokenProvider tokenProvider;

    @BeforeMethod
    public void setUp() {
        mockConfig = mock(SchwabConfig.class);
        tokenProvider = new TokenProvider(mockConfig);
        mockedRestAssured = mockStatic(RestAssured.class);
        
        when(mockConfig.getAppKey()).thenReturn("test-app-key");
        when(mockConfig.getAppSecret()).thenReturn("test-secret");
        when(mockConfig.getRefreshToken()).thenReturn("test-refresh-token");
    }

    @AfterMethod
    public void tearDown() {
        mockedRestAssured.close();
    }

    @Test
    public void testGetAccessToken_Refresh_Success() {
        RequestSpecification mockRequest = mock(RequestSpecification.class);
        Response mockResponse = mock(Response.class);
        io.restassured.specification.AuthenticationSpecification mockAuth = mock(io.restassured.specification.AuthenticationSpecification.class);
        io.restassured.specification.PreemptiveAuthSpec mockPreemptive = mock(io.restassured.specification.PreemptiveAuthSpec.class);
        
        when(RestAssured.given()).thenReturn(mockRequest);
        when(mockRequest.auth()).thenReturn(mockAuth);
        when(mockAuth.preemptive()).thenReturn(mockPreemptive);
        when(mockPreemptive.basic(anyString(), anyString())).thenReturn(mockRequest);
        
        when(mockRequest.contentType(anyString())).thenReturn(mockRequest);
        when(mockRequest.formParam(anyString(), (Object) any())).thenReturn(mockRequest);
        when(mockRequest.when()).thenReturn(mockRequest);
        when(mockRequest.post(anyString())).thenReturn(mockResponse);
        
        when(mockResponse.statusCode()).thenReturn(200);
        String jsonResponse = "{\"access_token\": \"new-token\", \"expires_in\": 3600}";
        when(mockResponse.asPrettyString()).thenReturn(jsonResponse);

        String token = tokenProvider.getAccessToken();
        assertEquals(token, "new-token");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetAccessToken_Refresh_Failure() {
        RequestSpecification mockRequest = mock(RequestSpecification.class);
        Response mockResponse = mock(Response.class);
        io.restassured.specification.AuthenticationSpecification mockAuth = mock(io.restassured.specification.AuthenticationSpecification.class);
        io.restassured.specification.PreemptiveAuthSpec mockPreemptive = mock(io.restassured.specification.PreemptiveAuthSpec.class);
        
        when(RestAssured.given()).thenReturn(mockRequest);
        when(mockRequest.auth()).thenReturn(mockAuth);
        when(mockAuth.preemptive()).thenReturn(mockPreemptive);
        when(mockPreemptive.basic(anyString(), anyString())).thenReturn(mockRequest);
        
        when(mockRequest.contentType(anyString())).thenReturn(mockRequest);
        when(mockRequest.formParam(anyString(), (Object) any())).thenReturn(mockRequest);
        when(mockRequest.when()).thenReturn(mockRequest);
        when(mockRequest.post(anyString())).thenReturn(mockResponse);
        
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.statusLine()).thenReturn("Unauthorized");
        when(mockResponse.asPrettyString()).thenReturn("Invalid credentials");

        tokenProvider.getAccessToken();
    }
}
