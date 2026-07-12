# Technical Filter Math Expressions

## Overview

The trading bot now uses a single, centralized math-expression evaluator for all technical filters. Instead of hardcoded `if/else` checks per indicator, every technical condition is converted into a `MathExpression` at configuration-load time and evaluated by `MathExpressionEvaluator`.

This document explains the supported syntax, variables, operators, JSON configuration examples, and migration notes.

## Core Concepts

- **MathExpression** — a comparison with a left-hand variable, a relational operator, and a right-hand side (constant or variable). The right-hand side can optionally be scaled by a percentage.
- **MathExpressionParser** — parses strings like `RSI >= 30` or `PRICE >= SMA50` into `MathExpression` objects.
- **MathExpressionEvaluator** — evaluates a list of expressions against a value provider (the `ScreeningResult`).
- **ScreeningResult.getIndicatorValue(String)** — resolves any supported variable name to its numeric value.

## Supported Variables

| Variable | Value |
|----------|-------|
| `PRICE`, `CURRENT_PRICE` | Current closing price |
| `VOLUME` | Current volume |
| `RSI` | Current RSI value |
| `PREVIOUS_RSI` | Previous bar RSI value |
| `BB_LOWER` | Lower Bollinger Band |
| `BB_MIDDLE` | Middle Bollinger Band (SMA20) |
| `BB_UPPER` | Upper Bollinger Band |
| `SMA<N>` | Simple moving average for period N, e.g. `SMA20`, `SMA50`, `SMA200` |
| `VOLUME_SMA<N>` | Volume simple moving average for period N, e.g. `VOLUME_SMA20` |
| `HV_RANK` | Historical volatility rank (percentile) |
| `DROP_PCT` | Price drop percentage (used by PRICE_DROP / HIGH_52W_DROP screeners) |

## Supported Operators

| Operator | Symbol | Example |
|----------|--------|---------|
| Greater than or equal | `>=` | `RSI >= 30` |
| Less than or equal | `<=` | `RSI <= 70` |
| Greater than | `>` | `RSI > 70` |
| Less than | `<` | `RSI < 30` |
| Equal | `==` | `RSI == 50` |

## JSON Configuration

### Legacy Format Still Works

All existing JSON configurations continue to work. The loader transparently converts them into `MathExpression` objects.

```json
{
  "technicalFilters": {
    "RSI": { "config": "default", "condition": "BULLISH_CROSSOVER" },
    "BOLLINGER_BAND": { "config": "default", "condition": "LOWER_BAND" },
    "VOLUME": { "conditions": [">= 1000000"] },
    "SIMPLE_MOVING_AVERAGE": { "conditions": ["PRICE >= SMA50", "SMA50 >= SMA200"] },
    "HISTORICAL_VOLATILITY": { "conditions": [">= 25", "<= 75"] },
    "PRICE_DROP": { "config": { "lookbackDays": 21 }, "conditions": [">= 10"] }
  }
}
```

### New Direct Math Expression Format

You can also write conditions directly as math expressions. Any condition string that begins with a variable name is treated as a full expression.

```json
{
  "technicalFilters": {
    "RSI": { "config": "default", "conditions": ["RSI >= 30", "RSI <= 70"] },
    "BOLLINGER_BAND": { "config": "default", "conditions": ["PRICE <= BB_LOWER"] },
    "VOLUME": { "conditions": ["VOLUME >= 1000000", "VOLUME_SMA20 >= VOLUME_SMA50 * 90%"] },
    "SIMPLE_MOVING_AVERAGE": { "conditions": ["PRICE >= SMA50", "SMA50 >= SMA200"] },
    "HISTORICAL_VOLATILITY": { "conditions": ["HV_RANK >= 25"] },
    "PRICE_DROP": { "config": { "lookbackDays": 21 }, "conditions": ["DROP_PCT >= 10"] }
  }
}
```

### Mixed Example: Oversold Bounce Screener

```json
{
  "technicalScreeners": [
    {
      "enabled": true,
      "alias": "Oversold Bounce",
      "screenerType": "TECHNICAL",
      "securitiesFile": "top100",
      "technicalFilters": {
        "RSI": { "config": "default", "condition": "OVERSOLD" },
        "BOLLINGER_BAND": { "config": "default", "condition": "LOWER_BAND" },
        "VOLUME": { "conditions": ["VOLUME >= 500000", "VOLUME_SMA20 >= VOLUME_SMA50 * 90%"] },
        "SIMPLE_MOVING_AVERAGE": { "conditions": ["PRICE >= SMA200"] },
        "HISTORICAL_VOLATILITY": { "conditions": ["HV_RANK >= 25"] }
      }
    }
  ]
}
```

This produces the following internal `MathExpression` rules:

```
RSI < 30
PRICE <= BB_LOWER
VOLUME >= 500000
VOLUME_SMA20 >= VOLUME_SMA50 * 90%
PRICE >= SMA200
HV_RANK >= 25
```

### Price Drop Screener Example

```json
{
  "technicalScreeners": [
    {
      "enabled": true,
      "alias": "1-Month Drop >= 10%",
      "screenerType": "PRICE_DROP",
      "securitiesFile": "SPY",
      "technicalFilters": {
        "PRICE_DROP": { "config": { "lookbackDays": 21 }, "conditions": ["DROP_PCT >= 10"] }
      }
    }
  ]
}
```

## Advanced Expressions

### Variable-vs-Variable Comparisons

```json
{
  "SIMPLE_MOVING_AVERAGE": { "conditions": ["SMA50 >= SMA200"] }
}
```

### Percentage Scaling

Used mainly for volume expansion checks:

```json
{
  "VOLUME": { "conditions": ["VOLUME_SMA20 >= VOLUME_SMA50 * 90%"] }
}
```

This evaluates as:

```
VOLUME_SMA20 >= VOLUME_SMA50 * 0.9
```

### Custom RSI Range

```json
{
  "RSI": {
    "config": "default",
    "condition": { "type": "CUSTOM_RANGE", "min": 40, "max": 60 }
  }
}
```

Produces:

```
RSI >= 40
RSI <= 60
```

## How It Works at Runtime

1. `StrategiesConfigLoader.buildFilterChainFromMap()` reads `technicalFilters` from JSON.
2. Each filter entry is converted into one or more `MathExpression` objects.
3. The expressions are stored in `TechFilterConditions.filterExpressions`.
4. `TechnicalScreener.analyzeStock()` calculates all indicator values and stores them in `ScreeningResult`.
5. `TechnicalScreener.meetsAllCriteria()` calls `MathExpressionEvaluator.evaluateAll(filterExpressions, result::getIndicatorValue)`.
6. `MathExpressionEvaluator` resolves each variable via `ScreeningResult.getIndicatorValue()` and applies the relational operator.

## Backward Compatibility

- Legacy enum conditions (`OVERSOLD`, `LOWER_BAND`, `MIN_VOLUME`, etc.) are still supported and converted automatically.
- Legacy `NumericRule` objects for HV and Price Drop are still stored alongside `filterExpressions`.
- If `filterExpressions` is empty, `TechnicalScreener.meetsAllCriteria()` falls back to the legacy evaluation logic.

## Migration Guide

### Before

```json
"SIMPLE_MOVING_AVERAGE": {
  "conditions": ["PRICE_ABOVE_SMA50", "SMA50_ABOVE_SMA200"]
}
```

### After

```json
"SIMPLE_MOVING_AVERAGE": {
  "conditions": ["PRICE >= SMA50", "SMA50 >= SMA200"]
}
```

### Before

```json
"VOLUME": { "config": { "min": 1000000 } }
```

### After

```json
"VOLUME": { "conditions": ["VOLUME >= 1000000"] }
```

## Error Handling

- Invalid expressions throw `IllegalArgumentException` with the offending rule in the message.
- Missing indicator values cause the expression to evaluate to `false` (the stock is filtered out).
- Unknown variables return `null` and fail the expression safely.

## Testing

Unit tests for the new classes are located in:

- `src/test/java/com/hemasundar/technical/MathExpressionTest.java`
- `src/test/java/com/hemasundar/technical/MathExpressionEvaluatorTest.java`
- `src/test/java/com/hemasundar/utils/MathExpressionParserTest.java`

Run them with:

```bash
mvn test -Dtest=MathExpressionTest,MathExpressionEvaluatorTest,MathExpressionParserTest
```
