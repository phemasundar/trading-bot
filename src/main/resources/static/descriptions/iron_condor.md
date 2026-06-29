# Iron Condor

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Neutral** | Minimal directional exposure, expecting the stock to remain within a specific price range. |
| **Gamma (Γ)** | **Negative** | Fast price movements toward either the put or call wings accelerate losses. |
| **Theta (Θ)** | **Positive** | Time decay is the primary profit driver, eroding value from both sides daily. |
| **Vega (V)** | **Negative** | Position loses value if volatility spikes; profits from volatility contraction (IV crush). |

An Iron Condor is a neutral, non-directional options strategy. It aims to profit from low volatility, expecting the underlying stock to remain within a specific price range.

### How it works
An Iron Condor is essentially the combination of two credit spreads:
1. A **Put Credit Spread** below the current stock price.
2. A **Call Credit Spread** above the current stock price.

### Risk & Reward
* **Max Profit:** The combined net credit received upfront from both spreads.
* **Max Loss:** The width of the wider spread minus the total credit received. Because you cannot simultaneously lose on both the put and call side, your risk is capped to just one of the wings.
