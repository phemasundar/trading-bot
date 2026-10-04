# Vanna

Vanna is a second-order cross-derivative Greek measuring the rate of change of an option's Delta ($\Delta$) relative to changes in Implied Volatility ($\sigma$), or equivalently, the rate of change of Vega ($\mathcal{V}$) relative to changes in the underlying spot price ($S$).

### Mathematical Formulation
$$\text{Vanna} = \frac{\partial^2 V}{\partial S \partial \sigma} = \frac{\partial \Delta}{\partial \sigma} = \frac{\partial \mathcal{V}}{\partial S}$$

Under the Black-Scholes-Merton model:
$$\text{Vanna} = -e^{-q\tau} N'(d_1) \frac{d_2}{\sigma} = -\frac{\mathcal{V}}{S} \left(\frac{d_2}{\sigma \sqrt{\tau}}\right)$$

Where:
- $d_1 = \frac{\ln(S/K) + (r - q + \frac{\sigma^2}{2})\tau}{\sigma \sqrt{\tau}}$
- $d_2 = d_1 - \sigma \sqrt{\tau}$

---

### Core Mechanics & Interpretations

1. **How Implied Volatility Affects Directional Exposure (Delta)**:
   - When an option is **Out-of-the-Money (OTM)**, an increase in IV increases the probability of reaching the strike, causing Delta to expand towards $0.50$.
   - When an option is **In-the-Money (ITM)**, an increase in IV adds uncertainty, causing Delta to pull back from $1.00$ towards $0.50$.
   - At-the-money (ATM), Vanna crosses zero.

2. **Market Maker Vanna Flows & Hedging Dynamics**:
   Options dealers and market makers maintain delta-neutral books. When market-wide implied volatility spikes (e.g. during a market sell-off), market maker short OTM puts experience negative Delta expansion due to Vanna. To re-hedge delta neutrality, dealers are forced to sell underlying futures, accelerating downward momentum.
   Conversely, when IV collapses post-event, Vanna causes dealers to buy back stock, contributing to sharp market rebounds (the "Vanna Rally").

---

### Vanna Sign Profile

| Option Type | Strike Moneyness | IV Expansion Effect on Delta | Vanna Sign |
| :--- | :--- | :--- | :--- |
| **Call Option** | Out-of-the-Money (OTM) | Delta increases towards $+0.50$ | Positive ($+\text{Vanna}$) |
| **Call Option** | In-the-Money (ITM) | Delta decreases towards $+0.50$ | Negative ($-\text{Vanna}$) |
| **Put Option** | Out-of-the-Money (OTM) | Delta becomes more negative (expands towards $-0.50$) | Negative ($-\text{Vanna}$) |
| **Put Option** | In-the-Money (ITM) | Delta becomes less negative (pulls back towards $-0.50$) | Positive ($+\text{Vanna}$) |

---

### Practical Application in Automated Bot Strategies

- **Post-Earnings Volatility Crush**:
  When selling credit spreads into high-IV periods, bots benefit not only from pure Vega crush and Theta decay, but also from Vanna contraction, which rapidly shrinks the Delta of out-of-the-money wings, taking directional pressure off the position.
- **Index Regimes & Dealer Positioning**:
  In index option trading (SPY / QQQ), aggregate dealer Vanna exposure dictates institutional support and resistance levels, helping automated bots avoid selling put spreads during negative gamma/vanna feedback loops.
