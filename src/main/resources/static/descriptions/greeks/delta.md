# Delta (Δ)

Delta ($\Delta$) is the primary first-order Greek measuring the rate of change of an option's theoretical value relative to a \$1.00 change in the price of the underlying asset.

### Mathematical Formulation
$$\Delta = \frac{\partial V}{\partial S}$$

Where:
- $V$ is the theoretical price of the option contract.
- $S$ is the spot price of the underlying asset.

For standard European options under the Black-Scholes-Merton model:
- **Call Option**: $\Delta_{\text{Call}} = e^{-q\tau} N(d_1)$ (bounded between $0.00$ and $+1.00$)
- **Put Option**: $\Delta_{\text{Put}} = -e^{-q\tau} N(-d_1)$ (bounded between $-1.00$ and $0.00$)

---

### Core Mechanics & Interpretations

1. **Directional Sensitivity**:
   If an underlying stock moves up by \$1.00, a call option with a Delta of $+0.50$ is expected to gain approximately \$0.50 in value, while a put with a Delta of $-0.30$ is expected to lose \$0.30.

2. **Hedge Ratio & Share Equivalence**:
   One standard option contract controls 100 shares. Multiplying Delta by 100 yields the equivalent number of shares:
   - $+0.50 \Delta$ Call = Long 50 shares of stock.
   - $-0.30 \Delta$ Put = Short 30 shares of stock.
   - To delta-hedge a short call with $+0.60 \Delta$, a market maker purchases 60 shares of underlying stock.

3. **Probability Proxy**:
   In practical quantitative trading, absolute Delta $|\Delta|$ serves as a quick heuristic for the market-implied risk-neutral probability that the option will expire in-the-money (ITM):
   - $0.16 \Delta \approx 16\%$ probability of expiring ITM (roughly 1 standard deviation OTM).
   - $0.30 \Delta \approx 30\%$ probability of expiring ITM.
   - $0.50 \Delta \approx 50\%$ probability of expiring ITM (At-the-Money).

---

### Delta Exposure by Position Type

| Position | Delta Polarity | Range | Sensitivity Behavior |
| :--- | :--- | :--- | :--- |
| **Long Call** | Positive ($+\Delta$) | $0.00$ to $+1.00$ | Gains value when underlying rises; approaches $+1.00$ deep ITM |
| **Long Put** | Negative ($-\Delta$) | $-1.00$ to $0.00$ | Gains value when underlying drops; approaches $-1.00$ deep ITM |
| **Short Call** | Negative ($-\Delta$) | $-1.00$ to $0.00$ | Collects premium; loses value if underlying spikes above strike |
| **Short Put** | Positive ($+\Delta$) | $0.00$ to $+1.00$ | Collects premium; loses value if underlying plunges below strike |
| **Long Stock** | Positive ($+\Delta$) | $+1.00$ per share | Linear $+1.00$ delta exposure per share (\$100 per 100 shares) |

---

### Practical Application in Automated Bot Strategies

- **Credit Spreads (PCS / CCS)**:
  Selling credit spreads typically targets short strikes between $0.16\Delta$ and $0.25\Delta$ (~75% to 84% probability of expiring OTM), balancing risk-reward with statistical edge.
- **Iron Condors**:
  Market-neutral condors sell equidistant put and call wings (e.g. $0.15\Delta$ Short Put and $0.15\Delta$ Short Call) to create a net zero-delta position upon inception ($\Delta_{\text{Net}} \approx 0.00$).
- **LEAPs & ZEBRA Stock Replacement**:
  Deep ITM Long Calls ($0.80\Delta$ to $0.90\Delta$) provide equity exposure at a fraction of capital with high delta efficiency and minimal theta drag.
- **Dynamic Delta Rebalancing**:
  Automated bots monitor net portfolio Delta to trigger hedge adjustments or profit exits when directional drift exceeds predefined risk bands.
