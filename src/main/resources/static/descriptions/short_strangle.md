# Short Strangle

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Neutral** | Minimal directional exposure, expecting the stock to remain within a specific price range. |
| **Gamma (Γ)** | **Negative** | Fast price movements toward either the put or call wings accelerate losses. |
| **Theta (Θ)** | **Positive** | Time decay is the primary profit driver, eroding value from both sides daily. |
| **Vega (V)** | **Negative** | Profits from a drop in implied volatility. Best opened when IV Rank is elevated. |

A Short Strangle is a neutral, non-directional options strategy. It aims to profit from high volatility contraction and time decay, expecting the underlying stock to remain within a specific price range.

### How it works
A Short Strangle involves selling two out-of-the-money (OTM) options on the same underlying stock with the same expiration date:
1. Sell an **OTM Put** below the current stock price.
2. Sell an **OTM Call** above the current stock price.

### Risk & Reward
* **Max Profit:** The combined net credit received upfront from selling both options. This occurs if the stock price remains between the two strike prices at expiration.
* **Max Loss:** Theoretically unlimited on the upside (because of the naked call) and substantial on the downside (if the stock falls to zero). 

### When to use
This strategy is best used when **Implied Volatility (IV) is high** (e.g., IV Rank >= 50). The goal is to capture the elevated premium and benefit from a subsequent drop in volatility (IV crush).

### Breakeven Points
* **Lower Breakeven:** `Put Strike - Total Premium Received`
* **Upper Breakeven:** `Call Strike + Total Premium Received`
