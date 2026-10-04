# Volga (Vomma)

Volga (also known as **Vomma** or $\text{Vega Convexity}$) is a second-order Greek that measures the sensitivity of an option's Vega ($\mathcal{V}$) to changes in Implied Volatility ($\sigma$). It is the second derivative of the option price with respect to implied volatility.

### Mathematical Formulation
$$\text{Volga} = \frac{\partial^2 V}{\partial \sigma^2} = \frac{\partial \mathcal{V}}{\partial \sigma}$$

Under the Black-Scholes-Merton model:
$$\text{Volga} = \mathcal{V} \frac{d_1 d_2}{\sigma}$$

Where:
- $\mathcal{V} = S e^{-q\tau} \sqrt{\tau} N'(d_1)$ is the first-order Vega.
- $d_1 = \frac{\ln(S/K) + (r - q + \frac{\sigma^2}{2})\tau}{\sigma \sqrt{\tau}}$
- $d_2 = d_1 - \sigma \sqrt{\tau}$

---

### Core Mechanics & Interpretations

1. **Vega Convexity (Acceleration of Volatility Exposure)**:
   Just as Gamma measures the acceleration of Delta as price moves, Volga measures the acceleration of Vega as Implied Volatility moves.
   - For **At-the-Money (ATM)** options, $d_1 d_2 < 0$, making Volga slightly negative or near zero.
   - For **Out-of-the-Money (OTM)** and **In-the-Money (ITM)** options, $d_1$ and $d_2$ share the same sign, so $d_1 d_2 > 0$, making Volga **strictly positive**.

2. **Tail Risk & Black Swan Protection**:
   Deep out-of-the-money options possess enormous positive Volga. In a severe market panic, not only does IV skyrocket, but the Vega of far OTM options multiplies exponentially due to Volga, creating explosive non-linear gains for tail-risk hedges.

3. **Volatility Smile & Skew Dynamics**:
   Volga plays a central role in quantitative volatility surface modeling. It explains why options markets exhibit a "volatility smile" (higher implied volatilities for OTM wings relative to ATM strikes).

---

### Volga Exposure Across Moneyness

| Moneyness | Vega ($\mathcal{V}$) | Volga Sign | Impact During Volatility Spikes |
| :--- | :--- | :--- | :--- |
| **Deep OTM Wings** | Low initially | Highly Positive ($+\text{Volga}$) | Vega expands exponentially; massive percentage value explosion |
| **At-the-Money (ATM)** | Highest | Near Zero or Slightly Negative | Vega remains relatively stable |
| **Deep In-the-Money (ITM)**| Low initially | Highly Positive ($+\text{Volga}$) | Vega expands alongside wings |

---

### Practical Application in Automated Bot Strategies

- **Tail-Risk Hedging with OTM Put Wings**:
  Buying cheap far-out-of-the-money puts (e.g. 5–10 delta) acts as a powerful portfolio crash hedge because positive Volga multiplies their volatility sensitivity precisely when market liquidity vanishes.
- **Short Iron Condor Wing Risk**:
  Iron condor sellers are short Volga on both wings. During unexpected black swan events (e.g. flash crashes or earnings shocks), Volga causes the short wings to inflate faster than traditional linear Black-Scholes models predict. Defined-risk spreads cap this liability.
- **Volatility Surface Arbitrage**:
  Quant funds trade long Volga wings against short ATM Vega straddles to harvest the spread between implied volatility curvature and realized volatility.
