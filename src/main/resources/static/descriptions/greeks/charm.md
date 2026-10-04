# Charm (Delta Decay)

Charm (also known as **Delta Decay** or $\text{DdeltaDtime}$) is a second-order cross-derivative Greek that measures the rate of change of an option's Delta ($\Delta$) with respect to the passage of time ($\tau$).

### Mathematical Formulation
$$\text{Charm} = \frac{\partial^2 V}{\partial S \partial t} = -\frac{\partial \Delta}{\partial \tau}$$

Under the Black-Scholes-Merton model for a standard European call option:
$$\text{Charm}_{\text{Call}} = -e^{-q\tau} \left[ q N(d_1) - N'(d_1) \left( \frac{r - q}{\sigma \sqrt{\tau}} - \frac{d_2}{2\tau} \right) \right]$$

For a standard European put option:
$$\text{Charm}_{\text{Put}} = e^{-q\tau} \left[ q N(-d_1) + N'(d_1) \left( \frac{r - q}{\sigma \sqrt{\tau}} - \frac{d_2}{2\tau} \right) \right]$$

---

### Core Mechanics & Interpretations

1. **Delta Bleed Without Price Movement**:
   Even if the underlying stock price does not move by a single penny, an option's Delta will naturally drift as expiration approaches:
   - **Out-of-the-Money (OTM) options**: Time erosion pulls Delta inexorably toward $0.00$.
   - **In-the-Money (ITM) options**: Time erosion pushes Delta inexorably toward $\pm 1.00$ (certainty of expiring ITM).
   - **At-the-Money (ATM) options**: Charm maintains equilibrium around $\pm 0.50$.

2. **Weekend & Expiration Pin Risk**:
   Charm accelerates drastically in the final days of an expiration cycle. A $0.20\Delta$ OTM short put will lose Delta daily purely due to Charm, working heavily in favor of option sellers.

3. **Dealer Re-Hedging Flows (The "Charm Effect")**:
   Because market makers must continuously balance their delta portfolios, predictable Charm decay causes systematic buying or selling of underlying assets toward the end of each week (particularly on expiration Fridays).

---

### Charm Drift Matrix

| Moneyness | Initial Delta | Effect of Time Passage (Charm) | Expiration Value |
| :--- | :--- | :--- | :--- |
| **Deep OTM** | $0.10 \Delta$ | Delta decays toward $0.00$ continuously | $0.00$ |
| **Slight OTM** | $0.30 \Delta$ | Delta decays toward $0.00$ continuously | $0.00$ |
| **At-the-Money (ATM)** | $0.50 \Delta$ | Delta stays relatively stable near $0.50$ | $0.00$ or $1.00$ at buzzer |
| **Slight ITM** | $0.70 \Delta$ | Delta drifts upward toward $1.00$ | $1.00$ |
| **Deep ITM** | $0.90 \Delta$ | Delta drifts upward toward $1.00$ | $1.00$ |

---

### Practical Application in Automated Bot Strategies

- **Credit Spread Win-Rate Acceleration**:
  When credit spreads are held beyond 21 DTE into the 7–14 DTE window, Charm actively bleeds away the Delta of the short OTM strike. An initial $0.20\Delta$ short put drops to $0.08\Delta$ without needing the underlying stock to rally, making it progressively harder for the strike to be breached.
- **Dynamic Hedge Adjustment**:
  Automated portfolio hedges that maintain delta neutrality must factor in Charm decay to avoid over-adjusting equity hedge positions as options approach expiration.
