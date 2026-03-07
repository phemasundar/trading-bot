# Bullish ZEBRA (Zero Extrinsic Back Ratio)

A ZEBRA is a stock replacement strategy designed to emulate the P&L of holding 100 shares, while defining risk and completely eliminating extrinsic value (time decay).

### How it works
This ratio spread involves buying multiple ITM long options and selling an ATM short option.
- **Sell 1** At-The-Money (ATM) Call (typically around 0.50 Delta)
- **Buy 2** In-The-Money (ITM) Calls (typically around 0.70+ Delta)

By balancing these strikes, the extrinsic value of the 1 short option offsets the combined extrinsic value of the 2 long options. This means theta (time decay) does not hurt you, and you essentially own 100 deltas of the stock for less buying power than buying 100 shares outright.

### Risk & Reward
* **Max Profit:** Unlimited to the upside.
* **Max Loss:** The actual net debit paid for the structure.
