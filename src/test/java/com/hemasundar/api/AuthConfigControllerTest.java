package com.hemasundar.api;

import com.hemasundar.config.RolesConfigLoader;
import com.hemasundar.config.properties.SupabaseConfig;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthConfigControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SupabaseConfig supabaseConfig;

    @Mock
    private RolesConfigLoader rolesConfigLoader;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        AuthConfigController controller = new AuthConfigController(supabaseConfig, rolesConfigLoader);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testGetAuthConfig_Success() throws Exception {
        when(supabaseConfig.getUrl()).thenReturn("https://example.supabase.co");
        when(supabaseConfig.getAnonKey()).thenReturn("anon-key-123");

        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.supabaseUrl").value("https://example.supabase.co"))
                .andExpect(jsonPath("$.supabaseAnonKey").value("anon-key-123"));
    }

    @Test
    public void testGetUserRole_WithReadonlyRole() throws Exception {
        when(rolesConfigLoader.getReadonlyAllowedPages()).thenReturn(
                List.of("/", "/index.html", "/screeners.html", "/earnings-calendar.html", "/config.html"));

        mockMvc.perform(get("/api/auth/role").requestAttr("userRole", "READONLY"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.role").value("READONLY"))
                .andExpect(jsonPath("$.allowedPages[0]").value("/"))
                .andExpect(jsonPath("$.allowedPages[3]").value("/earnings-calendar.html"))
                .andExpect(jsonPath("$.allowedPages[4]").value("/config.html"));
    }

    @Test
    public void testGetUserRole_DefaultsToAdminWhenRoleNotSet() throws Exception {
        when(rolesConfigLoader.getReadonlyAllowedPages()).thenReturn(List.of("/"));

        mockMvc.perform(get("/api/auth/role"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
