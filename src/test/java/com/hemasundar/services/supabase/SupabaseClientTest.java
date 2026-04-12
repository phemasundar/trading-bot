package com.hemasundar.services.supabase;

import com.hemasundar.config.properties.SupabaseConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class SupabaseClientTest {

    @Mock
    private SupabaseConfig supabaseConfig;

    private SupabaseClient supabaseClient;
    private MockedStatic<RestAssured> restAssuredMockedStatic;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(supabaseConfig.getUrl()).thenReturn("https://test.supabase.co/");
        when(supabaseConfig.getServiceRoleKey()).thenReturn("test-key");
        
        supabaseClient = new SupabaseClient(supabaseConfig);
        restAssuredMockedStatic = mockStatic(RestAssured.class);
    }

    @AfterMethod
    public void tearDown() {
        restAssuredMockedStatic.close();
    }

    @Test
    public void testConstructor_UrlSanitization() {
        // Test trailing slash removal
        Assert.assertEquals(supabaseClient.getUrl("/abc"), "https://test.supabase.co/abc");
        
        // Test without trailing slash
        when(supabaseConfig.getUrl()).thenReturn("https://test.supabase.co");
        SupabaseClient client2 = new SupabaseClient(supabaseConfig);
        Assert.assertEquals(client2.getUrl("/abc"), "https://test.supabase.co/abc");
    }

    @Test
    public void testRequest() {
        RequestSpecification requestSpec = mock(RequestSpecification.class);
        restAssuredMockedStatic.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);

        supabaseClient.request();

        verify(requestSpec).header("apikey", "test-key");
        verify(requestSpec).header("Authorization", "Bearer test-key");
        verify(requestSpec).header("Content-Type", "application/json");
    }

    @Test
    public void testTestConnection_Success() throws IOException {
        RequestSpecification requestSpec = mock(RequestSpecification.class);
        Response response = mock(Response.class);
        
        restAssuredMockedStatic.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);

        Assert.assertTrue(supabaseClient.testConnection());
    }

    @Test
    public void testTestConnection_Failure() throws IOException {
        RequestSpecification requestSpec = mock(RequestSpecification.class);
        Response response = mock(Response.class);
        
        restAssuredMockedStatic.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(404);

        Assert.assertFalse(supabaseClient.testConnection());
    }

    @Test(expectedExceptions = IOException.class)
    public void testTestConnection_Exception() throws IOException {
        RequestSpecification requestSpec = mock(RequestSpecification.class);
        
        restAssuredMockedStatic.when(RestAssured::given).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any())).thenReturn(requestSpec);
        when(requestSpec.get(anyString())).thenThrow(new RuntimeException("Network error"));

        supabaseClient.testConnection();
    }

    @Test
    public void testGetObjectMapper() {
        Assert.assertNotNull(supabaseClient.getObjectMapper());
    }
}
