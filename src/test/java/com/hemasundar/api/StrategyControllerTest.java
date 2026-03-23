package com.hemasundar.api;

import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.dto.ExecuteRequest;
import com.hemasundar.dto.CustomExecuteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class StrategyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StrategyExecutionService executionService;

    @Mock
    private com.hemasundar.services.ScreenerExecutionService screenerExecutionService;

    @Mock
    private com.hemasundar.utils.SecuritiesResolver securitiesResolver;

    @InjectMocks
    private StrategyController strategyController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        strategyController = new StrategyController(executionService, screenerExecutionService, securitiesResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(strategyController).build();
    }

    @Test
    public void testGetStrategies_Error() throws Exception {
        when(executionService.getEnabledStrategies()).thenThrow(new IOException("Disk error"));
        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to load strategies: Disk error"));
    }

    @Test
    public void testGetEnabledScreeners() throws Exception {
        com.hemasundar.technical.ScreenerConfig screener = com.hemasundar.technical.ScreenerConfig.builder()
                .alias("RSI Oversold")
                .screenerType(com.hemasundar.technical.ScreenerType.RSI_OVERSOLD)
                .build();
        when(screenerExecutionService.getEnabledScreeners()).thenReturn(Collections.singletonList(screener));

        mockMvc.perform(get("/api/screeners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("RSI Oversold"))
                .andExpect(jsonPath("$[0].index").value(0));
    }

    @Test
    public void testGetEnabledScreeners_Error() throws Exception {
        when(screenerExecutionService.getEnabledScreeners()).thenThrow(new IOException("Disk error"));
        mockMvc.perform(get("/api/screeners"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void testGetLatestResults_Error() throws Exception {
        when(executionService.getAllLatestStrategyResults()).thenThrow(new RuntimeException("DB error"));
        mockMvc.perform(get("/api/results"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void testGetScreenerResults() throws Exception {
        when(screenerExecutionService.getLatestScreenerResults()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/results/screeners"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetScreenerResults_Error() throws Exception {
        when(screenerExecutionService.getLatestScreenerResults()).thenThrow(new RuntimeException("DB error"));
        mockMvc.perform(get("/api/results/screeners"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void testExecuteStrategy_AlreadyRunning() throws Exception {
        ExecuteRequest request = ExecuteRequest.builder()
                .strategyIndices(Arrays.asList(0))
                .build();
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(post("/api/execute")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("An execution is already running"));
    }

    @Test
    public void testExecuteStrategies() throws Exception {
        ExecuteRequest request = ExecuteRequest.builder()
                .strategyIndices(Arrays.asList(0))
                .screenerIndices(Arrays.asList(1))
                .build();
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Execution started for 2 items"));
    }

    @Test
    public void testExecuteCustomStrategy_AlreadyRunning() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("PUT_CREDIT_SPREAD")
                .build();
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecuteCustomStrategy_WithoutAlias() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("PUT_CREDIT_SPREAD")
                .build();
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provide a securities file, inline tickers, or both"));
    }

    @Test
    public void testExecuteCustomStrategy_InvalidType() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("INVALID_TYPE")
                .securities("AAPL")
                .build();
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecuteCustomStrategy_WithSecuritiesFile() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("PUT_CREDIT_SPREAD")
                .securitiesFile("portfolio")
                .build();
        when(executionService.isExecutionRunning()).thenReturn(false);
        
        Map<String, List<String>> securities = new HashMap<>();
        securities.put("portfolio", Arrays.asList("AAPL", "MSFT"));
        when(securitiesResolver.loadSecuritiesMaps()).thenReturn(securities);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testExecuteCustomStrategy_WithComplexFilter() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("PUT_CREDIT_SPREAD")
                .securities("AAPL")
                .filter(Map.of(
                        "targetDTE", 45,
                        "maxLossLimit", 500.0,
                        "shortLeg", Map.of("minDelta", 0.15)))
                .build();
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
    
    @Test
    public void testExecuteCustomStrategy_WithAlias() throws Exception {
        CustomExecuteRequest request = CustomExecuteRequest.builder()
                .strategyType("LONG_CALL_LEAP")
                .securities("AAPL")
                .filter(Map.of("sortPriority", "delta,premium"))
                .build();
        
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testCancelExecution_NotRunning() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(false);
        mockMvc.perform(post("/api/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No execution is currently running"));
    }
}
