# Database Schema & Supabase Configuration

## Feature: Technical Filters in Execute Strategy UI + Filter Details on Dashboard (2026-07-04)

Added full visibility of applied Technical Filters in both the Execute Strategy page and the Options Dashboard Filter Details panel.

### Issue 1 Fixed: Technical Filters Missing on Execute Strategy Page

Added a dedicated **"Technical Filters"** collapsible card section in `execute.html` with inputs for:

- **RSI Condition** (dropdown: OVERSOLD, BULLISH_CROSSOVER, OVERBOUGHT, BEARISH_CROSSOVER)
- **Bollinger Band Condition** (dropdown: LOWER_BAND, UPPER_BAND)
- **Min Volume** (number)
- **HV Min/Max %** (Historical Volatility bounds)
- **Price Drop Min %**
- **Moving Average Rules** (free-text: `PRICE_ABOVE_SMA50, SMA50_ABOVE_SMA200`)

The `executeCustom()` function in `app.js` now collects these via `data-tech-filter` / `data-tech-field` attributes and packages them into a `technicalFilters` map in the POST body, sent to `/api/execute/custom`. The backend `StrategyController` already parses this into a `TechnicalFilterChain`.

### Issue 2 Fixed: Tech Filters Missing from Filter Details

Added `technicalFilterSummary` as a serialized field in `OptionsStrategyFilter`. `StrategyExecutionService.executeStrategy()` now populates this field (via `TechFilterConditions.getSummary()`) before building the `StrategyResult`, so the summary is persisted in the Supabase `filter_config` JSON blob and shown in the **"Filter Details"** section of each strategy card on the Options Dashboard.

### Config Page + Template Cards

The Config Viewer (`config.html`) and Execute Strategy template cards now also call `renderTechFiltersGrid()` to display the `technicalFilters` block from `strategies-config.json`.

### Architecture

| File                                | Change                                                                                                                                                                                                                                                                     |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`execute.html`**                  | Added "Technical Filters" collapsible card with RSI, BB, Volume, HV, Price Drop, MA rule inputs                                                                                                                                                                            |
| **`app.js`**                        | Added `renderTechFiltersGrid()` helper; updated `executeCustom()` to collect `[data-tech-filter]` inputs; updated `renderFilterGrid()` to render `technicalFilterSummary` prominently; updated `renderConfig()` and `renderStrategyTemplates()` to show `technicalFilters` |
| **`OptionsStrategyFilter.java`**    | Added `technicalFilterSummary` field (serialized into `filterConfig` JSON blob)                                                                                                                                                                                            |
| **`StrategyExecutionService.java`** | Populates `filter.technicalFilterSummary` from `TechFilterConditions.getSummary()` before building `StrategyResult`                                                                                                                                                        |

---

## Feature: Greek Exposure Pill Labels on Strategy Cards (2026-06-28)

Added four colored Greek exposure pill labels (Δ Delta, Γ Gamma, Θ Theta, V Vega) to every strategy result card on the Options Dashboard and Execute Strategy page. Each pill is color-coded based on the strategy's configured Greek polarity. Also added a standardized Greeks details table (explaining polarity and utility) to the top of all strategy description markdown files.

### Color Coding

| Value      | Color             |
| ---------- | ----------------- |
| `positive` | Green (`#22c55e`) |
| `negative` | Red (`#ef4444`)   |
| `neutral`  | Muted gray        |

### How It Works

Greek polarity is configured in `strategies-config.json` per strategy under a new `"greeks"` object. The data flows through the full stack and is persisted as part of the `filterConfig` JSON blob in Supabase.

### Architecture

| File                                 | Change                                                                                                                            |
| ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| **`strategies-config.json`**         | Added `"greeks": { "delta": "...", "gamma": "...", "theta": "...", "vega": "..." }` to all 17 option strategy entries             |
| **`StrategiesConfig.StrategyEntry`** | Added `private Map<String, String> greeks` field                                                                                  |
| **`OptionsConfig.java`**             | Added `private final Map<String, String> greeks` field                                                                            |
| **`OptionsStrategyFilter.java`**     | Added `private java.util.Map<String, String> greeks` field — auto-serialized into the `filterConfig` JSON blob stored in Supabase |
| **`StrategiesConfigLoader.java`**    | Passes `entry.getGreeks()` into both `filter.setGreeks()` and the `OptionsConfig` builder                                         |
| **`StrategyController.java`**        | Adds `greeks` to the `/api/strategies` response map                                                                               |
| **`app.js`**                         | Added `renderGreeksPills()` helper; reads `cfg.greeks` from `filterConfig` and renders pills in `buildResultCard()`               |
| **`style.css`**                      | Added `.greek-pills`, `.greek-pill`, `.greek-positive`, `.greek-negative`, `.greek-neutral` CSS classes                           |

### Greek Conventions by Strategy

| Strategy        | Δ Delta  | Γ Gamma  | Θ Theta  | V Vega   |
| --------------- | -------- | -------- | -------- | -------- |
| PCS / Short Put | positive | negative | positive | negative |
| CCS             | negative | negative | positive | negative |
| Iron Condor     | neutral  | negative | positive | negative |
| Long Call LEAP  | positive | positive | negative | positive |
| Bullish BWB     | positive | negative | positive | negative |
| Bullish ZEBRA   | positive | positive | negative | positive |

---

## Bug Fix: Return on Risk CAGR Display on Dashboards (2026-06-21)

Resolved an issue where the Return on Risk CAGR was only displayed on the Execute Strategy page, but was missing on the local Options Dashboard screen and the static GitHub Pages dashboard screen.

### Features

- **Local Dashboard Fallback:** Added a client-side calculation fallback to the local `app.js` table rendering. If `returnOnRiskCAGR` is missing from the database record payload (e.g., for older strategy execution runs stored in Supabase), it is computed dynamically on the fly from the raw `returnOnRisk` and `dte` values. This ensures CAGR is always visible on the Options Dashboard screen for all runs.
- **Sorting Fallback:** Updated the sorting logic (`handleTableSort`) to use the same on-the-fly CAGR calculation so that sorting by the ROR column behaves consistently even on older data.
- **GitHub Pages Dashboard Support:** Enabled ROR CAGR rendering on the static GitHub Pages dashboard (`docs/app.js`) by updating the `renderROR` helper function to accept and display the CAGR next to the ROR progress bar, with the same dynamic calculation fallback.
- **Breakeven CAGR on GitHub Pages:** Added Breakeven CAGR support to `renderBreakeven` in `docs/app.js` to match the local app.

### Architecture

| File                                   | Change                                                                                                                                                            |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`src/main/resources/static/app.js`** | Added on-the-fly fallback calculation for ROR CAGR in `buildTradeTable` and `handleTableSort`                                                                     |
| **`docs/app.js`**                      | Updated `createTradeGrid` to calculate/pass CAGR to `renderROR`, fixed `colspan` to `8` in details row, added CAGR rendering in `renderROR` and `renderBreakeven` |

---

## UI Enhancement: Return on Risk CAGR in Results Table (2026-06-05)

Updated the frontend and backend to natively compute, display, and sort by the Return on Risk CAGR alongside the raw ROR percentage.

### Features

- **Backend Metric & Serialization:** Added `getReturnOnRiskCAGR()` as a default method to the `TradeSetup` interface, computing annualized CAGR for every trade based on its Days to Expiration (DTE). Added the `returnOnRiskCAGR` field to the `Trade` DTO class and mapped it in `Trade.fromTradeSetup(TradeSetup, String)` to guarantee the metric is serialized inside the Supabase JSON payload and API responses.
- **UI Display:** The "ROR%" column now explicitly displays the raw return percentage followed by the annualized CAGR in brackets (e.g., `12.5% (150.0% CAGR)`).
- **Intelligent Sorting:** Clicking the "ROR%" column header will now prioritize sorting by the `returnOnRiskCAGR` metric if available, seamlessly falling back to `maxReturnOnRiskPercentage` or `returnOnRisk` for strategy setups missing this data (e.g. 0 DTE or missing max loss values).

### Architecture

| File                  | Change                                                                                                                                                               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`TradeSetup.java`** | Added `default Double getReturnOnRiskCAGR()` calculating `((1 + rawRoR)^(365/DTE)) - 1`                                                                              |
| **`Trade.java`**      | Declared `private Double returnOnRiskCAGR` field and set `.returnOnRiskCAGR(setup.getReturnOnRiskCAGR())` in `fromTradeSetup` builder                                |
| **`app.js`**          | Updated table rendering inside `buildTradeTable()` to format the ROR cell with the new dual metric, and updated `handleTableSort()` to sort using `returnOnRiskCAGR` |

## Bug Fix: Short Put Strategy Filters Ignored on Custom Execution (2026-06-04)

Fixed a bug where leg-specific filters (e.g., `maxDelta`, `minDelta`) for the **Short Put** strategy were silently ignored when executing from the Execute Strategy screen (Custom Execution), and subsequently failed to populate when clicking "Load Filters" on the execution result card.

### Root Cause

When executing a predefined strategy from the Dashboard, `StrategiesConfigLoader` uses `filterType: "CreditSpreadFilter"` explicitly from the JSON to instantiate a `CreditSpreadFilter` object, which natively includes the `shortLeg` field.

However, Custom Executions via `POST /api/execute/custom` use `FilterParser.buildFilter()` which instantiates the filter object based on a `switch` block evaluating the `StrategyType`. The `SHORT_PUT` enum was completely missing from this switch statement. Consequently, the parser fell back to instantiating the base `OptionsStrategyFilter`, which does **not** have a `shortLeg` nested object. The short leg constraints were silently discarded during deserialization, resulting in the strategy running with no short leg filter constraints, and no short leg data saved to the Supabase JSON payload for UI reloading.

### Fix

Added `case SHORT_PUT:` to the `CreditSpreadFilter` block in `FilterParser.java`. `SHORT_PUT` custom executions now correctly instantiate a `CreditSpreadFilter`, properly mapping the `shortLeg` constraints to the model for execution and persistence.

### Architecture

| File                    | Change                                                                           |
| ----------------------- | -------------------------------------------------------------------------------- |
| **`FilterParser.java`** | Added `case SHORT_PUT:` inside `buildFilter()` to map it to `CreditSpreadFilter` |

---

## Performance: PERF Timing Logs & Execution Time UI (2026-06-02)

### Phase 0 — `PERF` Timing Instrumentation

Added a dedicated, zero-noise performance timing system that writes to a separate `logs/trading-bot-perf.log` file. Output is completely isolated from the main log — enable/disable by changing the logger level in `logback-spring.xml` (no code changes needed).

**Architecture:**

- **`PerformanceLogger.java`** [NEW]: `com.hemasundar.utils` — static utility with `log(phase, symbol, ms)`, `log(phase, ms)`, `header(label)`, and `section(label)` methods. Uses a dedicated SLF4J logger routed exclusively to the PERF appenders.
- **`logback-spring.xml`** [MODIFIED]: Added `PERF_FILE` rolling file appender (`logs/trading-bot-perf.log`) and `PERF_CONSOLE` appender, wired only to `com.hemasundar.utils.PerformanceLogger` with `additivity="false"`. To disable all PERF output: set level to `OFF`.

**10 instrumented timing points:**

| #   | File                       | What's Timed                                             |
| --- | -------------------------- | -------------------------------------------------------- |
| 1   | `OptionChainCache`         | `getOptionChain` API call latency per symbol             |
| 2   | `OptionChainCache`         | Cache hit (0 ms) vs. miss detection                      |
| 3   | `StrategyExecutionService` | `strategy.findTrades()` pure CPU computation per symbol  |
| 4   | `StrategyExecutionService` | Total wall time per strategy                             |
| 5   | `AbstractTradingStrategy`  | `getYearlyPriceHistory` for Historical Volatility filter |
| 6   | `AbstractTradingStrategy`  | Supabase IV Rank lookup per symbol                       |
| 7   | `TechnicalScreener`        | Full `analyzeStock()` wall time per symbol               |
| 8   | `TechnicalScreener`        | `getYearlyPriceHistory` inside `analyzeStock()`          |
| 9   | `PriceDropScreener`        | `getPriceHistory` per symbol in `screenMultiDayDrop()`   |
| 10  | `StrategyExecutionService` | Total end-to-end execution wall time                     |

**Modified files:** `OptionChainCache.java`, `StrategyExecutionService.java`, `AbstractTradingStrategy.java`, `TechnicalScreener.java`, `PriceDropScreener.java`

### Phase 1 — Execution Time in UI Cards

All result cards on the Options Dashboard, Execute Strategy page, Screeners Dashboard, and Execute Screener page now show the per-strategy/screener execution time next to "Last run". The `executionTimeMs` field was already stored in Supabase and returned by the API — only the frontend rendering was missing.

**Example:** `Last run: 3m ago · Trades: 4 · ⏱ 12.3s`

**Architecture:**

- **`app.js`** [MODIFIED]:
  - `formatDuration(ms)` [NEW]: Converts ms to human-readable `412ms` / `5.3s` / `2.1m`. Returns `null` for zero/falsy so cards with no timing data show nothing.
  - `buildResultCard()`: Card stats now appends `· ⏱ ${formatDuration(result.executionTimeMs)}` when available.
  - `buildScreenerCard()`: Same pattern for screener result cards.

---

## Supabase Security Fix: Enable RLS on custom_screener_results (2026-06-02)

Resolved a **critical Supabase security advisory** (`rls_disabled_in_public`) detected on the `trading-bot-iv-data` project. The `public.custom_screener_results` table was publicly accessible (anyone with the project URL could read, edit, and delete all data) because Row-Level Security was not enabled when the table was created in the Custom Screener Persistence feature.

### Root Cause

When `custom_screener_results` was introduced (2026-05-22), the Supabase migration did not include `ENABLE ROW LEVEL SECURITY`. All other tables (`iv_data`, `strategy_executions`, `latest_strategy_results`, `custom_execution_results`, `latest_screener_results`) already had RLS enabled with matching policies.

### Fix Applied

- **RLS Enabled**: `ALTER TABLE public.custom_screener_results ENABLE ROW LEVEL SECURITY;`
- **Policy Created**: `"Allow service role full access"` — `FOR ALL` with `USING (true) / WITH CHECK (true)`, matching the identical policy on `custom_execution_results`. The Spring backend uses the `service_role` key which is server-side only and never exposed to the browser.

### Architecture

- **`enable_rls_custom_screener_results.sql`** [NEW]: Migration file documenting and reproducing the fix for source control tracking.
- **Supabase `custom_screener_results`** [MODIFIED]: RLS enabled + `"Allow service role full access"` policy applied directly via MCP.

---

## JWT Verification Clock Skew Resilience (2026-06-01)

Implemented clock skew tolerance in `BearerTokenFilter` to prevent unexpected authentication errors when client and server clocks are slightly out of sync.

### Features

- **10-Second Clock Skew Leeway**: Added a 10-second leeway window to the JWT verification pipeline. This fully resolves issues where tokens are rejected with a `"Token can't be used before X"` error due to minor sub-second time discrepancies between the client's device and the Supabase auth server.

### Architecture

- **`BearerTokenFilter.java`** [MODIFIED]: Updated the verifier builder at `doFilter()` to include `.acceptLeeway(10)` before verification.

## UI Display Suffix: Alias + Securities File (2026-06-01)

Modified the display of options strategy result blocks on the Options Dashboard (`/index.html`) and the Execute Strategy page (`/execute.html`) to append the active `securitiesFile` suffix to the strategy alias in the UI, while keeping the database representation clean (storing just the `alias` to avoid stale data clutter when configurations change).

### Features

- **UI Concatenation**: Options strategy result blocks are now rendered with the suffix ` - <securitiesFile>` (e.g. `Short-Term PCS - Portfolio`) dynamically in the UI.
- **Database Cleanliness**: Restores the database's `strategy_name` representation to the clean, base alias configured in `strategies-config.json`. This eliminates the stale/dangling execution block issue when a user updates `securitiesFile` in the configuration without renaming the alias.
- **Predefined Options Strategy Enrichment**: Automatically passes the configured `securitiesFile` into the `filterConfig` object of predefined strategies during configuration load, so that it is natively saved and returned by the backend.

### Architecture

- **`StrategiesConfigLoader.java`** [MODIFIED]: Enhanced the `convertToOptionsConfig` pipeline to copy `securitiesFile` (and inline `securities` if present) from the strategy configuration block into the parsed `OptionsStrategyFilter` model so it is persisted to Supabase as part of `filterConfig`.
- **`strategies-config.json`** [MODIFIED]: Cleaned up all 15 options strategy aliases by removing the manually appended `- <securitiesFile>` suffixes, standardizing them to clean base aliases (e.g. `Short Put`, `Short-Term PCS`).
- **`app.js`** [MODIFIED]:
  - `buildResultCard()`: Dynamically parses the `result.filterConfig` JSON payload, extracts `securitiesFile`, and constructs `displayName = strategyName + " - " + securitiesFile` before rendering `card-name` and info modals.

## Robust Unit Test Coverage Optimization: 85% Target (2026-05-31)

Significantly optimized unit test coverage across the application to confidently meet the 85% coverage gate by creating dedicated test suites for custom persistence components, facade service layers, and REST API controller mappings.

### Features

- **Custom Repository Coverage**: Created a complete unit test suite for `CustomScreenerRepository` to mock, serialize, and verify database reads, writes, and deletions using REST APIs.
- **Facade and Service Hardening**: Added robust verification tests to `SupabaseServiceTest` and `ScreenerExecutionServiceTest` to cover the execution of one-off custom technical screeners and their persistence layers.
- **REST Controller Mapping Tests**: Expanded `StrategyControllerTest` to fully test 7 previously uncovered endpoints, including MockMvc suites for:
  - Custom screener executions (`POST /api/execute/custom-screener`)
  - Custom strategy results retrieval (`GET /api/results/custom/screeners`)
  - Database result deletions (`DELETE /api/results/custom/screeners/{id}` and `DELETE /api/results/custom/{id}`)
  - Execution pipeline logging (`GET /api/filter-logs` and `POST /api/filter-logs/clear`)
  - Volatility metrics analysis (`GET /api/iv-rank` under empty, configured, and error scenarios)

### Architecture

- **`CustomScreenerRepositoryTest.java`** [NEW]: Comprehensive mock-based persistence tests.
- **`SupabaseServiceTest.java`** [MODIFIED]: Added tests for custom screener facade delegation.
- **`ScreenerExecutionServiceTest.java`** [MODIFIED]: Covered custom screener execution logic path.
- **`StrategyControllerTest.java`** [MODIFIED]: Added MockMvc tests covering 7 endpoints.

## Execute Screener: "Load Filters" from History (2026-05-23)

Added a **"⬆ Load Filters"** button to each custom screener result card in the _Recent Custom Screener Results_ section, mirroring the same feature available on the Execute Strategy page. Clicking the button re-populates all form fields (screener type, alias, securities, and all filter conditions) from the stored parameters of that historical run.

### How It Works

1. When a custom screener is executed via `POST /api/execute/custom-screener`, the controller now captures the full request as a `LinkedHashMap<String, Object>` (`requestParams`).
2. `requestParams` is passed through `ScreenerExecutionService.executeCustomScreener()` → `SupabaseService.saveCustomScreenerResult()` → `CustomScreenerRepository.saveCustomScreenerResult()`.
3. The repository serialises it as JSONB into the new `request_params` column of the `custom_screener_results` table.
4. On subsequent page loads, `parseCustomScreenerResult()` deserialises it back and attaches it to `ScreenerExecutionResult.requestParams`.
5. The frontend `buildScreenerCard()` renders a "⬆ Load Filters" button (only on `execute-screener.html`, only when `requestParams` is present).
6. `loadScreenerFiltersFromResult()` in `app.js` populates every form field and calls `onScreenerTypeChange()` to ensure drop-specific fields and matching templates are correctly shown.

### Architecture

- **`ScreenerExecutionResult.java`** [MODIFIED]: Added `Map<String, Object> requestParams` field (nullable, `@JsonInclude(NON_NULL)`).
- **`CustomScreenerRepository.java`** [MODIFIED]: Updated `saveCustomScreenerResult()` to accept and store a `requestParams` map in the new `request_params` JSONB column; updated `parseCustomScreenerResult()` to read it back.
- **`SupabaseService.java`** [MODIFIED]: Updated `saveCustomScreenerResult()` wrapper to accept and forward `requestParams`.
- **`ScreenerExecutionService.java`** [MODIFIED]: Updated `executeCustomScreener()` to accept `Map<String, Object> requestParams` and pass it through.
- **`StrategyController.java`** [MODIFIED]: Builds `requestParams` map from the raw `CustomScreenerRequest` and passes it to `executeCustomScreener()`.
- **`app.js`** [MODIFIED]: `buildScreenerCard()` now renders a "⬆ Load Filters" button; added `loadScreenerFiltersFromResult()`.
- **`custom_screener_load_filters_migration.sql`** [NEW]: Supabase SQL migration to add `request_params JSONB` column to `custom_screener_results`.

> **Backward compatibility**: Older rows without `request_params` (pre-migration) will have `null` and will not show the Load Filters button. Only new executions after running the migration will have it.

## Custom Screener Persistence (2026-05-22)

Finalized the isolation of manual technical screener results from the global automated dashboard by implementing a dedicated persistence layer and updating UI interactions.

### Features

- **Dedicated Persistence**: Created `CustomScreenerRepository` and the `custom_screener_results` table in Supabase to permanently store manual executions separately from scheduled automated executions.
- **Service Isolation**: Updated `ScreenerExecutionService` to toggle between saving to the global dashboard vs the new custom persistence layer.
- **Custom Endpoints**: Added `GET /api/results/custom/screeners` and `DELETE /api/results/custom/screeners/{id}` endpoints in `StrategyController` to manage isolated history.
- **Frontend Integration**: Updated `app.js` to fetch recent executions from the custom endpoint for the Execute Screener page and integrated a UI delete button to allow manual history management.

### Architecture

- **`CustomScreenerRepository.java`** [NEW]: Dedicated Supabase DB access layer for manual screener runs.
- **`SupabaseService.java`** [MODIFIED]: Added service wrapper methods (`saveCustomScreenerResult`, `getRecentCustomScreenerExecutions`, `deleteCustomScreenerExecution`).
- **`ScreenerExecutionService.java`** [MODIFIED]: Added `executeCustomScreener` to enforce decoupled persistence paths.
- **`StrategyController.java`** [MODIFIED]: Created history and deletion endpoints.
- **`app.js`** [MODIFIED]: Adapted `loadCustomScreenerResults()` and added `deleteCustomScreenerResult()` methods with corresponding UI prompts.

## IV Rank Frontend Integration & Test Stabilization (2026-05-16)

Finalized the IV Rank strategy implementation by integrating frontend visualization and resolving constructor-based compilation failures in the test suite.

### Features

- **Dynamic IV Data Display**: Updated `initTradeRowClicks` in `app.js` to fetch IV Rank metrics from the `/api/iv-rank` endpoint upon clicking a trade row in the execute results table.
- **Volatility Context Panel**: Added a dedicated "Volatility Context (1Y)" panel to the trade detail flyout, dynamically displaying Current IV, IV Rank, and 52-week High/Low values. Includes graceful error handling for missing IV data.
- **Test Suite Stabilization**: Resolved all test compilation failures introduced by the `Optional<SupabaseService>` dependency injection refactoring across 6 different strategy test classes and controller tests.
- **IV Scaling Bug Fix**: Corrected a frontend scaling issue where Current IV, Low, and High were being multiplied by 100 twice, resulting in 100x larger values in the UI.

## Execution Log Expiry Grouping: Backend-Driven Hierarchy (2026-05-10)

Corrected the three-level log hierarchy to be driven by real backend data rather than fragile string parsing. Added an `expiry` field to `ExecutionLogEntry` so filters inside the per-expiry loop carry their date, and symbol-level filters (volatility, DTE) appear in a dedicated "Other" block.

### Features

- **`expiry` field on `ExecutionLogEntry`**: Nullable `String`. Set to the expiry date (e.g. `"2025-01-17"`) for all filters run inside `findValidTrades`. Left `null` for symbol-level filters (`Historical Volatility`, `DTE Filter`) that run in `AbstractTradingStrategy.findTrades` before the per-expiry loop.
- **`FilterLogStore.logFilter()` overloaded**: A new 6-arg overload `logFilter(strategy, symbol, expiry, stage, in, out)` is the primary method. The original 5-arg signature delegates to it with `expiry = null`, so `AbstractTradingStrategy` call sites are unchanged.
- **All 5 strategies updated**: `PutCreditSpreadStrategy`, `CallCreditSpreadStrategy`, `ZebraStrategy`, `BrokenWingButterflyStrategy`, `LongCallLeapStrategy` — every `logFilter` call inside `findValidTrades` now passes `expiryDate` as the 3rd argument. The old `"Generated Candidates (expiry YYYY-MM-DD)"` stage name is simplified to `"Generated Candidates"`.
- **UI grouping via `entry.expiry`**: `renderLogSymbolGroups` splits entries by `e.expiry != null` directly — no regex needed.
  - Expiry-date blocks render in chronological order; each badge shows `N candidates → M trades` sourced from the "Generated Candidates" entry.
  - Symbol-level (null-expiry) entries appear in a collapsible **"Other (symbol-level)"** block, styled with `.log-expiry-other` (de-emphasised, italic label).
  - `"Generated Candidates"` rows are excluded from the filter table (tradesIn === tradesOut, no filtering occurs).
- **Collapsible Filters Section (Execute Screen)**: The "Filters" section on `/execute.html` is collapsible, collapsed by default on page load.

### Architecture

- **`ExecutionLogEntry.java`** [MODIFIED]: Added `private String expiry` field with Javadoc explaining null vs non-null semantics.
- **`FilterLogStore.java`** [MODIFIED]: Added 6-arg `logFilter(..., String expiry, ...)` primary method; 5-arg overload delegates with `null`.
- **`PutCreditSpreadStrategy`, `CallCreditSpreadStrategy`, `ZebraStrategy`, `BrokenWingButterflyStrategy`, `LongCallLeapStrategy`** [MODIFIED]: All `logFilter` calls pass `expiryDate` as 3rd arg.
- **`app.js`** [MODIFIED]:
  - `renderLogSymbolGroups()`: Groups by `e.expiry` directly. Computes summary counts excluding "Generated Candidates" entries.
  - `renderLogSymbolContent()`: Expiry blocks listed first (sorted); "Other" block appended last using `otherId = 'other-' + stratSlug + '-' + symSlug` for state tracking.
- **`style.css`** [MODIFIED]: Added `.log-expiry-other` and `.log-expiry-other .log-expiry-date` for de-emphasised styling of the Other block.
- **`execute.html`** [MODIFIED]: Filters section wrapped in a collapsible card.

Improved the debuggability and observability of the trading bot by implementing a delete function for execution results, adding granular per-filter trade count logging, and creating a new UI screen to visualize these logs in a collapsible, strategy-grouped format.

### Features

- **Custom Execution Deletion**: Users can now delete custom execution results directly from the Execute Strategy dashboard via a new 🗑️ Delete button.
  - Implements a safety confirmation modal before permanent removal from the Supabase database.
- **Granular Filter Logging**: Implemented a thread-safe `FilterLogStore` to track and record the exact number of trades passing through each stage of a strategy's filter pipeline (e.g., Volatility, Delta, Break-Even).
  - Logs are collected dynamically during both Global and Custom executions, giving developers/users full transparency into which filter condition is aggressively removing trades.
- **Execution Logs Dashboard (`/logs.html`)**: Added a dedicated real-time log viewer to the UI sidebar.
  - Groups filter logs hierarchically by Strategy → Symbol.
  - Features collapsible sections, color-coded pass-rate progress bars, and percentage drop-offs to visually identify bottlenecks.
  - Includes a manual "Clear Logs" functionality and auto-clears on new execution starts.
- **Backend Infrastructure Optimization**: Refactored Stream pipelines in all major option strategies (`PutCreditSpread`, `CallCreditSpread`, `Zebra`, `LongCallLeap`, `BrokenWingButterfly`) to support intermediate logging hooks without breaking functionality.

### Architecture

- **`FilterLogStore.java`** [NEW]: Thread-safe singleton for caching `ExecutionLogEntry` DTOs during the active run cycle.
- **`ExecutionLogEntry.java`** [NEW]: Immutable record mapping strategy, symbol, filter name, and input/output counts.
- **`StrategyController.java`** [MODIFIED]: Introduced `DELETE /api/results/custom/{id}`, `GET /api/filter-logs`, and `POST /api/filter-logs/clear` endpoints.
- **`CustomExecutionRepository.java` & `SupabaseService.java`** [MODIFIED]: Added Supabase delegate mapping for the `DELETE` API.
- **`app.js` & `style.css`** [MODIFIED]: Added `deleteCustomResult()`, confirmation modal, and the comprehensive `initLogsPage()` UI rendering logic.
- **`AbstractTradingStrategy.java`** [MODIFIED]: Base strategy instrumented to log common filters (e.g., DTE, Volatility).

## Alert Message Centralization (2026-04-19)

Refactored all alert message strings out of service/controller classes into a dedicated constants class for mobile-friendly brevity and easy maintenance.

### Features

- **`AlertMessages.java`** [NEW]: Single utility class holding all alert message constants and source-format templates.
  - Short, action-oriented messages (e.g. `"Auth failed — update REFRESH_TOKEN & redeploy"` instead of long prose)
  - `_FMT` suffix marks templates requiring `String.format()` for dynamic values
  - Source constants (`SRC_SUPABASE`, `SRC_EXECUTION`, `SRC_SCREENER_FMT`, `SRC_STRATEGY_SYMBOL_FMT`) prevent inconsistent source labels
- **Caller updates**: `StrategyExecutionService`, `ScreenerExecutionService`, `StrategyController` — all `addAlert()` calls replaced with `AlertMessages.*` references; no inline strings remain.

## Cloud Run Startup Fix & Environment Resilience (2026-04-12)

Resolved the "container failed to start and listen" crash in Cloud Run caused by primitive boolean binding failures and empty secret propagation.

### Features

- **Boxed Boolean Migration**: Converted all critical feature-flag booleans (`TelegramConfig`, `SupabaseConfig`, `GoogleSheetsConfig`) from `boolean` to `Boolean`. This prevents the `IllegalArgumentException: A null value cannot be assigned to a primitive type` when Spring Boot handles unexpected empty strings.
- **Dynamic Env Var Assembly**: Rewrote the `deploy-cloud-run.yml` deployment logic to dynamically construct the `--set-env-vars` argument.
- **Improved Fallback Logic**: Secrets that are missing or empty in GitHub are now **completely omitted** from the deployment command. This ensures the environment variable is unset in the container, allowing Spring Boot to correctly fallback to defaults defined in `application.properties`.
- **Resource Hardening**: Increased Cloud Run memory allocation to `1Gi` and startup timeout to `300s` to ensure reliable Spring context initialization in production.

### Architecture

- **Environment Isolation**: Prevented "pollution" of the container environment with empty strings (`""`) which can override valid configuration defaults.
- **Type Safety**: Improved configuration robustness by allowing null-safe property binding.

## Job Service DI Refactoring & Lombok Standardization (2026-04-12)

Completed the standardization of Dependency Injection across the job service layer and core utilities, replacing field-level `@Autowired` with constructor-based injection using Lombok.

### Features

- **Constructor Injection Standardization**: Refactored `ScheduledJobRunner`, `IVDataJobService`, `ScreenerJobService`, and `IVDataRepository` to use final fields and `@RequiredArgsConstructor`.
- **Lombok Integration**: Replaced manual constructors in `TokenProvider`, `AbstractTradingStrategy`, and various repositories with `@RequiredArgsConstructor` (and `@Service`/@Component as needed) to reduce boilerplate and enforce immutability.
- **Base Strategy Refactoring**: Moved common dependencies (ThinkOrSwinAPIs, SupabaseService, TelegramUtils, etc.) to `AbstractTradingStrategy` class fields with constructor injection, significantly simplifying concrete strategy classes.
- **Safe Optional DI**: Implemented `Optional<SupabaseService>` injection in `IVDataJobService` to handle conditional database features cleanly while maintaining strict DI patterns.

### Architecture

- **`AbstractTradingStrategy.java`**: Now centralizes 5+ core dependencies as final fields, injected via constructor.
- **`TokenProvider.java`**: Switched from manual singleton/static initialization hints to `@RequiredArgsConstructor` for its `StrategiesConfigLoader` dependency.
- **Job Layer Stability**: All scheduled jobs (IV collection, screeners, token generation) now operate on fully initialized, immutable service instances.

## Execute Screen Filter Display & Reload (2026-04-11)

Added full filter visibility and reload capability to custom execution result cards on the Execute Strategy screen.

### Features

- **Filter Details Section**: Each custom execution result card now includes a collapsible "Filter Details" section that displays the complete filter configuration used during that execution, using the same `renderFilterGrid()` layout as the Config page.
- **Load Filters Button**: On the Execute Strategy page, each result card shows a "⬆ Load Filters" button that populates the filter form with the exact parameters from that previous execution. This enables rapid iteration — users can re-run a strategy with the same or slightly modified filters without re-entering everything manually.
- **Strategy Type Auto-Detection**: The Load Filters function automatically detects the strategy type from the result's strategy name and sets the dropdown + renders strategy-specific filters accordingly.
- **Alias Annotation**: When loading filters from a result, the alias field is auto-populated with the original name + " (Reload)" suffix for traceability.

### Architecture

- **`app.js`** [MODIFIED]: Updated `buildResultCard()` to render a collapsible filter details section and a contextual "Load Filters" button (only visible on the execute page). Added `loadFiltersFromResult()` function with strategy type detection, filter flattening, and form population logic.
- **`style.css`** [MODIFIED]: Added `.filter-details-section`, `.filter-details-toggle`, and `.filter-details-body` CSS classes for the collapsible filter details UI.

Replaced the manual static bearer token authentication system with Supabase Auth using Google and Apple OAuth providers. Users now sign in via their Google or Apple account, and the backend verifies Supabase-issued JWTs.

6. **GitHub Secrets**: Add `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, and `ALLOWED_EMAILS` (comma-separated emails) to repository secrets. No JWT secret is needed as we use the public JWKS endpoint.

## Supabase Auth Stability & Redirect Fixes (2026-04-08)

Resolved the "infinite loop" bug where users were being kicked back to the login screen immediately after signing in. Fixed the backend JWT verification logic to properly handle Supabase's JWKS (JSON Web Key Set) public keys.

### Features

- **JWKS Verification Finalized**: The backend now correctly fetches and caches Supabase's public keys from the `.well-known/jwks.json` endpoint. Supports both ECC P-256 (ES256) and RSA (RS256) algorithms.
- **Race Condition Resolution**: Fixed a frontend bug where global API calls (like market status) were firing before the authentication session was fully initialized, triggering a 401 redirect loop.
- **Improved UX**: Authentication checks and market status fetches now happen in a strict sequence. Removed diagnostic "Redirect Reason" messages from the login page for a cleaner production experience.
- **Email Allowlist Hardening**: Enforced the `ALLOWED_EMAILS` check in the filter layer, ensuring only authorized users can access the system.

### Architecture

- **`BearerTokenFilter.java`** [STABILIZED]: Refined JWT verification with better error handling for signature, expiration, and issuer mismatches.
- **`app.js`** [SYNCHRONIZED]: Moved all authorized API calls inside the page initialization flow to ensure they wait for `initAuth()` to complete.
- **`login.html`** [POLISHED]: Cleared legacy redirect reasons and simplified the loading state.
- **`AUTH_SETUP_GUIDE.md`** [NEW]: Comprehensive guide for Google Cloud Console and Supabase Auth configuration.
- **`ReadMe.md` & `SUPABASE_SETUP_GUIDE.md`** [MODIFIED]: Added links to the new auth guide and updated outdated authentication descriptions.

## DTO Immutability & Service Extraction (2026-03-22)

Refactored core Data Transfer Objects to enforce immutability and centralized redundant execution logic into robust service classes.

### Bug Fixes

- **Dashboard Technical Screener Leak**: Fixed an issue where the Technical Screener results on the UI (`/api/results/screeners`) were failing to load due to a Jackson deserialization error from Supabase (`Unrecognized field "formattedSummary"`). Solved by adding `@JsonIgnore` and `@JsonIgnoreProperties` annotations to the `ScreeningResult` DTO.

### Features

- **Immutable DTOs**: Converted `ExecuteRequest`, `CustomExecuteRequest`, `ExecutionResult`, and `ScreenerExecutionResult` to be strictly immutable using Lombok's `@Value` and Heavily integrated `@Jacksonized` to preserve controller JSON deserialization.
- **Service Segregation**: Extracted duplicate execution logic from `StrategyController` and UI views into `StrategyExecutionService` to streamline scheduled (TestNG) and ad-hoc (REST/UI) executions.
- **Securities Resolution**: Extracted raw ticker arrays and file-parsing routines into a centralized `SecuritiesResolver`.

### Architecture

- **`StrategyControllerTest.java`**: Eliminated all setter-based DTO mutations in UI tests; migrated entirely to the Builder pattern.
- **`ScreenerExecutionResultTest.java`**: Updated assertion paths to reflect immutable DTO state copies (`toBuilder()`).
- **Code Clean-up**: Purged obsolete imports and dead setter references across services and test suites.

## Unit Test Coverage Expansion (2026-03-20)

Significantly increased the project's unit test coverage to ensure long-term stability and robust CI/CD gating.

### Features

- **Coverage Boost**: Instruction coverage increased from **59% to 78.00%**, significantly exceeding the 75% target.
- **Strict Gating**: The project enforces a **minimum 85% instruction coverage** for all core business logic using JaCoCo. This is enforced locally during Maven verification and via GitHub Actions for any Pull Request targeting the `main` branch.
- **Improved Test Infrastructure**: Addressed low-coverage areas in core services and utilities.

### Improved Test Suites

- **`SupabaseServiceTest.java`**: Added comprehensive tests for all CRUD operations, custom executions, and complex retry scenarios using mocked static RestAssured responses.
- **`TelegramUtilsTest.java`**: Verified all alert formatting logic (Option strategies & Technical screeners) and edge cases for message splitting.
- **`ThinkOrSwinAPIsTest.java`**: Refactored to properly mock the RestAssured fluent API chain and Jackson deserialization.
- **`GoogleSheetsServiceTest.java`**: Implemented missing verification for sheet updates and credential handling.
- **`StrategyControllerTest.java`**: Expanded to cover all REST endpoints including selective screener execution and securities file resolution.

### Architecture

- **`TokenProvider.java`**: Added `clearToken()` to reset singleton state between test runs, ensuring perfect test isolation.
- **`UnitTests.xml`**: Added `com.hemasundar.apis` package to the primary unit test suite.

## Technical Screener Results on Dashboard (2026-03-10)

Implemented the ability to view the execution results of configured Technical Screeners distinctly from Options Strategies.

### Features

- **Dashboard Separation**: Added a dedicated "Technical Screeners" section to the dashboard (`/index.html`) parallel to the existing Options Strategies section.
- **Screener Cards**: Technical screener results correctly utilize the `alias` from configurations (e.g., "RSI/BB Bullish Crossover") and present matched stock tickers in a clean format using the existing UI components.
- **Zero-Match Handling**: Screeners that do not find any matching tickers are rendered as disabled (grayed out) cards that cannot be expanded, mirroring the behavior of zero-trade Option Strategies.
- **Real-Time Data**: The dashboard concurrently fetches both option trades and technical screening outputs via `Promise.all()` to minimize loading times.

### Architecture

- **API Endpoints**: Introduced `/api/results/screeners` to `StrategyController.java` with aggressive `Cache-Control` headers.
- **Data Transfer**: Created the `ScreenerExecutionResult.java` DTO to securely encapsulate technical outputs isolated from options outputs.
- **Database Persistence**: Extended `SupabaseService.java` with `saveScreenerResult` and `getAllLatestScreenerResults`, writing to a dedicated `latest_screener_results` remote table.
- **Execution Pipeline**: `StrategyExecutionService` now natively executes technical filters en-masse, groups the results, and persists them via the new service methods.

## Dashboard Custom Execution Leak Bugfix (2026-03-09)

Resolved an issue where executing a custom strategy from the `/execute.html` screen would inadvertently save the results to the dashboard's `latest_strategy_results` database table, polluting the dashboard with non-predefined strategies.

### Architecture

- **Backend Refactor**: Modified `StrategyExecutionService.executeStrategy()` to accept a `boolean isCustomExecution` flag.
- **Conditional Persistence**: The call to `supabaseService.saveStrategyResult()` (which targets the dashboard table) is now wrapped in an `if (!isCustomExecution)` block.
- **Isolated Execution**: `executeCustomStrategy()` now explicitly passes `true`, guaranteeing that custom trades exclusively persist to `custom_execution_results` and no longer leak into the dashboard's `latest_strategy_results`.

## Vaadin → Static Frontend Migration (2026-03-05)

Migrated from Vaadin's server-side Java UI to a **plain HTML/CSS/JS frontend** with REST API wrapper endpoints. Vaadin completely removed from the codebase.

### Architecture

- **Frontend**: Plain HTML + CSS + vanilla JS served from `src/main/resources/static/`
- **Backend**: Spring Boot REST APIs at `/api/*` wrap all Supabase reads and strategy execution
- **Security**: Bearer token auth via `BearerTokenFilter` for `/api/*`; static files are public
- **API Docs**: Swagger UI at `/swagger-ui.html` via `springdoc-openapi-starter-webmvc-ui`

### Files Created

- **`StrategyController.java`** [NEW]: 8 REST endpoints (strategies, results, config, execute, status, cancel)
- **`BearerTokenFilter.java`** [NEW]: Bearer token filter for `/api/*` endpoints
- **`static/index.html`** [NEW]: Dashboard page (replaces `MainView.java`)
- **`static/execute.html`** [NEW]: Custom execution page (replaces `ExecuteStrategyView.java`)
- **`static/config.html`** [NEW]: Config viewer (replaces `StrategyConfigView.java`)
- **`static/style.css`** [NEW]: Dark theme CSS (Stitch palette, `#1349ec` primary)
- **`static/app.js`** [NEW]: API client, UI builders, page logic

### Files Deleted

- **`ui/`** directory: `MainView.java`, `ExecuteStrategyView.java`, `StrategyConfigView.java`, `MainLayout.java`, `TradeGridBuilder.java`, `ResultCardBuilder.java`
- **`config/AppShellConfig.java`**: Vaadin AppShell configurator
- **`frontend/`** directory: 67+ Vaadin generated JS/TS files

### Files Modified

- **`pom.xml`**: Replaced `vaadin-spring-boot-starter` with `spring-boot-starter-web`, removed Vaadin BOM + production profile, added `springdoc-openapi`
- **`Dockerfile`**: Removed `COPY frontend`, removed `-Pproduction` flag
- **`application.properties`**: Removed `vaadin.productionMode`, added `api.bearer.token`
- **`application-production.properties`**: Removed `vaadin.productionMode=true`
- **`README.md`**: Updated usage, project structure, deployment, and dependencies sections

## Google Cloud Run Deployment (2026-03-01)

Added full Docker + GitHub Actions CI/CD pipeline to deploy the Vaadin web app to **Google Cloud Run** with automatic HTTPS.

### Architecture

- **Docker**: Multi-stage build (Maven JDK 17 → JRE 17 Alpine). Produces a production JAR with Vaadin pre-compiled via `-Pproduction`.
- **Google Artifact Registry**: Docker images are pushed here on each deployment.
- **Cloud Run**: Always-on (`min-instances=1`), 512Mi RAM, 1 CPU. Public HTTPS URL auto-generated by GCP.
- **Secrets**: Supabase keys injected as Cloud Run environment variables (not baked into the image).

### Files Created/Modified

- **`Dockerfile`** [NEW]: Multi-stage build (Maven build → JRE runtime). JVM flags tuned for 512Mi container.
- **`.dockerignore`** [NEW]: Excludes `target/`, `.git/`, `logs/`, `tokens*/`, `service-account.json`.
- **`application-production.properties`** [NEW]: Sets `vaadin.productionMode=true` when `spring.profiles.active=production`.
- **`application.properties`** [MODIFIED]: Replaced hardcoded Supabase keys with `${ENV_VAR:default}` expressions. Local dev still works without env vars.
- **`.github/workflows/deploy-cloud-run.yml`** [NEW]: Full CI/CD pipeline — triggered on push to `main`. Authenticates to GCP, builds image, pushes to Artifact Registry, deploys to Cloud Run.
- **`CLOUD_RUN_DEPLOYMENT.md`** [NEW]: Detailed step-by-step GCP console setup guide (project, APIs, Artifact Registry, Service Account, GitHub Secrets).
- **`README.md`** [MODIFIED]: Added Google Cloud Run deployment section with secrets table and URL info.

### Required GitHub Secrets

`GCP_PROJECT_ID`, `GCP_SA_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`

### Release Process

Push to `main` → GitHub Actions builds image → pushes to Artifact Registry → deploys to Cloud Run (zero downtime, ~5 min).

---

## Execute Strategy Vaadin View (2026-02-25)

Added a new dedicated screen in the Vaadin UI for dynamically building and executing custom trading strategies without modifying the configuration files.

### Problem

- Strategy execution was limited to the predefined configurations in `strategies-config.json` via the main dashboard.
- Users had no way to quickly test a strategy with custom filters on a specific set of stock symbols directly from the UI.
- The UI had only a single view attached to the root URL.
- Custom executions polluted the dashboard's `latest_strategy_results` table.

### Changes

- **`ExecuteStrategyView.java`**: A new Vaadin view allowing users to select a `StrategyType`, configure common filters (DTE, Max Loss, etc.), and dynamically render strategy-specific fields (e.g. Short/Long Legs, Margin settings). Includes inline field validation and a "Recent Executions" section showing the last 20 custom runs with collapsible result cards and trade grids (same layout as dashboard).
- **`MainLayout.java`**: Extracted the `AppLayout` wrapper from `MainView.java` to support a sidebar with `RouterLink` navigation between multiple views (Dashboard and Execute Strategy).
- **`MainView.java`**: Refactored to act as a standard route component (`@Route(value = "", layout = MainLayout.class)`) instead of extending `AppLayout`.
- **`StrategyExecutionService.java`**: Added `executeCustomStrategy()` and `getRecentCustomExecutions()` methods. Custom executions now save to the separate `custom_execution_results` table.
- **`SupabaseService.java`**: Added `saveCustomExecutionResult()` (INSERT) and `getRecentCustomExecutions(int limit)` (SELECT) methods for the new `custom_execution_results` table.

### Supabase Schema

```sql
CREATE TABLE custom_execution_results (
  id BIGSERIAL PRIMARY KEY,
  strategy_name TEXT NOT NULL,
  execution_time_ms BIGINT,
  trades_found INT,
  trades JSONB,
  filter_config JSONB,
  securities TEXT[],
  created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### Behavior

- The Execute Strategy view relies entirely on strategy selection for form rendering and does not read from `strategies-config.json`.
- Users can input comma-separated securities manually in a text field, and then run a strategy.
- Results are saved to the `custom_execution_results` table (append-only), completely separate from the dashboard's `latest_strategy_results`.
- Last 20 custom executions display in-page under "Recent Executions" with the same card/grid layout as the dashboard.
- Dashboard is not affected by custom executions.

## Strategy Filter Persistence to Supabase (2026-02-22)

Strategy filter configurations are now saved alongside execution results in Supabase's `latest_strategy_results` table. The Vaadin UI displays filter parameters from the DB instead of from in-memory config.

### Problem

- Filters from `strategies-config.json` were not persisted — only trade results were saved
- The Vaadin UI read filter display data from in-memory config, which could differ from the actual filters used during execution (especially for CI vs. local runs)
- The static GitHub Pages dashboard had no way to display filter data at all

### Changes

- **`StrategyResult.java`**: Added `filterConfig` field (JSON string), updated `fromTrades()` to accept and serialize `OptionsStrategyFilter`
- **`SupabaseService.java`**: Save `filter_config` JSONB in payload using ObjectNode; parse it back in `parseStrategyResult()`
- **`StrategyExecutionService.java`** & **`SampleTestNG.java`**: Pass `config.getFilter()` to `fromTrades()`
- **`MainView.java`**: Replaced `getStrategyParams()` (in-memory config) with `getStrategyParamsFromJson()` (DB JSON). Shows "Filter data not available" for old rows without filter data

### Supabase Schema

```sql
ALTER TABLE latest_strategy_results
ADD COLUMN IF NOT EXISTS filter_config JSONB;
```

### Bonus

Since `StrategyResult` is serialized into the `results` JSONB column of `strategy_executions`, filter data automatically flows into the execution history audit log with no extra code.

---

## Bullish ZEBRA Strategy Options Trading Profile (2026-02-19)

Implemented a new Bullish ZEBRA (Zero Extrinsic Back Ratio Spread) strategy. This strategy simulates a stock equivalent directional play with less capital and defines risk.

### Strategy Rules

- **Leg 1**: Sell 1 In-The-Money (ITM) Call
- **Leg 2**: Buy 2 further In-The-Money (ITM) Calls
- Target Delta: Typically, sell the 0.50 delta (ATM) call and buy two 0.70+ delta calls.
- Net Extrinsic Value Constraint: The trade should ideally be entered for 0 or close to 0 total extrinsic value to negate time-decay.

### New Components

- **`ZebraFilter.java`**: POJO defining filter criteria (longCall and shortCall leg filters, along with maximum net extrinsic value).
- **`ZebraTrade.java`**: Implements `TradeSetup` to structure the Zebra trade metrics and calculation.
- **`ZebraStrategy.java`**: Implements `AbstractTradingStrategy`. Generates permutations of shorts against longs, calculating max loss, net debit, and the combined net extrinsic value against limits defined by user config.

### Configuration

All targets like Extrinsic Value, Min/Max Delta for legs, and general filters are driven via the unified `strategies-config.json` system instead of being hardcoded in Java.

### UI and Dashboard Integration

- Configured dynamic extraction of `maxBreakEvenPercentage` and ZEBRA's `maxNetExtrinsicValue` filters for visibility in the **Vaadin Web app** (`MainView.java`).
- Mapped specific `ZebraTrade` execution details (like Net Extrinsic Value) into the DTO layer (`Trade.java`). This guarantees the new strategy data automatically synchronizes to the expandable Supabase rows on the **GitHub Static Dashboard** (`docs/app.js`) without rewriting core frontend layers.

```json
        {
            "enabled": true,
            "alias": "Bullish ZEBRA - Portfolio",
            "strategyType": "BULLISH_ZEBRA",
            "filterType": "ZebraFilter",
            "filter": {
                "maxNetExtrinsicValueToPricePercentage": 0.5,
                "ignoreEarnings": false,
                "maxTotalDebit": 5000,
                "maxBreakEvenPercentage": 5.0,
                ...
            }
        }
```

---

## Supabase Security Hardening (2026-02-17)

Enhanced the security of the Supabase integration by restricting public access and using privileged keys for backend operations.

### Changes

- **Backend**: Switched `SupabaseService` to use the **Service Role Key** instead of the Anon Key for write operations.
- **Database**: Created `secure_rls.sql` to lock down Row-Level Security (RLS) policies.
  - Public (`anon`): **Read-Only** access.
  - Backend (`service_role`): **Full Access** (Read/Write).
- **Configuration**: Added `SUPABASE_SERVICE_ROLE_KEY` support to `ServiceConfig` and `IVDataCollectionTest`.

### Impact

- **Security**: The exposed `anon` key in the frontend (`app.js`) can no longer validly write to the database, preventing unauthorized data modification.
- **Reliability**: Backend jobs now use admin privileges, bypassing RLS restrictions.

---

## Dashboard Extraction (2026-02-16)

The static strategy dashboard has been extracted from this repository into a standalone project.

- **Removed**: `docs/` folder (HTML/JS/CSS) and `.github/workflows/deploy-pages.yml`
- **New Architecture**: Dashboard now lives in a separate Git repository, fetching data from the same Supabase instance.
- **Reason**: Decouple frontend deployment from the main trading bot codebase.

---

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

## Supabase Integration for IV Data Collection (2026-02-03)

Added Supabase as a database option for storing daily Implied Volatility (IV) data.

### Features

- **Automatic Connection Testing**: Supabase connection verified during setup
- **Retry Logic**: Exponential backoff for rate limiting and transient errors
- **Environment Variable Support**: CI/CD-friendly configuration

### Architecture

- **SupabaseService**: REST API client using OkHttp
- **UPSERT Support**: Supabase automatically handles duplicate entries (symbol + date)
- **Telegram Notifications**: Summary shows which databases were used

### Database Configuration

```properties


# Supabase Configuration
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

- `IVDataCollectionTest.java`: Added database support
- `test.properties`: Added database configuration options
- `pom.xml`: Added OkHttp dependency
- `README.md`: Added IV Data Tracking section
- `Gemini.md`: This entry

### Benefits

- **PostgreSQL Power**: Superior querying and analytics
- **Free Tier**: 500MB database, 2GB bandwidth/month
- **Auto-Generated REST API**: Easy integration with Java
- **Built-in Dashboard**: View and query data via Supabase web interface

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

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

### Recent Fixes (2026-02-03)

**UPSERT Logic:**
Fixed 409 duplicate key error by adding PostgREST `on_conflict` parameter to properly handle updates when same symbol+date exists.

**Logging:**
Changed database save operations from DEBUG to INFO level for better visibility.
