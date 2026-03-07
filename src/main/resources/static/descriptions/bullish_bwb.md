# Bullish Broken Wing Butterfly (BWB)

A Broken Wing Butterfly is an options strategy designed to have a very high probability of profit while eliminating risk on one side of the trade (typically the downside for a bullish BWB).

### How it works
Unlike a symmetrical butterfly where the "wings" (long options) are equidistant from the "body" (short options), a broken wing butterfly skips a strike on the unpaid side, creating an asymmetrical risk profile.

- **Buy 1** ITM Call (Lower Strike)
- **Sell 2** OTM Calls (Middle Strike)
- **Buy 1** further OTM Call (Upper Strike, but skipping a standard interval width)

When structured for a net credit, this guarantees you will not lose money if the stock crashes, but retains a "tent" of maximum profit if it pins the middle strike, while carrying risk to the upside if the stock rallies too hard.
