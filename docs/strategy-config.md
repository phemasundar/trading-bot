# Strategy Configuration Documentation

This document describes the structure and available configurations for `strategies-config.json`.

## `earningsFilters`

Replaces the legacy `ignoreEarnings` boolean flag. This section uses the common `MathExpressionEvaluator` to allow flexible, math-based rules for filtering options chains based on upcoming earnings events.

### JSON Structure

```json
"earningsFilters": {
    "conditions": [
        "EXPRESSION_1",
        "EXPRESSION_2"
    ]
}
```

### Available Variables

| Variable | Description | Value if no matching earnings found |
|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days from **today** to the **soonest** upcoming earnings date. Used for "IV pump" scenarios where you want to trade *into* earnings. | `999999` (A very large number ensures filters requiring nearby earnings will fail). |
| `EARNINGS_NEAREST_TO_DTE` | Calendar days from **today** to the earnings event **closest to DTE**. Critically, this only considers earnings events that fall *on or before* the DTE. If a stock has multiple earnings before expiration, it picks the latest one (the one closest to DTE). Earnings after the DTE are strictly ignored. Used for "safe close" scenarios where you want to avoid volatility spikes just before position expiration. | `0` (Filters checking proximity to DTE will pass safely). |
| `DTE` | Days to expiration for the current option chain being evaluated. | Computed from the current chain's expiry date. |

### Configuration Examples

#### 1. Avoid Earnings Risk Near Expiration (Safe Close)
Ensure no earnings event falls within 5 days before expiration. This prevents holding a position through high volatility just before it expires.
```json
"earningsFilters": {
    "conditions": ["EARNINGS_NEAREST_TO_DTE <= DTE - 5"]
}
```

#### 2. Capture Earnings Premium (IV Pump)
Trade only if there is an earnings event within the next 14 days. Ideal for SELL strategies aiming to collect elevated IV premium.
```json
"earningsFilters": {
    "conditions": ["DAYS_TO_NEXT_EARNINGS <= 14"]
}
```

#### 3. No Earnings Before Expiration
Ensure there are absolutely no earnings events before the option expires (Equivalent to the legacy `"ignoreEarnings": false`).
```json
"earningsFilters": {
    "conditions": ["DAYS_TO_NEXT_EARNINGS >= DTE"]
}
```

#### 4. Ignore Earnings Entirely
Do not filter based on earnings at all (Equivalent to the legacy `"ignoreEarnings": true`).
**How to configure:** Simply omit the `"earningsFilters"` block from the strategy configuration entirely.
