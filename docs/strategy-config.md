# Strategy Configuration Documentation

This document describes the structure and available configurations for `strategies-config.yml`.

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

---

## Options Strategy Math Filters (`conditions`)

Options strategies support declarative, mathematical formula-like filter conditions directly inside the `filter:` block under `conditions: [...]`. This unifies options filtering with Technical Indicators and screeners, reuses `MathExpression` and `MathExpressionParser`, and provides granular logging in `FilterPipeline` and `FilterLogStore`.

### Syntax

```text
<LEFT_VARIABLE> <OPERATOR> <RIGHT_VALUE_OR_VARIABLE> [ARITHMETIC]
```

- **Relational Operators**: `>=`, `<=`, `>`, `<`, `==`
- **Right-Hand Arithmetic**:
  - Offset additions/subtractions: `VAR + offset` or `VAR - offset` (e.g. `DTE - 10`)
  - Scaling: `VAR * multiplier` or `VAR * percentage%` (e.g. `SMA50 * 90%`)
  - Scaled offsets: `VAR * scale% +/- offset`
  - Literal percentages: `12%`, `25%` (equivalent to numbers `12`, `25`)
- **Dotted Leg Identifiers**:
  - Dotted property lookups route conditions to specific strategy legs: e.g. `SHORT_LEG.DELTA <= 0.2`, `LONG_LEG.OPEN_INTEREST >= 500`.

### Variable Reference Table

| Scope | Variable | Description |
|---|---|---|
| **Chain / Expiry** | `DTE` / `DAYS_TO_EXPIRATION` | Days to expiration for the chain |
| **Symbol IV** | `IV_RANK` | Implied Volatility Rank (0 - 100) from historical cache |
| **Symbol IV** | `IV_PERCENTILE` | Implied Volatility Percentile (0 - 100) from historical cache |
| **Earnings** | `DAYS_TO_NEXT_EARNINGS` | Calendar days to upcoming earnings |
| **Earnings** | `EARNINGS_NEAREST_TO_DTE` | Calendar days to earnings closest to (on or before) DTE |
| **Trade Level** | `MAX_LOSS` | Maximum loss of the trade setup in dollars |
| **Trade Level** | `NET_CREDIT` / `CREDIT` | Net credit collected for credit strategies |
| **Trade Level** | `NET_DEBIT` / `DEBIT` | Net debit paid for debit strategies |
| **Trade Level** | `ROR` / `RETURN_ON_RISK` | Return on risk percentage |
| **Trade Level** | `CAGR` / `ROR_CAGR` | Annualized Return on Risk percentage |
| **Trade Level** | `BREAK_EVEN` / `BREAK_EVEN_PRICE` | Trade breakeven stock price |
| **Trade Level** | `BREAK_EVEN_PCT` | Breakeven distance as a percentage of current stock price |
| **Trade Level** | `UPPER_BREAK_EVEN_PRICE` | Upper breakeven price for 2-sided strategies (IC, Strangle) |
| **Trade Level** | `UPPER_BREAK_EVEN_PCT` | Upper breakeven distance percentage |
| **Trade Level** | `ANNUALIZED_EXTRINSIC_PCT` | Annualized net extrinsic value relative to capital |
| **Trade Level** | `CURRENT_PRICE` | Current underlying stock price |
| **Leg Level** | `DELTA` / `ABS_DELTA` | Absolute delta of the leg (e.g. `0.20`) |
| **Leg Level** | `RAW_DELTA` / `SIGNED_DELTA` | Signed delta of the leg (e.g. `-0.20` for puts) |
| **Leg Level** | `OPEN_INTEREST` / `OI` | Open interest contracts |
| **Leg Level** | `VOLUME` | Total trading volume |
| **Leg Level** | `PREMIUM` / `MARK` / `PRICE` | Mid/mark price per share |
| **Leg Level** | `BID` | Bid price |
| **Leg Level** | `ASK` | Ask price |
| **Leg Level** | `STRIKE` / `STRIKE_PRICE` | Strike price |
| **Leg Level** | `IV` / `VOLATILITY` | Implied volatility of the leg |
| **Leg Level** | `GAMMA`, `THETA`, `VEGA`, `RHO` | Option Greeks |

### Leg Prefix Routing

Dotted conditions in `filter.conditions` automatically attach to the corresponding `LegFilter` for candidate pre-filtering and evaluate during trade setup validation:

| Strategy | Leg Prefixes | Target Leg |
|---|---|---|
| **Credit Spreads (PCS, CCS)** | `SHORT_LEG.`, `SHORT.` | Short leg |
| **Credit Spreads (PCS, CCS)** | `LONG_LEG.`, `LONG.` | Long leg |
| **Short Put** | `SHORT_LEG.`, `SHORT.` | Short put leg |
| **Short Strangle** | `PUT_SHORT.`, `SHORT_PUT.` | Short put leg |
| **Short Strangle** | `CALL_SHORT.`, `SHORT_CALL.` | Short call leg |
| **Iron Condor** | `PUT_SHORT.`, `SHORT_PUT.` | Put spread short leg |
| **Iron Condor** | `CALL_SHORT.`, `SHORT_CALL.` | Call spread short leg |
| **ZEBRA** | `SHORT_LEG.`, `SHORT.` | Short call |
| **ZEBRA** | `LONG_LEG.`, `LONG.` | Long call |
| **Broken Wing Butterfly** | `LEG1.` | Wing 1 (Lower Long) |
| **Broken Wing Butterfly** | `LEG2.` | Body (2x Short) |
| **Broken Wing Butterfly** | `LEG3.` | Wing 2 (Upper Long) |

### Configuration Styles

You can define leg conditions using either **Nested Leg Objects** (recommended standard practice) or **Dotted Notation** in the root conditions list. Both are fully supported and evaluated identically.

#### Style 1: Nested Leg & Filter Objects (Recommended Standard)
Clean, modular, and directly reflects the domain model hierarchy. Leg-level metrics (`DELTA`, `OPEN_INTEREST`, `VOLUME`, etc.) and event-specific filters (`earningsFilters`) are defined inside their respective objects without redundant prefixes:

```yaml
  - alias: "PCS"
    enabled: true
    termType: "Short Term"
    strategyType: "PUT_CREDIT_SPREAD"
    filterType: "CreditSpreadFilter"
    filter:
      conditions:
        - "DTE >= 25"
        - "DTE <= 50"
        - "MAX_LOSS <= 1000"
        - "ROR >= 12"
      shortLeg:
        conditions:
          - "DELTA <= 0.2"
          - "OPEN_INTEREST >= 500"
      earningsFilters:
        conditions:
          - "DAYS_TO_NEXT_EARNINGS >= DTE"

  - alias: "Iron Condor"
    enabled: true
    termType: "Medium Term"
    strategyType: "IRON_CONDOR"
    filterType: "IronCondorFilter"
    filter:
      conditions:
        - "DTE >= 40"
        - "DTE <= 65"
        - "MAX_LOSS <= 1000"
        - "ROR >= 24"
      putShortLeg:
        conditions:
          - "DELTA <= 0.15"
          - "OPEN_INTEREST >= 100"
      callShortLeg:
        conditions:
          - "DELTA <= 0.15"
          - "OPEN_INTEREST >= 100"
      earningsFilters:
        conditions:
          - "DAYS_TO_NEXT_EARNINGS >= DTE"

  - alias: "Short Strangle"
    enabled: true
    termType: "Up To Medium Term"
    strategyType: "SHORT_STRANGLE"
    filterType: "ShortStrangleFilter"
    filter:
      conditions:
        - "DTE >= 0"
        - "DTE <= 100"
        - "MAX_LOSS <= 20000"
        - "ROR >= 12"
        - "IV_PERCENTILE >= 30"
      putShortLeg:
        conditions:
          - "DELTA <= 0.2"
      callShortLeg:
        conditions:
          - "DELTA <= 0.2"
      earningsFilters:
        conditions:
          - "DAYS_TO_NEXT_EARNINGS >= DTE"
          - "EARNINGS_NEAREST_TO_DTE <= DTE - 10"
```

#### Style 2: Dotted Notation (Flat Syntax)
Convenient for quick inline configurations, flat interfaces, or cross-leg expressions:
```yaml
  - alias: "PCS"
    enabled: true
    termType: "Short Term"
    strategyType: "PUT_CREDIT_SPREAD"
    filterType: "CreditSpreadFilter"
    filter:
      conditions:
        - "DTE >= 25"
        - "DTE <= 50"
        - "MAX_LOSS <= 1000"
        - "ROR >= 12"
        - "SHORT_LEG.DELTA <= 0.2"
        - "SHORT_LEG.OPEN_INTEREST >= 500"
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
```

