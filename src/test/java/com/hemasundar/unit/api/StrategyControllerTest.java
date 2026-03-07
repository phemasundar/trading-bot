package com.hemasundar.unit.api;

import com.hemasundar.api.StrategyController;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.services.StrategyExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class StrategyControllerTest {

    private MockMvc mockMvc;
    private StrategyExecutionService executionService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeMethod
    public void setUp() {
        executionService = mock(StrategyExecutionService.class);
        StrategyController controller = new StrategyController(executionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testGetEnabledStrategies() throws Exception {
        OptionsConfig config = OptionsConfig.builder()
                .alias("Test")
                .strategy(StrategyType.PUT_CREDIT_SPREAD.createStrategy())
                .build();

        when(executionService.getEnabledStrategies()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test"))
                .andExpect(jsonPath("$[0].type").value("PUT_CREDIT_SPREAD"));
    }

    @Test
    public void testGetEnabledStrategiesFailure() throws Exception {
        when(executionService.getEnabledStrategies()).thenThrow(new IOException("Disk error"));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    public void testGetLatestResults() throws Exception {
        StrategyResult result = StrategyResult.builder().strategyName("Test").build();
        when(executionService.getAllLatestStrategyResults()).thenReturn(List.of(result));

        mockMvc.perform(get("/api/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("Test"));
    }

    @Test
    public void testGetCustomResults() throws Exception {
        StrategyResult result = StrategyResult.builder().strategyName("Custom").build();
        when(executionService.getRecentCustomExecutions(anyInt())).thenReturn(List.of(result));

        mockMvc.perform(get("/api/results/custom?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("Custom"));

        verify(executionService).getRecentCustomExecutions(5);
    }

    @Test
    public void testGetSecuritiesMaps() throws Exception {
        Map<String, List<String>> maps = Map.of("portfolio", List.of("AAPL"));
        when(executionService.loadSecuritiesMaps()).thenReturn(maps);

        mockMvc.perform(get("/api/securities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolio[0]").value("AAPL"));
    }

    @Test
    public void testExecuteStrategiesSuccess() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(false);

        StrategyController.ExecuteRequest request = new StrategyController.ExecuteRequest();
        request.setStrategyIndices(List.of(0, 1));

        mockMvc.perform(post("/api/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));

        verify(executionService, timeout(1000)).executeStrategies(anySet());
    }

    @Test
    public void testExecuteStrategiesAlreadyRunning() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(true);

        StrategyController.ExecuteRequest request = new StrategyController.ExecuteRequest();
        request.setStrategyIndices(List.of(0));

        mockMvc.perform(post("/api/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testExecuteCustomStrategySuccess() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(false);

        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        request.setSecurities("AAPL, MSFT");
        request.setAlias("My Custom");
        request.setFilter(Map.of("targetDTE", 45));

        mockMvc.perform(post("/api/execute/custom")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));

        verify(executionService, timeout(1000)).executeCustomStrategy(any());
    }

    @Test
    public void testExecuteCustomStrategyInvalidType() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("INVALID_TYPE");
        request.setSecurities("AAPL");

        mockMvc.perform(post("/api/execute/custom")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetStatus() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(true);
        when(executionService.getExecutionStartTimeMs()).thenReturn(1000L);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.startTimeMs").value(1000));
    }

    @Test
    public void testCancelExecution() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(post("/api/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));

        verify(executionService).cancelExecution();
    }

    @Test
    public void testCancelExecutionNotRunning() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/cancel"))
                .andExpect(status().isBadRequest());
    }
}
