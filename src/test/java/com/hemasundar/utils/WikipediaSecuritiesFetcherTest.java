package com.hemasundar.utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.mockito.MockedStatic;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class WikipediaSecuritiesFetcherTest {

    private WikipediaSecuritiesFetcher fetcher;

    @BeforeMethod
    public void setUp() {
        fetcher = new WikipediaSecuritiesFetcher();
        ReflectionTestUtils.setField(fetcher, "cacheHours", 24);
    }

    @Test
    public void testFetchSPY_Success() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            Connection mockConnection = mock(Connection.class);
            Connection.Response mockResponse = mock(Connection.Response.class);

            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            mockedJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenReturn(mockResponse);

            // Mock HTML table constituents under action=parse response format
            String mockHtml = "{\"parse\":{\"text\":{\"*\":\"<table id=\\\"constituents\\\"><tr><th>Symbol</th><th>Company</th></tr><tr><td>AAPL</td><td>Apple</td></tr><tr><td>MSFT</td><td>Microsoft</td></tr></table>\"}}}";
            when(mockResponse.body()).thenReturn(mockHtml);

            List<String> tickers = fetcher.fetch("SPY");

            assertNotNull(tickers);
            assertEquals(tickers.size(), 2);
            assertTrue(tickers.contains("AAPL"));
            assertTrue(tickers.contains("MSFT"));
        }
    }

    @Test
    public void testFetchQQQ_Success() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            // Mock connection responses for QQQ (first TOC, then components section)
            Connection mockConnection = mock(Connection.class);
            Connection.Response mockResponse = mock(Connection.Response.class);

            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            mockedJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenReturn(mockResponse);

            // TOC Response first, then Section Response
            String mockTocJson = "{\"parse\":{\"toc\":[{\"index\":\"13\",\"anchor\":\"Current_components\",\"linkAnchor\":\"Current_components\"}]}}";
            String mockSectionHtml = "{\"parse\":{\"text\":{\"*\":\"<table id=\\\"constituents\\\"><tr><th>Ticker</th><th>Company</th></tr><tr><td>TSLA</td><td>Tesla</td></tr><tr><td>NVDA</td><td>Nvidia</td></tr></table>\"}}}";

            when(mockResponse.body()).thenReturn(mockTocJson, mockSectionHtml);

            List<String> tickers = fetcher.fetch("QQQ");

            assertNotNull(tickers);
            assertEquals(tickers.size(), 2);
            assertTrue(tickers.contains("TSLA"));
            assertTrue(tickers.contains("NVDA"));
        }
    }

    @Test
    public void testFetch_CacheHit() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            Connection mockConnection = mock(Connection.class);
            Connection.Response mockResponse = mock(Connection.Response.class);

            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            mockedJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenReturn(mockResponse);

            String mockHtml = "{\"parse\":{\"text\":{\"*\":\"<table id=\\\"constituents\\\"><tr><th>Symbol</th></tr><tr><td>AAPL</td></tr></table>\"}}}";
            when(mockResponse.body()).thenReturn(mockHtml);

            // 1st Fetch (Cache Miss)
            List<String> tickers1 = fetcher.fetch("SPY");
            assertEquals(tickers1.size(), 1);

            // 2nd Fetch (Cache Hit - Jsoup should not be called again)
            List<String> tickers2 = fetcher.fetch("SPY");
            assertEquals(tickers2.size(), 1);

            // Jsoup.connect should have been called only once for SPY (as section=0, fallback won't trigger)
            mockedJsoup.verify(() -> Jsoup.connect(anyString()), times(1));
        }
    }

    @Test
    public void testEvictAndEvictAll() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            Connection mockConnection = mock(Connection.class);
            Connection.Response mockResponse = mock(Connection.Response.class);

            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            mockedJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenReturn(mockResponse);

            String mockHtml = "{\"parse\":{\"text\":{\"*\":\"<table id=\\\"constituents\\\"><tr><th>Symbol</th></tr><tr><td>AAPL</td></tr></table>\"}}}";
            when(mockResponse.body()).thenReturn(mockHtml);

            fetcher.fetch("SPY");

            fetcher.evict("SPY");
            fetcher.fetch("SPY");
            // Jsoup.connect called twice now
            mockedJsoup.verify(() -> Jsoup.connect(anyString()), times(2));

            fetcher.evictAll();
            fetcher.fetch("SPY");
            // Jsoup.connect called three times now
            mockedJsoup.verify(() -> Jsoup.connect(anyString()), times(3));
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFetch_UnsupportedKeyword() {
        fetcher.fetch("INVALID");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testFetch_WikipediaError() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            Connection mockConnection = mock(Connection.class);
            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenThrow(new IOException("Timeout"));

            fetcher.fetch("SPY");
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testFetch_NoTableFound() throws Exception {
        try (MockedStatic<Jsoup> mockedJsoup = mockStatic(Jsoup.class)) {
            Connection mockConnection = mock(Connection.class);
            Connection.Response mockResponse = mock(Connection.Response.class);

            mockedJsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConnection);
            mockedJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();
            when(mockConnection.userAgent(anyString())).thenReturn(mockConnection);
            when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
            when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
            when(mockConnection.execute()).thenReturn(mockResponse);

            String mockHtml = "{\"parse\":{\"text\":{\"*\":\"<div>No table here</div>\"}}}";
            when(mockResponse.body()).thenReturn(mockHtml);

            fetcher.fetch("SPY");
        }
    }
}
