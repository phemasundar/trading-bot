package com.hemasundar.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches index constituent ticker lists dynamically from Wikipedia.
 *
 * <p>Supported keywords:
 * <ul>
 *   <li>{@code SPY} → S&amp;P 500 companies
 *       (<a href="https://en.wikipedia.org/wiki/List_of_S%26P_500_companies">Wikipedia</a>)</li>
 *   <li>{@code QQQ} → Nasdaq-100 companies
 *       (<a href="https://en.wikipedia.org/wiki/Nasdaq-100">Wikipedia</a>)</li>
 * </ul>
 *
 * <p>Results are cached in-memory for {@code securities.wiki.cache-hours} hours (default 24)
 * so repeated strategy executions within a day do not re-fetch Wikipedia.
 *
 * <p>Throws {@link IllegalStateException} if Wikipedia is unreachable or the page structure
 * has changed in an incompatible way (hard-failure, per design decision).
 *
 * <p><b>No hardcoded column indices.</b> The ticker column is always resolved at runtime
 * by scanning the table's header row for a cell whose text matches a known ticker-header name
 * (e.g., "Ticker", "Symbol", "Stock Symbol"). This makes the parser resilient to column
 * reordering between Wikipedia edits.
 */
@Component
@Log4j2
public class WikipediaSecuritiesFetcher {

    // ── Wikipedia page identifiers (Wikipedia REST API page parameter) ──────────

    private static final String SP500_PAGE    = "List_of_S%26P_500_companies";
    private static final String NASDAQ100_PAGE = "List_of_NASDAQ-100_companies";

    /**
     * Wikipedia REST API base URL.
     * We use {@code action=parse&prop=text&section=N} which returns only the
     * requested section as rendered HTML — much more reliable than scraping the
     * full page (avoids JS-rendered content and navigation noise).
     */
    private static final String WIKI_API_URL = "https://en.wikipedia.org/w/api.php";

    /**
     * Section anchor used to locate the "Current components" section of the
     * Nasdaq-100 article. The Wikipedia REST API can retrieve a section by its
     * anchor ID, which is far more stable than a section number (section numbers
     * shift whenever new sections are added/removed).
     */
    private static final String NASDAQ100_COMPONENTS_ANCHOR = "Nasdaq-100_component_stocks";

    /**
     * Column header names (case-insensitive) that are recognised as the ticker column.
     * Wikipedia editors sometimes rename "Ticker" to "Symbol", "Stock symbol", etc.
     */
    private static final Set<String> TICKER_HEADER_NAMES = Set.of(
            "ticker", "symbol", "stock symbol", "ticker symbol", "exchange:ticker"
    );

    // ── HTTP timeout ────────────────────────────────────────────────────────────

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    // ── In-memory TTL cache ─────────────────────────────────────────────────────

    private record CachedEntry(List<String> tickers, Instant fetchedAt) {}

    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    @Value("${securities.wiki.cache-hours:24}")
    private int cacheHours;

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Returns the constituent tickers for the given index keyword.
     *
     * @param keyword one of {@code "SPY"} or {@code "QQQ"} (case-sensitive)
     * @return non-null, non-empty list of ticker symbols
     * @throws IllegalArgumentException if the keyword is not supported
     * @throws IllegalStateException    if Wikipedia is unreachable or the page cannot be parsed
     */
    public List<String> fetch(String keyword) {
        CachedEntry cached = cache.get(keyword);
        if (cached != null && !isExpired(cached)) {
            log.debug("Cache hit for '{}': {} tickers (age: {}h)", keyword,
                    cached.tickers().size(),
                    java.time.Duration.between(cached.fetchedAt(), Instant.now()).toHours());
            return cached.tickers();
        }

        log.info("Fetching constituent list for '{}' from Wikipedia…", keyword);
        List<String> tickers = switch (keyword) {
            case "SPY" -> fetchSP500();
            case "QQQ" -> fetchNasdaq100();
            default    -> throw new IllegalArgumentException(
                    "Unsupported dynamic securities keyword: '" + keyword + "'. Supported: SPY, QQQ");
        };

        cache.put(keyword, new CachedEntry(tickers, Instant.now()));
        log.info("Fetched and cached {} tickers for '{}'", tickers.size(), keyword);
        return tickers;
    }

    /** Evicts the cached entry for the given keyword, forcing a re-fetch on next call. */
    public void evict(String keyword) {
        cache.remove(keyword);
        log.info("Evicted Wikipedia cache for '{}'", keyword);
    }

    /** Evicts all cached entries. */
    public void evictAll() {
        cache.clear();
        log.info("Evicted all Wikipedia securities cache entries");
    }

    // ── Private fetch methods ───────────────────────────────────────────────────

    /**
     * Fetches S&amp;P 500 constituents.
     *
     * <p>The Wikipedia article has a {@code <table id="constituents">} containing the
     * full list. The ticker column is resolved dynamically from the header row.
     */
    private List<String> fetchSP500() {
        // The S&P 500 article's constituents table lives in the first section (section=0).
        Document doc = fetchWikiApiSection(SP500_PAGE, "0", "SPY (S&P 500)");

        Element table = doc.selectFirst("table#constituents");
        if (table == null) {
            // Fallback: the section parameter occasionally excludes the table; try full page
            log.warn("SPY: table#constituents not found in section 0; trying full page");
            doc   = fetchWikiApiSection(SP500_PAGE, null, "SPY (S&P 500) full");
            table = doc.selectFirst("table#constituents");
        }
        if (table == null) {
            // Last resort: find any wikitable with a recognised ticker header
            table = findTableWithTickerHeader(doc, "SPY");
        }
        if (table == null) {
            throw new IllegalStateException(
                    "Could not locate the S&P 500 constituents table on Wikipedia. " +
                    "Expected a <table id=\"constituents\"> or a wikitable with a 'Ticker' column header.");
        }
        return extractTickers(table, "SPY");
    }

    /**
     * Fetches Nasdaq-100 constituents.
     *
     * <p>The Wikipedia article has a "Current components" section containing a
     * {@code <table id="constituents">}. We locate the section by its stable anchor
     * ID rather than a fragile section number. The ticker column is resolved
     * dynamically from the header row.
     */
    private List<String> fetchNasdaq100() {
        // Resolve the section number for "Current_components" via the TOC API, then fetch it.
        int sectionNumber = resolveSectionNumber(NASDAQ100_PAGE, NASDAQ100_COMPONENTS_ANCHOR);
        Document doc = fetchWikiApiSection(NASDAQ100_PAGE, String.valueOf(sectionNumber),
                "QQQ (Nasdaq-100) §" + sectionNumber);

        Element table = doc.selectFirst("table#constituents");
        if (table == null) {
            table = findTableWithTickerHeader(doc, "QQQ");
        }
        if (table == null) {
            log.warn("QQQ: components table not found in section {}; trying full page", sectionNumber);
            doc   = fetchWikiApiSection(NASDAQ100_PAGE, null, "QQQ (Nasdaq-100) full");
            table = doc.selectFirst("table#constituents");
        }
        if (table == null) {
            table = findTableWithTickerHeader(doc, "QQQ");
        }
        if (table == null) {
            throw new IllegalStateException(
                    "Could not locate the Nasdaq-100 components table on Wikipedia. " +
                    "Expected a <table id=\"constituents\"> or a wikitable with a 'Ticker' column header.");
        }
        return extractTickers(table, "QQQ");
    }

    // ── Core parsing: dynamic header detection ──────────────────────────────────

    /**
     * Extracts ticker symbols from a Wikipedia wikitable by dynamically locating
     * the column whose header matches a known ticker-header name.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Read the first {@code <tr>} that contains {@code <th>} cells as the header row.</li>
     *   <li>Identify which cell index has a header text in {@link #TICKER_HEADER_NAMES}.</li>
     *   <li>For every subsequent data row ({@code <tr>} with {@code <td>} cells), read that column.</li>
     *   <li>Validate: non-blank, ≤ 10 chars, no spaces (typical exchange ticker constraints).</li>
     * </ol>
     *
     * @param table   the Jsoup table element to parse
     * @param keyword index keyword used in log/error messages
     * @return non-empty list of ticker symbols
     * @throws IllegalStateException if no ticker column is found or no tickers are parsed
     */
    private List<String> extractTickers(Element table, String keyword) {
        // Step 1: locate the header row and find the ticker column index dynamically
        int tickerColIndex = findTickerColumnIndex(table, keyword);

        // Step 2: extract tickers from all data rows using the discovered column index
        List<String> tickers = new ArrayList<>();
        for (Element row : table.select("tr")) {
            // Skip header rows (any row that has <th> cells)
            if (!row.select("th").isEmpty()) {
                continue;
            }

            Elements cells = row.select("td");
            if (cells.size() <= tickerColIndex) {
                continue; // row doesn't have enough columns (e.g., colspan rows)
            }

            String ticker = cells.get(tickerColIndex).text().trim();

            // Normalize: Wikipedia uses dots (e.g. BRK.B), Broker APIs often expect slashes (BRK/B)
            ticker = ticker.replace('.', '/');

            // Basic validation: a ticker is short, no spaces, not empty
            if (!ticker.isEmpty() && ticker.length() <= 10 && !ticker.contains(" ")) {
                tickers.add(ticker);
            }
        }

        Validate.validState(!tickers.isEmpty(),
                "Parsed 0 tickers from %s Wikipedia table (ticker column header '%s' found at index %d but no valid tickers extracted).",
                keyword, TICKER_HEADER_NAMES, tickerColIndex);

        log.debug("Extracted {} tickers from {} Wikipedia table (header-resolved column: {})",
                tickers.size(), keyword, tickerColIndex);
        return tickers;
    }

    /**
     * Scans the header row of a table to find the index of the column whose
     * header text matches one of the known {@link #TICKER_HEADER_NAMES}.
     *
     * @param table   Jsoup table element
     * @param keyword for error messages
     * @return 0-based index of the ticker column
     * @throws IllegalStateException if no matching header is found
     */
    private int findTickerColumnIndex(Element table, String keyword) {
        // Find the first <tr> that is a header row (contains <th> cells)
        Element headerRow = table.selectFirst("tr:has(th)");
        Validate.validState(headerRow != null,
                "No header row (<tr> with <th> cells) found in %s table.", keyword);

        Elements headers = headerRow.select("th");
        for (int i = 0; i < headers.size(); i++) {
            String headerText = headers.get(i).text().trim().toLowerCase();
            if (TICKER_HEADER_NAMES.contains(headerText)) {
                log.debug("{}: resolved ticker column at index {} (header: '{}')",
                        keyword, i, headers.get(i).text().trim());
                return i;
            }
        }

        // Build a readable list of found headers for the error message
        List<String> foundHeaders = new ArrayList<>();
        for (Element th : headers) {
            foundHeaders.add("'" + th.text().trim() + "'");
        }
        throw new IllegalStateException(
                "Could not find a ticker column in the " + keyword + " Wikipedia table. " +
                "Expected a header matching one of " + TICKER_HEADER_NAMES + " but found: " +
                foundHeaders + ". The page structure may have changed.");
    }

    /**
     * Searches all wikitables in the document for one that contains a column header
     * matching {@link #TICKER_HEADER_NAMES}. Returns the first matching table, or
     * {@code null} if none is found.
     */
    private Element findTableWithTickerHeader(Document doc, String keyword) {
        for (Element table : doc.select("table.wikitable")) {
            try {
                findTickerColumnIndex(table, keyword); // throws if not found
                return table; // found a matching table
            } catch (IllegalStateException ignored) {
                // This table doesn't have a ticker header; continue scanning
            }
        }
        return null;
    }

    // ── Wikipedia API helpers ───────────────────────────────────────────────────

    /**
     * Resolves the Wikipedia section <em>number</em> for a given section anchor ID
     * by querying the Wikipedia parse API's {@code tocdata} property.
     *
     * <p>Using the anchor ("Current_components") instead of a hardcoded number
     * means this survives article restructuring without code changes.
     *
     * @param page   Wikipedia page name (URL-encoded)
     * @param anchor section anchor/id (e.g., {@code "Current_components"})
     * @return section number as a string (e.g., {@code "13"})
     * @throws IllegalStateException if the anchor is not found in the TOC
     */
    private int resolveSectionNumber(String page, String anchor) {
        String tocUrl = WIKI_API_URL + "?action=parse&page=" + page + "&prop=tocdata&format=json";
        try {
            org.jsoup.Connection.Response response = Jsoup.connect(tocUrl)
                    .userAgent("Mozilla/5.0 (compatible; TradingBot/1.0)")
                    .timeout(CONNECT_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute();

            String json = response.body();

            // We look for the anchor in the JSON: "linkAnchor":"Current_components"
            // and then extract the nearby "index":"N" value.
            String anchorPattern = "\"linkAnchor\":\"" + anchor + "\"";
            int anchorPos = json.indexOf(anchorPattern);
            if (anchorPos < 0) {
                // Try the plain anchor (without "link" prefix)
                anchorPattern = "\"anchor\":\"" + anchor + "\"";
                anchorPos = json.indexOf(anchorPattern);
            }
            if (anchorPos < 0) {
                throw new IllegalStateException(
                        "Section anchor '" + anchor + "' not found in Wikipedia TOC for page '" + page + "'.");
            }

            // Scan backwards from the anchor position to find the "index":"N" for this section entry
            // The TOC JSON looks like: {"index":"13","anchor":"...","linkAnchor":"Current_components",...}
            // Find the nearest "index": before the anchor
            int searchFrom = Math.max(0, anchorPos - 200);
            String snippet = json.substring(searchFrom, anchorPos);
            int indexKeyPos = snippet.lastIndexOf("\"index\":\"");
            if (indexKeyPos < 0) {
                throw new IllegalStateException(
                        "Could not find 'index' field near anchor '" + anchor + "' in TOC JSON.");
            }
            int valueStart = indexKeyPos + 9; // skip past '"index":"'
            int valueEnd   = snippet.indexOf("\"", valueStart);
            if (valueEnd < 0) {
                throw new IllegalStateException("Malformed 'index' value in TOC JSON near anchor '" + anchor + "'.");
            }
            int sectionNumber = Integer.parseInt(snippet.substring(valueStart, valueEnd));
            log.debug("Resolved '{}' anchor to section number {} on page '{}'", anchor, sectionNumber, page);
            return sectionNumber;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to fetch TOC from Wikipedia API for page '" + page + "': " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a single Wikipedia article section (or the full article if
     * {@code section} is {@code null}) via the REST parse API and returns the
     * rendered HTML body parsed as a Jsoup {@link Document}.
     *
     * <p>The Wikipedia parse API response format is:
     * <pre>{@code {"parse":{"text":{"*":"<rendered HTML>"}}}}</pre>
     *
     * @param page    Wikipedia page name (URL-encoded)
     * @param section section number as a string, or {@code null} for the full article
     * @param label   human-readable label for log/error messages
     * @return Jsoup Document containing the section's rendered HTML
     * @throws IllegalStateException on network error or unexpected API response shape
     */
    private Document fetchWikiApiSection(String page, String section, String label) {
        String apiUrl = WIKI_API_URL + "?action=parse&page=" + page + "&prop=text&format=json";
        if (section != null) {
            apiUrl += "&section=" + section;
        }

        try {
            org.jsoup.Connection.Response response = Jsoup.connect(apiUrl)
                    .userAgent("Mozilla/5.0 (compatible; TradingBot/1.0)")
                    .timeout(CONNECT_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute();

            String json = response.body();

            // Extract the HTML blob from: {"parse":{"text":{"*":"HTML_CONTENT"}}}
            int startMarker = json.indexOf("\"*\":\"");
            if (startMarker < 0) {
                throw new IllegalStateException(
                        "Wikipedia API response for '" + label + "' is missing the '*' HTML field. " +
                        "Response length: " + json.length());
            }
            String htmlEncoded = extractJsonStringValue(json, startMarker + 5); // skip past '"*":"'
            // Unescape JSON string escape sequences
            String html = htmlEncoded
                    .replace("\\\"", "\"")
                    .replace("\\n",  "\n")
                    .replace("\\t",  "\t")
                    .replace("\\\\", "\\");

            Document doc = Jsoup.parse(html);
            log.debug("Fetched '{}' via Wikipedia API ({} chars HTML)", label, html.length());
            return doc;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to fetch '" + label + "' from Wikipedia API (" + apiUrl + "): "
                    + e.getMessage(), e);
        }
    }

    /**
     * Extracts the raw content of a JSON string value starting at {@code startIndex}
     * (the character immediately after the opening {@code "}).
     * Handles backslash escape sequences; stops at the first unescaped {@code "}.
     */
    private String extractJsonStringValue(String json, int startIndex) {
        StringBuilder sb = new StringBuilder();
        int i = startIndex;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(c).append(json.charAt(i + 1));
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private boolean isExpired(CachedEntry entry) {
        return Instant.now().isAfter(entry.fetchedAt().plusSeconds((long) cacheHours * 3600));
    }
}
