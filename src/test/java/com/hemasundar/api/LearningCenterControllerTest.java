package com.hemasundar.api;

import com.hemasundar.dto.StrategyDescriptionDto;
import com.hemasundar.services.LearningCenterService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LearningCenterControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LearningCenterService learningCenterService;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        LearningCenterController controller = new LearningCenterController(learningCenterService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testGetOptionStrategies_ReturnsList() throws Exception {
        StrategyDescriptionDto dto = StrategyDescriptionDto.builder()
                .id("iron_condor")
                .name("Iron Condor")
                .filename("iron_condor.md")
                .category("Options Strategy")
                .bias("Neutral")
                .summary("A neutral non-directional options strategy")
                .greeks(Map.of("Delta", "Neutral", "Theta", "Positive"))
                .build();

        when(learningCenterService.getOptionStrategies()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/learning/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("iron_condor"))
                .andExpect(jsonPath("$[0].name").value("Iron Condor"))
                .andExpect(jsonPath("$[0].bias").value("Neutral"))
                .andExpect(jsonPath("$[0].greeks.Delta").value("Neutral"));
    }

    @Test
    public void testGetStrategy_Found() throws Exception {
        StrategyDescriptionDto dto = StrategyDescriptionDto.builder()
                .id("short_put")
                .name("Cash-Secured Put (Short Put / CSP)")
                .filename("short_put.md")
                .bias("Bullish")
                .build();

        when(learningCenterService.getStrategyById("short_put")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/learning/strategies/short_put"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("short_put"))
                .andExpect(jsonPath("$.name").value("Cash-Secured Put (Short Put / CSP)"));
    }

    @Test
    public void testGetStrategy_NotFound() throws Exception {
        when(learningCenterService.getStrategyById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/learning/strategies/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetOptionGreeks_ReturnsList() throws Exception {
        com.hemasundar.dto.OptionGreekDto dto = com.hemasundar.dto.OptionGreekDto.builder()
                .id("delta")
                .name("Delta")
                .symbol("Δ")
                .order("First-Order")
                .derivative("∂V / ∂S")
                .filename("greeks/delta.md")
                .summary("Measures directional risk")
                .exposure("Long Calls: +Δ | Long Puts: -Δ")
                .impact("Directional sensitivity")
                .build();

        when(learningCenterService.getOptionGreeks()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/learning/greeks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("delta"))
                .andExpect(jsonPath("$[0].name").value("Delta"))
                .andExpect(jsonPath("$[0].symbol").value("Δ"))
                .andExpect(jsonPath("$[0].order").value("First-Order"))
                .andExpect(jsonPath("$[0].derivative").value("∂V / ∂S"));
    }

    @Test
    public void testGetGreek_Found() throws Exception {
        com.hemasundar.dto.OptionGreekDto dto = com.hemasundar.dto.OptionGreekDto.builder()
                .id("gamma")
                .name("Gamma")
                .symbol("Γ")
                .order("Second-Order")
                .build();

        when(learningCenterService.getGreekById("gamma")).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/learning/greeks/gamma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("gamma"))
                .andExpect(jsonPath("$.name").value("Gamma"))
                .andExpect(jsonPath("$.symbol").value("Γ"));
    }

    @Test
    public void testGetGreek_NotFound() throws Exception {
        when(learningCenterService.getGreekById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/learning/greeks/unknown"))
                .andExpect(status().isNotFound());
    }
}
