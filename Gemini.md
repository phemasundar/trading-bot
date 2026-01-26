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

### Bug Fixes (2026-01-24)
- **Critical Logic Fix**: Fixed `LegFilter` using `||` instead of `&&` for delta checks, which was causing all trades to be rejected if any filter was configured.
- **Data Validation Fix**: Added sanity check in `LegFilter` to reject options with absolute Delta > 10, filtering out invalid API data (e.g., -999.00 delta).
- **Data Cleaning at Source**: Implemented `removeInvalidOptions()` in `OptionChainResponse` and integrated it into `ThinkOrSwinAPIs` to filter invalid options (abs(delta) > 10) immediately after API response parsing.

### Upper Breakeven Delta Filter (2026-01-25)
Added new filter capability for `BrokenWingButterflyStrategy` (and available for others in future):
- **Field**: `maxUpperBreakevenDelta` in `OptionsStrategyFilter`
- **Logic**: Calculates Upper BE Price `(Short Strike + Lower Wing Width - Net Debit)`, finds nearest strike, and verifies its delta is ≤ limit.
- **Goal**: Ensure the trade doesn't have too much risk if the price shoots up past the short strikes.

### Telegram Message Enhancement - Dual Break Evens (2026-01-25)
Updated Telegram alert format to support strategies with multiple break-even points:
- **Generic Support**: Updated `TradeSetup` interface with `getUpperBreakEvenPrice()` (default 0).
- **Dual Display**: `TelegramUtils` now checks if `Upper BE > 0` and is different from `Lower BE`. If so, it displays both.
  - Example: `BE: $86.91 (0.92%) | Upper BE: $95.50 (10.1%)`
- **Strategies Supported**: Automatically works for `BrokenWingButterfly` and `IronCondor`.

### MinTotalCredit Filter (2026-01-25)
Added explicitly named filter for minimum credit requirement (replacing the need for negative debit values):
- **New Field**: `minTotalCredit` in `OptionsStrategyFilter`.
- **Usage**: Set `"minTotalCredit": 0.5` to ensure trade generates at least $0.50 credit.
- **Implemented In**: `BrokenWingButterflyStrategy`.


## OptionsStrategyFilter Fix (2026-01-25)

Fixed an issue where debit trades were being rejected even when configured correctly.

### Problem
Primitive `double` fields in `OptionsStrategyFilter` (specifically `minTotalCredit`) defaulted to `0.0`. This implicitly enforced a `credit >= 0` check, blocking all debit trades (where credit is negative).

### Fix
- Changed filter fields to reference `Double` types:
  - `minTotalCredit`
  - `maxTotalCredit`
  - `maxTotalDebit`
  - `maxLossLimit`
- Updated `OptionsStrategyFilter` validation methods to handle `null` values as "no limit".

## Trade Sorting and Limiting (2026-01-25)

Implemented logic to sort trades by quality and limit the volume of Telegram alerts.

### Features
1.  **Sorting by Return on Risk**: All found trades are now sorted by `Return on Risk` in descending order before any other processing.
2.  **Configurable Limiting**: Added `maxTradesToSend` parameter to limits the number of trades sent to Telegram per strategy/symbol.
    *   Default: 30 trades
    *   Configurable via `strategies-config.json`
3.  **Top-N Selection**: Only the top N trades (after sorting) are sent to Telegram.
4.  **Full Console Logging**: All found trades are still logged to the console for analysis, regardless of the limit.

### Configuration
Add `maxTradesToSend` to your strategy implementation in `strategies-config.json`:
```json
{
  "strategyType": "PUT_CREDIT_SPREAD",
  "maxTradesToSend": 10,
  ...
}
```

### Files Modified
-   `StrategiesConfig.java`: Added `maxTradesToSend` to `StrategyEntry`.
-   `OptionsConfig.java`: Added `maxTradesToSend` field.
-   `StrategiesConfigLoader.java`: Updated to populate the new field.
-   `SampleTestNG.java`: Implemented matching logic (sort -> limit -> group -> send).

## Broken Wing Butterfly Default Filters (2026-01-25)

Added mandatory filters to the `BrokenWingButterflyStrategy` to ensure valid trade structure and risk profile.

### 1. Default Debit Filter
Ensures that the total debit paid for the strategy is strictly less than **half** the cost of buying the Long Call (Leg 1) alone.

- **Check**: `TotalDebit < (Leg1.Ask / 2)`
- **Rationale**: If the debit paid is too high relative to the long call, the risk profile is suboptimal.

### 2. Wing Width Ratio Filter
Ensures that the Upper Wing (Leg 2 to Leg 3) is not fundamentally larger than the Lower Wing (Leg 1 to Leg 2), preventing "inverted" or overly risky structures.

- **Check**: `UpperWingWidth <= (2 * LowerWingWidth)`
- **Rationale**: Keeps the butterfly structure balanced and prevents extreme tail risk scenarios.

### 3. Debit vs Price Filter
Ensures that the total cost of the trade (Total Debit) is less than the price of the underlying stock per share.

### 3. Debit vs Price Filter
Ensures that the total cost of the trade (Total Debit) is not excessive relative to the underlying stock price.

- **Check**: `TotalDebit < (UnderlyingPrice * Ratio)`
- **Rationale**: Prevents trades where the cost of the spread is excessive relative to the stock price.
- **Configuration**: configurable via `"priceVsMaxDebitRatio"` (Double) in JSON. e.g. `1.0` means max debit is 1x price. `0.5` means max debit is half price.

### Implementation
- Added `priceVsMaxDebitRatio` field to `OptionsStrategyFilter`.
- `BrokenWingButterflyStrategy` checks this field; if null, filter is skipped.
- `defaultDebitFilter()` and `wingWidthRatioFilter()` remain **hardcoded** and **mandatory**.
- Applied in the stream pipeline before configurable filters.
