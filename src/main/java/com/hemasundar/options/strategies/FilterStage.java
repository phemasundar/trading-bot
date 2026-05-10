package com.hemasundar.options.strategies;

/**
 * Canonical catalog of all filter stage display names used across option strategies.
 *
 * <p>Each constant owns the string that appears in the UI logs. Strategy classes reference
 * {@code FilterStage} enum values when building their {@link FilterPipeline}, so there is
 * exactly one place to change a display name.
 *
 * <p>Filters shared by multiple strategies are at the top; strategy-specific filters follow.
 */
public enum FilterStage {

    // ── Shared across multiple strategies ──────────────────────────────────
    GENERATED_CANDIDATES("Generated Candidates"),
    DELTA_FILTER("Delta Filter"),
    VOLUME_FILTER("Volume Filter"),
    OPEN_INTEREST_FILTER("Open Interest Filter"),
    LEG_PREMIUM_FILTER("Leg Premium Filter"),
    LEG_VOLATILITY_FILTER("Leg Volatility Filter"),
    MAX_LOSS_FILTER("Max Loss Filter"),
    MIN_RETURN_ON_RISK_FILTER("Min Return on Risk Filter"),
    MAX_EXTRINSIC_VALUE_FILTER("Max Extrinsic Value Filter"),
    MIN_EXTRINSIC_VALUE_FILTER("Min Extrinsic Value Filter"),
    BREAK_EVEN_FILTER("Break-Even Filter"),

    // ── Credit spread strategies (Put & Call) ───────────────────────────────
    POSITIVE_CREDIT_FILTER("Positive Credit Filter"),
    MAX_CREDIT_FILTER("Max Credit Filter"),
    MIN_CREDIT_FILTER("Min Credit Filter"),

    // ── Zebra strategy ──────────────────────────────────────────────────────
    MAX_DEBIT_FILTER("Max Debit Filter"),

    // ── Broken Wing Butterfly strategy ─────────────────────────────────────
    DEFAULT_DEBIT_FILTER("Default Debit Filter"),
    WING_WIDTH_RATIO_FILTER("Wing Width Ratio Filter"),
    DEBIT_VS_PRICE_FILTER("Debit vs Price Filter"),
    DEBIT_LIMIT_FILTER("Debit Limit Filter"),
    CREDIT_FILTER("Credit Filter"),
    UPPER_BREAKEVEN_FILTER("Upper Breakeven Filter"),

    // ── Long Call LEAP strategy ─────────────────────────────────────────────
    PREMIUM_LIMIT_FILTER("Premium Limit Filter"),
    COST_EFFICIENCY_FILTER("Cost Efficiency Filter"),
    CAGR_FILTER("CAGR Filter"),
    COST_SAVINGS_FILTER("Cost Savings Filter");

    private final String displayName;

    FilterStage(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable label shown in the Execution Logs UI.
     */
    public String displayName() {
        return displayName;
    }
}
