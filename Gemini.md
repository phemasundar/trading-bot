# Project Updates

> **CRITICAL AI RULE**: NEVER execute `git commit` or `git push` unless explicitly requested by the user. Do not assume permission to commit changes.
> **CRITICAL AI RULE**: NEVER use GitHub MCP tools (create PR, merge, create release, etc.) unless the user explicitly asks. Do not assume permission for any GitHub operations.

## Cloud Run Auth Error Visibility & Daily Redeployment (2026-04-03)

Resolved silent "0 results" failures in the production Cloud Run deployment caused by Schwab API `REFRESH_TOKEN` expiry. Implemented automated redeployment and full error visibility.

### Features
- **Daily Auto-Redeployment**: Updated `deploy-cloud-run.yml` with a daily cron schedule (`0 6 * * *`) so Cloud Run picks up the latest rotated `REFRESH_TOKEN` from GitHub Secrets every 24 hours.
- **Auth Error Detection**: `StrategyExecutionService` now tracks `lastExecutionError` via `AtomicReference`. An `isAuthError()` helper detects 401/Unauthorized/Access Token failures and persists them across the execution lifecycle.
- **Auth Error Banner**: Frontend now displays a fixed, full-width red banner at the top of every page when the backend reports an authentication error. The banner:
  - Slides in with a smooth CSS animation (`slideInDown`)
  - Shows the specific error message from the backend
  - Has a "Dismiss ✕" button that removes the banner AND calls `/api/clear-error` to reset backend state
  - Deduplicates (only one banner ever shown at a time)
- **`/api/clear-error` Endpoint**: Added to `StrategyController` to allow the UI to reset the error state after the user acknowledges it.
- **Startup Auth Check**: `checkExecutionStatus()` and `checkCustomExecutionStatus()` both check for `lastError` on page load, so a previously cached error is shown immediately even if the user refreshes.

### Architecture
- **`.github/workflows/deploy-cloud-run.yml`** [MODIFIED]: Added `schedule: - cron: '0 6 * * *'` trigger.
- **`StrategyExecutionService.java`** [MODIFIED]: Added `AtomicReference<String> lastExecutionError`, `isAuthError()` detection, `getLastExecutionError()` / `clearLastExecutionError()` methods.
- **`StrategyController.java`** [MODIFIED]: Added `lastError` field to `/api/status` response body; added `POST /api/clear-error` endpoint.
- **`app.js`** [MODIFIED]: Added `showAuthErrorBanner()` / `dismissAuthErrorBanner()` functions; wired into `startPolling()`, `checkExecutionStatus()`, and `checkCustomExecutionStatus()`.
- **`style.css`** [MODIFIED]: Added `@keyframes slideInDown` animation for the banner entrance.

## Price Drop Technical Screeners (2026-03-28)

Added two new Technical Screener types to identify stocks with significant price declines.

### Features
- **Multi-day Price Drop** (`PRICE_DROP`): Screens stocks down ≥ X% over N trading days. When `lookbackDays=0`, uses intraday (daily) percent change from Quotes API. When `lookbackDays>0`, uses Price History API for multi-day lookback.
- **52-Week High Drop** (`HIGH_52W_DROP`): Screens stocks down ≥ X% from their 52-week high using the Quotes API.
- **Configurable Parameters**: `minDropPercent` and `lookbackDays` are configurable per screener entry in `strategies-config.json`.
- **Screener Consistency Fix**: Changed `screenerId` to use the screener name (alias) instead of the enum type name. This prevents screeners sharing the same type (like "Intraday Drop" and "5-Day Drop") from overwriting each other in the database.
- **DTO Hardening & Null Safety**: Converted 100+ primitive fields (`double`, `long`, `int`, `boolean`) in `QuotesResponse`, `PriceHistoryResponse`, and `MarketHoursResponse` to boxed Wrapper types (`Double`, `Long`, `Integer`, `Boolean`). This prevents Jackson deserialization failures when the Schwab API returns `null` for fields like `nAV`, `previousClose`, or `isOpen`.
- **Dashboard UI Improvements**:
  - **Checkbox Default State**: All strategy and screener checkboxes on the dashboard are now unchecked by default on page load.
  - **Technical Screener Sorting**: Implemented interactive column sorting for technical screener and price drop screener tables, sharing the same unified sorting logic as the options strategy tables.
- **Strategy Architecture Consolidation**: Merged `LONG_CALL_LEAP_TOP_N` into the base `LONG_CALL_LEAP` strategy. The unified strategy now natively supports sorting, trade limits, and progressive relaxation, simplifying the codebase and user configuration.
- **Execute Screen Persistence**: Fixed an issue where the progress bar would disappear on the `/execute` screen if the page was refreshed during strategy execution. Added `checkCustomExecutionStatus()` to the page initialization flow to sync with the backend state.
- **LEAP Top N Strategy Update**: Changed default behavior of `LONG_CALL_LEAP_TOP_N` to return all matching results if `topTradesCount` is not specified in the filter. This also bypasses the progressive relaxation logic in "no-limit" mode to ensure only strict metadata matches are returned.
- **Dashboard Display**: Drop screener results show dedicated columns (Ticker, Current Price, Ref Price, Drop %, Volume, Type) sorted by largest drops, with red color intensity scaling.

### Architecture
- **`PriceDropScreener.java`** [NEW]: Standalone screener class with `screenPriceDrop()` and `screen52WeekHighDrop()` methods, using Quotes API and Price History API.
- **`ScreenerType.java`** [MODIFIED]: Added `PRICE_DROP` and `HIGH_52W_DROP` enum values.
- **`ScreenerExecutionService.java`** [MODIFIED]: Added switch-based routing for new screener types to `PriceDropScreener` vs `TechnicalScreener`.
- **`TechnicalScreener.ScreeningResult`** [MODIFIED]: Added `dropPercent`, `referencePrice`, `dropType` fields with formatted summary support.
- **`TechFilterConditions.java`** [MODIFIED]: Added `minDropPercent` and `lookbackDays` fields.
- **`StrategiesConfig.java`** [MODIFIED]: Added drop fields to `ScreenerConditionsConfig`.
- **`StrategiesConfigLoader.java`** [MODIFIED]: Maps new drop condition fields.
- **`strategies-config.json`** [MODIFIED]: Added 3 default screener entries (Intraday Drop ≥ 3%, 5-Day Drop ≥ 5%, Down ≥ 20% from 52W High).
- **`app.js`** [MODIFIED]: Added `buildDropScreenerTable()` for drop-specific display with color-coded severity.

## Schwab Market Data API Documentation & Full Coverage (2026-03-28)

Documented all available Schwab Market Data API endpoints from the saved Swagger HTML and implemented the 4 missing endpoints to achieve full API coverage.

### Documentation
- **`SchwabAPI/schwab-market-data-api.md`** [NEW]: Comprehensive reference covering all 10 endpoints across 7 API categories (Quotes, Option Chains, Expiration Chain, Price History, Movers, Market Hours, Instruments), 40+ data schemas, gap analysis table, and error response formats.

### New API Methods
- **`ThinkOrSwinAPIs.java`** [MODIFIED]: Added 4 missing Market Data API endpoints:
  - `getMarketHour(marketId, date)`: Single market hours lookup (equity/option/bond/future/forex).
  - `getMovers(indexSymbol, sort, frequency)`: Top 10 movers for a given index ($DJI, $SPX, etc.).
  - `getInstruments(symbol, projection)`: Instrument search by symbol/description with configurable projection type.
  - `getInstrumentByCusip(cusipId)`: Direct CUSIP-based instrument lookup.

## Legacy Vaadin Artifact Removal (2026-03-27)

Removed obsolete Vaadin-related directories and configuration files following the successful migration to a static HTML/JS frontend.

### Cleanup
- **Deleted `src/main/bundles`**: Removed pre-compiled Vaadin assets that were no longer used.
- **Deleted NPM Configs**: Removed `package.json`, `package-lock.json`, `tsconfig.json`, and `types.d.ts` as the project no longer requires an npm build step for the frontend.
- **Deleted `node_modules`**: Reclaimed ~266MB of disk space by removing unused legacy dependencies.

## Market Hours Live Status (2026-03-25)


Integrated the Charles Schwab Market Hours API to display real-time Equity and Options market status directly on the dashboard.

### Features
- **Live Status Badges**: Added dynamic floating badges to the top-right corner of the web UI indicating whether the US Equity and Options markets are currently OPEN or CLOSED.
- **Graceful Degradation**: The API call safely recovers from network timeouts or parsing errors, defaulting to a "CLOSED" indicator rather than breaking the UI.

### Architecture
- **`MarketHoursResponse.java`** [NEW]: Immutable POJO mapping the nested Schwab `/v1/markets` JSON structure.
- **`ThinkOrSwinAPIs.java`** [MODIFIED]: Added `getMarketHours()` to execute the REST call. 
- **`StrategyController.java`** [MODIFIED]: Added the `GET /api/market-status` endpoint to serve the flattened boolean map to the frontend.
- **`app.js` & `style.css`** [MODIFIED]: Implemented global DOM injection on `DOMContentLoaded` to fetch the status and style the "pill" badges identically to the main application theme.

## Scheduled Jobs Migration to Spring (2026-03-24)

Migrated the execution of background scheduled jobs from TestNG to native Spring Boot components, enabling TestNG to be used purely for functional integration/UI tests.

### Architecture
- **`ScheduledJobRunner.java`** [NEW]: A `CommandLineRunner` that acts as the entry point for GitHub actions via the `--app.job.name` parameter. It triggers jobs and gracefully exits via `System.exit(0)`.
- **`ScreenerJobService.java`** [NEW]: Extracted options strategies and technical screener loops into a standard Spring `@Service`.
- **`IVDataJobService.java`** [NEW]: Extracted the IV data scraper collection script into a Spring `@Service`. 
- **`SchwabTokenGenerator.java`** [NEW]: Extracted the interactive `main` method for generating OAuth tokens out of `SampleTestNG.java`.

### Cleanup
- **Deleted**: `SampleTestNG.java` and `IVDataCollectionTest.java` from the test suite. 
- **Deleted**: `execute-strategies.xml` and `iv-data-collection.xml`.
- **CI/CD**: Replaced all `mvn test` usage for scheduled jobs with `mvn spring-boot:run` in `.github/workflows/daily-iv-collection.yml` and `ci.yml`. Removed the Surefire report upload steps for those background jobs.

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
- **Strict Gating**: Updated `jacoco-maven-plugin` in `pom.xml` to mandate a **minimum 75% instruction coverage** for all future builds.
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

## Dashboard Technical Screener Selection (2026-03-19)

Added the ability to select and execute technical screeners independently from the dashboard, with visual separation from options strategies.

### Features
- **Screener Selection**: Dashboard now shows technical screeners as selectable checkboxes alongside options strategies, separated by a visual divider.
- **Selective Execution**: Previously all screeners ran unconditionally when any strategy was executed. Now only selected screeners are executed. If none are selected, screeners are skipped entirely.
- **Select All / Clear All**: Toggles both options strategies and technical screeners.

### Architecture
- **`StrategyController.java`**: Added `GET /api/screeners` endpoint returning enabled screener list (index, name, type). Added `screenerIndices` to `ExecuteRequest` DTO. Updated `POST /api/execute` to pass screener indices to service.
- **`StrategyExecutionService.java`**: Added `getEnabledScreeners()` public method. Updated `executeStrategies()` to accept `Set<Integer> screenerIndices` parameter for selective screener execution.
- **`index.html`**: Added `#screener-checkboxes` container with visual `<hr>` separator and "Technical Screeners" sub-header.
- **`app.js`**: Updated `loadStrategies()` to fetch `/api/screeners`, `executeSelected()` to send both strategy and screener indices, `selectAll()` to toggle both groups.

## Execute Screen Filter Audit & SecuritiesFile Support (2026-03-19)

Fixed multiple silent bugs where backend `buildFilter()` was ignoring most filter inputs from the /execute screen, and added `securitiesFile` support.

### Features
- **Securities File Input**: Added a text field on `/execute.html` allowing users to specify predefined securities file names (e.g., `portfolio, top100, tracking`) instead of manually typing all ticker symbols. File symbols and inline tickers are merged and deduplicated.
- **Complete Backend Filter Parsing**: `buildFilter()` in `StrategyController.java` now correctly parses ALL filter fields sent from the frontend.

### Bug Fixes (Silent Ignores)
- **Leg Filters**: All strategy-specific leg filters (`shortLeg`, `longLeg`, `putShortLeg`, etc.) were being sent by the frontend but **silently ignored** by the backend. Now parsed via new `applyLegFilter()` helper.
- **LEAP-Specific Fields**: `minCostSavingsPercent`, `minCostEfficiencyPercent`, `topTradesCount`, `relaxationPriority`, `sortPriority` were all silently ignored. Now fully parsed.
- **Missing Common Filters**: `maxTotalDebit`, `maxTotalCredit`, `minTotalCredit`, `priceVsMaxDebitRatio`, `maxCAGRForBreakEven`, `maxOptionPricePercent`, `marginInterestRate`, `savingsInterestRate`, `minNetExtrinsicValueToPricePercentage` were all missing from the backend parser. Now fully parsed.

### Architecture
- **`execute.html`**: Added `securities-file-input` text field with info tooltip.
- **`StrategyController.java`**: Added `securitiesFile` to `CustomExecuteRequest` DTO. Added securities resolution from file names via `loadSecuritiesMaps()`. Added `applyLegFilter()` and `toStringList()` helpers. Completed `buildFilter()` with all 18 common + strategy-specific fields.
- **`app.js`**: Updated `executeCustom()` to send `securitiesFile`. Updated `loadTemplateParams()` to populate securities file from templates. Updated validation to accept either file or tickers.

## Technical Screener Table Customization (2026-03-10)

Implemented a data-rich, customizable results table for Technical Screeners on the dashboard.

### Features
- **Enhanced Data Visibility**: Added comprehensive technical indicator columns including Price, Volume, RSI, Bollinger Bands (Lower-Upper range), and Moving Averages (MA200, MA100, MA50, MA20).
- **Smart Formatting**: 
  - Tickers are color-coded based on signal type (Bullish/Bearish).
  - Large volume numbers are formatted with 'K' and 'M' suffixes for scannability.
  - RSI values are highlighted when in overbought (>70) or oversold (<30) territory.
- **Responsive Table UI**: Enabled horizontal scrolling for wide screener tables within the dashboard cards, ensuring visibility on all screen sizes without breaking the layout.
- **Robust Field Mapping**: Standardized the data bridge between the backend `ScreeningResult` DTO and the frontend, resolving a bug where numeric `0.0` values were being treated as missing.

### Architecture
- **Frontend Refactor**: Upgraded `buildScreenerTable()` in `app.js` with dynamic column generation and conditional styling.
- **Utility Logic**: Integrated `formatLargeNumber()` and refined numeric type checking using `typeof` to handle Java's default numeric values correctly.
- **Styling**: Appended `overflow-x: auto` to `.card-content` in `style.css` to govern wide table behavior.

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

## Disabled Zero-Trade Strategy Cards (2026-03-10)

Implemented a visual enhancement to the dashboard to clearly distinguish strategy configurations that did not yield any executing trades.

### Features
- **Visual Distinction**: Strategy result cards that have 0 trades are now visually grayed out (opacity reduced to 50%) to immediately signal they are empty.
- **Interaction Prevention**: The click-to-expand functionality is disabled on zero-trade cards, preventing users from opening empty tables, improving the overall UX and saving unnecessary clicks.

### Architecture
- **Frontend Refactor**: Modified `buildResultCard()` in `app.js` to conditionally apply a `.disabled` CSS class if `result.trades` is empty or length > 0. Conditionally adds the `click` event listener.
- **Styling**: Added `.card.disabled` rules to `style.css` to govern opacity and cursor behavior.

## Technical Screener Aliases (2026-03-10)

Implemented support for custom alias names for technical screeners in the configuration screen, mirroring the functionality available for options strategies.

### Features
- **Semantic Headers**: The configuration viewer now displays the user-defined `alias` from `strategies-config.json` in the collapsible card headers (e.g., "RSI/BB Bullish Crossover") instead of the generic "Screener" label.
- **Improved UX**: Provides immediate context for why a specific screener is configured without needing to inspect its underlying parameters.

### Architecture
- **Backend Refactor**: Added `alias` field to `ScreenerEntry` in `StrategiesConfig.java` to allow property mapping during JSON deserialization.
- **Frontend Update**: Modified `app.js` to prioritize `screener.alias` in the header rendering logic of `initConfigPage()`.

## Dashboard Custom Execution Leak Bugfix (2026-03-09)

Resolved an issue where executing a custom strategy from the `/execute.html` screen would inadvertently save the results to the dashboard's `latest_strategy_results` database table, polluting the dashboard with non-predefined strategies.

### Architecture
- **Backend Refactor**: Modified `StrategyExecutionService.executeStrategy()` to accept a `boolean isCustomExecution` flag.
- **Conditional Persistence**: The call to `supabaseService.saveStrategyResult()` (which targets the dashboard table) is now wrapped in an `if (!isCustomExecution)` block.
- **Isolated Execution**: `executeCustomStrategy()` now explicitly passes `true`, guaranteeing that custom trades exclusively persist to `custom_execution_results` and no longer leak into the dashboard's `latest_strategy_results`.

## Trades Table Column Sorting (2026-03-09)

Added interactive column sorting to the trades tables on the dashboard and custom execution screens.

### Features
- **Dynamic Sorting**: Users can now click on specific table headers manually sort the underlying trades. Supports toggling between ascending (`↑`) and descending (`↓`) orders.
- **Smart Data Handling**: Sorting logic natively understands various data types without breaking formats. 
  - **Alphabetical**: `Ticker`
  - **Numeric**: `Max Loss`, `ROR%`
  - **Property-based**: `Expiry` (sorts by DTE), `Extrinsic` (sorts by annualized percentage), `Breakeven` (sorts by CAGR percentage, falling back to standard percentage if CAGR is absent).
- **Independent State**: Each strategy result card maintains its own isolated sorting state, preventing interference when operating multiple cards concurrently.

### Architecture
- **State Management**: Added global `window.tableSortState` and `window.tradeDataMap` to `app.js` to preserve trade arrays and sort direction independently per card ID.
- **Frontend Refactor**: Upgraded `buildTradeTable` to inject interactive `<th>` headers with `onclick` bindings hitting the new `handleTableSort` function.
- **Styling**: Appended `.sort-header` to `style.css` supplying active/inactive pointer states, primary color highlighting, and bolding.

## Filter Descriptions with Speech Balloons (2026-03-08)

Implemented interactive filter descriptions using a premium "speech balloon" (tooltip) UI across the execution and configuration screens.

### Features
- **Speech Balloon UI**: Replaced modal dialogs with glassmorphic, auto-positioning tooltips. The tooltips feature a dark translucent background, primary colored borders, and dynamic pointer arrows that flip based on available screen space.
- **Enriched Information**: Descriptions now include possible enum values for strategy types and priority list keys (e.g., `relaxationPriority`, `sortPriority`), providing immediate context for valid filter inputs.
- **Externalized Content**: Detailed explanations are managed in a standalone `filter-descriptions.json` file for easier maintenance without code changes.
- **Improved UX**: Added info icons to all common filters, strategy-specific filters, and configuration viewer fields. Replaced the bright blue 'ℹ️' emoji with a custom, lightweight, transparent SVG icon inspired by Stitch MCP for a premium and non-intrusive look. Tooltips auto-close on outside clicks or when toggling another icon.

### Architecture
- **Frontend**: Updated `app.js` with `loadFilterDescriptions()` and a dynamically positioned `showFilterHelp()` function.
- **Styling**: Added `.tooltip-balloon` and `.tooltip-arrow` components to `style.css`.
- **Data**: Created `src/main/resources/static/filter-descriptions.json`.

## Unit Test Infrastructure & CI Coverage Resolution (2026-03-08)

Resolved the CI pipeline coverage failure through comprehensive test suite optimization and architectural realignment.

### Improvements
- **Coverage Restoration**: Expanded `UnitTests.xml` to include all unit test packages, restoring instruction coverage to >60% (from 27%).
- **Package Realignment**: Refactored the test directory structure to perfectly mirror the `src/main/java` packages, resolving protected-access compilation issues.
- **CI Environment Isolation**: Automated the injection of `TELEGRAM_ENABLED=false` in GitHub Actions to ensure non-blocking test execution in PR environments.
- **Test Stability**: Fixed `UnsupportedOperationException` in `IronCondorStrategyTest` by utilizing mutable collections for expiration mappings.

## Unit Testing & CI/CD Coverage Isolation (2026-03-07)

Expanded unit tests to achieve >60% instruction coverage enforcing a robust CI/CD gate.

### Testing Architecture
- **Suite Separation**: Cleanly separated unit tests from functional tests using `UnitTests.xml` and `FunctionalTests.xml`. 
- **Default Behavior**: Running `mvn test` or `mvn clean verify` runs only Unit Tests by default to prevent rate limits and database bloat.
- **JaCoCo Enforcement**: Configured `jacoco-maven-plugin` to mandate 60% instruction coverage on all subsequent builds.

### CI/CD PR Gate
Created `.github/workflows/pr-gate.yml` to automatically execute unit tests and JaCoCo coverage checks on all pull requests targeting the `main` branch.

## Execute Screen Templates (2026-03-07)

Added dynamic configuration template loading to the Custom Execute screen (`/execute.html`).

### Features
- **Dynamic Template Display**: Selecting a strategy from the dropdown on the Execute screen now fetches and displays all matching configured templates from `strategies-config.json`.
- **Predefined Filter Loading**: Added a "Load Filters" button to each template card. Clicking this button automatically populates the custom execution form (Securities, Alias, nested Leg filters, Priorities, etc.) with the exact filter criteria defined in the template.
- **Improved UX**: Templates are displayed as collapsible cards matching the `/config` page layout, providing easy reference and reducing manual data entry for custom execution runs.

## Strategy Filter UI Enhancements (2026-03-06)

Expanded the custom strategy execution UI to include comprehensive filter coverage.

### Features
- **Dynamic Leg Filters**: Added a full suite of 8 filter fields (Delta, Premium, OI, Volume, Volatility) for every leg in all strategies (Credit Spreads, Iron Condors, BWB, Zebra, LEAPs).
- **LEAP Specifics**: Added missing `marginInterestRate`, `savingsInterestRate`, `minCostSavingsPercent`, `minCostEfficiencyPercent`, and versioned priority lists (`sortPriority`, `relaxationPriority`).
- **Common Filters**: Added `Max Total Debit`, `Max Total Credit`, and `Min Extrinsic %` to the common filter grid.
- **Robust Parsing**: Updated `app.js` to handle nested objects (e.g., `shortLeg.minDelta`) and comma-separated array conversion for priority lists.
- **Backend Sync**: Implemented missing filter logic in `AbstractTradingStrategy.java` and all concrete strategies (`Put/Call Credit Spreads`, `IronCondor`, `LongCallLeap`, `Zebra`) for `maxTotalDebit`, `maxTotalCredit`, and `minTotalCredit`.

## Securities Configuration Viewer Section (2026-03-06)

Added the ability to view the actual list of stock symbols directly on the Configuration viewer screen.

### Backend
- **`StrategyExecutionService.java`**: Exposed `loadSecuritiesMaps()` as a public method.
- **`StrategyController.java`**: Added a new `/api/securities` endpoint that returns a JSON map of all available security files (e.g., `portfolio`, `top100`) to their respective array of ticker symbols.

### Frontend
- **`app.js`**: Updated `initConfigPage()` to fetch from both `/api/config` and `/api/securities` concurrently.
- Added a dedicated "Securities" section between Options Strategies and Technical Screeners. Each security file is rendered as a collapsible card showing the file name, symbol count badge, and the full comma-separated list of symbols.

## Strategy Descriptions & Info Modal (2026-03-06)

Added the ability to present detailed strategy descriptions in the Static UI through a Markdown modal. 

### Architecture
- **Backend**: Added `descriptionFile` field to `StrategiesConfig.StrategyEntry` and mapped it through `StrategyResult` DTO.
- **API**: Exposed `descriptionFile` at `/api/strategies` and `/api/results`.
- **Frontend**: Integrated `marked.js` to parse markdown descriptions dynamically.
- **UI**: Added a custom `info-btn` icon to both the Strategy Results cards (dashboard) and the Configuration viewer page. 

### Features
- Clicking the "ℹ️" button performs an asynchronous fetch to a local markdown file, parses the content using `marked.parse()`, and displays the HTML in a custom dark-mode modal. 
- The description Markdown files are decoupled from `strategies-config.json` configuration blocks for cleaner code and maintainable text.

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


## Vaadin UI Code Simplification (2026-03-05)

Extracted shared UI components from `MainView.java` and `ExecuteStrategyView.java` to eliminate code duplication and reduce file sizes.

### Shared Components Created
- **`TradeGridBuilder.java`** [NEW]: Centralized trade grid builder with all columns (TICKER, PRICE, TYPE, EXPIRY, CREDIT/DEBIT, MAX LOSS, BREAKEVEN, ROR%), cell renderers, click-to-dialog behavior, and empty state component.
- **`ResultCardBuilder.java`** [NEW]: Strategy result card using Vaadin's built-in `Details` component for expand/collapse (replacing ~50 lines of manual toggle logic per view). Includes filter JSON parsing and time-ago formatting.

### Line Count Reduction
| File | Before | After | Reduction |
|------|--------|-------|-----------|
| `MainView.java` | 1003 | 506 | **~50%** |
| `ExecuteStrategyView.java` | 892 | 634 | **~29%** |
| **Total removed** | — | — | **~755 lines** |

### What was Eliminated
- Duplicated `createTradeGrid()` method (~160 lines × 2 views)
- Duplicated result card builder (~140 lines × 2 views)
- `getStrategyParamsFromJson()` filter parser (moved to `ResultCardBuilder.parseFilterParams()`)
- Manual expand/collapse toggle logic (replaced by Vaadin `Details` component)
- Dead `updateResults()` method in MainView
- Duplicated `createEmptyState()` method


Refactored all Vaadin frontend views for consistent responsive behavior across Desktop, iPad, and Mobile — without complex CSS.

### Problem
- Layouts used rigid `HorizontalLayout` containers that didn't reflow on smaller screens
- Complex CSS with `@media` queries, shadow DOM selectors (`[part="group-field"]`), and layout-specific overrides were fragile and hard to maintain
- Fixing desktop broke mobile and vice versa

### Solution: Stack-First Layouts
- **MainView.java**: Replaced `HorizontalLayout topRow` + `HorizontalLayout bottomRow` with a single `VerticalLayout` that stacks: Title → Subtitle → Execute/Stop buttons → Label → CheckboxGroup → Select/Clear buttons
- **ExecuteStrategyView.java**: Same pattern — replaced `HorizontalLayout topRow` with stacked elements in the parent `VerticalLayout`
- **StrategyConfigView.java** & **MainLayout.java**: Already responsive (CSS Grid with `auto-fill` and Vaadin `AppLayout`) — no changes

### CSS Simplification
- Removed **~90 lines** of complex responsive CSS
- Removed all `.strategy-top-row`, `.strategy-bottom-row`, `.strategy-action-buttons` rules
- Removed all `vaadin-checkbox-group` shadow DOM selectors
- Removed tablet `@media` query entirely
- Kept only **5 simple mobile overrides** (tighter padding, edge-to-edge panels, stacked card headers, grid horizontal scroll, scan status padding)

### Files Modified
- **`MainView.java`**: Replaced `createStrategySelector()` layout structure
- **`ExecuteStrategyView.java`**: Replaced `createConfigPanel()` top row
- **`frontend/styles/styles.css`**: Simplified responsive section



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

## Underlying Price UI Column (2026-02-26)

Added a dedicated "PRICE" column to all trading grids across the platform to easily view the underlying stock price for a given strategy.

### Files Modified
- **`MainView.java`**: Added `$%.2f` formatted `PRICE` column after `TICKER`
- **`ExecuteStrategyView.java`**: Added `$%.2f` formatted `PRICE` column after `TICKER` 
- **`docs/app.js`**: Inserted `<th>Price</th>` and corresponding data cell with `trade.underlyingPrice` to match the Java grids.

---

## Net Extrinsic Value Percentage Formula Change (2026-02-26)

Changed the `netExtrinsicValueToPricePercentage` formula from a price-based calculation to an annualized max-loss-based calculation, and renamed the method/field across the codebase.

### Old Formula
```
(Net Extrinsic Value / Underlying Price) × 100
```

### New Formula
```
(Net Extrinsic Value / Max Loss) × (365 / DTE)
```

This normalizes extrinsic value against risk capital and annualizes it, providing a more meaningful metric for comparing trades across different underlyings and expiration dates.

### Method/Field Rename
- `getNetExtrinsicValueToPricePercentage()` → `getAnulizedNetExtrinsicValueToCapitalPercentage()`
- `netExtrinsicValueToPricePercentage` field → `anulizedNetExtrinsicValueToCapitalPercentage`

### IronCondor Refactoring
- Removed inline net extrinsic value calculation from `IronCondorStrategy`
- IronCondor now builds the `IronCondor` object first, then uses the common `TradeSetup.getAnulizedNetExtrinsicValueToCapitalPercentage()` method for filtering
- All 6 strategies consistently use the common filter predicates from `AbstractTradingStrategy`

### Filter Availability
The `maxNetExtrinsicValueToPricePercentage` and `minNetExtrinsicValueToPricePercentage` filter fields in `strategies-config.json` are applied to **all** strategies:
- PutCreditSpread, CallCreditSpread, IronCondor, BrokenWingButterfly, LongCallLeap, Zebra

### Files Modified
- **`TradeSetup.java`**: Renamed method to `getAnulizedNetExtrinsicValueToCapitalPercentage()`
- **`IronCondorStrategy.java`**: Removed inline calculation, uses common method
- **`AbstractTradingStrategy.java`**: Updated common filter predicates
- **`Trade.java`**: Renamed field to `anulizedNetExtrinsicValueToCapitalPercentage`
- **`MainView.java`**: Updated getter call
- **`ExecuteStrategyView.java`**: Updated getter call
- **`TelegramUtils.java`**: Updated getter call
- **`docs/app.js`**: Updated field reference

---

## Oracle Cloud Deployment Guide (2026-02-26)

Added `ORACLE_CLOUD_DEPLOYMENT.md` with step-by-step instructions for deploying the Vaadin web app to an Oracle Cloud Infrastructure (OCI) Always-Free compute instance.

### Covered Topics
- OCI Always-Free instance creation (VM.Standard.A1.Flex recommended)
- Security List ingress rules and OS firewall configuration
- Java 17, Maven, and Git installation (Oracle Linux & Ubuntu)
- Building the production JAR with Vaadin `-Pproduction` profile
- Systemd service for auto-start, restart-on-failure, and log management  
- Environment variable configuration via `.env` file
- Optional Nginx reverse proxy with WebSocket support for Vaadin Push
- Let's Encrypt SSL/TLS via Certbot
- Appendix: Vaadin production Maven profile and `application-production.properties`
- Troubleshooting table and security recommendations

### Files
- **`ORACLE_CLOUD_DEPLOYMENT.md`** [NEW]: Full deployment guide

---

## Strategy Configuration Viewer (2026-02-25)

Added a read-only configuration viewer screen at `/config` that displays the full `strategies-config.json` content in a collapsible, UI-friendly layout.

### Features
- **Options Strategies**: Each of the 12 strategies is shown as a collapsible dark card with enabled/disabled pill, alias, strategy type badge, and securities source. Expanding reveals all filter parameters (DTE, Max Loss, Delta, OI, etc.) in a clean CSS grid with auto-formatted labels. Nested leg filters (shortLeg, longLeg, etc.) render as labeled sub-sections. Array fields (relaxationPriority, sortPriority) display as chip badges.
- **Technical Screeners**: 5 collapsible cards showing RSI/Bollinger conditions and volume filters.
- **Technical Indicators**: Static info card with RSI Period, thresholds, and Bollinger settings.

### Files
- **`StrategyConfigView.java`** [NEW]: Vaadin view at `@Route("config")` using `MainLayout`. Reads `strategies-config.json` via Jackson, iterates the POJO structure to build collapsible cards.
- **`MainLayout.java`**: Added COG icon sidebar link to the config view, replacing the placeholder. Optimized drawer layout using flex `Div`s to prevent horizontal scrolling.

### Visual Refinements
- **Sidebar**: Nav items use plain `Div` elements with `display: flex` and `overflow: hidden` to fit icon and text perfectly without triggering horizontal scrollbars.
- **Config Cards**: Headers are split into a compact two-line structure (Alias/Type on top, explicitly green `hsla(145, 65%, 42%)` Enabled badge on the bottom). Collapsible sections use `display: none` instead of `max-height` for layout stability.

### Net Extrinsic Value Display
- **Backend Model**: Updated `Trade.java` to map `netExtrinsicValue` and `netExtrinsicValueToPricePercentage` into the JSON payload and stringified details block. Ensured `PutCreditSpreadStrategy.java` correctly retrieves and sets these values on trade building.
- **Telegram ALerts**: Modified `TelegramUtils.java` to inject the `Extrinsic: $X (Y%)` inline right below `Max Loss`.
- **UI & Web**: Added the inline Extrinsic label rendering into the TYPE grid column within `ExecuteStrategyView.java` and `docs/app.js`, bringing data parity across all interfaces.

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

## Inline Securities in Strategy Config (2026-02-23)

Added support for specifying individual stock symbols directly in `strategies-config.json` via a new `securities` key, alongside the existing `securitiesFile` option.

### Usage
```json
{
    "strategyType": "PUT_CREDIT_SPREAD",
    "securitiesFile": "portfolio, tracking",
    "securities": "GOOG, AMZN, META",
    "filter": { ... }
}
```

### Behavior
- **Both fields are optional** and work independently or together
- The final securities list is the **combined, deduplicated** union of file-based and inline symbols
- Uses `LinkedHashSet` to preserve insertion order while ensuring uniqueness
- If a symbol appears in both a file and inline, it's included only once

### Files Modified
- `StrategiesConfig.java`: Added `securities` field to `StrategyEntry`
- `StrategiesConfigLoader.java`: Updated `convertToOptionsConfig()` to parse and combine inline securities

---

## Schwab API Buffer Overflow Fix (2026-02-20)

Fixed a `502 Bad Gateway` error (`protocol.http.TooBigBody`) from the Schwab API Apigee gateway when fetching option chains for major ETFs like QQQ and SPY. 
- **Cause**: QQQ's near-daily expirations and densely packed strikes resulted in massive JSON payloads exceeding the API's buffer size.
- **Fix**: Appended `strikeCount=200` to `ThinkOrSwinAPIs.getOptionChain()` to restrict the returned option chain to 100 strikes above and below the current underlying price. This dramatically shrinks payload sizes while safely capturing the required ranges for both credit spreads and deep ITM/OTM LEAPs.

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

