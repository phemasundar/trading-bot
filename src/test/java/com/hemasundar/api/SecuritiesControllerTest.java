package com.hemasundar.api;

import com.hemasundar.dto.SecuritiesGroupDto;
import com.hemasundar.services.SupabaseService;
import com.hemasundar.technical.SecuritiesFilterConfig;
import com.hemasundar.technical.TechnicalScreener.ScreeningResult;
import com.hemasundar.utils.SecuritiesResolver;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecuritiesControllerTest {

    @Mock
    private SecuritiesResolver securitiesResolver;

    @Mock
    private SupabaseService supabaseService;

    private MockMvc mockMvc;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        SecuritiesController controller = new SecuritiesController(securitiesResolver, supabaseService);
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

    @Test
    public void testGetSecuritiesGroups_Success() throws Exception {
        SecuritiesGroupDto dto = SecuritiesGroupDto.builder()
                .id("1_portfolio")
                .fileName("1_portfolio.yaml")
                .displayName("Portfolio")
                .symbolCount(2)
                .symbols(List.of("NVDA", "AAPL"))
                .build();
        when(securitiesResolver.loadSecuritiesGroups()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/securities/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1_portfolio"))
                .andExpect(jsonPath("$[0].displayName").value("Portfolio"))
                .andExpect(jsonPath("$[0].symbols[0]").value("NVDA"));
    }

    @Test
    public void testGetSecuritiesFilterConfig_Success() throws Exception {
        SecuritiesFilterConfig config = SecuritiesFilterConfig.builder()
                .filters(SecuritiesFilterConfig.FilterSettings.builder()
                        .rsi(SecuritiesFilterConfig.RsiConfig.builder()
                                .enabled(true)
                                .period(14)
                                .oversold(30.0)
                                .overbought(70.0)
                                .build())
                        .build())
                .build();
        when(securitiesResolver.loadSecuritiesFiltersConfig()).thenReturn(config);

        mockMvc.perform(get("/api/securities/filter-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.rsi.period").value(14));
    }

    @Test
    public void testGetSecuritiesData_Success() throws Exception {
        ScreeningResult res = ScreeningResult.builder()
                .symbol("NVDA")
                .companyName("NVIDIA Corporation")
                .currentPrice(120.5)
                .rsi(28.4)
                .rsiOversold(true)
                .ivPercentile(45.0)
                .ivRank(35.5)
                .build();
        when(supabaseService.getSecurityIndicatorsForSymbols(any())).thenReturn(Map.of("NVDA", res));

        mockMvc.perform(post("/api/securities/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbols\": [\"NVDA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.NVDA.symbol").value("NVDA"))
                .andExpect(jsonPath("$.NVDA.currentPrice").value(120.5))
                .andExpect(jsonPath("$.NVDA.rsiOversold").value(true))
                .andExpect(jsonPath("$.NVDA.ivPercentile").value(45.0))
                .andExpect(jsonPath("$.NVDA.ivRank").value(35.5));
    }

    @Test
    public void testGetSecuritiesData_EmptyRequest() throws Exception {
        mockMvc.perform(post("/api/securities/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}

