package com.hemasundar.api;

import com.hemasundar.utils.SecuritiesResolver;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecuritiesControllerTest {

    @Mock
    private SecuritiesResolver securitiesResolver;

    private MockMvc mockMvc;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        SecuritiesController controller = new SecuritiesController(securitiesResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testGetSecuritiesMaps_Success() throws Exception {
        when(securitiesResolver.loadSecuritiesMaps()).thenReturn(Map.of("portfolio", List.of("AAPL", "MSFT")));

        mockMvc.perform(get("/api/securities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolio[0]").value("AAPL"));
    }

    @Test
    public void testGetSecuritiesMaps_Error() throws Exception {
        when(securitiesResolver.loadSecuritiesMaps()).thenThrow(new IOException("Disk read error"));

        mockMvc.perform(get("/api/securities"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to load securities map: Disk read error"));
    }
}
