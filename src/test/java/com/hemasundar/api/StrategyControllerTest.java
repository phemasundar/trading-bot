package com.hemasundar.api;

import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.api.StrategyController.ExecuteRequest;
import com.hemasundar.api.StrategyController.CustomExecuteRequest;
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
import java.util.Set;
import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class StrategyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StrategyExecutionService executionService;

    @InjectMocks
    private StrategyController strategyController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        strategyController = new StrategyController(executionService);
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
        when(executionService.getEnabledScreeners()).thenReturn(Collections.singletonList(screener));

        mockMvc.perform(get("/api/screeners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("RSI Oversold"))
                .andExpect(jsonPath("$[0].index").value(0));
    }

    @Test
    public void testGetEnabledScreeners_Error() throws Exception {
        when(executionService.getEnabledScreeners()).thenThrow(new IOException("Disk error"));
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
        when(executionService.getLatestScreenerResults()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/results/screeners"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetScreenerResults_Error() throws Exception {
        when(executionService.getLatestScreenerResults()).thenThrow(new RuntimeException("DB error"));
        mockMvc.perform(get("/api/results/screeners"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void testExecuteStrategy_AlreadyRunning() throws Exception {
        StrategyController.ExecuteRequest request = new StrategyController.ExecuteRequest();
        request.setStrategyIndices(Arrays.asList(0));
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(post("/api/execute")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("An execution is already running"));
    }

    @Test
    public void testExecuteStrategy_WithScreeners() throws Exception {
        StrategyController.ExecuteRequest request = new StrategyController.ExecuteRequest();
        request.setStrategyIndices(Arrays.asList(0));
        request.setScreenerIndices(Arrays.asList(1));
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Execution started for 2 items"));
    }

    @Test
    public void testExecuteCustomStrategy_AlreadyRunning() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecuteCustomStrategy_NoSecurities() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provide a securities file, inline tickers, or both"));
    }

    @Test
    public void testExecuteCustomStrategy_InvalidType() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("INVALID_TYPE");
        request.setSecurities("AAPL");
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecuteCustomStrategy_WithSecuritiesFile() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        request.setSecuritiesFile("portfolio");
        when(executionService.isExecutionRunning()).thenReturn(false);
        
        Map<String, List<String>> securities = new HashMap<>();
        securities.put("portfolio", Arrays.asList("AAPL", "MSFT"));
        when(executionService.loadSecuritiesMaps()).thenReturn(securities);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testExecuteCustomStrategy_WithComplexFilter() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        request.setSecurities("AAPL");
        
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("targetDTE", 45);
        filterMap.put("maxLossLimit", 500.0);
        
        Map<String, Object> shortLeg = new HashMap<>();
        shortLeg.put("minDelta", 0.15);
        filterMap.put("shortLeg", shortLeg);
        
        request.setFilter(filterMap);
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
    
    @Test
    public void testExecuteCustomStrategy_LeapFilter() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("LONG_CALL_LEAP");
        request.setSecurities("AAPL");
        
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("sortPriority", "delta,premium");
        request.setFilter(filterMap);
        
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
