# Volatility (IV & HV)

Volatility is the heartbeat of options pricing. While equity traders typically focus on directional price action, options traders rely on volatility to evaluate the expected magnitude and velocity of price moves. A durable edge in algorithmic options trading often stems from identifying divergences where market expectations of future movement (**Implied Volatility**) diverge significantly from actual realized historical movement (**Historical Volatility**).

---

## 1. Historical Volatility (HV) vs. Implied Volatility (IV)

Understanding the distinction between realized and expected volatility is fundamental to systematic options trading:

- **🪞 Historical Volatility (HV)** is your **rearview mirror**. It quantifies exactly how turbulent the asset's price has been over a past lookback window.
- **🔦 Implied Volatility (IV)** is your **headlights**. It reflects how turbulent the market expects the asset's price to be up ahead.

| Metric | Nature | Calculation Source | Primary Bot Role |
| :--- | :--- | :--- | :--- |
| **Historical Volatility (HV)** | Realized / Backward-looking | Daily close-to-close log returns ($\sigma_{\text{daily}} \times \sqrt{252}$) | Ground truth baseline of actual asset movement |
| **Implied Volatility (IV)** | Forward-looking expectation | Backed out from market option prices via Black-Scholes | Indicator of demand, market sentiment, fear, and greed |

When $\text{IV} > \text{HV}$, option buyers are paying a premium above the asset's realized movement (the **Volatility Risk Premium**). This environment provides algorithmic selling strategies (credit spreads, iron condors, cash-secured puts) with a statistical advantage.

---

## 2. Historical Volatility (HV) Calculation

Historical volatility measures the annualized standard deviation of logarithmic daily price returns over a defined lookback window (typically 20 or 30 trading days).

### Mathematical Formulation

$$\text{HV} = \sigma_{\text{daily}} \times \sqrt{252}$$

Where:

1. **Daily Logarithmic Return ($r_t$)**:
   $$r_t = \ln\left(\frac{P_t}{P_{t-1}}\right)$$
   Where $P_t$ is the closing price on day $t$ and $P_{t-1}$ is the closing price on day $t-1$.

2. **Daily Standard Deviation ($\sigma_{\text{daily}}$)** over an $N$-day sample:
   $$\sigma_{\text{daily}} = \sqrt{\frac{1}{N - 1} \sum_{t=1}^{N} (r_t - \bar{r})^2}$$
   Where $\bar{r} = \frac{1}{N} \sum_{t=1}^{N} r_t$ is the mean daily log return.

3. **Annualization Factor ($\sqrt{252}$)**:
   Because standard deviation scales with the square root of time ($\sqrt{t}$), daily volatility is annualized by multiplying by the square root of approximately 252 trading days per year:
   $$\sqrt{252} \approx 15.8745$$

---

## 3. Why Does Implied Volatility Exceed Historical Volatility?

Implied volatility frequently exceeds historical volatility due to two primary market dynamics:

1. **The Volatility Risk Premium (VRP)**:
   Investors consistently pay an insurance premium to hedge downside risks. Option sellers collect this premium to compensate for absorbing tail risk.

2. **Binary Catalysts (Earnings Announcements, FDA Approvals, Macro Releases)**:
   Ahead of high-impact events, market uncertainty peaks and demand for protective puts or speculative calls escalates, inflating option premiums and driving IV upward.

### Post-Earnings IV Crush 📉

The instant an earnings report or binary catalyst is published, the event uncertainty dissolves. Implied volatility immediately collapses back to normalized baseline levels. In options trading, this rapid deflation is known as **IV Crush**. 

Because extrinsic value is tied directly to volatility via Vega ($\mathcal{V}$), an option's market value deflates rapidly following the announcement, enabling short volatility positions to capture rapid decay.

---

## 4. Normalizing Volatility: Raw IV, IV Rank, and IV Percentile

### The Raw IV Problem

Raw IV cannot be compared directly across different tickers. High-growth technology stocks naturally display much higher baseline volatility than defensive consumer staples like Coca-Cola or Procter & Gamble. 

To determine whether an option contract is truly expensive or cheap, the bot standardizes current IV against the underlying asset's own 52-week historical distribution using **IV Rank (IVR)** and **IV Percentile (IVP)**.

---

### IV Rank (IVR)

IV Rank measures where the current implied volatility sits relative to its absolute high and low points over the past 52 weeks (252 trading days).

#### Mathematical Formulation

$$\text{IVR} = \frac{\text{Current IV} - \text{52-Week Low IV}}{\text{52-Week High IV} - \text{52-Week Low IV}} \times 100$$

#### Example Calculation

Assume a stock has a 52-week IV range between $20\%$ ($\text{Low}$) and $80\%$ ($\text{High}$), and today's IV is $50\%$:

$$\text{IVR} = \frac{50\% - 20\%}{80\% - 20\%} \times 100 = \frac{30\%}{60\%} \times 100 = 50.0$$

The stock sits exactly at an IV Rank of $50$.

#### The Limitation of IV Rank

IV Rank has one critical blind spot: **outlier sensitivity**. If a freak geopolitical or earnings headline causes a 1-day IV spike to $80\%$, while the stock traded at $20\%$ for the remaining 364 days, a subsequent move to $50\%$ still outputs an IV Rank of $50$. The denominator remains stretched by a single isolated spike.

---

### IV Percentile (IVP)

IV Percentile solves this outlier issue by measuring the frequency of days. It calculates the percentage of trading days over the past year where implied volatility was strictly lower than the current level.

#### Mathematical Formulation

$$\text{IVP} = \frac{\text{Number of Trading Days with } \text{IV} < \text{Current IV}}{\text{Total Trading Days in Lookback Window } (N \approx 252)} \times 100$$

#### Example Calculation (The Outlier Spike Scenario)

Revisiting the scenario above:
- Today's IV is $50\%$.
- For 250 out of 252 trading days, the IV was $20\%$ (which is strictly below $50\%$).
- Only 2 days registered higher IV ($80\%$).

$$\text{IVP} = \frac{250}{252} \times 100 \approx 99.2\%$$

#### Comparing IVR vs. IVP

| Metric | Focus | Formula | Vulnerability to Outlier Spikes |
| :--- | :--- | :--- | :--- |
| **IV Rank (IVR)** | Absolute High-Low Range | $\frac{\text{Current} - \text{Low}}{\text{High} - \text{Low}} \times 100$ | **High**: One freak spike stretches the entire range for 52 weeks |
| **IV Percentile (IVP)** | Time Spent Below Current Level | $\frac{\text{Days Below Current}}{\text{Total Days}} \times 100$ | **Low**: A single day's spike represents only $\frac{1}{252} \approx 0.4\%$ of the sample |

While IV Rank reports average volatility ($50$), IV Percentile reveals that the stock's current IV is higher than $99.2\%$ of all days in the past year. IV Percentile is significantly more robust for automated bot decision-making.

---

## 5. CBOE Volatility Index (VIX) as a Market Regime Filter

While IV and IVP monitor single stocks, the **CBOE Volatility Index (VIX)** measures the 30-day implied volatility of the entire S&P 500 ($\text{SPX}$). It serves as the financial markets' primary **fear gauge** and regime benchmark.

### Typical VIX Market Regimes

| VIX Level | Market Regime | Environment Characteristics | Automated Bot Guidelines |
| :--- | :--- | :--- | :--- |
| **$\text{VIX} < 20$** | Calm / Normal | Orderly bull market or gentle consolidation | Standard credit spreads, LEAP call accumulation, cash-secured puts |
| **$20 \le \text{VIX} \le 30$** | Elevated / Choppy | Increased macro sensitivity and larger swings | Wider strike margins, reduced position sizing, active delta hedging |
| **$\text{VIX} > 30$** | High / Systemic Panic | Market crises, liquidation, or broad selloffs | Heightened tail risk; prioritize strictly defined-risk strategies |

---

### Diagnosing Idiosyncratic vs. Systemic Volatility

By cross-referencing single-stock IV Percentile against the market-wide VIX, trading bots distinguish between company-specific catalysts and macro crises:

```
                      ┌──────────────────────────────────────────────┐
                      │              Single-Stock IVP                │
                      │               High (≥ 80%)                   │
                      └──────────────────────┬───────────────────────┘
                                             │
                      ┌──────────────────────┴───────────────────────┐
                      ▼                                              ▼
          VIX Low (< 20)                                 VIX High (> 30)
 ┌───────────────────────────────┐              ┌───────────────────────────────┐
 │     Idiosyncratic Catalyst    │              │       Systemic Turmoil        │
 │ • Earnings / Clinical Trial   │              │ • Market-wide contagion       │
 │ • Calm macro background       │              │ • Correlated selloff          │
 │ • Favorable for defined risk  │              │ • Elevated risk across boards │
 └───────────────────────────────┘              └───────────────────────────────┘
```

- **Case 1 (Idiosyncratic Catalyst)**: Stock $\text{IVP} \ge 85\%$ while $\text{VIX} = 13$. The elevated options premium is driven exclusively by a company event (e.g. earnings announcement or regulatory filing). If earnings filters pass, premium selling offers substantial statistical edge.
- **Case 2 (Systemic Market Panic)**: Stock $\text{IVP} \ge 85\%$ while $\text{VIX} = 38$. Elevated options pricing reflects macro liquidation and broad equity turbulence. Selling naked options carries severe tail risk; bots should restrict entries or expand wing widths.

---

## 6. Summary & Bot Decision Framework

- **🪞 Historical Volatility (HV)**: Realized, backward-looking price fluctuations ($\sigma_{\text{daily}} \times \sqrt{252}$).
- **🔦 Implied Volatility (IV)**: Forward-looking market expectation embedded in option pricing.
- **📏 IV Rank (IVR)**: Position relative to absolute 52-week extremes; susceptible to single-day outlier distortion.
- **🎯 IV Percentile (IVP)**: Percentage of days trading below current IV; highly resilient metric for entry filtering.
- **🌊 VIX Filter**: Distinguishes between company-specific opportunities and systemic market crises.

### Algorithmic Execution Rule

$$\text{Short Volatility Candidate} \iff (\text{IVP} \ge 75) \land (\text{VIX} \le 28) \land (\text{DAYS\_TO\_EARNINGS} > \text{DTE})$$

## Volatility Skew / Volatility Smirk

So far, we have treated a stock's Implied Volatility as a single, uniform number. But in reality, if you open an options chain, you will notice that IV changes depending on which specific strike price you are looking at.

Volatility Skew is the difference in implied volatility between out-of-the-money (OTM) puts and OTM calls for the same expiration date.

Here is why it exists: Large financial institutions hold massive amounts of stock. To protect themselves from a sudden market crash, they constantly buy OTM puts. This acts exactly like fire insurance for their portfolios. This massive, relentless demand for downside protection drives up the price of puts, which in turn mathematically drives up their IV.

Because of this, in a normal market, OTM Puts almost always have a higher IV than equidistant OTM Calls. The market prices in fear (crashes) more aggressively than greed (rallies).

For your trading bot, you can measure this sentiment directly. A standard way to program this is to have your bot compare the IV of the 25-delta Put to the 25-delta Call:

- Normal Skew: Put IV > Call IV (Market is bracing for normal downside risk). 
- Reverse Skew: Call IV > Put IV (Market is aggressively betting on an upside explosion).

Your bot can use Volatility Skew as a directional safety filter. If Call_IV > Put_IV by a significant margin, the bot should immediately block any strategies that sell calls. Conversely, if Put_IV is massively higher than Call_IV, it knows the market is pricing in heavy crash risk.

### Skew Ratio:

Skew Ratio = (25-delta Put IV) / (25-delta Call IV)

If the ratio drops below 1, your bot flags a potential upside squeeze.

If the ratio spikes to something like 1.8, your bot knows the market is in a state of extreme panic.

### Probable Bot Logic
#### 1. Block Call Credit Spreads (Upside Squeeze Filter)
IF (Skew_Ratio < 1.0) OR (Skew_Percentile < 20):
Action = "BLOCK Call Credit Spreads"
Reason = "Call IV is higher than Put IV, or call demand is abnormally high."

#### 2. Block Put Credit Spreads (Crash Panic Filter)
IF (Skew_Percentile > 80):
Action = "BLOCK Put Credit Spreads"
Reason = "Put demand is in the top 20% of its yearly range (heavy crash risk)."

#### 3. Safe Zone for Selling Spreads
IF (Skew_Ratio >= 1.0) AND (Skew_Percentile >= 20 AND Skew_Percentile <= 80):
Action = "ALLOW Put Credit Spreads, Call Credit Spreads, or Iron Condors"

### Strike prices selection
$$\text{Expected Move (in $ terms)} = \text{Stock Price} \times \text{IV} \times \sqrt{\frac{\text{Days to Expiration}}{365}}$$
