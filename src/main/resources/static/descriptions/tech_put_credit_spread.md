# Technical Put Credit Spread (PCS)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Profits as the underlying stock price rises or stays above the short put strike. |
| **Gamma (Γ)** | **Negative** | Directional risk increases if the stock drops rapidly toward the short strike. |
| **Theta (Θ)** | **Positive** | Time decay works in your favor, eroding the premium of the options sold. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |

A Put Credit Spread configured to automatically trigger based on underlying technical momentum indicators (like RSI and Bollinger Bands).

### Technical Rules
- **Sell** a Put Credit Spread to collect premium when the underlying stock reaches an extreme technical state (e.g. Oversold RSI crossing bullishly back upwards, or touching the Lower Bollinger Band).

### Risk & Reward
* **Max Profit:** The net credit received upfront.
* **Max Loss:** Difference between strikes minus the net credit received.
