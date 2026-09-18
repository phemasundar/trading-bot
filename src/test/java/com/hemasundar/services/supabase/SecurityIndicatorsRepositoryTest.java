package com.hemasundar.services.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SecurityIndicatorsRepositoryTest {

    @Mock
    private SupabaseClient client;

    @Mock
    private RequestSpecification requestSpec;

    @Mock
    private Response response;

    private SecurityIndicatorsRepository repository;
    private ObjectMapper mapper;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        when(client.getObjectMapper()).thenReturn(mapper);
        when(client.request()).thenReturn(requestSpec);
        when(client.getUrl(anyString())).thenAnswer(invocation -> "https://test.supabase.co" + invocation.getArgument(0));

        repository = new SecurityIndicatorsRepository(client);
    }

    @Test
    public void testSaveSecurityIndicators_Success() throws IOException {
        ScreeningResult result = ScreeningResult.builder()
                .symbol("NVDA")
                .companyName("NVIDIA Corp")
                .currentPrice(128.5)
                .volume(45000000L)
                .rsi(55.0)
                .maValues(Map.of(20, 125.0))
                .emaValues(Map.of(9, 127.0))
                .build();

        when(requestSpec.header(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.body(anyString())).thenReturn(requestSpec);
        when(requestSpec.post(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(201);

        repository.saveSecurityIndicators(List.of(result));

        verify(requestSpec).header("Prefer", "resolution=merge-duplicates");
        verify(requestSpec).post("https://test.supabase.co/rest/v1/latest_security_indicators");
    }

    @Test
    public void testSaveSecurityIndicators_Empty() throws IOException {
        repository.saveSecurityIndicators(Collections.emptyList());
        verify(requestSpec, never()).post(anyString());
    }

    @Test
    public void testGetAllSecurityIndicators_Success() throws IOException {
        String json = "[{\"symbol\":\"NVDA\",\"company_name\":\"NVIDIA Corp\",\"current_price\":128.5,\"volume\":45000000,\"rsi\":55.0,\"ma_values\":{\"20\":125.0},\"ema_values\":{\"9\":127.0}}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(response);
        when(response.asString()).thenReturn(json);

        List<ScreeningResult> list = repository.getAllSecurityIndicators();
        Assert.assertNotNull(list);
        Assert.assertEquals(list.size(), 1);
        Assert.assertEquals(list.get(0).getSymbol(), "NVDA");
        Assert.assertEquals(list.get(0).getCurrentPrice(), 128.5);
    }

    @Test
    public void testGetSecurityIndicatorsForSymbols_Success() throws IOException {
        String json = "[{\"symbol\":\"AAPL\",\"company_name\":\"Apple Inc\",\"current_price\":224.0}]";

        when(requestSpec.get(anyString())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(response);
        when(response.asString()).thenReturn(json);

        Map<String, ScreeningResult> map = repository.getSecurityIndicatorsForSymbols(List.of("AAPL"));
        Assert.assertNotNull(map);
        Assert.assertTrue(map.containsKey("AAPL"));
        Assert.assertEquals(map.get("AAPL").getCurrentPrice(), 224.0);
    }
}
