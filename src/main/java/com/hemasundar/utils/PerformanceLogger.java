package com.hemasundar.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated performance timing logger.
 *
 * <p>All messages emitted through this class are routed exclusively to
 * {@code logs/trading-bot-perf.log} (and optionally the console), completely
 * isolated from the main application log.  This is achieved by giving this
 * class its own SLF4J logger and wiring it to a dedicated Logback appender in
 * {@code logback-spring.xml}.
 *
 * <h3>Enabling / Disabling</h3>
 * Set the logger level in {@code logback-spring.xml}:
 * <pre>
 *   &lt;!-- ENABLED --&gt;
 *   &lt;logger name="com.hemasundar.utils.PerformanceLogger" level="DEBUG" additivity="false"&gt;
 *
 *   &lt;!-- DISABLED --&gt;
 *   &lt;logger name="com.hemasundar.utils.PerformanceLogger" level="OFF" additivity="false"&gt;
 * </pre>
 *
 * <h3>Output format</h3>
 * <pre>
 *   21:10:05.412 [PERF] getOptionChain API          | AAPL       | 412 ms
 *   21:10:05.002 [PERF] TOTAL execution             | ---        | 40210 ms
 * </pre>
 */
public final class PerformanceLogger {

    /** Dedicated logger — mapped to the PERF appenders in logback-spring.xml. */
    private static final Logger log = LoggerFactory.getLogger(PerformanceLogger.class);

    /** Header printed once at the start of each execution run. */
    private static final String HEADER =
            String.format("%-32s | %-12s | %s", "Phase", "Symbol", "Elapsed");
    private static final String SEPARATOR =
            "-".repeat(32) + "-+-" + "-".repeat(12) + "-+-" + "-".repeat(10);

    private PerformanceLogger() {
        // Utility class — no instances
    }

    /**
     * Logs a timing entry with symbol context.
     *
     * @param phase     short description of what was timed (≤ 32 chars for alignment)
     * @param symbol    the stock symbol being processed
     * @param elapsedMs elapsed time in milliseconds
     */
    public static void log(String phase, String symbol, long elapsedMs) {
        log.debug(String.format("%-32s | %-12s | %d ms", phase, symbol, elapsedMs));
    }

    /**
     * Logs a timing entry without a specific symbol (e.g. totals, batch ops).
     *
     * @param phase     short description of what was timed
     * @param elapsedMs elapsed time in milliseconds
     */
    public static void log(String phase, long elapsedMs) {
        log.debug(String.format("%-32s | %-12s | %d ms", phase, "---", elapsedMs));
    }

    /**
     * Logs the header separator — call once at the start of each execution run
     * to make the perf log easier to read.
     *
     * @param label a label for this execution run (e.g. strategy name or execution ID)
     */
    public static void header(String label) {
        log.debug("══════════ {} ══════════", label);
        log.debug(HEADER);
        log.debug(SEPARATOR);
    }

    /**
     * Logs a section separator — useful for grouping logs per strategy.
     *
     * @param label a short label for this section
     */
    public static void section(String label) {
        log.debug("── {} ──", label);
    }
}
