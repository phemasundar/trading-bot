package com.hemasundar.api;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeMethod
    public void setup() {
        ConfigController configController = new ConfigController();
        mockMvc = MockMvcBuilders.standaloneSetup(configController).build();
    }

    @Test
    public void testGetConfig_Success() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk());
    }
}
