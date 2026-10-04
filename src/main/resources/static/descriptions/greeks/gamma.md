# Gamma (Γ)

Gamma ($\Gamma$) is a second-order Greek that measures the rate of change of an option's Delta ($\Delta$) relative to a \$1.00 move in the underlying asset's price. It represents the acceleration of directional exposure and curvature (convexity) of the option value.

### Mathematical Formulation
$$\Gamma = \frac{\partial^2 V}{\partial S^2} = \frac{\partial \Delta}{\partial S}$$

Under the Black-Scholes-Merton model:
$$\Gamma = \frac{e^{-q\tau} N'(d_1)}{S \sigma \sqrt{\tau}}$$

Where:
- $N'(d_1) = \frac{1}{\sqrt{2\pi}} e^{-\frac{d_1^2}{2}}$ is the standard normal probability density function.
- $\sigma$ is the implied volatility of the underlying asset.
- $\tau$ is the annualized time to expiration.

Gamma is strictly positive ($\Gamma > 0$) for long standard option positions (both long calls and long puts) and negative ($\Gamma < 0$) for short option positions.

---

### Core Mechanics & Interpretations

1. **Delta Acceleration**:
   If an option has $\Delta = 0.50$ and $\Gamma = 0.05$, a \$1.00 increase in the underlying price increases Delta to $0.55$, while a \$1.00 decrease lowers Delta to $0.45$.

2. **Convexity & The Gamma Peak**:
   Gamma reaches its maximum value when an option is exactly **At-the-Money (ATM)** and near expiration ($\tau \to 0$). Deep ITM and deep OTM options have Gamma approaching zero because their Deltas are fixed near $1.00$ or $0.00$ respectively.

3. **Gamma Risk (Pin Risk & Tail Risk)**:
   For option sellers, negative Gamma represents escalating directional exposure. If the stock rallies against a short call, Delta becomes increasingly negative (short stock equivalent), compounding losses at an accelerating pace.

---

### Gamma Exposure by Position Type

| Position | Gamma Polarity | Behavior & Risk Characteristics |
| :--- | :--- | :--- |
| **Long Call / Long Put** | Positive ($+\Gamma$) | Favorable convexity; Delta moves in your favor as underlying trends |
| **Short Call / Short Put** | Negative ($-\Gamma$) | Unfavorable convexity; losses accelerate when price moves against strike |
| **Long Straddle / Strangle** | High Positive ($+\Gamma$) | High acceleration on explosive price breakouts in either direction |
| **Short Iron Condor** | Negative ($-\Gamma$) | Stable in center of tent; severe Gamma acceleration near short wings |
| **Long Stock** | Zero ($\Gamma = 0$) | Stock Delta is static ($+1.00$ per share); linear risk with zero acceleration |

---

### Practical Application in Automated Bot Strategies

- **DTE Selection (0-7 DTE vs 30-45 DTE)**:
  Options with fewer than 7 days to expiration feature violent Gamma spikes near the strike. Trading bots avoid selling short premium under 14–21 DTE to avoid excessive Gamma risk, preferring 30–45 DTE cycles where Gamma remains benign.
- **Stop Losses on Credit Spreads**:
  Negative Gamma causes credit spread losses to mount exponentially once the short strike is breached. Bots execute disciplined delta/loss thresholds before short strikes go deep ITM.
- **Gamma Scalping**:
  Market makers dynamically buy underlying shares when stock falls and sell when stock rises to lock in profits while maintaining delta neutrality.
- **Broken Wing Butterflies (BWB)**:
  Asymmetric butterfly structures place wider out-of-the-money wings to mitigate upside Gamma risk, ensuring favorable payoff even on sharp bullish spikes.
