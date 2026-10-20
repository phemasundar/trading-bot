# Bullish Broken Wing Butterfly (BWB)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Positive overall, benefiting from flat to upward movement in the stock. |
| **Gamma (Γ)** | **Negative** | Negative at short body strikes; rapid price movement near the body increases risk. |
| **Theta (Θ)** | **Positive** | Time decay works in your favor, eroding the middle sold options faster than the wings. |
| **Vega (V)** | **Negative** | Position typically benefits from a decrease in implied volatility. |

A Broken Wing Butterfly is an options strategy designed to have a very high probability of profit while eliminating risk on one side of the trade (typically the downside for a bullish BWB).

### How it works
Unlike a symmetrical butterfly where the "wings" (long options) are equidistant from the "body" (short options), a broken wing butterfly skips a strike on the unpaid side, creating an asymmetrical risk profile.

- **Buy 1** ITM Call (Lower Strike)
- **Sell 2** OTM Calls (Middle Strike)
- **Buy 1** further OTM Call (Upper Strike, but skipping a standard interval width)

When structured for a net credit, this guarantees you will not lose money if the stock crashes, but retains a "tent" of maximum profit if it pins the middle strike, while carrying risk to the upside if the stock rallies too hard.

---

## Available Filters

The Bullish Broken Wing Butterfly strategy utilizes the unified mathematical expression filtering engine (`MathExpression`). Filters can be applied at the **trade level**, across the **three legs** (`leg1Long`, `leg2Short`, `leg3Long`), within **earnings gates** (`earningsFilters`), or against underlying **volatility and technical indicators**.

### Mathematical Expression Syntax
* **Supported Operators:** `>=` (greater or equal), `<=` (less or equal), `==` (equal), `>` (strictly greater), `<` (strictly less).
* **LHS (Left-Hand Side):** Standard metric identifier (e.g., `DTE`, `TOTAL_DEBIT`, `MAX_LOSS`, `UPPER_BREAK_EVEN_DELTA`, `DAYS_TO_NEXT_EARNINGS`).
* **RHS (Right-Hand Side):** Supports numeric constants (`100`, `1000`), percentages (`20%`), variables (`DTE`), offsets (`DTE - 10`), or scaled variables.

---

### 1. Trade-Level Conditions (`conditions: [...]`)
Applied to the assembled 3-strike butterfly combination:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DTE` | Days to expiration for the structure | `DTE` (integer days) | Numeric constant range | `DTE >= 25`<br>`DTE <= 55` |
| `TOTAL_DEBIT` | Total net debit paid for the structure: `(Leg1 Ask + Leg3 Ask - 2 * Leg2 Bid) * 100` | Dollars ($) | Numeric constant debit cap | `TOTAL_DEBIT <= 100` |
| `MAX_LOSS` | Maximum capital risk (greater of upside risk or downside risk) | Dollars ($) | Numeric constant dollar ceiling | `MAX_LOSS <= 1000` |
| `MAX_LOSS_UPSIDE` | Capital risk if stock rallies beyond the upper wing | Dollars ($) | Numeric constant or expression | `MAX_LOSS_UPSIDE <= 1000` |
| `MAX_LOSS_DOWNSIDE`| Capital risk if stock drops below lower wing (net debit) | Dollars ($) | Numeric constant | `MAX_LOSS_DOWNSIDE <= 100` |
| `UPPER_BREAK_EVEN_DELTA` | Delta of option strike closest to upper breakeven price | `0.0` to `1.0` | Numeric decimal (e.g. `0.15` to `0.20`) | `UPPER_BREAK_EVEN_DELTA <= 0.15` |
| `RETURN_ON_RISK` | Return on Risk: `(Max Profit / Max Loss) * 100` | Percentage (%) | Numeric constant or percentage literal | `RETURN_ON_RISK >= 15%` |
| `LOWER_WING_WIDTH` | Strike distance between Leg 1 and Leg 2 | Dollars ($) | Numeric constant | `LOWER_WING_WIDTH >= 5.0` |
| `UPPER_WING_WIDTH` | Strike distance between Leg 2 and Leg 3 | Dollars ($) | Numeric constant | `UPPER_WING_WIDTH >= 5.0` |

> **Built-in Hard Constraints:**
> 1. `Total Debit < (Leg 1 Ask * 100) / 2` (cannot pay more than half the cost of the ITM long call).
> 2. `Upper Wing Width <= 2 * Lower Wing Width` (prevents disproportionate upside tail risk).

---

### 2. Option Leg Conditions (`leg1Long`, `leg2Short`, `leg3Long`)
Configured in distinct leg blocks or via positional dotted syntax (`LEG1.<METRIC>`, `LEG2.<METRIC>`, `LEG3.<METRIC>`):

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DELTA` | Absolute delta of option contract | `0.0` to `1.0` | Numeric decimal representing strike depth | `leg1Long: DELTA >= 0.50`<br>`leg2Short: DELTA <= 0.40` |
| `OPEN_INTEREST` | Open contracts outstanding | Integer contracts | Numeric constant enforcing liquidity | `OPEN_INTEREST >= 100`<br>`LEG1.OPEN_INTEREST >= 100` |
| `VOLUME` | Daily contract volume | Integer contracts | Numeric constant | `VOLUME >= 25` |
| `IV` | Leg implied volatility | Percentage (%) | Numeric decimal or variable | `IV <= 60.0` |
| `BID`, `ASK`, `MARK` | Contract pricing per share | Dollars ($) | Numeric dollar limit or offset | `BID >= 0.20` |

---

### 3. Volatility Filters
Ensures favorable implied volatility conditions:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `IV_PERCENTILE` | 52-week IV percentile rank | `0.0` to `100.0` | Numeric constant | `IV_PERCENTILE >= 20` |
| `IV_RANK` | 52-week IV rank | `0.0` to `100.0` | Numeric constant | `IV_RANK >= 20` |
| `HV_RANK` | 20-day historical volatility rank | `0.0` to `100.0` | Numeric constant | `HV_RANK >= 20` |

---

### 4. Earnings Quality Gates (`earningsFilters`)
Guards against binary earnings events disrupting the tent:

| Filter (LHS) | Description | Possible LHS Values | Possible RHS Values & Syntax | Example Condition |
|---|---|---|---|---|
| `DAYS_TO_NEXT_EARNINGS` | Calendar days to next earnings event | Integer days (`999999` if none) | Variable reference (`DTE`) or numeric days | `DAYS_TO_NEXT_EARNINGS >= DTE` |
| `EARNINGS_NEAREST_TO_DTE` | Days to nearest earnings event on or before expiry | Integer days | Offset expression | `EARNINGS_NEAREST_TO_DTE <= DTE - 10` |

---

### 5. Technical Filters (Underlying Indicators)
* **Trend & Momentum**: Designed for slow, steady upward drift or consolidation.
* **Indicators**: `PRICE >= SMA50`, `RSI >= 45` and `RSI <= 65`, `VOLUME_SMA20 >= VOLUME_SMA50 * 90%`.
* **Liquidity**: `DOLLAR_VOLUME >= 50000000`.

---

### 6. Example Configuration (`strategies-config.yml`)

```yaml
- alias: "BWB Active"
  enabled: true
  termType: "Short Term"
  maxTradesToSend: 10
  strategyType: "BULLISH_BROKEN_WING_BUTTERFLY"
  descriptionFile: "strategies/bullish_broken_wing_butterfly.md"
  filterType: "BrokenWingButterflyFilter"
  securitiesFile: "portfolio, tracking, 2026, top100, SPY, QQQ"
  filter:
    conditions:
      - "DTE >= 25"
      - "DTE <= 55"
      - "TOTAL_DEBIT <= 100"
      - "MAX_LOSS <= 1000"
      - "UPPER_BREAK_EVEN_DELTA <= 0.15"
    leg1Long:
      conditions:
        - "DELTA >= 0.50"
        - "OPEN_INTEREST >= 100"
    leg2Short:
      conditions:
        - "DELTA <= 0.40"
        - "OPEN_INTEREST >= 100"
    leg3Long:
      conditions:
        - "OPEN_INTEREST >= 100"
    earningsFilters:
      conditions:
        - "DAYS_TO_NEXT_EARNINGS >= DTE"
```
