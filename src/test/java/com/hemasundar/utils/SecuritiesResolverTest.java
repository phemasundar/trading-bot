package com.hemasundar.utils;

import com.hemasundar.pojos.Securities;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SecuritiesResolverTest {

    private SecuritiesResolver resolver;
    private MockedStatic<FilePaths> mockedFilePaths;
    private MockedStatic<JavaUtils> mockedJavaUtils;

    @BeforeMethod
    public void setUp() {
        resolver = new SecuritiesResolver();
        mockedFilePaths = mockStatic(FilePaths.class);
        mockedJavaUtils = mockStatic(JavaUtils.class);
    }

    @AfterMethod
    public void tearDown() {
        mockedFilePaths.close();
        mockedJavaUtils.close();
    }

    @Test
    public void testLoadSecuritiesMaps() throws IOException {
        String mockYaml = "securities:\n  - AAPL\n  - TSLA";
        Securities mockSecurities = new Securities(List.of("AAPL", "TSLA"));
        
        when(FilePaths.readResource(anyString())).thenReturn(mockYaml);
        when(JavaUtils.convertYamlToPojo(eq(mockYaml), eq(Securities.class))).thenReturn(mockSecurities);

        Map<String, List<String>> result = resolver.loadSecuritiesMaps();

        assertNotNull(result);
        assertTrue(result.containsKey("portfolio"));
        assertEquals(result.get("portfolio").size(), 2);
        assertTrue(result.get("portfolio").contains("AAPL"));
    }

    @Test(expectedExceptions = IOException.class)
    public void testLoadSecuritiesMaps_Error() throws IOException {
        when(FilePaths.readResource(anyString())).thenThrow(new IOException("Disk error"));
        resolver.loadSecuritiesMaps();
    }
}
