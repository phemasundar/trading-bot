# ATR Screener

## Overview
The **ATR Screener** is a highly dynamic technical filter designed to identify stocks that have pulled back significantly relative to their natural daily volatility.

By utilizing the Average True Range (ATR), this screener natively adapts to the inherent volatility of each individual asset, avoiding the trap of hardcoded percentage or dollar drops that fail across different asset classes.

## Configuration

In `strategies-config.json`, the screener is registered under the `"screenerType": "ATR"` type.

### Example Configuration
```json
{
    "enabled": true,
    "alias": "ATR Pullback Screener",
    "screenerType": "ATR",
    "descriptionFile": "docs/screeners/atr_screener.md",
    "securitiesFile": "top100, portfolio, tracking, 2026, SPY, QQQ",
    "technicalFilters": {
        "AVERAGE_TRUE_RANGE": {
            "config": {
                "period": 14
            },
            "conditions": [
                "NATR >= 1.5",
                "PRICE_DROP_FROM_HIGH_5D >= 2 * ATR"
            ]
        }
    }
}
```

## Key Variables

- **`ATR` (Average True Range):** Represents the raw dollar amount of the asset's average daily movement (high to low) over the configured period (default 14 days).
- **`NATR` (Normalized Average True Range):** Represents the percentage of the asset's average daily movement relative to its current price `(ATR / Price) * 100`. This prevents expensive stocks from being inadvertently favored over lower-priced stocks with high relative volatility.
- **`PRICE_DROP_FROM_HIGH_<N>D`**: Calculates the exact dollar amount the asset has dropped from its `<N>`-day high.

## Mathematical Evaluation
The condition `"PRICE_DROP_FROM_HIGH_5D >= 2 * ATR"` is executed natively by the bot's mathematical evaluation engine as follows:
1. Calculates the Highest High over the last 5 days (`HIGH_5D`).
2. Measures the dollar drop: `HIGH_5D - CURRENT_PRICE`.
3. Verifies if that drop is greater than or equal to `2 * ATR`.

This guarantees the bot only triggers when the asset has pulled back *twice* its average daily range from its local peak, signaling a deep and potentially exhausted sell-off.
