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

#### ANNUALIZED_EXTRINSIC_PCT
#### Calculation Formula and Implementation

The mathematical equation is:

$$\text{ANNUALIZED\_EXTRINSIC\_PCT} = \left( \frac{\text{Net Extrinsic Value}}{\text{Max Loss}} \right) \times \left( \frac{365}{\text{DTE}} \right) \times 100$$

Where:
- **`getNetExtrinsicValue()`**: Net time value of the option position.
- **`getMaxLoss()`**: Total capital at risk for the position.
- **`getDaysToExpiration()` (`DTE`)**: Number of calendar days until expiration.
- **`365.0 / DTE`**: Annualization scaling factor.
- **`100`**: Conversion from a decimal ratio to a percentage.
- **Guard Clause**: If $\text{Max Loss} \le 0$ or $\text{DTE} \le 0$, the method safely returns `0.0`.


#### How `getNetExtrinsicValue()` Is Calculated by Strategy

$2 \times \text{Extrinsic}_{\text{Long}} - \text{Extrinsic}_{\text{Short}}$ |


#### Detailed Walkthrough Example (ZEBRA Strategy)

##### Setup Parameters
- **Stock Price**: \$150.00
- **DTE**: 45 days
- **Long Leg (Buy 2 ITM Calls)**:
    - Strike: \$140.00
    - Ask Price: \$14.05 per share
    - Intrinsic Value: $\max(0, 150.00 - 140.00) = \$10.00$
    - Extrinsic Value per leg: $14.05 - 10.00 = \$4.05$
    - Total Long Extrinsic: $2 \times 4.05 = \$8.10$ per share
- **Short Leg (Sell 1 ATM Call)**:
    - Strike: \$150.00
    - Bid Price: \$8.05 per share
    - Intrinsic Value: $\max(0, 150.00 - 150.00) = \$0.00$
    - Extrinsic Value: \$8.05 per share

##### Step 1: Compute `netExtrinsicValue`
Using [`ZebraTrade.getNetExtrinsicValue()`](file:///c:/Projects/trading-bot/src/main/java/com/hemasundar/options/models/ZebraTrade.java#L30-L34):
$$\text{Net Extrinsic} = (4.05 \times 2) - 8.05 = 8.10 - 8.05 = \$0.05 \text{ per share}$$

Per contract block (100 shares):
$$\text{Net Extrinsic} = \$0.05 \times 100 = \$5.00$$

##### Step 2: Compute `maxLoss`
In [`ZebraStrategy`](file:///c:/Projects/trading-bot/src/main/java/com/hemasundar/options/strategies/ZebraStrategy.java#L196-L204), max loss equals the net debit paid:
$$\text{Net Debit} = ((14.05 \times 2) - 8.05) \times 100 = (28.10 - 8.05) \times 100 = 20.05 \times 100 = \$2,005.00$$
$$\text{Max Loss} = \$2,005.00$$

##### Step 3: Compute `ANNUALIZED_EXTRINSIC_PCT`
Applying [`TradeSetup.getAnnualizedNetExtrinsicValueToCapitalPercentage()`](file:///c:/Projects/trading-bot/src/main/java/com/hemasundar/options/models/TradeSetup.java#L30-L34):

1. **Extrinsic to Capital Ratio**:
   $$\frac{\text{Net Extrinsic}}{\text{Max Loss}} = \frac{5.00}{2005.00} \approx 0.00249376$$

2. **Annualization Factor**:
   $$\frac{365.0}{\text{DTE}} = \frac{365.0}{45} \approx 8.1111$$

3. **Annualized Percentage**:
   $$\text{ANNUALIZED\_EXTRINSIC\_PCT} = 0.00249376 \times 8.1111 \times 100 \approx 2.02\%$$

If a candidate has a net extrinsic of \$0.01 (\$1.00 total):
$$\text{ANNUALIZED\_EXTRINSIC\_PCT} = \left(\frac{1.00}{2005.00}\right) \times \left(\frac{365.0}{45}\right) \times 100 \approx 0.404\%$$
This candidate would satisfy a condition filter of `ANNUALIZED_EXTRINSIC_PCT <= 0.5`.

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