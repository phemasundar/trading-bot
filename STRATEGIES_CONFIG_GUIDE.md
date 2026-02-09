# Strategies Configuration Guide

Complete reference for configuring `strategies-config.json` for the trading bot.

---

## Table of Contents
1. [File Structure](#file-structure)
2. [Strategy Types](#strategy-types)
3. [Filter Types & Parameters](#filter-types--parameters)
4. [Long Call LEAP Top N Strategy](#long-call-leap-top-n-strategy)
5. [Configuration Examples](#configuration-examples)

---

## File Structure

The `strategies-config.json` file is located at: `src/main/resources/strategies-config.json`

### Basic Structure
```json
{
  "strategies": [
    {
      "enabled": true,
      "alias": "Custom Display Name (Optional)",
      "strategyType": "STRATEGY_NAME",
      "filterType": "FilterClassName",
      "securitiesFile": "file-name-without-extension",
      "filter": {
        // Filter parameters here
      }
    }
  ]
}
```

### Common Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enabled` | Boolean | Yes | Enable/disable this strategy |
| `alias` | String | No | Custom display name for Telegram messages (falls back to strategyType if not specified) |
| `strategyType` | String | Yes | Strategy identifier (see [Strategy Types](#strategy-types)) |
| `filterType` | String | Yes | Filter class name (must match strategy) |
| `securitiesFile` | String | Yes | Name of YAML file in `securities/` folder (without `.yaml`) |
| `filter` | Object | Yes | Strategy-specific filter configuration |

---

## Strategy Types

### Available Strategies

| Strategy Type | Filter Type | Description |
|--------------|-------------|-------------|
| `LONG_CALL_LEAP` | `LongCallLeapFilter` | Standard LEAP strategy - all qualifying trades |
| `LONG_CALL_LEAP_TOP_N` |  `LongCallLeapFilter` | Top N LEAP trades per stock with progressive relaxation |
| `BULLISH_BROKEN_WING_BUTTERFLY` | `BullishBrokenWingButterflyFilter` | Broken wing butterfly spreads |
| `RSI_BOLLINGER_BULL_PUT` | `RsiBollingerFilter` | Bull put spreads based on RSI/Bollinger |
| `RSI_BOLLINGER_BEAR_CALL` | `RsiBollingerFilter` | Bear call spreads based on RSI/Bollinger |

---

## Filter Types & Parameters

### Common Filter Parameters (All Strategies)

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minDTE` | Integer | - | Minimum days to expiration |
| `maxDTE` | Integer | `Integer.MAX_VALUE` | Maximum days to expiration |
| `targetDTE` | Integer | `0` | Specific target DTE (0 = use min/max range) |
| `ignoreEarnings` | Boolean | `true` | Skip options near earnings dates |
| `maxLossLimit` | Double | - | Maximum potential loss per trade |
| `minReturnOnRisk` | Integer | - | Minimum return on risk percentage |
| `marginInterestRate` | Double | `6.0` | Annual margin interest rate (%) |
| `savingsInterestRate` | Double | `10.0` | Annual savings interest rate (%) |

---

## Long Call LEAP Top N Strategy

### Complete Configuration Reference

```json
{
  "enabled": true,
  "strategyType": "LONG_CALL_LEAP_TOP_N",
  "filterType": "LongCallLeapFilter",
  "securitiesFile": "bullish",
  "filter": {
    // ===== TRADE COUNT =====
    "topTradesCount": 5,
    
    // ===== HARD FILTERS (Priority 0 - NEVER Relaxed) =====
    "minDTE": 330,
    "maxDTE": 2147483647,
    "ignoreEarnings": true,
    "marginInterestRate": 6.0,
    "savingsInterestRate": 10.0,
    
    "longCall": {
      "minDelta": 0.4,
      "maxDelta": 1.0,
      "minOpenInterest": 100
    },
    
    // ===== SOFT FILTERS (Relaxed Progressively) =====
    "maxOptionPricePercent": 40.0,
    "maxCAGRForBreakEven": 10.0,
    "minCostSavingsPercent": 10.0,
    
    // ===== OPTIONAL FILTERS =====
    "minCostEfficiencyPercent": 90.0,
    
    // ===== PRIORITY CONFIGURATION =====
    "relaxationPriority": [
      "maxCAGRForBreakEven",
      "maxOptionPricePercent",
      "minCostSavingsPercent"
    ],
    
    "sortPriority": [
      "daysToExpiration",
      "costSavingsPercent",
      "optionPricePercent",
      "breakevenCAGR"
    ]
  }
}
```

### LongCallLeapFilter Parameters

#### 🔒 Hard Filters (Never Relaxed)

| Parameter | Type | Default | Hard? | Description |
|-----------|------|---------|-------|-------------|
| `minDTE` | Integer | - | YES | Minimum days to expiration (defines LEAP) |
| `maxDTE` | Integer | `MAX` | YES | Maximum days to expiration |
| `ignoreEarnings` | Boolean | `true` | YES | Skip options near earnings |
| `marginInterestRate` | Double | `6.0` | YES | Margin borrowing cost |
| `savingsInterestRate` | Double | `10.0` | YES | Interest earned on savings |
| `longCall.minDelta` | Double | - | YES | Minimum delta value |
| `longCall.maxDelta` | Double | - | YES | Maximum delta value |
| `longCall.minOpenInterest` | Integer | - | YES | Minimum open interest (liquidity) |

#### ☁️ Soft Filters (Progressively Relaxed)

| Parameter | Type | Default | Relax Level | Description |
|-----------|------|---------|-------------|-------------|
| `maxCAGRForBreakEven` | Double | null | Level 1 | Maximum breakeven CAGR (%) |
| `maxOptionPricePercent` | Double | null | Level 2 | Maximum option price as % of stock |
| `minCostSavingsPercent` | Double | null | Level 3 | Minimum cost savings vs buying stock |

**Note**: If not specified, these filters are not applied (null = no constraint).

#### ➕ Optional Filters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minCostEfficiencyPercent` | Double | null | Option cost must be ≤ this % of stock cost |

#### 📊 Priority Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `topTradesCount` | Integer | `3` | Number of trades to return per stock |
| `relaxationPriority` | String[] | **none** | Order of filter relaxation (**OPT-IN**) |
| `sortPriority` | String[] | See below | Order of trade sorting |

**Default sortPriority**:
```json
["daysToExpiration", "costSavingsPercent", "optionPricePercent", "breakevenCAGR"]
```

> [!IMPORTANT]
> **Relaxation is OPT-IN**: If `relaxationPriority` is **not specified** or is **empty**, the strategy will return **ONLY strict results** without any progressive relaxation, even if fewer than N trades are found.

**To enable progressive relaxation**, explicitly set `relaxationPriority`:
```json
{
  "relaxationPriority": [
    "maxCAGRForBreakEven",      // Relax first
    "maxOptionPricePercent",     // Then this
    "minCostSavingsPercent"      // Last resort
  ]
}
```

---

## Configuration Examples

### Example 1: Conservative LEAP Strategy
```json
{
  "enabled": true,
  "alias": "Conservative Tech LEAPs",
  "strategyType": "LONG_CALL_LEAP_TOP_N",
  "filterType": "LongCallLeapFilter",
  "securitiesFile": "tech-stocks",
  "filter": {
    "topTradesCount": 3,
    "minDTE": 365,
    "maxOptionPricePercent": 30.0,
    "maxCAGRForBreakEven": 8.0,
    "minCostSavingsPercent": 15.0,
    "longCall": {
      "minDelta": 0.5,
      "minOpenInterest": 200
    }
  }
}
```
**Use Case**: High-quality LEAPs only. Deep ITM, strict cost requirements.

### Example 2: Aggressive Discovery
```json
{
  "enabled": true,
  "strategyType": "LONG_CALL_LEAP_TOP_N",
  "filterType": "LongCallLeapFilter",
  "securitiesFile": "growth-stocks",
  "filter": {
    "topTradesCount": 5,
    "minDTE": 330,
    "longCall": {
      "minDelta": 0.3,
      "minOpenInterest": 50
    }
    // No soft filters = maximum discovery
  }
}
```
**Use Case**: Find as many options as possible. Progressive relaxation will find 5 trades per stock.

### Example 3: Custom Priorities (Prefer Longer LEAPs)
```json
{
  "enabled": true,
  "strategyType": "LONG_CALL_LEAP_TOP_N",
  "filterType": "LongCallLeapFilter",
  "securitiesFile": "dividend-stocks",
  "filter": {
    "topTradesCount": 5,
    "minDTE": 365,
    "maxOptionPricePercent": 35.0,
    "minCostSavingsPercent": 12.0,
    
    "sortPriority": [
      "daysToExpiration",
      "breakevenCAGR",
      "costSavingsPercent",
      "optionPricePercent"
    ],
    
    "relaxationPriority": [
      "minCostSavingsPercent",
      "maxOptionPricePercent"
    ],
    
    "longCall": {
      "minDelta": 0.4,
      "minOpenInterest": 100
    }
  }
}
```
**Use Case**: 
- Prefer longest DTE first
- Relax cost savings before price percentage
- Never relax CAGR (not in relaxationPriority)

### Example 4: Price-Conscious Strategy
```json
{
  "enabled": true,
  "strategyType": "LONG_CALL_LEAP_TOP_N",
  "filterType": "LongCallLeapFilter",
  "securitiesFile": "value-stocks",
  "filter": {
    "topTradesCount": 3,
    "minDTE": 330,
    "maxOptionPricePercent": 25.0,
    "maxCAGRForBreakEven": 12.0,
    
    "sortPriority": [
      "optionPricePercent",
      "costSavingsPercent",
      "daysToExpiration",
      "breakevenCAGR"
    ],
    
    "longCall": {
      "minDelta": 0.45,
      "minOpenInterest": 150
    }
  }
}
```
**Use Case**: Prioritize cheapest options first, regardless of DTE.

---

## How Relaxation Works

### Progressive Relaxation Flow

1. **Strict Filters**: Apply all configured filters
   - If ≥ N trades found → return top N
   - If < N trades found → proceed to relaxation

2. **Level 1**: Relax first filter in `relaxationPriority`
   - Combine with strict results
   - If ≥ N trades → return top N
   - Otherwise continue

3. **Level 2**: Also relax second filter
   - Combine all results
   - If ≥ N trades → return top N
   - Otherwise continue

4. **Level 3**: Also relax third filter
   - Return best N from all combined results

### Example Scenario

**Config**: `topTradesCount: 5`, `minCostSavingsPercent: 10`

| Step | Filters Applied | Trades Found | Total | Action |
|------|----------------|--------------|-------|--------|
| Strict | All filters | 2 | 2 | < 5, continue |
| Level 1 | Relax CAGR | +1 | 3 | < 5, continue |
| Level 2 | + Relax price % | +2 | 5 | Got 5, done! |

**Result**: Returns 5 trades, all still meeting:
- ✅ minDTE ≥ 330
- ✅ minDelta ≥ 0.4
- ✅ minOpenInterest ≥ 100

---

## Sort Priority Fields

### Valid Sort Fields

| Field Name | Direction | Description |
|------------|-----------|-------------|
| `daysToExpiration` | Descending ↓ | Longer expirations ranked higher |
| `costSavingsPercent` | Descending ↓ | Higher savings ranked higher |
| `optionPricePercent` | Ascending ↑ | Lower prices ranked higher |
| `breakevenCAGR` | Ascending ↑ | Lower CAGRs ranked higher |

### Sorting Examples

**Default** (Prefer longer LEAPs):
```json
["daysToExpiration", "costSavingsPercent", "optionPricePercent", "breakevenCAGR"]
```

**Cost-focused**:
```json
["costSavingsPercent", "optionPricePercent", "breakevenCAGR", "daysToExpiration"]
```

**Low breakeven priority**:
```json
["breakevenCAGR", "daysToExpiration", "costSavingsPercent", "optionPricePercent"]
```

---

## Best Practices

### 1. **Start Conservative**
Begin with strict filters and progressively relax based on results.

### 2. **Always Set Hard Filters**
- `minDTE`: Define your LEAP threshold (typically 330-365 days)
- `minDelta`: Define ITM/ATM level (0.4-0.6 recommended)
- `minOpenInterest`: Ensure liquidity (100+ recommended)

### 3. **Use relaxationPriority Wisely**
Order from "least important" to "most important":
- First filter = relaxed first (if needed)
- Last filter = relaxed last (only if desperate)

### 4. **Customize sortPriority**
Match your investment philosophy:
- **Long-term hold**: Prioritize `daysToExpiration`
- **Cost-conscious**: Prioritize `optionPricePercent`
- **Growth-focused**: Prioritize `breakevenCAGR`

### 5. **Test with Different topTradesCount**
- `topTradesCount: 1-3` → Very selective, only highest quality
- `topTradesCount: 5-10` → More diversification
- Watch relaxation logs to see which levels trigger

---

## Troubleshooting

### Getting Too Few Trades?
1. **Lower soft filters**: Remove or increase thresholds
2. **Reduce minDelta**: Allow more OTM options
3. **Reduce minOpenInterest**: Accept lower liquidity
4. **Check relaxation logs**: See which filters block trades

### Getting Low-Quality Trades?
1. **Add soft filters**: Set strict CAGR, price %, savings
2. **Increase minDelta**: Stay deeper ITM
3. **Customize relaxationPriority**: Keep important filters strict

### All Trades Have Same DTE?
1. **Check sortPriority**: Ensure `daysToExpiration` is first
2. **Verify stock has multiple LEAP expirations available**

---

## Additional Resources

- [Implementation Plan](./implementation_plan.md): Technical design details
- [Walkthrough](./walkthrough.md): Code changes and examples
- [Config Guide](./config_guide.md): Quick reference with scenarios
