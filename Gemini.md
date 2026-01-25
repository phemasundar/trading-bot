# Project Updates

## Filter Refactoring - Object-Oriented Approach

### Overview
Refactored the filter system to use an Object-Oriented approach with strategy-specific filter classes. This provides better scalability, type safety, and clearer semantics for leg-specific filtering.

### New Filter Hierarchy

```
OptionsStrategyFilter (base - common filters)
    ├── CreditSpreadFilter (for Put/Call Credit Spreads, Iron Condor)
    │       ├── shortLeg: LegFilter
    │       └── longLeg: LegFilter
    │
    ├── BrokenWingButterflyFilter (Call or Put variants)
    │       ├── leg1Long: LegFilter
    │       ├── leg2Short: LegFilter
    │       └── leg3Long: LegFilter
    │
    └── LongCallLeapFilter
            └── longCall: LegFilter
```

### New Classes Created
- `LegFilter.java` - Reusable filter for any single leg (minDelta, maxDelta, etc.)
- `CreditSpreadFilter.java` - For Put/Call Credit Spreads and Iron Condor
- `BrokenWingButterflyFilter.java` - For Broken Wing Butterfly strategy
- `LongCallLeapFilter.java` - For LEAP strategy

### Modified Classes
- `OptionsStrategyFilter.java` - Now base class using `@SuperBuilder`
- `CallCreditSpreadStrategy.java` - Uses `CreditSpreadFilter`
- `PutCreditSpreadStrategy.java` - Uses `CreditSpreadFilter`
- `IronCondorStrategy.java` - Uses `CreditSpreadFilter`
- `LongCallLeapStrategy.java` - Uses `LongCallLeapFilter`
- `BrokenWingButterflyStrategy.java` - Uses `BrokenWingButterflyFilter`
- `SampleTestNG.java` - Updated all filter configurations
- `SampleTestNG1.java` - Updated all filter configurations

### Key Features
1. **Optional Filters**: All leg filters are optional. If null, no restriction is applied.
2. **Named Fields**: Each leg has a named filter field (e.g., `leg1Long`, `leg2Short`)
3. **Type Safety**: Compile-time type checking for filter classes
4. **Helper Methods**: `LegFilter` includes null-safe helper methods like `passesMinDelta()`, `passesMaxDelta()`

### Usage Example
```java
.filter(BrokenWingButterflyFilter.builder()
    .targetDTE(45)
    .maxLossLimit(1000)
    .leg1Long(LegFilter.builder().minDelta(0.5).build())
    .leg2Short(LegFilter.builder().maxDelta(0.2).build())
    // leg3Long not set - no filter applied
    .build())
```

---

## Telegram Technical Screener Message Enhancement (2026-01-22)

Enhanced the Telegram message for technical screener alerts to include all available technical values:

### Previous Message Content:
- Price, RSI, BB Lower, MA20, MA50

### New Message Content:
- **Price** - Current stock price
- **Volume** - Formatted as K/M for readability (e.g., 1.5M, 500K)
- **RSI** - Current and previous values, with status indicators:
  - ⬆️ CROSSOVER (bullish)
  - ⬇️ CROSSOVER (bearish)
  - 🔴 OVERSOLD
  - 🟢 OVERBOUGHT
- **Bollinger Bands** - Upper, Middle, Lower (with ✓ if price touching)
- **Moving Averages** - MA20, MA50, MA100, MA200 (with "(below)" indicator)

### Files Modified:
- `TelegramUtils.java` - Updated `formatScreeningResult()` method and added `formatVolume()` helper

---

## Telegram Message Splitting (2026-01-22)

Implemented automatic message splitting in `TelegramUtils` to handle Telegram's 4096 character limit.

### Features:
- **Automatic Splitting**: Long messages are automatically split into multiple parts
- **Smart Break Points**: Prefers splitting at paragraph breaks (`\n\n`), then newlines (`\n`), then spaces
- **Part Indicators**: Multi-part messages show `(1/3)`, `(2/3)`, etc.
- **Rate Limiting**: 100ms delay between message parts to avoid Telegram rate limits
- **Common Functionality**: Applied to all Telegram messages (alerts, screeners, etc.)

### Implementation:
- `splitMessage(message, maxLength)` - Splits message respecting max length
- `findSplitPoint(text, maxLength)` - Finds best break point (paragraph > newline > space)
- `sendSingleMessage(botToken, chatId, message)` - Sends individual message part

### Configuration:
- `MAX_MESSAGE_LENGTH = 4000` (slightly below Telegram's 4096 limit for HTML escaping overhead)

---

## Externalized Strategy & Screener Configuration to JSON (2026-01-24)

Moved all hardcoded filter configurations and screeners from `SampleTestNG.java` to a single JSON file.

### Configuration Files
- `strategies-config.json` - Contains ALL configurations:
  - Options strategies (9 entries)
  - Technical screeners (3 entries)
  - Technical indicator settings
  - Technical filter presets
- `StrategiesConfigLoader.java` - Unified loader with `load()` for strategies and `loadScreeners()` for screeners
- `StrategiesConfig.java` - Root POJO with inner classes (`StrategyEntry`, `ScreenerEntry`, `TechnicalFilterConfig`, etc.)
- `FilterType.java` - Enum for type-safe filter deserialization

### JSON Structure
```json
{
  "optionsStrategies": [
    {
      "enabled": true,
      "strategyType": "PUT_CREDIT_SPREAD",
      "filterType": "CreditSpreadFilter",
      "filter": { ... },
      "securitiesFile": "portfolio",
      "technicalFilter": { ... }
    }
  ],
  "technicalScreeners": [
    {
      "enabled": true,
      "screenerType": "RSI_BB_BULLISH_CROSSOVER",
      "conditions": {
        "rsiCondition": "BULLISH_CROSSOVER",
        "bollingerCondition": "LOWER_BAND",
        "minVolume": 1000000
      }
    }
  ],
  "technicalIndicators": { "rsiPeriod": 14, ... },
  "technicalFilters": { "oversold": { ... }, "overbought": { ... } }
}
```

### Key Features
- **Unified Config**: Both strategies AND screeners in single `strategies-config.json`
- **Enabled Flags**: Each strategy/screener has an `enabled` flag
- **POJO-based Parsing**: Uses Jackson's automatic `ObjectMapper.readValue()`
- **Factory Methods**: `StrategyType.createStrategy()` and `StrategiesConfigLoader.loadScreeners()`

### IronCondorFilter (2026-01-24)
Added `IronCondorFilter` to support independent put and call short leg filters:
```json
{
    "filterType": "IronCondorFilter",
    "filter": {
        "putShortLeg": { "maxDelta": 0.15 },
        "callShortLeg": { "maxDelta": 0.20 }
    }
}
```
- Allows different `maxDelta` values for put vs call short legs
- Backward compatible: `CreditSpreadFilter` still works with shared `shortLeg`

### Strategy Modularization (2026-01-24)
Refactored options strategies to use **Java Streams and Lambdas** instead of nested for-loops.
- **Declarative Pipelines**: Strategies now use `.filter()` and `.map()` chains.
- **Candidate Records**: Intermediate `TradeCandidate` records hold calculation logic (maxLoss, netCredit, etc).
- **Refactored Strategies**: `BrokenWingButterflyStrategy`, `PutCreditSpreadStrategy`, `CallCreditSpreadStrategy`.

### Hybrid Filter Approach (2026-01-24)
Moved validation logic to filter classes for better reusability and testability.

#### OptionsStrategyFilter
Added reusable validation methods:
- `passesMaxLoss(double maxLoss)` - Checks if max loss is within limit
- `passesDebitLimit(double debit)` - Checks if total debit is within limit  
- `passesCreditLimit(double credit)` - Checks if total credit is within limit
- `passesMinReturnOnRisk(double profit, double maxLoss)` - Checks if profit meets minimum return on risk

#### LegFilter
Consolidated delta checking with **null-safe instance and static methods**:
- Instance methods handle null field values (`minDelta`, `maxDelta`)
- **Comprehensive `passes(OptionData leg)` method validates ALL fields**: delta, premium, volume, open interest
- Static helpers handle null LegFilter objects:
  - `passesMinDelta(LegFilter, double)` - Null-safe min delta check
  - `passesMaxDelta(LegFilter, double)` - Null-safe max delta check
  - `passes(LegFilter, double)` - Null-safe delta filter (both min and max)
  - `passes(LegFilter, OptionData)` - **Comprehensive null-safe filter (all fields)**

#### Strategy Updates
All strategies now use comprehensive `LegFilter.passes(filter, leg)` method:
- `BrokenWingButterflyStrategy`
- `PutCreditSpreadStrategy` 
- `CallCreditSpreadStrategy`
- `LongCallLeapStrategy`
- `IronCondorStrategy` (via Put/Call composition)

#### Benefits
- **Zero null checks in strategies** - All null handling is in `LegFilter`
- **Future-proof** - Adding new filter fields (premium, volume, etc.) to JSON config automatically validates them
- **DRY principle** - No duplicated validation logic
- **Cleaner strategy code** - Single method call validates everything

### Deleted Files
- `runtime-config.json` - No longer needed (all config in strategies-config.json)
- `runtimeConfig` path from `FilePaths.java`

### Modified Filter POJOs
Added `@NoArgsConstructor` and `@JsonIgnoreProperties(ignoreUnknown = true)` to:
- `OptionsStrategyFilter.java`, `CreditSpreadFilter.java`, `LongCallLeapFilter.java`, `BrokenWingButterflyFilter.java`, `LegFilter.java`

