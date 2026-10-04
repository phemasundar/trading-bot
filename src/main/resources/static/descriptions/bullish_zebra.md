# Bullish ZEBRA (Zero Extrinsic Back RAtio)

### Strategy Option Greeks
| Greek | Polarity | Description & Utility |
|---|---|---|
| **Delta (Δ)** | **Positive** | Emulates holding 100 shares of stock, moving dollar-for-dollar with the underlying. |
| **Gamma (Γ)** | **Positive** | Net positive gamma accelerates delta gains as the stock rallies. |
| **Theta (Θ)** | **Negative** | Near zero; the short option decay offsets the combined decay of the two long options. |
| **Vega (V)** | **Positive** | Position gains value if implied volatility rises, as the long options dominate. |

A ZEBRA is a stock replacement strategy designed to emulate the P&L of holding 100 shares, eliminating extrinsic value (time decay).

### How it works
This ratio spread involves buying multiple ITM long options and selling an ATM short option.
- **Sell 1** At-The-Money (ATM) Call (typically around 0.50 Delta)
- **Buy 2** In-The-Money (ITM) Calls (typically around 0.70+ Delta)

By balancing these strikes, the extrinsic value of the 1 short option offsets the combined extrinsic value of the 2 long options. This means theta (time decay) does not hurt you, and you essentially own 100 deltas of the stock for less buying power than buying 100 shares outright.

### Safe strategy
- zero extrinsic value

This strategy will work as a buying call option for short DTEs.

**Problem with Buying CALL option for short DTEs:**
Long CALL — Positive Delta, Negative Theta
Buy CALL price = Intrinsic value + Extrinsic value (Time/ Theta Decay)

As the DTE is smaller, the Time Decay/ Theta is more. Extrinsic value part in Option value will be lost more rapidly if the price doesn't move fast enough.

**How do Bullish Zebra fix this?**
Extrinsic value is almost Zero. So theta decay doesn't affect us.
Positive Delta

**Target DTE:** 20 – 50 days
close the trade at least **10 days before the expiry**.

### How the strategy filters are designed/ configured in the project


### Influence
https://www.youtube.com/watch?v=WF8mxhDRY08&list=PLsyOA1hM02gwv7ZaLQgTGgtFcCvU7B-1K&index=1&t=1900s

#### Trade Setup & Mechanics
The Zebra is a **back ratio spread** configured so that the extrinsic value of the options cancels out (washes)
- Bullish Setup (Long Stock Replacement):
  - Sell 1 $\times$ 50-delta call
  - Buy 2 $\times$ 70-delta calls
- Bearish Setup (Short Stock Replacement):
  - Sell 1 $\times$ 50-delta put
  - Buy 2 $\times$ 70-delta puts
By combining two 70-delta options (+140 or -140 delta) with one sold 50-delta option (-50 or +50 delta), the net delta approximates 90 to 100, mimicking the price movement of 100 shares of long or short stock.

#### Key Benefits & Characteristics
**Capital Efficiency:** Allows a trader to control 100 shares of long or short stock at a fraction of the cost of buying or shorting actual shares.
**Eliminates Theta Decay:** The strike selection washes out the extrinsic value of the options, removing theta decay drag.
**Keeps Option Leverage:** Preserves all the leverage and profit potential of long options without time working against you.

#### Timing & Expiration Guidelines
**Entry Timing:** Typically opened with 20 to 50 days to expiration (DTE).
**Exit Timing:** Should not be held past 10 days to expiration.

#### Risk Profile & Profit Targets
**Maximum Risk:** Defined risk setup—the only risk is the net debit paid to place the trade.
**Profit Potential:** Unlimited profit potential.
**Profit Target:** There is **no predefined percentage profit target** (unlike traditional short option trades). Because it behaves like stock with a 50-50 profit/loss profile, you manage and close the trade based on where you would exit if you held actual shares.

#### Ideal Use Case
**Who Should Use It:** Traders who want exposure to 100 shares of long or short stock while utilizing options leverage.
**Role in Portfolio:** Described as the **"hybrid club"** in a trader's bag—not meant for every trade, but an excellent tool to get out of a difficult position or replace expensive stock holdings.