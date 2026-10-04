# Vega (V)

Vega ($\mathcal{V}$ or $V$) is a first-order Greek that measures the sensitivity of an option's theoretical price to a 1% (0.01 or 100 bps) change in the implied volatility ($\sigma$) of the underlying asset.

### Mathematical Formulation
$$\mathcal{V} = \frac{\partial V}{\partial \sigma}$$

Under the Black-Scholes-Merton model for both European calls and puts:
$$\mathcal{V} = S e^{-q\tau} \sqrt{\tau} N'(d_1) = S e^{-q\tau} \sqrt{\tau} \frac{1}{\sqrt{2\pi}} e^{-\frac{d_1^2}{2}}$$

Notice that:
- Vega is **identical for standard calls and puts** sharing the same strike price, underlying price, and expiration date.
- Long option positions have strictly positive Vega ($\mathcal{V} > 0$).
- Short option positions have strictly negative Vega ($\mathcal{V} < 0$).
- Vega scales directly with the square root of time to expiration ($\sqrt{\tau}$).

---

### Core Mechanics & Interpretations

1. **Volatility Pricing Sensitivity**:
   If an option has a Vega of $0.15$ and the underlying stock's IV expands from $25\%$ to $28\%$ (+3% or +300 bps), the option's theoretical value is expected to increase by approximately $3 \times 0.15 = \$0.45$, regardless of any movement in stock price.

2. **DTE Relationship**:
   Because long-dated options have more time for future volatility events to unfold, Vega is much higher for long-dated contracts (e.g. 180 DTE or LEAPs) than for short-dated contracts (e.g. 7 DTE).

3. **Implied Volatility Crush**:
   Following anticipated binary events such as corporate earnings announcements or major economic releases, IV collapses rapidly (IV Crush). Net short Vega positions profit substantially as option extrinsic values deflate.

---

### Vega Exposure by Position Type

| Position | Vega Polarity | Sensitivity & Real-World Impact |
| :--- | :--- | :--- |
| **Long Call / Long Put** | Positive ($+\mathcal{V}$) | Benefits from IV expansion; hurt by volatility contraction |
| **Short Call / Short Put** | Negative ($-\mathcal{V}$) | Benefits from volatility collapse; hurt by sudden market panic or IV spikes |
| **Credit Spreads (PCS / CCS)** | Negative ($-\mathcal{V}$) | Short strike Vega outweighs long strike Vega; gains when IV deflates |
| **Iron Condors** | High Negative ($-\mathcal{V}$) | Profits from range-bound price action and broad IV contraction |
| **Long LEAP Calls** | High Positive ($+\mathcal{V}$) | Significant volatility exposure; protects against market turbulence over years |

---

### Practical Application in Automated Bot Strategies

- **IV Rank & IV Percentile Filtering**:
  Trading bots avoid selling premium during historically suppressed volatility regimes. Requiring `minIVRank >= 20` or `minIVPercentile >= 25` ensures the bot sells options when Vega is inflated, maximizing post-entry IV contraction edge.
- **Earnings Volatility Filters**:
  Unscheduled earnings events introduce massive Vega risk. Bots enforce event-based earnings exclusion rules (`DAYS_TO_NEXT_EARNINGS > DTE`) to prevent binary earnings gap surprises.
- **Vega Neutrality & Risk Balancing**:
  In multi-strategy portfolios, long Vega positions (such as LEAP calls or debit spreads) hedge the short Vega exposure of income strategies (such as credit spreads and cash-secured puts).
