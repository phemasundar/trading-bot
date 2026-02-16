# Project Updates

## TestNG → Supabase Strategy Result Saving (2026-02-15)

Strategy results from TestNG runs are now saved to Supabase, updating the GitHub Pages dashboard. Previously only the Vaadin UI path saved results.

### Refactoring
- Extracted `Trade.fromTradeSetup(TradeSetup, String)` — shared static factory in `Trade.java`
- Extracted `StrategyResult.fromTrades(String, Map, long)` — shared static factory in `StrategyResult.java`
- `StrategyExecutionService` (Vaadin) and `SampleTestNG` (TestNG) both use these shared methods
- Removed ~150 lines of duplicated conversion code
- `SampleTestNG` has `initializeSupabase()` (reads env vars → `test.properties` fallback); returns null gracefully if not configured

---
## Static GitHub Pages Dashboard (2026-02-15)

Built a read-only static dashboard deployable to GitHub Pages that fetches and displays the latest strategy execution results from Supabase.

### Architecture
- **Separate from Vaadin app**: All static files live in `docs/` (HTML/CSS/JS only)
- **Supabase REST API**: Uses `@supabase/supabase-js` CDN to fetch `latest_strategy_results` table
- **GitHub Actions deployment**: `deploy-pages.yml` injects Supabase credentials from GitHub Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) during build

### Files Created
- `docs/index.html` — Main page with dark theme, Inter font, responsive layout
- `docs/style.css` — Dark theme matching Vaadin app design tokens
- `docs/app.js` — Supabase client, data fetching, collapsible cards, trade grids with ROR bars
- `.github/workflows/deploy-pages.yml` — GitHub Actions workflow using `actions/deploy-pages@v4`

### Features
- Collapsible strategy cards with trade count, execution time, and time-ago display
- Trade grid columns: Ticker | Type (legs) | Expiry (DTE) | Credit/Debit | Max Loss | Breakeven | ROR%
- Click-to-expand trade details
- Refresh button for manual data reload
- Mobile-responsive layout

### Prerequisites
- Enable RLS on `latest_strategy_results` table with a public SELECT policy
- Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` to GitHub repository secrets
- Enable GitHub Pages with "GitHub Actions" as the source

---

## Web UI - Stop Execution Button & Execution State Persistence (2026-02-15)

### Stop Button
- Added red Stop button next to Execute button during execution
- Calls `cancelExecution()` on `StrategyExecutionService`
- Cancellation happens between strategies (current strategy finishes first)

### Execution State Persistence
- Added `executionRunning` AtomicBoolean and `executionStartTimeMs` to `StrategyExecutionService`
- `MainView` checks execution state on init and restores progress bar after page refresh
- Elapsed timer polls `isExecutionRunning()` and auto-hides progress bar when done

### Files Modified
- `StrategyExecutionService.java`: Added `executionRunning`, `cancellationRequested`, `cancelExecution()`, `isCancellationRequested()`
- `MainView.java`: Added stop button, execution state check on init, cancellation polling in timer

---

## Web UI - Grid Expansion Fix (2026-02-15)

Fixed grid row expansion breaking the UI by replacing Vaadin's `setItemDetailsRenderer` (which has an overlap bug with `setAllRowsVisible(true)`) with an external `Div` details panel below the grid.

### Problem
Vaadin Grid's built-in detail row renderer overlaps data rows when used with `setAllRowsVisible(true)`, regardless of theme variants like `LUMO_WRAP_CELL_CONTENT`.

### Solution
- Removed `setItemDetailsRenderer` and `setDetailsVisibleOnClick`
- Added external `Div` panel below the grid within a `VerticalLayout` wrapper
- Panel shows a header with ticker symbol (`▶ TICKER — Trade Details`)
- Selected row is highlighted via `grid.select()`
- Click same row to toggle details off

### Files Modified
- `MainView.java`: Replaced detail renderer with external panel approach

---

## Web UI - Strategy-Specific Trade Display (2026-02-13)

Updated trade grid to display strategy-specific leg details instead of generic shortStrike/longStrike columns.

### Changes
- **`TradeLegDTO.java`** [NEW] — DTO for individual trade legs (action, optionType, strike, delta, premium)
- **`Trade.java`** — Replaced `shortStrike`/`longStrike` with `List<TradeLegDTO> legs` and `String tradeDetails`
- **`StrategyExecutionService.java`** — Updated `convertToTradeDTO()` to preserve all legs and generate detail text
- **`MainView.java`** — Grid now shows:
  - **Legs column**: Condensed summary (e.g., "SELL 450 PUT / BUY 440 PUT")
  - **Expandable details**: Click any row to see full trade details (delta, premium, BE, RoR)

### Strategy Leg Examples
| Strategy | Legs |
|----------|------|
| PCS/CCS | SELL PUT / BUY PUT |
| Iron Condor | 4 legs (2 puts + 2 calls) |
| BWB | 3 legs |
| Long Call LEAP | BUY CALL (single leg) |

---

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

## Volatility Filter (2026-01-29)

Added volatility filtering capability to `LegFilter` for better control over option selection based on implied volatility.

### Features
- **Min/Max Volatility**: Added `minVolatility` and `maxVolatility` fields to `LegFilter`
- **Automatic Validation**: Volatility filters are automatically validated in the comprehensive `passes(OptionData leg)` method
- **Null-Safe**: Like other filters, null values mean no restriction

### Implementation
- Added `minVolatility` and `maxVolatility` fields to `LegFilter.java`
- Updated `passes()` method to validate volatility against configured thresholds
- Volatility data is read from `OptionData.volatility` field in option chain response

### Usage Example
```json
{
  "strategyType": "PUT_CREDIT_SPREAD",
  "filter": {
    "shortLeg": {
      "maxDelta": 0.20,
      "minVolatility": 0.20,
      "maxVolatility": 0.80,
      "minOpenInterest": 500
    }
  }
}
```

### Files Modified
- `LegFilter.java`: Added volatility fields and validation logic

### Benefits
- **Volatility Control**: Filter out options with extreme or insufficient volatility
- **Better Trade Quality**: Select options that meet volatility preferences
- **JSON Configurable**: Easy to adjust volatility constraints via configuration

## Comma-Separated Securities Files (2026-01-29)

Added support for specifying multiple securities files in a single strategy configuration using comma-separated values.

### Features
- **Multiple Files**: Specify comma-separated file names in `securitiesFile` field
- **Automatic Deduplication**: Securities from all files are combined and duplicates are automatically removed
- **Order Preservation**: Uses `LinkedHashSet` to maintain insertion order while ensuring uniqueness
- **Backward Compatible**: Single file names still work as before

### Implementation
- Added `parseSecuritiesFromFiles()` method to `StrategiesConfigLoader.java`
- Splits comma-separated file names and loads securities from each
- Combines all securities into a unique list using `LinkedHashSet`
- Logs warnings for missing or empty files

### Usage Example
```json
{
  "strategyType": "PUT_CREDIT_SPREAD",
  "filterType": "CreditSpreadFilter",
  "filter": {
    "targetDTE": 30,
    "maxLossLimit": 1000,
    "shortLeg": {
      "maxDelta": 0.20
    }
  },
  "securitiesFile": "portfolio,tracking,2026"
}
```

The strategy will now run on all unique securities from:
- `portfolio.yaml`
- `tracking.yaml`
- `2026.yaml`

### Files Modified
- `StrategiesConfigLoader.java`: Added `parseSecuritiesFromFiles()` helper method

### Benefits
- **Flexibility**: Easily combine different security lists without manual merging
- **Maintainability**: Keep security files organized by category (portfolio, tracking, year, etc.)
- **Reusability**: Share common securities across strategies while allowing customization

## LEAP Cost Savings Filter (2026-01-31)

Added a new filter to `LONG_CALL_LEAP` strategy to filter trades based on minimum cost savings percentage compared to buying stock directly.

### Features
- **Minimum Cost Savings**: Added `minCostSavingsPercent` field to `LongCallLeapFilter`
- **Percentage-Based Filtering**: Filter passes only if the option route is at least X% cheaper than buying stock
- **Automatic Calculation**: Cost savings percentage is calculated automatically during trade evaluation
- **Optional Filter**: If not specified, all trades pass through (backward compatible)

### Implementation
- Added `minCostSavingsPercent` field to `LongCallLeapFilter.java`
- Added `costSavingsPercent` to `LeapCandidate` record in `LongCallLeapStrategy.java`
- Created `costSavingsFilter()` predicate that validates cost savings percentage
- Integrated filter into the trade evaluation pipeline

### Calculation
The cost savings percentage is calculated as:
```
costSavingsPercent = ((costOfBuyingStock - costOfOptionBuying) / costOfBuyingStock) * 100
```

Where:
- `costOfOptionBuying` = Extrinsic value + Dividend amount
- `costOfBuyingStock` = Margin interest + Interest earnings on extra money

### Usage Example
```json
{
  "strategyType": "LONG_CALL_LEAP",
  "filterType": "LongCallLeapFilter",
  "filter": {
    "minDTE": 330,
    "minCostSavingsPercent": 15.0,
    "longCall": {
      "minDelta": 0.4,
      "minOpenInterest": 100
    }
  }
}
```

This configuration will only show trades where the option route is at least 15% cheaper than buying the stock directly.

### Files Modified
- `LongCallLeapFilter.java`: Added `minCostSavingsPercent` field
- `LongCallLeapStrategy.java`: Added cost savings calculation and filter

### Benefits
- **Better Trade Selection**: Only show trades with significant cost advantages
- **Risk Management**: Avoid trades where the option route doesn't provide sufficient savings
- **Customizable Threshold**: Set different minimum savings requirements per strategy configuration

## Historical Volatility Filter (2026-02-01)

Added a `minHistoricalVolatility` filter to all options strategies. Filters out symbols with historical volatility below the specified threshold, with caching to minimize API calls.

### Features
- **Common Filter**: Added `minHistoricalVolatility` field to `OptionsStrategyFilter` (applies to all strategies)
- **Automatic Calculation**: Calculates annualized historical volatility from 1-year daily price data using log returns
- **Intelligent Caching**: Caches both price history and calculated volatility in-memory per execution
- **Fail-Open**: If calculation fails, allows trade to proceed (logged as warning)

### Implementation
- **PriceHistoryCache**: Thread-safe singleton cache with `HistoricalData` POJO storing both `PriceHistoryResponse` and calculated `annualizedVolatility`
- **VolatilityCalculator**: Static utility class calculating volatility using log returns method
  - Formula: `stdDev(log returns) × √252 × 100%`
  - Uses 252 trading days for annualization
- **AbstractTradingStrategy**: Added `checkHistoricalVolatility()` method called at start of `findTrades()`
- Symbol is skipped entirely if volatility is below threshold (no option chain fetched)

### Calculation Method
```
1. Get 1-year daily closing prices
2. Calculate log returns: ln(price[i] / price[i-1])
3. Calculate standard deviation of returns
4. Annualize: stdDev × √252
5. Convert to percentage
```

### Usage Example
```json
{
  "strategyType": "PUT_CREDIT_SPREAD",
  "filter": {
    "minDTE": 25,
    "maxDTE": 50,
    "minHistoricalVolatility": 25.0,
    "shortLeg": {
      "maxDelta": 0.20
    }
  }
}
```

This configuration only trades symbols with at least 25% annualized historical volatility.

### Cache Behavior
- **First Symbol Check**: Fetches price history from API, calculates HV, caches both
- **Subsequent Checks**: Uses cached HV value (no API call, no recalculation)
- **Scope**: In-memory, per execution run only
- **Statistics**: Cache hit/miss stats available via `PriceHistoryCache.getInstance().getStats()`

### Files Created
- `PriceHistoryCache.java`: Cache with `HistoricalData` POJO
- `VolatilityCalculator.java`: Volatility calculation utility

### Files Modified
- `OptionsStrategyFilter.java`: Added `minHistoricalVolatility` field
- `AbstractTradingStrategy.java`: Added `checkHistoricalVolatility()` method and integrated into `findTrades()`

### Benefits
- **Better Symbol Selection**: Focus on volatile stocks with more option premium
- **Reduced API Calls**: Single price history call per symbol per run
- **No Redundant Calculations**: HV calculated once and cached
- **Universal Application**: Works with all strategies (spreads, butterflies, LEAPs, etc.)

### Critical Bug Fix (2026-02-01)
Fixed variance calculation to use **sample variance (N-1)** instead of population variance (N):

**Problem**: Original implementation used population standard deviation (`variance = sumSquaredDiff / N`), which systematically underestimated volatility. For example, SOFI showed ~23% HV vs actual ~50%+.

**Solution**: Applied Bessel's correction by using sample standard deviation (`variance = sumSquaredDiff / (N-1)`). This is the standard approach in finance when working with sample data.

**Impact**: Historical volatility values now align with industry sources (e.g., alphaquery.com). The fix approximately doubles the calculated volatility, making the filter work as intended.

**Files Modified**:
- `VolatilityCalculator.java`: Changed variance calculation on line 98

## Supabase Integration for IV Data Collection (2026-02-03)

Added Supabase as an additional database option alongside Google Sheets for storing daily Implied Volatility (IV) data.

### Features
- **Dual Database Support**: Both Google Sheets and Supabase can run simultaneously
- **Enable/Disable Configuration**: Individual database control via `test.properties`
- **Automatic Connection Testing**: Supabase connection verified during setup
- **Retry Logic**: Exponential backoff for rate limiting and transient errors
- **Environment Variable Support**: CI/CD-friendly configuration

### Architecture
- **SupabaseService**: REST API client using OkHttp
- **Dual-Write Logic**: `IVDataCollectionTest` saves to all enabled databases
- **UPSERT Support**: Supabase automatically handles duplicate entries (symbol + date)
- **Telegram Notifications**: Summary shows which databases were used

### Database Configuration
```properties
# Enable/disable individual databases (at least one must be enabled)
google_sheets_enabled=true
supabase_enabled=false

# Supabase Configuration (required if supabase_enabled=true)
supabase_url=https://YOUR_PROJECT_ID.supabase.co
supabase_anon_key=YOUR_PUBLISHABLE_KEY
```

### Table Schema
```sql
CREATE TABLE public.iv_data (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL,
    date DATE NOT NULL,
    strike NUMERIC(10, 2),
    dte INTEGER,
    expiry_date TEXT,
    put_iv NUMERIC(10, 4),
    call_iv NUMERIC(10, 4),
    underlying_price NUMERIC(10, 2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_symbol_date UNIQUE (symbol, date)
);
```

### Files Created
- `SupabaseService.java`: REST API client for Supabase
- `SUPABASE_SETUP_GUIDE.md`: Complete setup documentation

### Files Modified
- `IVDataCollectionTest.java`: Added dual-database support with enable/disable flags
- `test.properties`: Added database configuration options
- `pom.xml`: Added OkHttp dependency
- `README.md`: Added IV Data Tracking section
- `Gemini.md`: This entry

### Benefits
- **PostgreSQL Power**: Superior querying and analytics vs Google Sheets
- **Free Tier**: 500MB database, 2GB bandwidth/month
- **Auto-Generated REST API**: Easy integration with Java
- **Built-in Dashboard**: View and query data via Supabase web interface
- **Backup/Redundancy**: Run both databases simultaneously for data redundancy

### Setup Instructions
See `SUPABASE_SETUP_GUIDE.md` for complete setup instructions including:
- Account creation
- Database table creation
- API key configuration
- Security setup (Row Level Security)
- Connection testing

### CI/CD Configuration

The code automatically works in GitHub Actions without `test.properties`:
- Reads configuration from environment variables first
- Falls back to `test.properties` for local development
- Uses sensible defaults if neither are available

**GitHub Secrets Required:**
- `GOOGLE_SPREADSHEET_ID` (if Google Sheets enabled)
- `GOOGLE_SERVICE_ACCOUNT_JSON` (if Google Sheets enabled)  
- `SUPABASE_URL` (if Supabase enabled)
- `SUPABASE_ANON_KEY` (if Supabase enabled)

Optional environment variables:
- `GOOGLE_SHEETS_ENABLED` (default: true)
- `SUPABASE_ENABLED` (default: false)

### Recent Fixes (2026-02-03)

**UPSERT Logic:**
Fixed 409 duplicate key error by adding PostgREST `on_conflict` parameter to properly handle updates when same symbol+date exists.

**Logging:**
Changed database save operations from DEBUG to INFO level for better visibility.

**GitHub Actions:**
Added `GOOGLE_SHEETS_ENABLED` and `SUPABASE_ENABLED` environment variables to workflow for full control over database selection in CI/CD.

## MaxLossLimit Filter Fix for LONG_CALL_LEAP (2026-02-07)

Fixed a critical bug where the `maxLossLimit` filter was not being applied to LONG_CALL_LEAP strategies.

### Problem
The `maxLossLimit` parameter was configured in `strategies-config.json` but was never applied in the filter chain. This allowed trades with losses exceeding the configured limit to pass through.

### Root Cause
- `LongCallLeapStrategy` calculated `maxLoss` (line 126) but **did not filter** based on it
- Other strategies like `CallCreditSpreadStrategy` properly used `filter.passesMaxLoss()`
- The filter predicate method was completely missing

### Fix
- **Added `maxLossFilter()` predicate** to `LongCallLeapStrategy.java`
  - Uses existing `filter.passesMaxLoss()` method from `OptionsStrategyFilter`
  - Calculates max loss as `callPremium * 100`
- **Added filter to pipeline** between `premiumLimitFilter` and `costEfficiencyFilter`
- **Fixed `LongCallLeapTopNStrategy`** to preserve `maxLossLimit` during progressive relaxation
  - Added `.maxLossLimit(filter.getMaxLossLimit())` to relaxed filter builder
  - Ensures risk limits remain enforced even when quality filters are relaxed

### Impact
Strategies configured with `maxLossLimit` (e.g., 10000) will now correctly reject trades where:
- Max Loss = Call Premium × 100 > maxLossLimit

### Files Modified
- `LongCallLeapStrategy.java`: Added `maxLossFilter()` method and applied in stream pipeline
- `LongCallLeapTopNStrategy.java`: Added `maxLossLimit` preservation in relaxed filter

## Common Filter Abstraction (2026-02-07)

Refactored common filter logic from individual strategies into reusable helper methods in `AbstractTradingStrategy`.

### Problem
Common filters like `maxLossLimit` and `minReturnOnRisk` were duplicated across multiple strategy implementations:
- Each strategy had its own `maxLossFilter()` and `minReturnOnRiskFilter()` methods
- `IronCondorStrategy` had hardcoded filter checks instead of using filter helper methods
- `BrokenWingButterflyStrategy` was missing `minReturnOnRisk` filter entirely
- No centralized mechanism to ensure consistent filter application

### Solution
Created protected helper methods in `AbstractTradingStrategy` that strategies can call:

```java
protected <T> Predicate<T> commonMaxLossFilter(
    OptionsStrategyFilter filter,
    Function<T, Double> maxLossExtractor)

protected <T> Predicate<T> commonMinReturnOnRiskFilter(
    OptionsStrategyFilter filter,
    Function<T, Double> profitExtractor,
    Function<T, Double> maxLossExtractor)
```

### Changes Made

**AbstractTradingStrategy.java**:
- Added `commonMaxLossFilter()` - generic helper for maxLossLimit validation
- Added `commonMinReturnOnRiskFilter()` - generic helper for minReturnOnRisk validation
- Both methods use generics and function extractors for flexibility

**CallCreditSpreadStrategy.java**:
- Replaced custom `maxLossFilter()` with `.filter(commonMaxLossFilter(filter, CallSpreadCandidate::maxLoss))`
- Replaced custom `minReturnOnRiskFilter()` with `.filter(commonMinReturnOnRiskFilter(filter, CallSpreadCandidate::netCredit, CallSpreadCandidate::maxLoss))`
- Deleted 10 lines of duplicate code

**PutCreditSpreadStrategy.java**:
- Same refactoring as CallCreditSpreadStrategy
- Deleted 10 lines of duplicate code

**IronCondorStrategy.java**:
- Replaced hardcoded `if (maxRisk > filter.getMaxLossLimit())` with `if (!filter.passesMaxLoss(maxRisk))`
- Replaced hardcoded `requiredProfit` calculation with `if (!filter.passesMinReturnOnRisk(totalCredit, maxRisk))`
- Eliminated 3 lines of manual filter logic

**BrokenWingButterflyStrategy.java**:
- Added missing `minReturnOnRisk` filter to filter chain
- Now validates: `commonMinReturnOnRiskFilter(filter, BWBCandidate::maxProfit, BWBCandidate::maxLoss)`

### Benefits
- **DRY Principle**: Eliminated 20+ lines of duplicate filter code
- **Consistency**: All strategies now use the same filter validation logic
- **Centralized**: Common filter logic lives in one place (AbstractTradingStrategy)
- **Type-Safe**: Generic methods work with any candidate type
- **Maintainable**: Future filter changes only need to update helper methods
- **Explicit**: Strategies explicitly call common filters - no magic/hidden behavior

### Verification
- ✅ Build successful: `mvn clean compile -DskipTests` 
- ✅ All strategies compile without errors
- ✅ No breaking changes to existing functionality

## Strategy Alias Feature (2026-02-08)

Added optional `alias` field to strategy configurations, allowing custom display names in Telegram message headers while maintaining backward compatibility with the existing `StrategyType` enum system.

### Features
- **Optional Alias Field**: Add `alias` to any strategy in `strategies-config.json` for custom display names
- **Backward Compatible**: Strategies without alias automatically use `StrategyType` display name
- **Telegram Integration**: Aliases appear in Telegram message headers (📊 Custom Name)
- **Flexible Naming**: Distinguish multiple instances of the same strategy with descriptive names

### Implementation
- **StrategiesConfig.StrategyEntry**: Added `alias` field
- **OptionsConfig**: Added `alias` field and updated `getName()` method with fallback logic
- **StrategiesConfigLoader**: Updated builder to pass alias through
- **Smart Fallback**: `getName()` returns alias if present and non-blank, otherwise falls back to `StrategyType.toString()`

### Usage Examples
```json
{
  "enabled": true,
  "alias": "Short-Term PCS - Portfolio",
  "strategyType": "PUT_CREDIT_SPREAD",
  "filterType": "CreditSpreadFilter",
  "filter": { ... }
}
```

**Telegram Message Header:**
```
📊 Short-Term PCS - Portfolio
💰 AAPL @ $150.00
📅 Expiry: 2026-03-20 (30 DTE)
```

### Files Modified
- `StrategiesConfig.java`: Added `alias` field to `StrategyEntry`
- `OptionsConfig.java`: Added `alias` field and updated `getName()` logic
- `StrategiesConfigLoader.java`: Added alias to builder chain
- `strategies-config.json`: Added example aliases to demonstrate feature
- `STRATEGIES_CONFIG_GUIDE.md`: Updated with alias field documentation

### Benefits
- **Clarity**: Easily distinguish between multiple instances of the same strategy type
- **Flexibility**: Name strategies based on portfolio structure, risk tolerance, or securities list
- **Organization**: Better Telegram message organization with meaningful headers
- **No Breaking Changes**: Existing configs without alias continue to work exactly as before


## GitHub Actions Workflow Updates for Develop Branch (2026-02-08)

Updated GitHub Actions workflows to run scheduled jobs against the develop branch instead of main.

### Changes Made
- **ci.yml**: Added `ref: develop` to checkout step
- **daily-iv-collection.yml**: Added `ref: develop` to checkout step

### Workflow Configuration
Both workflows now:
1. **Run on schedule** - Maintains existing cron schedules
2. **Checkout develop branch** - Uses `ref: develop` in checkout action
3. **Manual dispatch** - Can still be triggered manually from GitHub UI

### Files Modified
- `.github/workflows/ci.yml`: Checkout step now uses develop branch
- `.github/workflows/daily-iv-collection.yml`: Checkout step now uses develop branch

### Benefits
- **Development Testing**: Scheduled runs test the latest development code
- **Early Detection**: Issues are caught in develop before merging to main
- **No CI Noise**: Workflows only run on schedule, not on every push

