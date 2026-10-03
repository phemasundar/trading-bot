package com.hemasundar.config;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class RolesConfigLoaderTest {

    @Test
    public void testLoadRolesConfig() {
        RolesConfigLoader loader = new RolesConfigLoader();
        loader.load();

        List<String> pages = loader.getReadonlyAllowedPages();
        assertNotNull(pages);
        assertFalse(pages.isEmpty(), "Pages should not be empty!");
        assertTrue(pages.contains("/"), "Should contain '/'");
        assertTrue(pages.contains("/index.html"), "Should contain '/index.html'");
        assertTrue(pages.contains("/screeners.html"), "Should contain '/screeners.html'");
    }
}
