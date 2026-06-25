Option Greeks:
Positive Delta
Positive Theta
Negative VEGA -- profit when the IV crash
So try picking the trade, when the IV rank is high


The primary options strategy for acquiring a stock at a lower future price is a **Cash-Secured Put (CSP)**.

This strategy allows you to get paid to wait for the stock to drop to your preferred entry point.

### How It Works

Instead of placing a standard limit order to buy shares, you sell a put option at a strike price you are comfortable paying for the stock.

* **Collect Premium:** You receive cash (premium) immediately for selling the put.
* **Cash Requirement:** You must hold enough cash in your brokerage account to purchase 100 shares of the underlying stock at the strike price if the option is assigned.

### Outcomes at Expiration

| Market Scenario | What Happens | Result |
| --- | --- | --- |
| **Stock stays above Strike** | The put option expires worthless. | You keep 100% of the premium collected. You do not buy the stock. |
| **Stock drops below Strike** | You are assigned and obligated to buy 100 shares at the strike price. | You purchase the stock at your desired lower price, and keep the premium. |

### The Math

If the stock drops and you are assigned the shares, the premium you collected lowers your overall purchase price. The breakeven point can be calculated as:

$$Breakeven = Strike\_Price - Premium\_Received$$

> **Example:** If a stock is trading at $50 and you want to buy it at $45, you sell a $45 strike put and collect a $1.50 premium. If assigned, you buy the shares at $45, but your actual cost basis is $43.50.