# Long Call LEAPs

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Deep ITM long call moves almost dollar-for-dollar with the underlying stock price. |
| **Gamma (Γ)** | **Positive** | Accelerates Delta exposure toward 1.0 (equivalent to 100 shares) as stock price rises. |
| **Theta (Θ)** | **Negative** | Time decay hurts the buyer, though long DTE minimizes the daily decay speed. |
| **Vega (V)** | **Positive** | Implied volatility expansion inflates the option premium, boosting position value. |

A LEAP (Long-Term Equity Anticipation Securities) is simply an options contract that expires far in the future (generally over 1 year). Buying a deep in-the-money (ITM) long call LEAP is an alternative to buying 100 shares of the stock outright.

### How it works
- **Buy** a deep ITM Call (typically delta 0.70 to 0.90) expiring in 1+ years.
- By buying deep ITM, the option moves almost dollar-for-dollar with the stock (high delta).
- This provides essentially the same upside exposure as buying 100 shares, but for significantly less capital upfront, offering implicit leverage.

### Risk & Reward
* **Max Profit:** Technically unlimited as the stock price rises.
* **Max Loss:** The premium paid to buy the option contract. Max loss occurs if the stock drops to $0, or below the strike price at expiration.

---

## Strategy Filters & Configuration

The Long Call LEAP strategy supports a comprehensive suite of symbol-level, leg-level, risk-management, and carrying-cost filters. Below is a detailed breakdown of each filter, explaining why and how to use it.

---

### 1. Expiration & Time Horizon Filters

#### `minDTE` (Integer)
- **What it does**: Sets the minimum days to expiration required for candidate option chains.
- **Why to use it**: A true LEAP requires a long duration (typically >= 1 year / 365 days). Long DTE keeps theta decay (time decay) exceptionally slow, giving the trade ample runway to absorb market drawdowns without rapid value loss.
- **How to use it**: Set to `330` or `365` (1 year). For multi-year LEAPs, set to `500` or `700`.

#### `maxDTE` (Integer)
- **What it does**: Caps the maximum expiration horizon.
- **Why to use it**: Prevents selecting illiquid options expiring 2.5–3+ years out where bid-ask spreads are wide and pricing models may be inefficient.
- **How to use it**: Set to `730` (2 years) or leave blank/null to allow any available LEAP beyond `minDTE`.

#### `targetDTE` (Integer)
- **What it does**: Targets a specific expiration duration. If set, the engine prioritizes or anchors to the chain nearest to this DTE.
- **How to use it**: Leave blank/null when scanning across all available LEAP expiries, or set to `500` if targeting an exact ~1.5-year sweet spot.

---

### 2. Option Leg Selection Filters (`longCall`)

#### `longCall.minDelta` & `longCall.maxDelta` (Double)
- **What it does**: Enforces the Delta range of the long call (e.g. `0.70` to `0.90`).
- **Why to use it**: In a stock-replacement LEAP, delta represents your share-equivalence. A 0.80 delta call moves $0.80 for every $1.00 move in the stock. Deep ITM calls (delta > 0.70) consist primarily of intrinsic value (equity) and very little extrinsic value (time premium), maximizing leverage while minimizing theta decay.
- **How to use it**:
  - **Conservative / Stock Replacement**: `minDelta: 0.75`, `maxDelta: 0.90` (low theta drag, high stock tracking).
  - **Moderate**: `minDelta: 0.60`, `maxDelta: 0.80` (balanced leverage).
  - **Aggressive**: `minDelta: 0.40`, `maxDelta: 0.60` (higher leverage, but higher extrinsic decay).

#### `longCall.minOpenInterest` (Integer)
- **What it does**: Requires the contract to have at least N open interest contracts outstanding.
- **Why to use it**: Long-dated options can suffer from poor liquidity. Enforcing minimum open interest protects you from entering positions with wide bid-ask spreads that are costly to enter or exit.
- **How to use it**: Set to `50` or `100` for large-cap stocks. For highly liquid mega-caps (e.g., AAPL, MSFT), set to `200+`.

#### `longCall.minVolume` (Integer)
- **What it does**: Requires daily trading volume on the option leg to meet or exceed this threshold.
- **Why to use it**: Confirms active intraday liquidity and tight market maker quotes on the day of execution.
- **How to use it**: Typically set to `1` or `10` for LEAPs (since long-dated contracts trade less frequently than weeklies).

#### `longCall.minVolatility` & `longCall.maxVolatility` (Double)
- **What it does**: Sets minimum and maximum implied volatility (%) bounds on the specific option leg.
- **Why to use it**: Avoids buying LEAPs when implied volatility is inflated (e.g. before binary events) where you risk an IV crush, and avoids contracts priced with stale or erroneous volatility.
- **How to use it**: Set `maxVolatility: 50.0` or `60.0` depending on the stock's normal volatility regime.

#### `longCall.minPremium` & `longCall.maxPremium` (Double)
- **What it does**: Caps or floors the absolute ask price of the option contract (per share).
- **Why to use it**: Keeps contract outlays within account sizing parameters (e.g., avoiding contracts that cost > $50/share = $5,000/contract).
- **How to use it**: Set based on portfolio account size, or leave blank if using `maxOptionPricePercent` and `maxLossLimit`.

---

### 3. Capital & Risk Allocation Filters

#### `maxOptionPricePercent` (Double)
- **What it does**: Ensures the option premium represents at most X% of the current stock price:
  `Call Premium <= Current Stock Price * (maxOptionPricePercent / 100)`
- **Why to use it**: One of the primary advantages of LEAPs is capital efficiency (spending only a fraction of the stock's cost). If a deep ITM call costs 70% or 80% of the stock price, you tie up almost as much capital as owning shares with less downside protection.
- **How to use it**:
  - `maxOptionPricePercent: 40.0` or `50.0`: Guarantees you never spend more than 40%–50% of the stock price to control 100 shares.
  - Soft filter: Can be relaxed automatically via `relaxationPriority`.

#### `maxLossLimit` (Double)
- **What it does**: Sets the maximum dollar loss allowed per contract:
  `Call Premium * 100 <= maxLossLimit`
- **Why to use it**: For a Long Call LEAP, maximum loss is 100% of the premium paid. This hard ceiling protects against oversized portfolio allocations on high-priced stocks (e.g. booking an expensive contract on a $500 stock).
- **How to use it**: Set to your max account risk per trade (e.g., `3000` or `5000` for $3,000 or $5,000 max loss per contract).

#### `maxTotalDebit` (Double)
- **What it does**: Limits the maximum debit allowed per contract (`callPremium * 100`).
- **Why to use it**: Acts as a direct debit cap equivalent to `maxLossLimit` for long debit strategies.
- **How to use it**: Configure to your preferred per-trade dollar cap (e.g. `2500`).

---

### 4. Breakeven & Growth Hurdle Filters

#### `maxCAGRForBreakEven` (Double)
- **What it does**: Calculates the Compound Annual Growth Rate (CAGR) the underlying stock must achieve from today's price to the option's breakeven price (`Strike + Call Premium`) by expiration:
  ```text
  Years = DTE / 365.0
  BreakEven% = ((Strike + Premium - Current Price) / Current Price) * 100
  Breakeven CAGR% = ((1 + BreakEven% / 100) ^ (1 / Years) - 1) * 100
  ```
  The filter checks: `Breakeven CAGR% <= maxCAGRForBreakEven`.
- **Why to use it**: This is the single best metric for evaluating option hurdle rate. An option might need the stock to gain 20% over 2 years, which is only ~9.5% CAGR (very achievable for strong growth stocks). If an option requires a 25%+ CAGR just to break even, the hurdle rate is too aggressive.
- **How to use it**: Set to `10.0` (Conservative), `15.0` (Moderate), or `20.0` (Aggressive). It is a soft filter and can be relaxed first in `relaxationPriority`.

#### `maxBreakEvenPercentage` (Double)
- **What it does**: Limits the unannualized percentage gain required for the stock to hit breakeven by expiration:
  `((Strike + Premium - Current Price) / Current Price) * 100 <= maxBreakEvenPercentage`
- **Why to use it**: Caps the nominal distance to breakeven regardless of DTE.
- **How to use it**: Typically `15.0%` to `25.0%`.

---

### 5. Cost Savings & Carrying-Cost Analysis ("Money Wasted" Model)

The strategy features an advanced carrying-cost model comparing the money wasted on buying the option versus the money wasted on buying stock on 50% Regulation T margin over the option's DTE.

#### The "Money Wasted" Model Formula:
```text
1. Money Wasted on Option:
   Option Cost = Extrinsic Value (time decay to $0) + Missed Dividends

2. Money Wasted on Stock (on 50% margin):
   Margin Interest = 0.5 * Stock Price * (marginInterestRate / 100) * (DTE / 365)
   Saved Cash = (0.5 * Stock Price) - Call Premium
   Forfeited Market Return = Saved Cash * (savingsInterestRate / 100) * (DTE / 365)
   Stock Cost = Margin Interest + Forfeited Market Return

3. Cost Savings %:
   costSavingsPercent = ((Stock Cost - Option Cost) / Stock Cost) * 100
```

#### `minCostSavingsPercent` (Double)
- **What it does**: Requires the option route to save at least X% in wasted carrying costs compared to buying stock on margin:
  `costSavingsPercent >= minCostSavingsPercent`
- **Why to use it**: Evaluates whether financing the position through the option's time decay is cheaper than borrowing on 50% margin and forfeiting the return on saved cash.
- **How to use it**:
  - **When using 50% margin carrying costs**: Because multi-year option time decay ($8–$15) is typically larger than 6% margin interest on stock ($4–$6), `costSavingsPercent` is naturally negative (e.g. -20% to -60% for high-delta LEAPs). Setting a threshold like `-30.0` or `-50.0` filters out low-delta, high-decay options while keeping high-efficiency LEAPs.
  - **If unconfigured (null)**: The filter passes all candidate trades, ensuring no valid setups are prematurely blocked.

#### `marginInterestRate` (Double)
- **What it does**: Sets the annual margin interest rate (%) paid to the broker on the 50% borrowed stock loan.
- **Why to use it**: Reflects your actual broker margin borrowing rate (e.g. Schwab, Interactive Brokers, Robinhood).
- **How to use it**: Default in template is `6.0%`. Set to `8.0%`–`11.0%` if your broker charges higher retail margin rates. A higher margin rate increases the cost of holding stock, making the option route relatively more attractive.

#### `savingsInterestRate` (Double)
- **What it does**: Sets the expected annual CAGR (%) earned on the capital saved by purchasing the option instead of stock.
- **Why to use it**: Reflects the opportunity cost of cash. When you buy a LEAP for $20 instead of putting down $37.50 for margin stock, you keep $17.50 of cash. If that cash generates ~10% annual returns in the S&P 500, that profit is an opportunity cost of buying stock.
- **How to use it**: Default in template is `10.0%` (historical long-term market return).

---

### 6. Volatility & Timing Filters

#### `minIVRank` & `maxIVRank` (Double)
- **What it does**: Checks where current Implied Volatility sits within its 52-week absolute low-to-high range (0–100).
- **Why to use it**: For LEAP buyers, high IV inflates option premiums. You want to buy LEAPs when IV is relatively low or normal, not at extreme peaks.
- **How to use it**: Set `maxIVRank: 40.0` or `50.0` to avoid purchasing inflated options.

#### `minIVPercentile` & `maxIVPercentile` (Double)
- **What it does**: Measures the percentage of trading days in the past year where IV was lower than today's IV (0–100).
- **Why to use it**: More robust than IV Rank because it is not distorted by a single historical spike. Buying LEAPs at low IV Percentile ensures you are buying options during low-volatility regimes where future IV expansion will boost your option value (positive Vega).
- **How to use it**: Set `maxIVPercentile: 30.0` (as used in `Moderate LEAP`) to ensure you only initiate LEAPs when volatility is in the bottom 30% of its yearly distribution.

---

### 7. Earnings Quality Gates

#### `earningsFilters` (Expression Map)
- **What it does**: Evaluates dynamic mathematical expressions comparing earnings proximity to expiration (e.g. `DAYS_TO_NEXT_EARNINGS >= 14`).
- **Why to use it**: Protects against unexpected binary shocks right after initiating a LEAP position, or ensures entry timing avoids pre-earnings volatility inflation.
- **How to use it**: Use standard expressions like `DAYS_TO_NEXT_EARNINGS >= 10` or leave empty/ignore earnings since LEAPs span 4 to 8 quarters of earnings.

---

### 8. Extrinsic Value Filters

#### `maxNetExtrinsicValueToPricePercentage` (Double)
- **What it does**: Limits total extrinsic value as a percentage of the underlying stock price:
  `(Extrinsic Value / Current Stock Price) * 100 <= maxNetExtrinsicValueToPricePercentage`
- **Why to use it**: Directly caps the "time decay rent" you pay to hold the option. If a stock is $100 and extrinsic value is $15, you are paying 15% in time value over the LEAP duration.
- **How to use it**: Set to `15.0%` or `20.0%` to ensure the position consists mostly of intrinsic equity.

#### `minNetExtrinsicValueToPricePercentage` (Double)
- **What it does**: Sets a minimum floor on extrinsic value percentage.
- **How to use it**: Typically left null/unconstrained for LEAPs.

---

### 9. Priority, Relaxation & Sorting

#### `topTradesCount` (Integer)
- **What it does**: Returns the top N qualifying trades per ticker.
- **How to use it**: Set to `3` or `5` to prevent overwhelming alerts when scanning large option chains.

#### `relaxationPriority` (List of Strings)
- **What it does**: Defines the progressive relaxation order when fewer than N trades survive strict filtering.
- **Supported fields**:
  - `maxCAGRForBreakEven` (Relaxed first)
  - `maxOptionPricePercent` (Relaxed second)
  - `minCostSavingsPercent` (Relaxed last)
- **Why to use it**: If your strict hurdle rate (e.g. 10% CAGR) finds 0 trades, the engine relaxes that constraint to find the best available trade without abandoning hard risk boundaries (like max loss).
- **How to use it**: In YAML or Custom Execute:
  `["maxCAGRForBreakEven", "maxOptionPricePercent", "minCostSavingsPercent"]`

#### `sortPriority` (List of Strings)
- **What it does**: Controls the multi-attribute comparator that ranks surviving trades.
- **Supported keys**:
  - `daysToExpiration` (Desc: longer DTE first)
  - `costSavingsPercent` (Desc: higher cost savings first)
  - `optionPricePercent` (Asc: cheaper premium first)
  - `breakevenCAGR` (Asc: lower required CAGR first)
- **Default order**:
  `["daysToExpiration", "costSavingsPercent", "optionPricePercent", "breakevenCAGR"]`
