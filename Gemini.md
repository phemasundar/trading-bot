# Project Updates

> **CRITICAL AI RULE**: NEVER execute `git commit` or `git push` unless explicitly requested by the user. Do not assume permission to commit changes.
> **CRITICAL AI RULE**: NEVER use GitHub MCP tools (create PR, merge, create release, etc.) unless the user explicitly asks. Do not assume permission for any GitHub operations.

## Strategy Filter UI Enhancements (2026-03-07)

Expanded the custom strategy execution UI to include comprehensive filter coverage and synchronized backend logic.

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
- **`README.md`**: Updated usage, project structure, deployment, and dependencies sections


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

Enhanced the security of the Sup
