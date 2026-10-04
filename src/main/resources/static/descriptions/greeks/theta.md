# Theta (Θ)

Theta ($\Theta$) is a first-order Greek measuring the rate of change of an option's theoretical value with respect to the passage of time, holding all other variables constant. It is commonly referred to as **time decay**.

### Mathematical Formulation
$$\Theta = \frac{\partial V}{\partial t} = -\frac{\partial V}{\partial \tau}$$

Under the Black-Scholes-Merton model for a standard European call option:
$$\Theta_{\text{Call}} = -\frac{S \sigma e^{-q\tau} N'(d_1)}{2\sqrt{\tau}} - r K e^{-r\tau} N(d_2) + q S e^{-q\tau} N(d_1)$$

For a standard European put option:
$$\Theta_{\text{Put}} = -\frac{S \sigma e^{-q\tau} N'(d_1)}{2\sqrt{\tau}} + r K e^{-r\tau} N(-d_2) - q S e^{-q\tau} N(-d_1)$$

Where:
- $\tau$ is the time remaining to expiration in years.
- $\Theta$ is usually quoted per trading day or per calendar day by dividing the annual value by 252 or 365.

---

### Core Mechanics & Interpretations

1. **Extrinsic Value Erosion**:
   Every option consists of intrinsic value (in-the-money amount) and extrinsic value (time value and volatility premium). Theta only erodes **extrinsic value**. At expiration, extrinsic value reaches exactly zero.

2. **The Non-Linear Decay Curve (The Theta Cliff)**:
   Time decay does not occur in a straight line. For at-the-money options:
   - 90–180 DTE: Decay is gradual and linear (~1–2% per week).
   - 45–60 DTE: Decay begins accelerating into the optimal sweet spot for premium sellers.
   - 0–30 DTE: Decay accelerates exponentially (the "Theta Cliff"), rapidly destroying remaining extrinsic value.

3. **Weekend & Holiday Decay**:
   Options markets price continuous calendar decay ($365$-day clock). Over a standard weekend, options burn roughly 2.5 to 3 days of theoretical decay, though market makers adjust implied volatility slightly on Friday afternoons to balance Monday opening gaps.

---

### Theta Exposure by Position Type

| Position | Theta Polarity | Impact & Behavior |
| :--- | :--- | :--- |
| **Long Call / Long Put** | Negative ($-\Theta$) | Position loses value each day as time elapses (Theta drag / decay bleed) |
| **Short Call / Short Put** | Positive ($+\Theta$) | Position gains value each day as time elapses (Premium collection) |
| **Credit Spreads (PCS / CCS)** | Positive ($+\Theta$) | Net seller of time value; profits if price remains stable or drifts favorably |
| **Iron Condors** | High Positive ($+\Theta$) | Dual premium decay harvesting; maximum profit when price stays range-bound |
| **Calendar Spreads** | Positive ($+\Theta$) | Sells short-dated (fast-decaying) option against long-dated (slow-decaying) option |

---

### Practical Application in Automated Bot Strategies

- **Optimal DTE Entry Window (30–45 DTE)**:
  Selling credit spreads and iron condors at 30–45 DTE captures the steepest slope of the theta decay curve while maintaining sufficient distance from violent near-expiry Gamma risk.
- **Profit Target Management (50% Rule)**:
  Closing winning credit spreads at 50% of maximum credit collected significantly enhances capital velocity. Holding until expiration takes twice as long to capture the remaining 50% while exposing the portfolio to black-swan reversals.
- **Theta/Vega Tradeoff**:
  Option sellers buy high implied volatility and sell time decay. When IV drops alongside steady theta decay, strategies capture dual tailwinds ("IV crush" + "Theta bleed").
