The **3-Tier Waterfall Expected Move (EM) Engine** calculates a single, reliable $\pm 1$ standard deviation ($1\sigma$, ~68.2% probability) price boundary for any underlying ticker and expiration date by dynamically selecting the highest-precision formula that current option chain liquidity and time-to-expiration allow. Once computed from the At-The-Money (ATM) anchor, your trading bot scales that baseline EM to select strikes across any strategy—from 0DTE credit spreads to 3-year LEAPS.

---

### 1. All Tiers & Regimes Explained

The engine evaluates market data in a strict priority hierarchy, routing multi-year LEAPS to a geometric lognormal branch and standard expirations (`DTE` at most `365` days) down a 3-tier waterfall:

#### A. LEAPS Regime (`DTE` greater than `365` days) — Geometric Lognormal Model

* **Formula:**

$$\text{Upper Bound} = S \times e^{+(\text{IV} \sqrt{T})}, \qquad \text{Lower Bound} = S \times e^{-(\text{IV} \sqrt{T})}$$


$$\text{Upside Move} = \text{Upper Bound} - S, \qquad \text{Downside Move} = S - \text{Lower Bound}$$


* **What it solves:** Over 2–3 years, two distortions break standard formulas: (1) risk-free interest rates inflate ATM Call premiums with non-volatility carry value, and (2) linear $S - \text{EM}$ can project impossible negative stock prices on high-IV tickers. Geometric bounds keep downside moves floored above `$0` while modeling compounding upside growth.
* **When it runs:** Automatically whenever `minutesToExp` is greater than `525,600` (365 days) and current calculated IV (based on the last 1 year saved ATM data) is valid. (Falls back to Tier 2 ATM Straddle only if current calculated IV is missing or corrupted).

#### B. Tier 1 (`1` to `365` DTE) — Institutional 5-Strike Blend (`60% / 30% / 10%`)

* **Formula:**

$$\text{EM} = 0.60 \times (\text{ATM Straddle}) + 0.30 \times (\text{1st OTM Strangle}) + 0.10 \times (\text{2nd OTM Strangle})$$

* **What it solves:** Samples **6 contracts across 5 strikes** (`atmIdx - 2` through `atmIdx + 2`), capturing real-world **volatility skew and fat tails** rather than assuming a flat bell curve. When spot sits halfway between two strikes, adjacent OTM wings offset the intrinsic value inflating the ATM strike.
* **When it runs:** Expirations with at least `1,440` minutes (`1` full day) remaining, symmetric strike spacing, at least 2 strikes available on each side of ATM, and all 6 contracts passing strict spread (`at most 15%`), penny-tick (`bid at least $0.05`) filters and no earnings in next 10 days (`DAYS_TO_NEXT_EARNINGS > 10`).

#### C. Tier 2 (`0DTE` or Wing Fallback) — `0.85 × ATM Straddle`

* **Formula:**

$$\text{EM} = 0.85 \times (\text{ATM Call Mid} + \text{ATM Put Mid})$$

* **What it solves:** Directly prices immediate event or intraday risk from the two most liquid contracts on the chain without relying on outer OTM strikes that may have wide spreads or `$0.01–$0.05` exchange tick floors.
* **When it runs:**
* **Primary engine for 0DTE** (`minutesToExp` less than `1,440`).
* **Automatic fallback** whenever Tier 1 (or LEAPS) is skipped due to earnings within the next 10 days, non-uniform strike spacing, chain boundary limits, illiquid outer OTM wings, or missing saved LEAPS IV, provided ATM Call and Put spreads are `at most 25%` (`bid at least $0.01`).



#### D. Tier 3 (Quote-Degraded Fallback) — Annualized IV Linear Model

* **Formula:**

$$\text{EM} = S \times \text{IV} \times \sqrt{\frac{\max(\text{minutesToExp},\ 1.0)}{525,600}}$$


* **What it solves:** Keeps the bot operational when live option quotes are wide, halted, or zeroed out, relying first on the saved 1-year 30-day ATM `currentIV` from `IVDataRepository` (`>= 20` historical records) and falling back to the broker's chain-wide smoothed Implied Volatility (`chain.getVolatility()`).
* **When it runs:** Pre-market/after-hours scans (`bid = 0`), thinly traded mid-cap chains where ATM spreads exceed `25%`, or brief quote halts.

---

### 2. "When to Use What" Master Decision Matrix

| Trading Scenario | Active Tier / Branch | Why This Tier Wins | How to Apply Output to Strikes |
| --- | --- | --- | --- |
| **Standard Credit Spreads / Iron Condors (7–60 DTE)** | **Tier 1** (`60/30/10` Blend) | Incorporates put/call skew and fat-tail wing pricing across 5 strikes. | Place short strikes at $S \pm (1.0 \text{ to } 1.25 \times \text{EM})$. |
| **Earnings / Binary Event Plays** | **Tier 1** (or **Tier 2** if wings are wide) | Reads exact event premium priced into that expiration cycle rather than smoothed 30-day IV. | Place short strikes at or outside $\pm 1.0 \times \text{EM}$; cap debit spreads at $1.0 \times \text{EM}$. |
| **0DTE / Intraday Strategies** | **Tier 2** (`0.85 ×` Straddle) | Avoids artificial `$0.05` penny-tick floors on outer OTM wings while capturing live intraday decay. | Scale down to $0.50\times$–$1.0\times \text{EM}$ depending on time of day. |
| **Spot Sitting Halfway Between Strikes** | **Tier 1** (with spread tie-breaker) | Outer OTM wings neutralize intrinsic value baked into the nearest ATM strike. | Use `upperBound` and `lowerBound` directly; snap outward to liquid strikes. |
| **Thin Mid-Cap / Wide-Spread Chains** | **Tier 3** (Annualized IV Linear) | Rejects arbitrary market-maker midpoint padding when bid-ask width exceeds `25%`. | Pair $1.0 \times \text{EM}$ with contract Delta to guard against wide-chain skew. |
| **Pre-Market / After-Hours Scans** | **Tier 3** (Annualized IV Linear) | Option bids reset to `$0.00` outside market hours; smoothed IV preserves range estimates. | Run nightly/pre-market watchlists; re-validate with Tier 1/2 after market open. |
| **2–3 Year LEAPS (Calls, Puts, PMCC)** | **LEAPS Regime** (Tier 3 Lognormal) | Strips out multi-year interest-rate carry on calls and prevents negative downside bounds. | Use `upsideMove` and `downsideMove` independently for long-term targets/breakevens. |

---

### 3. Summary of Validation Rules & Guardrails

Every incoming chain passes through these exact filters before a tier is trusted:

| Guardrail Check | Rule / Threshold | Purpose |
| --- | --- | --- |
| **Spot Price** | Live `(bid + ask) / 2` if valid, else `last`/`mark` (must be greater than `0`) | Prevents stale last-trade prints from shifting the ATM anchor. |
| **Strike Spacing** | Equal dollar steps across all 5 Tier 1 strikes (`tolerance 0.001`) | Prevents mixed `$1` and `$5` strike increments from distorting `30%`/`10%` weights. |
| **Bid-Ask Spread Cap** | `(ask - bid) / mid` at most `15%` (Tier 1) or `25%` (Tier 2) | Blocks illiquid market-maker stub quotes from polluting midpoints. |
| **Penny-Tick Floor** | `bid` at least `$0.05` (Tier 1 wings) or `$0.01` (Tier 2 ATM) | Prevents `$0.00 × $0.05` far-OTM quotes from inflating Tier 1. |
| **Intrinsic Parity** | `mid + $0.05` at least `intrinsicValue` | Rejects stale deep-ITM quotes lagging behind a fast underlying move. |
| **Tier 1 Sanity Cap** | `blendedEm` at most `1.05 × atmStraddle` | Catches corrupted outer-wing quotes that exceed ATM straddle cost. |

---

### 4. End-to-End Implementation Plan for Your Trading Bot

Follow these five sequential phases when handing the specification to Antigravity:

1. **Phase 1 — Data Normalization & Chain Pairing Layer**
* Convert the raw option chain payload into sorted `StrikeRow` objects containing paired `Call` and `Put` bid/ask quotes for a single expiration date.
* Normalize `spotPrice`, UTC `minutesToExp`, and decimal `fallbackIv` before invoking the waterfall.


2. **Phase 2 — Quote Hygiene & ATM Anchor Module**
* Implement `isValidContract` (two-sided market, bid floor, relative spread cap, intrinsic parity) and `findAtmIndex` (nearest strike to spot, breaking exact ties by choosing the tighter combined Call + Put dollar spread).


3. **Phase 3 — Core Waterfall Engine (`calculateExpectedMoveWaterfall`)**
* Implement the decision tree: **Pre-flight Validation $\rightarrow$ LEAPS Lognormal Branch (`DTE` greater than `365`) $\rightarrow$ Tier 1 (`60/30/10`) $\rightarrow$ Tier 2 (`0.85 × Straddle`) $\rightarrow$ Tier 3 (`IV Linear`) $\rightarrow$ Terminal Circuit Breaker**.
* Return a structured `ExpectedMoveResult` exposing `upsideMove`, `downsideMove`, `upperBound`, `lowerBound`, `isLognormal`, `tierUsed`, and diagnostic `reason`.
