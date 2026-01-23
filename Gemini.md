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

