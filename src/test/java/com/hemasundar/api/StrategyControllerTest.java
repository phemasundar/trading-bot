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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public void testGetStrategies() throws Exception {
        OptionsConfig config = OptionsConfig.builder()
                .strategy(StrategyType.PUT_CREDIT_SPREAD.createStrategy())
                .alias("Put Credit Spread")
                .build();
        when(executionService.getEnabledStrategies()).thenReturn(Collections.singletonList(config));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Put Credit Spread"));
    }

    @Test
    public void testGetLatestResults() throws Exception {
        StrategyResult result = new StrategyResult();
        result.setStrategyName("Strategy1");
        when(executionService.getAllLatestStrategyResults()).thenReturn(Collections.singletonList(result));

        mockMvc.perform(get("/api/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("Strategy1"));
    }

    @Test
    public void testGetConfig() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetSecurities() throws Exception {
        Map<String, List<String>> securities = new HashMap<>();
        securities.put("file1", Arrays.asList("AAPL", "GOOG"));
        when(executionService.loadSecuritiesMaps()).thenReturn(securities);

        mockMvc.perform(get("/api/securities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file1[0]").value("AAPL"));
    }

    @Test
    public void testExecuteStrategy() throws Exception {
        StrategyController.ExecuteRequest request = new StrategyController.ExecuteRequest();
        request.setStrategyIndices(Arrays.asList(0));
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));
    }

    @Test
    public void testExecuteCustomStrategy() throws Exception {
        StrategyController.CustomExecuteRequest request = new StrategyController.CustomExecuteRequest();
        request.setStrategyType("PUT_CREDIT_SPREAD");
        request.setSecurities("AAPL");
        request.setFilter(new HashMap<>());
        when(executionService.isExecutionRunning()).thenReturn(false);

        mockMvc.perform(post("/api/execute/custom")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"));
    }

    @Test
    public void testGetCustomExecutions() throws Exception {
        StrategyResult result = new StrategyResult();
        result.setStrategyName("CustomStrategy");
        when(executionService.getRecentCustomExecutions(anyInt()))
                .thenReturn(Collections.singletonList(result));

        mockMvc.perform(get("/api/results/custom")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyName").value("CustomStrategy"));
    }

    @Test
    public void testGetActiveStatus() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(true);

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    public void testCancelExecution() throws Exception {
        when(executionService.isExecutionRunning()).thenReturn(true);
        mockMvc.perform(post("/api/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
    }
}
