# Technical Call Credit Spread (CCS)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Negative** | Profits as the underlying stock price declines or stays below the short call strike. |
| **Gamma (Γ)** | **Negative** | Directional risk increases if the stock rallies rapidly toward the short strike. |
| **Theta (Θ)** | **Positive** | Time decay works in your favor, eroding the premium of the options sold. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |

A Call Credit Spread configured to automatically trigger based on underlying technical momentum indicators (like RSI and Bollinger Bands).

### Technical Rules
- **Sell** a Call Credit Spread to collect premium when the underlying stock reaches an extreme technical state (e.g. Overbought RSI crossing bearishly back downwards, or touching the Upper Bollinger Band).

### Risk & Reward
* **Max Profit:** The net credit received upfront.
* **Max Loss:** Difference between strikes minus the net credit received.
