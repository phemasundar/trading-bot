package com.hemasundar.utils;

import com.hemasundar.pojos.Securities;
import org.mockito.MockedStatic;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link SecuritiesResolver}.
 *
 * <p>Since the refactoring (lazy Wikipedia loading), SecuritiesResolver is now
 * responsible <em>only</em> for static YAML files. Dynamic keywords (SPY, QQQ)
 * are resolved lazily by {@link com.hemasundar.config.StrategiesConfigLoader}
 * via {@link WikipediaSecuritiesFetcher} at execution time.
 */
@Listeners(MockitoTestNGListener.class)
public class SecuritiesResolverTest {

    private SecuritiesResolver resolver;
    private MockedStatic<FilePaths> mockedFilePaths;
    private MockedStatic<JavaUtils> mockedJavaUtils;

    @BeforeMethod
    public void setUp() {
        resolver = new SecuritiesResolver();          // no Wikipedia dependency
        mockedFilePaths = mockStatic(FilePaths.class);
        mockedJavaUtils = mockStatic(JavaUtils.class);
    }

    @AfterMethod
    public void tearDown() {
        mockedFilePaths.close();
        mockedJavaUtils.close();
    }

    // ── Static YAML loading ──────────────────────────────────────────────────────

    @Test
    public void testLoadSecuritiesMaps_allStaticKeysPresent() throws IOException {
        String mockYaml = "securities:\n  - AAPL\n  - TSLA";
        Securities mockSecurities = new Securities(List.of("AAPL", "TSLA"));

        when(FilePaths.readResource(anyString())).thenReturn(mockYaml);
        when(JavaUtils.convertYamlToPojo(eq(mockYaml), eq(Securities.class))).thenReturn(mockSecurities);

        Map<String, List<String>> result = resolver.loadSecuritiesMaps();

        assertNotNull(result);
        assertTrue(result.containsKey("portfolio"), "Should contain 'portfolio'");
        assertTrue(result.containsKey("top100"),    "Should contain 'top100'");
        assertTrue(result.containsKey("bullish"),   "Should contain 'bullish'");
        assertTrue(result.containsKey("2026"),      "Should contain '2026'");
        assertTrue(result.containsKey("tracking"),  "Should contain 'tracking'");
        assertEquals(result.get("portfolio").size(), 2);
        assertTrue(result.get("portfolio").contains("AAPL"));
    }

    @Test
    public void testLoadSecuritiesMaps_dynamicKeysShouldNotBePresentEagerly() throws IOException {
        // SPY and QQQ must NOT be populated by SecuritiesResolver — they are loaded
        // lazily by StrategiesConfigLoader only when a strategy that uses them is executed.
        String mockYaml = "securities:\n  - AAPL";
        Securities mockSecurities = new Securities(List.of("AAPL"));
        when(FilePaths.readResource(anyString())).thenReturn(mockYaml);
        when(JavaUtils.convertYamlToPojo(eq(mockYaml), eq(Securities.class))).thenReturn(mockSecurities);

        Map<String, List<String>> result = resolver.loadSecuritiesMaps();

        assertFalse(result.containsKey("SPY"),
                "SPY should NOT be eagerly loaded by SecuritiesResolver (lazy via StrategiesConfigLoader)");
        assertFalse(result.containsKey("QQQ"),
                "QQQ should NOT be eagerly loaded by SecuritiesResolver (lazy via StrategiesConfigLoader)");
    }

    @Test(expectedExceptions = IOException.class)
    public void testLoadSecuritiesMaps_staticFileReadError() throws IOException {
        when(FilePaths.readResource(anyString())).thenThrow(new IOException("Disk error"));
        resolver.loadSecuritiesMaps(); // should propagate the IOException
    }
}
