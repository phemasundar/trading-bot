# Iron Condor

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Neutral** | Minimal directional exposure, expecting the stock to remain within a specific price range. |
| **Gamma (Γ)** | **Negative** | Fast price movements toward either the put or call wings accelerate losses. |
| **Theta (Θ)** | **Positive** | Time decay is the primary profit driver, eroding value from both sides daily. |
| **Vega (V)** | **Negative** | Position loses value if volatility spikes; profits from volatility contraction (IV crush). |

An Iron Condor is a neutral, non-directional options strategy. It aims to profit from low volatility, expecting the underlying stock to remain within a specific price range.

### How it works
An Iron Condor is essentially the combination of two credit spreads:
1. A **Put Credit Spread** below the current stock price.
2. A **Call Credit Spread** above the current stock price.

### Risk & Reward
* **Max Profit:** The combined net credit received upfront from both spreads.
* **Max Loss:** The width of the wider spread minus the total credit received. Because you cannot simultaneously lose on both the put and call side, your risk is capped to just one of the wings.

---

## Available Filters

The Iron Condor strategy utilizes the unified mathematical expression filtering engine (`MathExpression`). Filters can be applied at the **trade level**, across both the **Put wing** (`putShortLeg`, `putLongLeg`) and **Call wing** (`callShortLeg`, `callLongLeg`), within **earnings gates** (`earningsFilters`), or against underlying **volatility and technical indicators**.

### Mathematical Expression Syntax
* **Supported Operators:** `>=` (greater or equal), `<=` (less or equal), `==` (equal), `>` (strictly greater), `<` (strictly less).
* **LHS (Left-Hand Side):** Standard metric identifier (e.g., `DTE`, `RETURN_ON_RISK`, `PUT_SHORT.DELTA`, `CALL_SHORT.DELTA`, `DAYS_TO_NEXT_EARNINGS`).
* **RHS (Right-Hand Side):** Supports numeric constants (`0`, `50`, `1000`), percentages (`24%`), variables (`DTE`), offsets (`DTE - 10`), or scaled variables (`HV_RANK * 1.5`).

---

### 1. Trade-Level Conditions (`conditions: [...]`)
Applied to the assembled four-leg Iron Condor structure:

> [!IMPORTANT]
> **`MAX_LOSS` Filter Requirement (Practically Mandatory for Iron Condors):**
> Because an Iron Condor pairs every valid Put Credit Spread ($O(S^2)$) with every valid Call Credit Spread ($O(S^2)$), an unconstrained 4-leg search scales as $O(S^4)$ across strikes $S$ (producing over 1,000,000 combinations per expiry on deep option chains like `SPY`, `QQQ`, or `NVDA`). Always include a `MAX_LOSS <= <limit>` condition (e.g., `MAX_LOSS <= 1000` or `MAX_LOSS <= 2000`) so `IronCondorStrategy` can mathematically pre-prune overly wide single-wing credit spreads (`putSpread.maxLoss <= MAX_LOSS + maxCallCredit` and `callSpread.maxLoss <= MAX_LOSS + maxPutCredit`) before pairing.

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DTE` | Days to expiration (Daily 0-1 DTE, Medium 40-65 DTE, Long 55-185 DTE) | `DTE` (integer days) | Numeric constant range | `DTE >= 40`<br>`DTE <= 65` |
| `MAX_LOSS` | **Recommended / Mandatory** — Maximum risk: `max(Put Width, Call Width) * 100 - Net Credit`. Enables exact wing pre-pruning prior to 4-leg pairing. | Dollars ($) | Numeric constant dollar ceiling | `MAX_LOSS <= 1000`<br>`MAX_LOSS <= 2000` |
| `NET_CREDIT` | Combined net credit collected from both wings | Dollars ($) | Numeric constant minimum credit | `NET_CREDIT >= 200` |
| `RETURN_ON_RISK` | Return on Risk: `(Total Net Credit / Max Loss) * 100` | Percentage (%) | Numeric constant or percentage literal | `RETURN_ON_RISK >= 24%`<br>`RETURN_ON_RISK >= 36` |
| `CAGR` | Annualized Return on Risk: `((Credit/Loss + 1)^(365/DTE) - 1) * 100` | Percentage (%) | Numeric constant or percentage literal | `CAGR >= 30%` |
| `BREAK_EVEN_PRICE` | Lower breakeven stock price: `Put Short Strike - Total Credit` | Dollars ($) | Numeric price or variable reference | `BREAK_EVEN_PRICE <= CURRENT_PRICE * 95%` |
| `UPPER_BREAK_EVEN_PRICE` | Upper breakeven stock price: `Call Short Strike + Total Credit` | Dollars ($) | Numeric price or variable reference | `UPPER_BREAK_EVEN_PRICE >= CURRENT_PRICE * 105%` |
| `BREAK_EVEN_PCT` | Required downward percentage move to lower breakeven | Percentage (%) | Numeric threshold (buffer from current price) | `BREAK_EVEN_PCT >= 4.0` |
| `UPPER_BREAK_EVEN_PCT` | Required upward percentage move to upper breakeven | Percentage (%) | Numeric threshold (buffer from current price) | `UPPER_BREAK_EVEN_PCT >= 4.0` |

---

### 2. Option Leg Conditions (`putShortLeg`, `callShortLeg`, `putLongLeg`, `callLongLeg`)
Configured in distinct leg blocks or via dotted trade-level syntax (`PUT_SHORT.<METRIC>`, `CALL_SHORT.<METRIC>`):

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DELTA` | Absolute delta of option contract | `0.0` to `1.0` | Numeric decimal representing probability proxy | `DELTA <= 0.15`<br>`PUT_SHORT.DELTA <= 0.16`<br>`CALL_SHORT.DELTA <= 0.16` |
| `SIGNED_DELTA` | Raw directional delta (- for put, + for call) | `-1.0` to `+1.0` | Numeric decimal | `SIGNED_DELTA >= -0.20` |
| `OPEN_INTEREST` | Open contracts outstanding | Integer contracts | Numeric constant enforcing liquidity | `OPEN_INTEREST >= 100`<br>`PUT_SHORT.OPEN_INTEREST >= 200` |
| `VOLUME` | Daily contract volume | Integer contracts | Numeric constant | `VOLUME >= 50` |
| `IV` | Leg implied volatility | Percentage (%) | Numeric decimal or variable | `IV <= 60.0` |
| `BID`, `ASK`, `MARK` | Contract pricing per share | Dollars ($) | Numeric dollar limit or offset | `BID >= 0.30` |

---

### 3. Volatility Filters
Ensures the underlying has sufficiently elevated volatility to collect premium before volatility contracts:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `IV_PERCENTILE` | Percentage of past 252 days with IV below today | `0.0` to `100.0` | Numeric constant or scaled expression | `IV_PERCENTILE >= 30` |
| `IV_RANK` | Current IV position relative to 52-week range | `0.0` to `100.0` | Numeric constant | `IV_RANK >= 30` |
| `HV_RANK` | Historical Volatility percentile rank | `0.0` to `100.0` | Numeric constant | `HV_RANK >= 25` |

---

### 4. Earnings Quality Gates (`earningsFilters`)
Prevents explosive earnings gaps from violating both wings:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days to next earnings event | Integer days (`999999` if none) | Variable reference (`DTE`) or numeric days | `DAYS_TO_NEXT_EARNINGS >= DTE` |
| `EARNINGS_NEAREST_TO_DTE` | Days to nearest earnings event on or before expiry | Integer days | Offset expression | `EARNINGS_NEAREST_TO_DTE <= DTE - 10` |

---

### 5. Technical Filters (Underlying Indicators)
* **Range-Bound Bias**: Securities oscillating between support and resistance.
* **Historical Volatility**: `HV_RANK >= 25` to ensure adequate option premium.
* **Trend Neutrality**: `PRICE >= SMA200` and `PRICE <= SMA50 * 105%` or RSI hovering near 50 (`RSI >= 40` and `RSI <= 60`).

---

### 6. Example Configuration (`strategies-config.yml`)

```yaml
- alias: "Iron Condor"
  enabled: true
  termType: "Medium Term"
  strategyType: "IRON_CONDOR"
  descriptionFile: "strategies/iron_condor.md"
  filterType: "IronCondorFilter"
  securitiesFile: "portfolio, tracking, 2026"
  filter:
    conditions:
      - "DTE >= 40"
      - "DTE <= 65"
      - "MAX_LOSS <= 2000"
      - "RETURN_ON_RISK >= 24%"
      - "IV_PERCENTILE >= 30"
    putShortLeg:
      conditions:
        - "DELTA <= 0.16"
        - "OPEN_INTEREST >= 100"
    callShortLeg:
      conditions:
        - "DELTA <= 0.16"
        - "OPEN_INTEREST >= 100"
    earningsFilters:
      conditions:
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
```
