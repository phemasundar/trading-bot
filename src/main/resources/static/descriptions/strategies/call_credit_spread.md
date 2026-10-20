# Call Credit Spread (CCS)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Negative** | Profits as the underlying stock price declines or stays below the short call strike. |
| **Gamma (Γ)** | **Negative** | Directional risk increases if the stock rallies rapidly toward the short strike. |
| **Theta (Θ)** | **Positive** | Time decay works in your favor, eroding the premium of the options sold. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |

A Call Credit Spread is a bearish to neutral options strategy that involves selling a call option and buying another call option at a higher strike price with the same expiration date.

### How it works
- **Sell** an out-of-the-money (OTM) Call to collect a premium.
- **Buy** a further OTM Call to cap your maximum potential loss.

You collect a net credit upfront. As long as the stock stays below your short call strike through expiration, you keep the entire credit.

### Risk & Reward
* **Max Profit:** The net credit received upfront.
* **Max Loss:** Difference between strikes minus the net credit received.

---

## Available Filters

The Call Credit Spread strategy utilizes the unified mathematical expression filtering engine (`MathExpression`). Filters can be applied at the **trade level**, **individual leg level** (`shortLeg`, `longLeg`), within **earnings gates** (`earningsFilters`), or against underlying **volatility and technical indicators**.

### Mathematical Expression Syntax
* **Supported Operators:** `>=` (greater or equal), `<=` (less or equal), `==` (equal), `>` (strictly greater), `<` (strictly less).
* **LHS (Left-Hand Side):** Standard metric identifier (e.g., `DTE`, `RETURN_ON_RISK`, `SHORT_LEG.DELTA`, `DAYS_TO_NEXT_EARNINGS`).
* **RHS (Right-Hand Side):** Supports numeric constants (`25`, `1000`), percentages (`12%`), variables (`DTE`, `HV_RANK`), offsets (`DTE - 10`), or scaled variables (`HV_RANK * 1.5`, `VOLUME_SMA50 * 90%`).

---

### 1. Trade-Level Conditions (`conditions: [...]`)
Applied to the overall multi-leg trade setup:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DTE` | Days to expiration for the spread | `DTE` (integer days) | Numeric constant (`20` to `65`), or range with `>=` and `<=` | `DTE >= 25`<br>`DTE <= 50` |
| `MAX_LOSS` | Maximum capital risk per contract block: `(Width - Credit) * 100` | Dollars ($) | Numeric constant representing max dollar risk ceiling | `MAX_LOSS <= 1000` |
| `NET_CREDIT` | Net premium collected upfront per contract | Dollars ($) | Numeric constant representing minimum dollar credit | `NET_CREDIT >= 150` |
| `RETURN_ON_RISK` | Return on Risk: `(Net Credit / Max Loss) * 100` | Percentage (%) | Numeric constant or percentage literal | `RETURN_ON_RISK >= 12%`<br>`RETURN_ON_RISK >= 15` |
| `CAGR` | Annualized Return on Risk: `((Credit/Loss + 1)^(365/DTE) - 1) * 100` | Percentage (%) | Numeric constant or percentage literal | `CAGR >= 24%` |
| `BREAK_EVEN_PRICE` | Upper breakeven underlying price: `Short Strike + Net Credit` | Dollars ($) | Numeric price or variable reference | `BREAK_EVEN_PRICE >= CURRENT_PRICE * 105%` |
| `BREAK_EVEN_PCT` | Percentage rally required for the underlying stock to hit breakeven | Percentage (%) | Numeric threshold (buffer from current price) | `BREAK_EVEN_PCT >= 5.0` |

---

### 2. Option Leg Conditions (`shortLeg` & `longLeg`)
Applied to the short call (`shortLeg`) or protective long call (`longLeg`). Can be configured in leg blocks or via dotted trade-level syntax (`SHORT_LEG.<METRIC>`):

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DELTA` | Absolute delta of the option leg | `0.0` to `1.0` | Numeric decimal representing probability proxy | `DELTA <= 0.20`<br>`SHORT_LEG.DELTA <= 0.30` |
| `SIGNED_DELTA` | Directional delta (positive for call options) | `0.0` to `1.0` | Numeric decimal | `SIGNED_DELTA <= 0.25` |
| `OPEN_INTEREST` | Total open contracts outstanding on this strike | Integer contracts | Numeric constant enforcing contract liquidity | `OPEN_INTEREST >= 500`<br>`SHORT_LEG.OPEN_INTEREST >= 100` |
| `VOLUME` | Daily trading volume on this strike | Integer contracts | Numeric constant enforcing active execution | `VOLUME >= 50` |
| `IV` | Implied Volatility of the specific leg contract | Percentage (%) | Numeric decimal or variable | `IV <= 65.0` |
| `BID`, `ASK`, `MARK` | Option pricing points per share | Dollars ($) | Numeric dollar limit or offset | `BID >= 0.50` |
| `EXTRINSIC_VALUE` | Time value portion of the option premium | Dollars ($) | Numeric dollar amount | `EXTRINSIC_VALUE >= 0.40` |

---

### 3. Volatility Filters
Evaluated against underlying stock volatility before option combinations are assembled:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `IV_PERCENTILE` | Percentage of days in the past 252 days with IV lower than today | `0.0` to `100.0` | Numeric constant, or scaled variable expression | `IV_PERCENTILE >= 30`<br>`IV_PERCENTILE >= HV_RANK * 1.5` |
| `IV_RANK` | Where current IV sits between 52-week absolute low and high | `0.0` to `100.0` | Numeric constant | `IV_RANK >= 25` |
| `HV_RANK` | Percentile rank of 20-day Historical Volatility | `0.0` to `100.0` | Numeric constant | `HV_RANK >= 20` |
| `CURRENT_IV` | Raw 30-day implied volatility of underlying stock | Percentage (%) | Numeric decimal | `CURRENT_IV >= 25.0` |

---

### 4. Earnings Quality Gates (`earningsFilters`)
Guards against binary event risk by comparing expiration dates with upcoming corporate earnings:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days until the soonest upcoming earnings report | Integer days (`999999` if none) | Variable reference (`DTE`) or numeric days | `DAYS_TO_NEXT_EARNINGS >= DTE`<br>`DAYS_TO_NEXT_EARNINGS >= 14` |
| `EARNINGS_NEAREST_TO_DTE` | Days to earnings event closest to DTE on or before expiration | Integer days (`0.0` if none) | Offset expression | `EARNINGS_NEAREST_TO_DTE <= DTE - 10` |
| `DTE` | Expiration horizon under test | Integer days | Numeric or variable | `DTE <= DAYS_TO_NEXT_EARNINGS` |

---

### 5. Technical Filters (Underlying Indicators)
Configured under `technicalFilters` against the universal indicator whitelist in `securities-filters.yml`:
* **RSI (14)**: `RSI >= 70` (overbought), `RSI <= 50` (bearish confirmation), or bearish crossover.
* **Bollinger Bands (20, 2.0)**: `PRICE >= BB_UPPER` (resistance test / upper band rejection).
* **Moving Averages (SMA / EMA)**: `PRICE <= SMA50`, `PRICE <= SMA200`, `SMA50 <= SMA200` (death cross), `EMA9 <= EMA21`.
* **Volume SMAs (20, 50)**: `VOLUME_SMA20 >= VOLUME_SMA50 * 90%`.
* **Liquidity**: `DOLLAR_VOLUME >= 50000000` (minimum $50M daily turnover).

---

### 6. Example Configuration (`strategies-config.yml`)

```yaml
- alias: "Standard CCS"
  enabled: true
  termType: "Short Term"
  strategyType: "CALL_CREDIT_SPREAD"
  descriptionFile: "strategies/call_credit_spread.md"
  filterType: "CreditSpreadFilter"
  securitiesFile: "portfolio, tracking, bearish"
  filter:
    conditions:
      - "DTE >= 25"
      - "DTE <= 50"
      - "MAX_LOSS <= 1000"
      - "RETURN_ON_RISK >= 12%"
      - "IV_PERCENTILE >= 30"
    shortLeg:
      conditions:
        - "DELTA <= 0.20"
        - "OPEN_INTEREST >= 500"
    earningsFilters:
      conditions:
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
```
