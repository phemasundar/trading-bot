# Short Strangle

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Neutral** | Minimal directional exposure, expecting the stock to remain within a specific price range. |
| **Gamma (Γ)** | **Negative** | Fast price movements toward either the put or call wings accelerate losses. |
| **Theta (Θ)** | **Positive** | Time decay is the primary profit driver, eroding value from both sides daily. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |

A Short Strangle is a neutral, non-directional options strategy. It aims to profit from high volatility contraction and time decay, expecting the underlying stock to remain within a specific price range.

### How it works
A Short Strangle involves selling two out-of-the-money (OTM) options on the same underlying stock with the same expiration date:
1. Sell an **OTM Put** below the current stock price.
2. Sell an **OTM Call** above the current stock price.

### Risk & Reward
* **Max Profit:** The combined net credit received upfront from selling both options. This occurs if the stock price remains between the two strike prices at expiration.
* **Max Loss:** Theoretically unlimited on the upside (because of the naked call) and substantial on the downside (if the stock falls to zero). 

### When to use
This strategy is best used when **Implied Volatility (IV) is high** (e.g., IV Rank >= 50). The goal is to capture the elevated premium and benefit from a subsequent drop in volatility (IV crush).

### Breakeven Points
* **Lower Breakeven:** `Put Strike - Total Premium Received`
* **Upper Breakeven:** `Call Strike + Total Premium Received`

---

## Available Filters

The Short Strangle strategy utilizes the unified mathematical expression filtering engine (`MathExpression`). Filters can be applied at the **trade level**, across both short legs (`putShortLeg`, `callShortLeg`), within **earnings gates** (`earningsFilters`), or against underlying **volatility and technical indicators**.

### Mathematical Expression Syntax
* **Supported Operators:** `>=` (greater or equal), `<=` (less or equal), `==` (equal), `>` (strictly greater), `<` (strictly less).
* **LHS (Left-Hand Side):** Standard metric identifier (e.g., `DTE`, `MAX_LOSS`, `RETURN_ON_RISK`, `PUT_SHORT.DELTA`, `CALL_SHORT.DELTA`, `IV_PERCENTILE`, `DAYS_TO_NEXT_EARNINGS`).
* **RHS (Right-Hand Side):** Supports numeric constants (`20000`, `12`), percentages (`12%`), variables (`DTE`), offsets (`DTE - 10`), or scaled variables.

---

### 1. Trade-Level Conditions (`conditions: [...]`)
Applied to the assembled two-leg naked short strangle structure:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DTE` | Days to expiration (typically 30–60 DTE for optimal theta decay) | `DTE` (integer days) | Numeric constant range | `DTE >= 0`<br>`DTE <= 100`<br>`DTE >= 30` |
| `MAX_LOSS` | Notional capital risk ceiling: `max(Put Strike, Call Strike) * 100 - Net Credit` | Dollars ($) | Numeric constant representing capital ceiling | `MAX_LOSS <= 20000` |
| `NET_CREDIT` | Combined premium collected upfront: `(Put Bid + Call Bid) * 100` | Dollars ($) | Numeric constant minimum credit | `NET_CREDIT >= 250` |
| `RETURN_ON_RISK` | Return on Risk: `(Net Credit / Max Notional Loss) * 100` | Percentage (%) | Numeric constant or percentage literal | `RETURN_ON_RISK >= 12%`<br>`RETURN_ON_RISK >= 15` |
| `CAGR` | Annualized Return on Risk: `((Credit/Loss + 1)^(365/DTE) - 1) * 100` | Percentage (%) | Numeric constant or percentage literal | `CAGR >= 25%` |
| `BREAK_EVEN_PRICE` | Lower breakeven stock price: `Put Strike - Combined Credit` | Dollars ($) | Numeric price or variable reference | `BREAK_EVEN_PRICE <= CURRENT_PRICE * 90%` |
| `UPPER_BREAK_EVEN_PRICE` | Upper breakeven stock price: `Call Strike + Combined Credit` | Dollars ($) | Numeric price or variable reference | `UPPER_BREAK_EVEN_PRICE >= CURRENT_PRICE * 110%` |
| `BREAK_EVEN_PCT` | Percentage downward move to lower breakeven | Percentage (%) | Numeric threshold (cushion from current price) | `BREAK_EVEN_PCT >= 5.0` |
| `UPPER_BREAK_EVEN_PCT` | Percentage upward move to upper breakeven | Percentage (%) | Numeric threshold (cushion from current price) | `UPPER_BREAK_EVEN_PCT >= 5.0` |

---

### 2. Option Leg Conditions (`putShortLeg` & `callShortLeg`)
Configured in distinct leg blocks or via dotted syntax (`PUT_SHORT.<METRIC>`, `CALL_SHORT.<METRIC>`):

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DELTA` | Absolute delta of option contract | `0.0` to `1.0` | Numeric decimal (typically 10–20 delta OTM) | `DELTA <= 0.20`<br>`PUT_SHORT.DELTA <= 0.16`<br>`CALL_SHORT.DELTA <= 0.16` |
| `SIGNED_DELTA` | Raw directional delta (- for put, + for call) | `-1.0` to `+1.0` | Numeric decimal | `SIGNED_DELTA >= -0.20` |
| `OPEN_INTEREST` | Open contracts outstanding | Integer contracts | Numeric constant enforcing liquidity | `OPEN_INTEREST >= 100` |
| `VOLUME` | Daily contract volume | Integer contracts | Numeric constant | `VOLUME >= 50` |
| `IV` | Leg implied volatility | Percentage (%) | Numeric decimal or variable | `IV >= 35.0` |
| `BID`, `ASK`, `MARK` | Contract pricing per share | Dollars ($) | Numeric dollar limit or offset | `BID >= 0.75` |

---

### 3. Volatility Filters
Short Strangles carry undefined risk and must only be deployed during elevated implied volatility regimes:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `IV_PERCENTILE` | Percentage of past 252 days with IV below today | `0.0` to `100.0` | Numeric constant (strongly recommended `>= 30` to `50`) | `IV_PERCENTILE >= 30` |
| `IV_RANK` | Where current IV sits between 52-week low and high | `0.0` to `100.0` | Numeric constant | `IV_RANK >= 50` |
| `HV_RANK` | 20-day historical volatility rank | `0.0` to `100.0` | Numeric constant | `HV_RANK >= 25` |

---

### 4. Earnings Quality Gates (`earningsFilters`)
Undefined risk strategies should avoid unexpected earnings volatility:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days to next earnings event | Integer days (`999999` if none) | Variable reference (`DTE`) or numeric days | `DAYS_TO_NEXT_EARNINGS >= DTE` |
| `EARNINGS_NEAREST_TO_DTE` | Days to nearest earnings event on or before expiry | Integer days | Offset expression enforcing buffer | `EARNINGS_NEAREST_TO_DTE <= DTE - 10` |

---

### 5. Technical Filters (Underlying Indicators)
* **High Implied Volatility over Historical**: Seeks stocks where options are overpriced relative to realized movement.
* **Indicators**: `HV_RANK >= 25`, `DOLLAR_VOLUME >= 50000000`.

---

### 6. Example Configuration (`strategies-config.yml`)

```yaml
- alias: "Short Strangle"
  enabled: true
  termType: "Up To Medium Term"
  strategyType: "SHORT_STRANGLE"
  descriptionFile: "strategies/short_strangle.md"
  filterType: "ShortStrangleFilter"
  securitiesFile: "portfolio, tracking, 2026"
  filter:
    conditions:
      - "DTE >= 0"
      - "DTE <= 100"
      - "MAX_LOSS <= 20000"
      - "RETURN_ON_RISK >= 12%"
      - "IV_PERCENTILE >= 30"
    putShortLeg:
      conditions:
        - "DELTA <= 0.20"
    callShortLeg:
      conditions:
        - "DELTA <= 0.20"
    earningsFilters:
      conditions:
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
        - "EARNINGS_NEAREST_TO_DTE <= DTE - 10"
```
