# Cash-Secured Put (Short Put / CSP)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Profits as the stock price rises or stays above the strike price. |
| **Gamma (Γ)** | **Negative** | Risk accelerates if the underlying price drops rapidly towards or below the strike. |
| **Theta (Θ)** | **Positive** | Time decay works in your favor, eroding the value of the put option you sold. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |


The primary options strategy for acquiring a stock at a lower future price is a **Cash-Secured Put (CSP)**.

This strategy allows you to get paid to wait for the stock to drop to your preferred entry point.

### How It Works

Instead of placing a standard limit order to buy shares, you sell a put option at a strike price you are comfortable paying for the stock.

* **Collect Premium:** You receive cash (premium) immediately for selling the put.
* **Cash Requirement:** You must hold enough cash in your brokerage account to purchase 100 shares of the underlying stock at the strike price if the option is assigned.

### Outcomes at Expiration

| Market Scenario | What Happens | Result |
| --- | --- | --- |
| **Stock stays above Strike** | The put option expires worthless. | You keep 100% of the premium collected. You do not buy the stock. |
| **Stock drops below Strike** | You are assigned and obligated to buy 100 shares at the strike price. | You purchase the stock at your desired lower price, and keep the premium. |

### The Math

If the stock drops and you are assigned the shares, the premium you collected lowers your overall purchase price. The breakeven point can be calculated as:

$$Breakeven = Strike\_Price - Premium\_Received$$

> **Example:** If a stock is trading at $50 and you want to buy it at $45, you sell a $45 strike put and collect a $1.50 premium. If assigned, you buy the shares at $45, but your actual cost basis is $43.50.

---

## Available Filters

The Cash-Secured Put strategy utilizes the unified mathematical expression filtering engine (`MathExpression`). Filters can be applied at the **trade level**, the **short put leg** (`shortLeg`), within **earnings gates** (`earningsFilters`), or against underlying **volatility and technical indicators**.

### Mathematical Expression Syntax
* **Supported Operators:** `>=` (greater or equal), `<=` (less or equal), `==` (equal), `>` (strictly greater), `<` (strictly less).
* **LHS (Left-Hand Side):** Standard metric identifier (e.g., `DTE`, `MAX_LOSS`, `RETURN_ON_RISK`, `SHORT_LEG.DELTA`, `IV_PERCENTILE`, `DAYS_TO_NEXT_EARNINGS`).
* **RHS (Right-Hand Side):** Supports numeric constants (`20000`, `12`), percentages (`12%`), variables (`DTE`), offsets (`DTE - 10`), or scaled variables.

---

### 1. Trade-Level Conditions (`conditions: [...]`)
Applied to the single-leg cash-secured put setup:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DTE` | Days to expiration (Short Term 25–50, Medium Term 55–185, Extra Long 330+) | `DTE` (integer days) | Numeric constant range | `DTE >= 25`<br>`DTE <= 50`<br>`DTE >= 55` |
| `MAX_LOSS` | Maximum notional risk if stock drops to $0: `(Strike - Bid) * 100` | Dollars ($) | Numeric constant representing max capital allocation cap | `MAX_LOSS <= 20000` |
| `NET_CREDIT` | Premium collected upfront per contract: `Bid * 100` | Dollars ($) | Numeric constant minimum credit | `NET_CREDIT >= 100` |
| `RETURN_ON_RISK` | Return on Risk: `(Net Credit / Max Notional Loss) * 100` | Percentage (%) | Numeric constant or percentage literal | `RETURN_ON_RISK >= 12%`<br>`RETURN_ON_RISK >= 6%` |
| `CAGR` | Annualized Return on Risk: `((Credit/Loss + 1)^(365/DTE) - 1) * 100` | Percentage (%) | Numeric constant or percentage literal | `CAGR >= 18%` |
| `BREAK_EVEN_PRICE` | Breakeven purchase price: `Strike Price - Premium Received` | Dollars ($) | Numeric price or variable reference | `BREAK_EVEN_PRICE <= CURRENT_PRICE * 90%` |
| `BREAK_EVEN_PCT` | Percentage discount from current price to breakeven | Percentage (%) | Numeric threshold | `BREAK_EVEN_PCT >= 5.0` |

---

### 2. Option Leg Conditions (`shortLeg`)
Applied to the short put contract or via dotted syntax (`SHORT_LEG.<METRIC>`):

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DELTA` | Absolute delta of short put option | `0.0` to `1.0` | Numeric decimal representing probability of assignment | `DELTA <= 0.20`<br>`SHORT_LEG.DELTA <= 0.25` |
| `SIGNED_DELTA` | Directional delta (negative for put options) | `-1.0` to `0.0` | Numeric decimal | `SIGNED_DELTA >= -0.20` |
| `OPEN_INTEREST` | Open contracts outstanding | Integer contracts | Numeric constant enforcing liquidity | `OPEN_INTEREST >= 100`<br>`SHORT_LEG.OPEN_INTEREST >= 50` |
| `VOLUME` | Daily contract trading volume | Integer contracts | Numeric constant | `VOLUME >= 25` |
| `IV` | Leg implied volatility | Percentage (%) | Numeric decimal or variable | `IV <= 60.0` |
| `BID`, `ASK`, `MARK` | Contract pricing per share | Dollars ($) | Numeric dollar limit or offset | `BID >= 1.00` |

---

### 3. Volatility Filters
Evaluated against underlying stock volatility to ensure rich premium collection:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `IV_PERCENTILE` | 52-week IV percentile rank | `0.0` to `100.0` | Numeric constant (prefers elevated IV) | `IV_PERCENTILE >= 30` |
| `IV_RANK` | 52-week IV rank | `0.0` to `100.0` | Numeric constant | `IV_RANK >= 25` |
| `HV_RANK` | 20-day historical volatility rank | `0.0` to `100.0` | Numeric constant | `HV_RANK >= 25` |

---

### 4. Earnings Quality Gates (`earningsFilters`)
Guards against earnings assignment risk:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days to next earnings report | Integer days (`999999` if none) | Variable reference (`DTE`) or numeric days | `DAYS_TO_NEXT_EARNINGS >= DTE` |
| `EARNINGS_NEAREST_TO_DTE` | Days to nearest earnings event on or before expiry | Integer days | Offset expression | `EARNINGS_NEAREST_TO_DTE <= DTE - 10` |

---

### 5. Technical Filters (Underlying Indicators)
* **High-Conviction Support**: Best when selling puts on quality stocks pulling back to strong moving averages.
* **Indicators**: `PRICE >= SMA200` (long-term uptrend), `PRICE <= SMA50 * 102%`, `RSI <= 40` (pullback entry).
* **Liquidity**: `DOLLAR_VOLUME >= 50000000`.

---

### 6. Example Configuration (`strategies-config.yml`)

```yaml
- alias: "Short Put"
  enabled: true
  termType: "Medium Term"
  strategyType: "SHORT_PUT"
  descriptionFile: "strategies/short_put.md"
  filterType: "CreditSpreadFilter"
  securitiesFile: "portfolio, tracking, 2026"
  filter:
    conditions:
      - "DTE >= 55"
      - "DTE <= 185"
      - "MAX_LOSS <= 20000"
      - "RETURN_ON_RISK >= 12%"
      - "IV_PERCENTILE >= 30"
    shortLeg:
      conditions:
        - "DELTA <= 0.20"
        - "OPEN_INTEREST >= 100"
    earningsFilters:
      conditions:
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
```