# Long Call LEAPs

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Deep ITM long call moves almost dollar-for-dollar with the underlying stock price. |
| **Gamma (Γ)** | **Positive** | Accelerates Delta exposure toward 1.0 (equivalent to 100 shares) as stock price rises. |
| **Theta (Θ)** | **Negative** | Time decay hurts the buyer, though long DTE minimizes the daily decay speed. |
| **Vega (V)** | **Positive** | Implied volatility expansion inflates the option premium, boosting position value. |

A LEAP (Long-Term Equity Anticipation Securities) is simply an options contract that expires far in the future (generally over 1 year). Buying a deep in-the-money (ITM) long call LEAP is an alternative to buying 100 shares of the stock outright.

### How it works
- **Buy** a deep ITM Call (typically delta 0.70 to 0.90) expiring in 1+ years.
- By buying deep ITM, the option moves almost dollar-for-dollar with the stock (high delta).
- This provides essentially the same upside exposure as buying 100 shares, but for significantly less capital upfront, offering implicit leverage.

### Risk & Reward
* **Max Profit:** Technically unlimited as the stock price rises.
* **Max Loss:** The premium paid to buy the option contract. Max loss occurs if the stock drops to $0, or below the strike price at expiration.
