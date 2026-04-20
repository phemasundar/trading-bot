package com.hemasundar.dto;

/**
 * Centralized alert message templates for the error handling system.
 * Use {@link String#format} with these templates for dynamic values.
 * Messages are intentionally kept short for mobile readability.
 */
public final class AlertMessages {

    private AlertMessages() {}

    // ── Sources ──────────────────────────────────────────────────────────────

    public static final String SRC_SUPABASE = "Supabase";
    public static final String SRC_EXECUTION = "Execution";

    /** Format args: screenerName */
    public static final String SRC_SCREENER_FMT = "Screener: %s";

    /** Format args: strategyName, symbol */
    public static final String SRC_STRATEGY_SYMBOL_FMT = "%s / %s";

    // ── Supabase ─────────────────────────────────────────────────────────────

    public static final String SAVE_EXEC_RESULT_FAILED    = "Save exec result failed";
    public static final String SAVE_CUSTOM_RESULT_FAILED  = "Save custom result failed";
    public static final String SAVE_STRATEGY_RESULT_FAILED = "Save strategy result failed";
    public static final String SAVE_SCREENER_RESULT_FAILED = "Save screener result failed";

    // ── Strategy Execution ───────────────────────────────────────────────────

    public static final String AUTH_FAILED = "Auth failed — update REFRESH_TOKEN & redeploy";

    /** Format args: exception message */
    public static final String SYMBOL_PROCESSING_FAILED_FMT = "Failed: %s";

    /** Format args: exception message */
    public static final String UNEXPECTED_FAILURE_FMT = "Unexpected failure: %s";

    // ── Telegram ─────────────────────────────────────────────────────────────

    public static final String TELEGRAM_SEND_FAILED = "Telegram send failed";

    // ── Screener ─────────────────────────────────────────────────────────────

    public static final String NO_SECURITIES_CONFIGURED = "No securities configured";

    /** Format args: exception message */
    public static final String SCREENER_EXEC_FAILED_FMT = "Screener failed: %s";
}
