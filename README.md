# Trading Bot

A Java-based options trading analysis bot that integrates with the Schwab API to analyze various options strategies.

## Features

- **Options Chain Analysis**: Fetch and analyze options data from Schwab API with an adaptive retry mechanism (supporting automatic fallback down to `strikeCount = 50` on `502 Body buffer overflow` errors)
- **Multiple Trading Strategies**:
  - Put Credit Spread (PCS)
  - Call Credit Spread (CCS)
  - Iron Condor
  - Long Call LEAP
  - RSI Bollinger Bull Put Spread (oversold signal-based)
  - RSI Bollinger Bear Call Spread (overbought signal-based)
  - Bullish Broken Wing Butterfly (3-leg directional strategy)
  - Bullish ZEBRA (Zero Extrinsic Back Ratio Spread)
  - **OTM Short Put** (single-leg naked / cash-secured put; configurable delta, DTE, and max loss)
  - **Short Strangle** (two-leg naked put and call; configurable delta ranges per leg, DTE, and max loss)

- **Technical Screeners**:
  - RSI Bollinger Crossovers (Bullish/Bearish)
  - Moving Average Crossovers
  - Average True Range (ATR) Volatility filter
  - Multi-day Price Drop (Selectable lookback from 0-N days, with pre-configured Intraday Drop, 5-Day Drop, 1-Month Drop, and 3-Month Drop templates)
  - 52-Week High Drop (Percentage decline from yearly high)
- **Unified Mathematical Filter System**: Options strategies, individual option legs, and technical screeners use a unified, declarative mathematical expression filtering engine (`MathExpression`, `MathExpressionParser`, `MathExpressionEvaluator`). Filter criteria are defined via declarative math expressions under `conditions: [...]` (e.g. `DTE >= 25`, `MAX_LOSS <= 1000`, `RETURN_ON_RISK >= 12%`, `SHORT_LEG.DELTA <= 0.2`, `DAYS_TO_NEXT_EARNINGS >= DTE`), with support for arithmetic offsets (`DTE - 10`), percentages, and multi-leg dotted navigation. All historical strategy execution audit logs in Supabase (`strategy_executions`), custom execution history (`custom_execution_results`), and latest strategy results (`latest_strategy_results`) have been migrated from legacy property names (`minDTE`, `maxLossLimit`, etc.) to the unified expression condition syntax.
- **Strategy History & Similar Trades**: Synchronously records historical trade executions into Supabase (`historical_trades` table) upon strategy completion using SHA-256 deterministic trade hashes for duplicate prevention. Trade hashes incorporate strategy ID, ticker symbol, expiry date, calendar day, and full leg details (`action`, `optionType`, `strike`, `quantity`) to ensure distinct strike setups on the same ticker and expiry are uniquely persisted. Uses PostgREST upsert (`on_conflict=trade_hash` with `resolution=merge-duplicates`) so that when the same trade opportunity is scanned multiple times on a specific day (e.g. morning vs. evening execution), the **latest** trade details (such as updated underlying price, return on risk, net credit, and execution timestamp) are saved into the history table rather than retaining stale earlier entries. In-memory batch deduplication prevents intra-batch PostgreSQL unique constraint conflicts. Any database save failure is recorded with `ERROR` severity and immediately surfaced on the frontend dashboard and fails scheduled GitHub Action workflows (exit code `1`). Each trade row on the dashboard includes a **History button (🕒)** that opens an interactive modal listing matching historical trades filtered dynamically by symbol, expiry date, and leg structure, with automatic candidate deduplication and a dedicated **Date Found** column.


- **Term-Type Grouping & Single-Click Expand/Collapse**: Strategy execution results on the Options Dashboard are categorized and ordered by term duration (Extra Long, Long, Medium 2, Medium, Short, Daily, Other). All term groups are rendered collapsed by default to keep the dashboard uncluttered. A header action button allows collapsing or expanding all term categories with a single click, dynamically synchronizing its state (`▶ Expand All` / `▼ Collapse All`) with manual individual group interactions and search filter operations.
- **Greek Exposure Pill Labels**: Every strategy card on the Options Dashboard displays four colored Greek indicator pills — Δ (Delta), Γ (Gamma), Θ (Theta), V (Vega) — color-coded green for positive exposure, red for negative, and gray for neutral. Static Greek polarities are configured centrally by strategy type in `strategy-greeks.yml` (reducing boilerplate in `strategies-config.yml`) and flow through the full stack, persisted as part of the `filterConfig` JSON in Supabase.

- **Earnings Calendar**: A dedicated monthly calendar view (`/earnings-calendar.html`) sourced from the local `earnings_cache.json`. Displays all cached earnings events as color-coded chips (BMO = amber, AMC = purple) on their respective dates. Click any day to see a detailed table of that day's events including symbol, reporting timing, quarter, EPS and revenue estimates/actuals. Navigate months with Prev/Next or jump to today.
- **Securities Analysis Screen & Indicator Tracker**: A dedicated screen (`/securities.html`) under Research that dynamically discovers all securities files (`securities/*.yaml`) and renders collapsible blocks. Expanding a block lazy-loads precalculated technical indicators stored in Supabase with zero live Schwab API overhead. Securities are displayed in modern, responsive non-tabular cards featuring dynamic indicator trackers (SMAs, EMAs, RSI, Bollinger Bands, Volume, Volatility), price action progression flows, and trading playbook callouts configured via `securities-filters.yml`.

- **Robust Architecture**: Full Spring Dependency Injection (DI) system with standardized constructor-based bean management (via Lombok `@RequiredArgsConstructor`) for guaranteed initialization and enhanced testability. Strictly immutable Data Transfer Objects (DTOs) and standardized service layers ensure thread-safe concurrent execution. Extensive use of Lombok annotations (e.g. `@Data`, `@Builder`, `@ToString`) and Apache Commons utilities (e.g. `CollectionUtils`, `StringUtils`) eliminates boilerplate code and ensures resilient null/empty evaluations.
- **High Test Coverage**: Comprehensive TestNG unit test suite integrated with `jacoco-maven-plugin` enforcing a minimum of **85.00%** instruction coverage across all core business logic, services, technical indicators, and REST APIs. Fully integrated Node.js / Jest unit test suite (`npm test`) enforcing JSDOM-based DOM rendering, authentication flows, trade tables, filter grids, and interactive dashboard state with **285 unit tests** exceeding **87.7%** statement coverage across modular frontend JS components (`utils.js`, `auth-api.js`, `dashboard.js`, `screener-execute.js`, `custom-execute.js`, `config-page.js`, `logs-page.js`, `earnings-calendar.js`, `theme.js`, `app.js`).

## Prerequisites

- Java 21+
- Maven
- Schwab Developer Account (for API access)
- Telegram Account (for notifications)

## Configuration

### Schwab API Setup

1. Register for a Schwab Developer account
2. Create an application to get your `app_key` and `pp_secret`
3. Run the main method in `SchwabTokenGenerator` to generate a refresh token

### Telegram Bot Setup

To receive trade alerts via Telegram:

#### Step 1: Create a Telegram Bot

1. Open Telegram and search for **@BotFather**
2. Start a chat and send `/newbot`
3. Follow the prompts to choose a name and username
4. Copy the **Bot Token** provided by BotFather

#### Step 2: Get Your Chat ID

**Option A (Recommended):** Use @userinfobot

1. Search for **@userinfobot** in Telegram
2. Send `/start`
3. Copy the **User ID** - this is your Chat ID

**Option B:** Use getUpdates API

1. Start a chat with your bot and send any message
2. Open: `https://api.telegram.org/botYOUR_TOKEN/getUpdates`
3. Find `"chat":{"id":XXXXXXXXX}` in the response

#### Step 3: Update Configuration

Add to `src/test/resources/test.properties`:

```properties
telegram_bot_token=YOUR_BOT_TOKEN_HERE
telegram_chat_id=YOUR_CHAT_ID_HERE
```

### IV Data Tracking & Filtering

The bot can automatically collect and store daily Implied Volatility (IV) data for your securities. This data is used for calculating **IV Rank and IV Percentile** for better trade timing. Users can filter trades using either metric, and the UI provides a detailed "Volatility Context (1Y)" panel when clicking on individual trades. For full calculation details, see [docs/implied-volatility.md](docs/implied-volatility.md).

**IV Rank** measures where current IV sits within its 1-year absolute high/low range:
`(currentIV - minIV) / (maxIV - minIV) × 100`

**IV Percentile** counts the percentage of trading days in the past year where IV was *lower* than today's IV. It is more robust to one-off outlier spikes (e.g., earnings panic) since it measures frequency, not distance from the extremes:
`(count of days where historical IV < current IV) / totalDays × 100`

Both metrics are computed from the same single Supabase query per symbol (shared via `IVRankCache`), so there is no extra round-trip cost for enabling both filters simultaneously. A minimum of 20 historical daily records (~1 trading month) is required to calculate IV Rank and IV Percentile; if fewer than 20 records exist for a symbol, the system fails open (allows the trade). Configure them in `strategies-config.yml` via `minIVRank`, `maxIVRank`, `minIVPercentile`, and `maxIVPercentile`. All four are available as form fields in the Execute page (`/execute.html`). In addition, individual option leg filters validate contract IV via `minVolatility` and `maxVolatility` in `LegFilter`.

**Supported Databases:**

- **Supabase**: Store data in a PostgreSQL database (see `SUPABASE_SETUP_GUIDE.md`)



# Supabase Credentials (if enabled)
supabase_url=https://YOUR_PROJECT_ID.supabase.co
# Use Service Role Key for backend write access (Security Best Practice)
supabase_service_role_key=YOUR_SERVICE_ROLE_KEY
```

**Running IV Data Collection:**

```bash
# Using quotes ensures compatibility with PowerShell and CMD
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.job.name=IV_DATA"
```

This automated test runs daily (recommended via cron/scheduler) to collect ATM IV data for PUT and CALL options (~30 DTE) for all securities in your `securities/` folder.

For detailed setup instructions:

- Supabase Database: See `SUPABASE_SETUP_GUIDE.md` (Updated for Service Role Key security)
- Authentication (Used for dashboard login): See `AUTH_SETUP_GUIDE.md`

> ⚠️ **Supabase Security**: All tables must have **Row-Level Security (RLS) enabled**. When creating new tables, always include `ENABLE ROW LEVEL SECURITY` and add an `"Allow service role full access"` policy (`FOR ALL USING (true) WITH CHECK (true)`). The backend uses the `service_role` key (server-side only) — failure to enable RLS exposes the table to public read/write/delete access. See [`enable_rls_custom_screener_results.sql`](enable_rls_custom_screener_results.sql) for the canonical policy template.

### Security Technical Indicators Persistence (`latest_security_indicators`)

During technical screener and options strategy execution (both via daily scheduled cron jobs and web UI executions), the bot evaluates **all available technical indicators in the application** (RSI 14, Bollinger Bands 20/2, SMAs 20/50/100/200, EMAs 9/21, Volume SMAs 20/50, Highs 5/20/252, ATR 14, HV Rank 20, Market Cap, and summaries).

All computed indicator values are persisted to the Supabase table `latest_security_indicators` with:
- Exactly **1 row per security** (`symbol` as Primary Key).
- Automatic **in-place upsert (`resolution=merge-duplicates`)** replacing previous indicator values whenever fresh indicators are calculated.
- Zero live API overhead when viewing the **Securities** screen, as indicator values are directly retrieved from this pre-populated database table.


## Usage

### Running Strategy Analysis

#### 1. Via the Web Interface (Static HTML/JS)

Run `mvn spring-boot:run` to start the Spring Boot application with the built-in web dashboard.
The user interface features a clean, highly structured scrollable sidebar navigation divided into logical **Options**, **Screeners**, **Research**, and **System** blocks for superior workspace organization:

- **Options Dashboard (`/index.html`)**: Exclusively monitors options strategy runs and displays checkbox filters for option strategies. Results are grouped into collapsible **Term Type** blocks (e.g., `EXTRA LONG TERM | 330+`, `MEDIUM TERM | 40 - 185`, `SHORT TERM | 25 - 55`, `DAILY | 0 - 1`) with dynamically computed min & max DTE ranges derived from the underlying strategies' filter configurations (`DTE` conditions, `targetDTE`). Strategy identity (`strategyId`) is purely based on `Alias - TermType` (or `StrategyType - TermType`), strictly decoupled from the underlying securities universe. Securities files (`securitiesFile`) and inline symbols (`securities`) are transparently surfaced inside the collapsible **Filter Details** section of each card. Applied technical filters (`technicalFilters`) are parsed and rendered as formatted indicator blocks with human-readable conditions, thresholds, periods, and lookback windows (preventing raw `[object Object]` values). This guarantees that trade deduplication hashes in `historical_trades` and the similar trades popup remain 100% stable even if securities lists or files are added, modified, or renamed. Each trade table includes a live **"Today"** performance column (color-coded `+$X.XX (+Y.YY%)` / `-$X.XX (-Y.YY%)`) fetched from Schwab in real time, sortable by today's % change.

- **Screeners Dashboard (`/screeners.html`)**: Exclusively displays technical stock screener results and allows quick execution/cancel triggers for screeners.
- **Execute Strategy (`/execute.html`)**: Build custom configurations for any strategy type, dynamically compose trade-level and leg-specific filter criteria using mathematical expression rules (`conditions: [...]`), adjust strategy options, and view interactive "speech balloon" tooltips for every field. Dynamic condition builder rows feature inline **ℹ Info** icons next to the variable selector that update in real time to explain whichever variable is currently selected, complemented by native option tooltips while browsing dropdown entries. Form input fields feature lightened, theme-aware placeholder styling (`--text-placeholder`) so unpopulated parameter defaults/hints are immediately distinguishable from actual entered filter values. Configurable **Earnings Filters** replace the legacy `ignoreEarnings` checkbox with preset rules (e.g. *No Earnings Before Expiration*, *Safe Close*, *Capture Earnings Premium*) and custom expression conditions (`DAYS_TO_NEXT_EARNINGS`, `EARNINGS_NEAREST_TO_DTE`, `DTE`). Previous execution results show collapsible filter details, an **"⬆ Load"** button to quickly reload parameters (trade conditions, leg conditions, scalar inputs, and technical filters) into the form, a single-click **"▶ Execute"** button to reload parameters and re-execute the custom strategy while replacing results in-place within the same result card (without creating a duplicate card), and a **"🗑 Delete"** button.
- **Execute Screener (`/execute-screener.html`)**: Run one-off technical screeners with fully configurable conditions (RSI, Bollinger, Moving Averages, Price Drop, etc.) without modifying `strategies-config.yml`. Includes interactive **ℹ Info** icons and hover tooltips for Screener Types, Securities, and all Technical Filter conditions. Configured templates and historical custom screener results feature a seamless **"Load Filters"** button that repopulates all technical and fundamental filter inputs into the form.
- **Strategy Config (`/config.html`)**: Read-only view of all strategy configurations with full parameter descriptions including configured securities universes and technical filters.
- **Execution Logs (`/logs.html`)**: Real-time per-filter logs showing exactly where trade candidates are being discarded (e.g. Delta filter, Volume filter, DTE constraints) to help debug filter configurations.
- **Swagger API Docs (`/swagger-ui.html`)**: Interactive REST API documentation.

All frontend calls go through Spring Boot REST APIs (`/api/*`) — Supabase keys are never exposed to the browser.

#### 2. Via CLI (Scheduled Background Jobs)

```bash
# Using quotes ensures compatibility with PowerShell and CMD
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.job.name=SCREENER"
```

This will:

1. Fetch options chain data for configured securities
2. Analyze trades based on your strategy filters
3. Print results to console
4. Send alerts to Telegram (if configured)

### Configuring Securities Files Per Strategy

Each strategy and screener resolves its symbol universe from three sources, all configured via `securitiesFile` and/or `securities` in `strategies-config.yml`. Multiple names can be combined with commas; duplicates are automatically deduplicated.

```json
{
  "alias": "My Strategy",
  "strategyType": "PUT_CREDIT_SPREAD",
  "securitiesFile": "QQQ, portfolio",
  "securities": "TSLA, CRWD"
}
```

---

#### 1. Static YAML Files

Pre-defined symbol lists bundled inside the JAR (`src/main/resources/securities/`):

| Key | File | Description |
|---|---|---|
| `portfolio` | `securities/1_portfolio.yaml` | Personal holdings watchlist |
| `tracking` | `securities/2_tracking.yaml` | Tracked stocks (not yet in portfolio) |
| `bullish` | `securities/3_bullish.yaml` | High-conviction bullish candidates |
| `2026` | `securities/4_2026.yaml` | 2026 watchlist |
| `top100` | `securities/top100.yaml` | Top 100 liquid stocks by volume |

Edit the corresponding `.yaml` file to add/remove symbols.

#### 2. Inline Symbols (`securities` field)

Pass a comma-separated list of ticker symbols directly in the JSON config. These are merged with any `securitiesFile` symbols:

```json
"securities": "TSLA, CRWD, PLTR"
```

#### 3. Dynamic Wikipedia Index Lists ✨ **New**

Specify a magic keyword to fetch the live constituent list from Wikipedia at runtime. Lists are **cached in memory for 24 hours** (configurable via `securities.wiki.cache-hours` in `application.properties`). A hard `IllegalStateException` is thrown if Wikipedia is unreachable.

| Keyword | Index | Source | ~Size |
|---|---|---|---|
| `SPY` | S&P 500 | [Wikipedia – List of S&P 500 companies](https://en.wikipedia.org/wiki/List_of_S%26P_500_companies) | ~503 tickers |
| `QQQ` | Nasdaq-100 | [Wikipedia – Nasdaq-100](https://en.wikipedia.org/wiki/Nasdaq-100) | ~100 tickers |

```json
"securitiesFile": "QQQ"                  // Only Nasdaq-100
"securitiesFile": "SPY, portfolio"        // S&P 500 + personal watchlist
"securitiesFile": "QQQ, tracking, 2026"  // Mix of dynamic + static
```

**API Call Optimization:** The `OptionChainCache` ensures each symbol is fetched only once, even if it appears in multiple sources. Cache hit/miss statistics are logged at the end of every execution run.

## Securities Analysis Dashboard

A dedicated research interface accessible at `/securities.html` under the **Research → Securities** sidebar navigation.

### Key Architecture & Capabilities

1. **Dynamic File Discovery**:
   - Discovers all securities lists under `src/main/resources/securities/*.yaml` automatically (e.g. `1_portfolio.yaml`, `2_tracking.yaml`, `3_bullish.yaml`, `4_2026.yaml`, `top100.yaml`).
   - Cleanly formats filenames into human-readable titles (e.g. `1_portfolio.yaml` → `Portfolio`, `top100.yaml` → `Top 100`).
   - Renders collapsible accordion blocks displaying file badge and symbol count.

2. **Supabase Precalculated Indicator Sourcing**:
   - Zero live Schwab API calls are initiated on page load or block expansion.
   - All indicators are precalculated universally during daily scheduled screener jobs and manual screener runs via `TechnicalIndicatorPreCalculationService`.
   - Indicators and their respective configuration parameters are bundled together and upserted into the `latest_security_indicators` table (1 row per security).
   - Multi-config ready: Columns store JSONB structures keyed by configuration name (e.g. `default`: `{ period: 14, oversoldThreshold: 30.0, ... }`).

3. **Non-Tabular Card / Tile Grid & Calculation Specifications**:
   - When a block expands, each security is presented in a modern, 2-column card layout:
     - **Left Column**:
       - Alert pill badge (e.g. `⚡ PUSH PAST DAILY 50 SMA ($219.37)`, `⚡ RSI OVERSOLD (28.4)`, `⚡ TOUCHING LOWER BB`).
       - Ticker label and large bold company name.
       - Technical narrative summary.
       - **Price Action Progression**: Visual step sequence showing Support Floor (`100 SMA` / `50 SMA` / `BB Lower`) → Intermediate Reference (`50 SMA` / `20 SMA` / `BB Mid`) → **Latest Close** highlighted in a glowing emerald green pill box.
     - **Right Column**:
       - **Dynamic Indicator Tracker**: Metrics list with Moving Averages (`SMA 20/50/100/200`, `EMA 9/21/50`), RSI status badges, Bollinger Bands, Volume / Volume SMA, and Volatility (`ATR 14`, `HV Rank 20`).
       - **Trading Playbook**: Key signals and tactical triggers (e.g. momentum upside targets and support levels).

   - **Detailed Calculation Conditions & Priority Logic** (`securities.js`):
     - **Primary Alert Pill (Top Badge) — Priority Waterfall**:
       | Priority | Condition | Badge Text | Style / Color |
       | :--- | :--- | :--- | :--- |
       | **1 (Highest)** | `data.rsiOversold` (`RSI < 30.0`) | `⚡ RSI OVERSOLD (${rsi})` | Danger (Red) |
       | **2** | `data.rsiBullishCrossover` (`prevRsi <= 30 && rsi > 30`) | `⚡ RSI BULLISH CROSSOVER` | Success (Green) |
       | **3** | `sma50 != null && price > sma50` | `⚡ PUSH PAST DAILY 50 SMA ($${sma50})` | Success (Green) |
       | **4** | `data.priceTouchingLowerBand` (`price <= bbLower`) | `⚡ TOUCHING LOWER BB` | Warning (Orange) |
       | **5** | `sma20 != null && price > sma20` | `⚡ ABOVE DAILY 20 SMA ($${sma20})` | Success (Green) |
       | **Fallback** | None of the above | `⚡ MOMENTUM TRACKER` | Neutral (Slate) |

     - **Dynamic Indicator Tracker Status Badges**:
       - `Daily EMA 50 / Daily SMA 50`: Displays `(Cleared)` in green when `price > ema50 && price > sma50`.
       - `Daily EMA 21 / Daily SMA 20`: Displays `(Cleared)` in green when `price > ema21 && price > sma20`.
       - `RSI (14)`: Displays `Oversold` (`rsi < 30`), `Overbought` (`rsi > 70`), or `Neutral` (`30 <= rsi <= 70`).
       - `Bollinger Bands (20, 2σ)`: Displays `At Lower` (`priceTouchingLowerBand`), `At Upper` (`priceTouchingUpperBand`), or `Inside`.

     - **Trading Playbook Content — Priority Decision Tree**:
       | Priority | Condition | Tactical Message Generated | Formulas / Variables |
       | :--- | :--- | :--- | :--- |
       | **1 (Momentum)** | `sma50 && price > sma50` | `Reclaiming $${sma50} activates upside momentum toward $${targetPrice}. Catch dips into $${dipFloor}-$${dipCeiling}.` | `targetPrice = round(price * 1.10)`<br>`dipFloor = round(sma100 || sma50 * 0.96)`<br>`dipCeiling = round(sma50)` |
       | **2 (Oversold)** | `data.rsiOversold` | `RSI reached oversold threshold (${rsi}). Watch for reversal wick near $${reversalLevel}.` | `reversalLevel = bbLower || round(price * 0.98)` |
       | **3 (Reversion)** | `data.priceTouchingLowerBand` | `Testing Lower Bollinger Band ($${bbLower}). Mean reversion target towards BB Middle ($${bbMiddle}).` | `bbMiddle = bbMiddle || price` |
       | **4 (Default)** | Fallback | `Consolidating above support $${floorVal}. Trend strength confirmed while above $${midVal}.` | `floorVal` & `midVal` from Price Action Progression |

     - **Price Action Progression Hierarchy**:
       - `Support Floor`: Highest priority to `100 SMA Floor` (`$${sma100}`); falls back to `50 SMA Floor` (`$${sma50}`), then `BB Lower` (`$${bbLower}`).
       - `Intermediate Reference`: If both 50 & 100 SMA exist, evaluates `50 SMA` (`$${sma50}`); else `20 SMA` (`$${sma20}`), else `BB Mid` (`$${bbMiddle}`), else `Prev Ref`.
       - `Latest Close`: Highlighted current close price (`$${price}`).

4. **Master Indicator Catalog & Whitelist Registry (`securities-filters.yml`)**:
   - `securities-filters.yml` serves as the authoritative single source of truth for all supported technical indicators, allowed periods, and universal pre-calculation:
     ```yaml
     filters:
       rsi:
         enabled: true
         period: 14
         periods: [14]             # Allowed RSI periods across all strategies/screeners
         oversold: 30.0
         overbought: 70.0
       bollinger:
         enabled: true
         period: 20
         periods: [20]             # Allowed Bollinger Bands periods
         stdDev: 2.0
       moving_averages:
         enabled: true
         periods: [20, 50, 100, 200] # Allowed SMA periods
       exponential_moving_averages:
         enabled: true
         periods: [9, 21, 50]      # Allowed EMA periods
       volume:
         enabled: true
         sma_periods: [20, 50]     # Allowed Volume SMA periods
       volatility:
         enabled: true
         atr_period: 14
         atr_periods: [14]         # Allowed ATR periods
         hv_period: 20
         hv_periods: [20]          # Allowed Historical Volatility periods
       highs:
         enabled: true
         periods: [5, 20, 252]     # Allowed High/Drop periods (HIGH_5D, HIGH_20D, HIGH_252D, etc.)
     ```
   - **Startup & Runtime Validation**:
     - `StrategiesConfigLoader` automatically validates all strategy and screener rules against `securities-filters.yml` during Spring Boot startup (`@PostConstruct init()`). If an invalid indicator or unconfigured period (e.g. `SMA17` or `RSI period 7`) is defined in `strategies-config.yml`, the application fails fast immediately on bootstrap.
     - Custom screener executions submitted via REST (`POST /api/screeners/execute/custom-screener`) are validated at runtime; any unconfigured indicator or period rejects the request with `400 Bad Request`.
   - **Deterministic Pre-Calculation**:
     - `TechnicalIndicatorPreCalculationService` queries `SecuritiesFilterConfig` to generate the complete universal technical indicator suite and filter conditions, guaranteeing that all whitelisted indicators are calculated and cached into Supabase in a single pass.

5. **REST API Endpoints**:
   - `GET /api/securities/groups`: Returns discovered securities blocks metadata (`id`, `fileName`, `displayName`, `symbolCount`, `symbols`).
   - `GET /api/securities/filter-config`: Returns parsed active filter settings.
   - `POST /api/securities/data`: Accepts `{ "symbols": ["NVDA", "AAPL", ...] }` and returns precalculated indicators from Supabase.

## Technical Indicator Strategies

### RSI Bollinger Bull Put Spread

Triggered when **oversold conditions** are detected:

- RSI (14-day) < 30
- Price touching or below Lower Bollinger Band (20-day, 2 SD)
- Volume >= 100,000 shares (real-time via Quotes API, configurable)

**Trade Setup:**

- Sell Put at ~30 Delta (below current price)
- Buy Put at ~15-20 Delta (further below)
- DTE: 30 days

### RSI Bollinger Bear Call Spread

Triggered when **overbought conditions** are detected:

- RSI (14-day) > 70
- Price touching or above Upper Bollinger Band (20-day, 2 SD)
- Volume >= 100,000 shares (real-time via Quotes API, configurable)

**Trade Setup:**

- Sell Call at ~30 Delta (above current price)
- Buy Call at ~15-20 Delta (further above)
- DTE: 30 days

### Configuring Technical Filters

Technical filters are configured in **3 steps** for clear separation:

```java
// STEP 1: Define WHAT indicators to use (with their settings)
TechnicalIndicators indicators = TechnicalIndicators.builder()
    .rsiFilter(RSIFilter.builder()
        .period(14)
        .oversoldThreshold(30.0)   // RSI < 30 = Oversold
        .overboughtThreshold(70.0) // RSI > 70 = Overbought
        .build())
    .bollingerFilter(BollingerBandsFilter.builder()
        .period(20)
        .standardDeviations(2.0)
        .build())
    .volumeFilter(VolumeFilter.builder().build()) // Volume indicator is used
    .build();

// STEP 2: Define WHAT CONDITIONS to look for (separate from indicators)
FilterConditions oversoldConditions = FilterConditions.builder()
    .rsiCondition(RSICondition.OVERSOLD)           // RSI < 30
    .bollingerCondition(BollingerCondition.LOWER_BAND)  // Price at lower band
    .minVolume(1_000_000L)                         // Minimum 1M shares
    .build();

FilterConditions overboughtConditions = FilterConditions.builder()
    .rsiCondition(RSICondition.OVERBOUGHT)         // RSI > 70
    .bollingerCondition(BollingerCondition.UPPER_BAND)  // Price at upper band
    .minVolume(1_000_000L)                         // Minimum 1M shares
    .build();

// STEP 3: Combine indicators + conditions into filter chains
TechnicalFilterChain oversoldFilterChain = TechnicalFilterChain.of(indicators, oversoldConditions);
TechnicalFilterChain overboughtFilterChain = TechnicalFilterChain.of(indicators, overboughtConditions);
```

### Options Strategy Mathematical Expression Filters (`conditions`)

Options strategy filters are configured using declarative mathematical expressions under `conditions: [...]` in `strategies-config.yml`. This unifies technical indicators and options filters under the same `MathExpression` evaluation engine, removing fragmented property fields and hardcoded thresholds.

#### Supported Operators
- `>=`, `<=`, `>`, `<`, `==`, `!=`
- Percentage scaling: `* 90%` (e.g., `VOLUME_SMA20 >= VOLUME_SMA50 * 90%`)
- Arithmetic offsets: `+ X`, `- X` (e.g., `EARNINGS_NEAREST_TO_DTE <= DTE - 10`)
- Literal percentage values: `ROR >= 12%`, `IV_PERCENTILE >= 30%`

#### Supported Variables

| Category | Variable | Description |
|:---|:---|:---|
| **Expiry & IV** | `DTE`, `DAYS_TO_EXPIRATION` | Days until option contract expiration |
| | `IV_RANK` | Implied Volatility rank (0–100) from historical IV cache |
| | `IV_PERCENTILE` | Implied Volatility percentile (0–100) from historical IV cache |
| **Earnings** | `DAYS_TO_NEXT_EARNINGS` | Calendar days until the company's next earnings announcement |
| | `EARNINGS_NEAREST_TO_DTE` | Days until the earnings event closest to the expiry date |
| **Trade Risk & Return** | `MAX_LOSS` | Maximum dollar loss for the trade setup |
| | `NET_CREDIT`, `CREDIT` | Total credit received for the trade |
| | `NET_DEBIT`, `DEBIT` | Total debit paid for the trade |
| | `ROR`, `RETURN_ON_RISK` | Return on risk percentage (`netCredit / maxLoss * 100`) |
| | `CAGR`, `ROR_CAGR` | Annualized compound return on risk percentage |
| | `BREAK_EVEN_PRICE`, `BREAK_EVEN` | Trade breakeven stock price |
| | `BREAK_EVEN_PCT` | Percentage distance from current stock price to breakeven |
| | `UPPER_BREAK_EVEN_PRICE` | Upper breakeven price (Strangle, Iron Condor, BWB) |
| | `UPPER_BREAK_EVEN_PCT` | Upper breakeven percentage distance |
| | `ANNUALIZED_EXTRINSIC_PCT` | Annualized net extrinsic value to capital percentage |
| | `CURRENT_PRICE`, `PRICE` | Current underlying stock price |
| **Leg Metrics** | `DELTA`, `ABS_DELTA` | Absolute value of leg delta (e.g. `0.20`) |
| *(Dotted or Leg-level)* | `RAW_DELTA`, `SIGNED_DELTA` | Signed delta (negative for puts, positive for calls) |
| | `OPEN_INTEREST`, `OI` | Contract open interest |
| | `VOLUME`, `TOTAL_VOLUME` | Contract trading volume |
| | `PREMIUM`, `MARK` | Contract mark price |
| | `BID`, `ASK` | Contract bid / ask quotes |
| | `IV`, `VOLATILITY` | Contract implied volatility |
| | `GAMMA`, `THETA`, `VEGA` | Option Greeks |
| | `STRIKE` | Option strike price |

#### Leg Prefix Routing
Multi-leg conditions can be written directly on the strategy filter using dotted notation. The parser (`FilterParser`) automatically routes leg conditions to the corresponding `LegFilter` for early pruning and attaches them to `FilterPipeline<TradeSetup>`:

| Strategy | Leg Prefix | Examples |
|:---|:---|:---|
| **Credit Spreads (PCS / CCS)** | `SHORT_LEG.*`, `LONG_LEG.*` | `SHORT_LEG.DELTA <= 0.2`, `SHORT_LEG.OPEN_INTEREST >= 500` |
| **Iron Condor** | `PUT_SHORT.*`, `PUT_LONG.*`, `CALL_SHORT.*`, `CALL_LONG.*` | `PUT_SHORT.DELTA <= 0.15`, `CALL_SHORT.DELTA <= 0.15` |
| **Short Strangle** | `PUT_SHORT.*`, `CALL_SHORT.*` | `PUT_SHORT.DELTA <= 0.2`, `CALL_SHORT.DELTA <= 0.2` |
| **Broken Wing Butterfly** | `LEG1_LONG.*`, `LEG2_SHORT.*`, `LEG3_LONG.*` | `LEG1.DELTA >= 0.50`, `LEG2_SHORT.DELTA <= 0.40` |
| **ZEBRA** | `SHORT_LEG.*`, `LONG_LEG.*` | `SHORT_LEG.DELTA >= 0.45`, `LONG_LEG.DELTA >= 0.65` |

#### YAML Configuration Examples

##### Nested Leg Objects (Recommended Standard)
```yaml
optionsStrategies:
  - alias: "PCS"
    enabled: true
    termType: "Short Term"
    strategyType: "PUT_CREDIT_SPREAD"
    filterType: "CreditSpreadFilter"
    filter:
      conditions:
        - "DTE >= 25"
        - "DTE <= 50"
        - "MAX_LOSS <= 1000"
        - "RETURN_ON_RISK >= 12%"
      shortLeg:
        conditions:
          - "DELTA <= 0.2"
          - "OPEN_INTEREST >= 500"
      earningsFilters:
        conditions:
          - "DAYS_TO_NEXT_EARNINGS >= DTE"
    securitiesFile: "portfolio"
```

##### Flat Dotted Notation (Alternative)
```yaml
    filter:
      conditions:
        - "MAX_LOSS <= 1000"
        - "SHORT_LEG.DELTA <= 0.2"
        - "SHORT_LEG.OPEN_INTEREST >= 500"
```

## Testing & CI/CD Coverage

The project enforces a **minimum 85% instruction coverage** (currently standing at **85.02%**) for all core business logic using JaCoCo. This is enforced locally during Maven verification and via GitHub Actions for any Pull Request targeting the `develop` or `main` branch.

### Unit Tests

Unit tests run locally without making real external API calls (Schwab, Supabase, Telegram are mocked).
To execute all unit tests and generate the coverage report:

```bash
mvn clean verify
# OR
mvn test
```

_Coverage reports are generated at `target/site/jacoco/index.html`._

### Test Architecture

- **Package Mirroring**: The test suite (`src/test/java`) mirrors the main source package structure (`com.hemasundar.*`) for consistent access to package-private components.
- **Suite Separation**: Unit tests are isolated from functional tests to ensure fast CI gates and prevent rate-limiting.

### Functional Tests

Functional tests interact with real external APIs. To prevent rate-limiting and unnecessary data writes, they are **excluded from the default build**.
To execute functional tests:

```bash
mvn test -DsuiteXmlFile=FunctionalTests.xml
```

## API Methods

The `ThinkOrSwinAPIs` class provides the following methods for interacting with Schwab's Market Data API:

### Option Chain

```java
// Get full option chain for a symbol (features adaptive retry loop down to strikeCount = 50 on 502 buffer overflows)
OptionChainResponse chain = schwabApi.getOptionChain("AAPL");
```

### Price History

```java
// Get yearly price history with daily frequency
PriceHistoryResponse history = ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1);

// Get price history with custom parameters
PriceHistoryResponse history = ThinkOrSwinAPIs.getPriceHistory(
    "AAPL", "year", 1, "daily", 1, null, null, false, true);
```

### Quotes

```java
// Get quotes for multiple symbols
Map<String, QuotesResponse.QuoteData> quotes = ThinkOrSwinAPIs.getQuotes(
    List.of("AAPL", "TSLA", "AMZN"));

// Get quote for a single symbol
QuotesResponse.QuoteData quote = ThinkOrSwinAPIs.getQuote("TSLA");

// Access quote data
long volume = quote.getQuote().getTotalVolume();
double lastPrice = quote.getQuote().getLastPrice();
```

### Expiration Chain

```java
// Get all available expiration dates for a symbol
ExpirationChainResponse expirations = ThinkOrSwinAPIs.getExpirationChain("AAPL");

// Access expiration dates
expirations.getExpirationList().forEach(exp -> {
    System.out.println(exp.getExpirationDate() + " - DTE: " + exp.getDaysToExpiration());
});
```

### Market Hours

```java
// Get market hours for all equity and option markets
MarketHoursResponse hours = ThinkOrSwinAPIs.getMarketHours();

// Get market hours for a single market type
String equityHours = ThinkOrSwinAPIs.getMarketHour("equity", "2024-04-15");
```

### Movers

```java
// Get top 10 movers for the S&P 500
String movers = ThinkOrSwinAPIs.getMovers("$SPX", "PERCENT_CHANGE_UP", 0);
```

### Instruments

```java
// Search for instruments by symbol
String results = ThinkOrSwinAPIs.getInstruments("AAPL", "symbol-search");

// Get instrument by CUSIP
String instrument = ThinkOrSwinAPIs.getInstrumentByCusip("037833100");
```

> 📄 For full API documentation, see [`SchwabAPI/schwab-market-data-api.md`](SchwabAPI/schwab-market-data-api.md)

## Strategy Dashboard

The execution results dashboard is now maintained in a separate repository. It is a static HTML/JS application deployed to GitHub Pages that fetches live strategy results from Supabase.

### Setup (Separate Repo)

1. Initialize the dashboard repository from the provided standalone export.
2. Configure GitHub Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) in the new repo.
3. Enable GitHub Pages to deploy from the new repo.

## Deployment

### Authentication (Supabase Auth)

The app uses **Supabase Auth** with Google and Apple OAuth for user authentication. API endpoints (`/api/*`) are protected with JWT verification using the **Supabase JWKS public key endpoint** — no shared secret is required.

#### How It Works

1. Users sign in via Google or Apple on the `/login.html` page
2. Supabase issues a JWT (signed with ECC P-256 / ES256)
3. The frontend automatically attaches the JWT to all API requests
4. The backend `BearerTokenFilter` fetches Supabase's public key from the JWKS endpoint (cached 24 hours) and verifies the JWT signature — **no secret needed**
5. Optionally, access is restricted to specific emails via `ALLOWED_EMAILS`

> **JWKS endpoint**: `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` (auto-derived from `SUPABASE_URL`)

#### Supabase Dashboard Setup

1. **Enable Google OAuth**: Authentication → Providers → Google → Enable → Add OAuth credentials from [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. **Enable Apple OAuth** (optional): Authentication → Providers → Apple → Enable
3. **Set Site URL**: Authentication → URL Configuration → Site URL → `https://your-cloud-run-url`
4. **Set Redirect URLs**: Authentication → URL Configuration → Add `https://your-cloud-run-url/login.html`

#### Local Development

For local development, authentication is **bypassed** when `SUPABASE_URL` is not set in the environment. All API requests pass through without JWT verification.

---

### Google Cloud Run (CI/CD via GitHub Actions)

The web app is deployed to **Google Cloud Run** automatically on every push to `main` via `.github/workflows/deploy-cloud-run.yml`.

#### First-Time GCP Setup

1. **Create a GCP project** at [console.cloud.google.com](https://console.cloud.google.com)
2. **Enable APIs** — In the GCP Console, enable:
   - Cloud Run API
   - Artifact Registry API
   - Cloud Build API
3. **Create Artifact Registry repository:**
   ```bash
   gcloud artifacts repositories create trading-bot \
     --repository-format=docker \
     --location=us-central1
   ```
4. **Create a Service Account** with the following roles:
   - Cloud Run Admin
   - Artifact Registry Writer
   - Service Account User
5. **Download the Service Account JSON key** and save its contents

For detailed GCP console screenshots and instructions, see [`CLOUD_RUN_DEPLOYMENT.md`](CLOUD_RUN_DEPLOYMENT.md).

#### Configure GitHub Secrets

Go to your repo → **Settings → Secrets and variables → Actions** → **New repository secret** and add:

| Secret                      | How to Get It                                                      |
| --------------------------- | ------------------------------------------------------------------ |
| `GCP_PROJECT_ID`            | GCP Console → Dashboard → Project ID (e.g., `my-trading-bot-123`)  |
| `GCP_SA_KEY`                | Full JSON content of the Service Account key file downloaded above |
| `SUPABASE_URL`              | Supabase Dashboard → Settings → API → Project URL                  |
| `SUPABASE_ANON_KEY`         | Supabase Dashboard → Settings → API → `anon` / `public` key        |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Dashboard → Settings → API → `service_role` key           |
| `ALLOWED_EMAILS`            | Comma-separated list of authorized Google/Apple email addresses    |

#### Deploy

After configuring secrets:

1. Push to `main` branch — GitHub Actions automatically builds, pushes, and deploys
2. Or trigger manually: **Actions → Deploy to Google Cloud Run → Run workflow**

The deployed app URL is printed at the end of the workflow:

```
https://trading-bot-<hash>-uc.a.run.app
```

#### How the Pipeline Works

```
Push to main → GitHub Actions → Docker Build → Artifact Registry → Cloud Run
                                                                    ↓
                                              HTTPS URL with env vars injected
```

1. Builds a Docker image using the multi-stage `Dockerfile`
2. Pushes to **Google Artifact Registry** (tagged with commit SHA + `latest`)
3. Deploys to Cloud Run with:
   - 512Mi RAM, 1 CPU
   - `min-instances=1` (always-on, no cold starts)
   - HTTPS enabled automatically
   - Environment variables injected from GitHub Secrets

#### Production Environment Variables

These are injected into the Cloud Run container at deploy time:

| Variable                    | Purpose                                                                                 |
| --------------------------- | --------------------------------------------------------------------------------------- |
| `SUPABASE_URL`              | Supabase REST API base URL (also used to derive the JWKS endpoint for JWT verification) |
| `SUPABASE_ANON_KEY`         | Public key for frontend auth initialization                                             |
| `SUPABASE_SERVICE_ROLE_KEY` | Admin key for backend write operations                                                  |
| `ALLOWED_EMAILS`            | Comma-separated list of authorized email addresses                                      |

---

### Oracle Cloud Free Tier

See [`ORACLE_CLOUD_DEPLOYMENT.md`](ORACLE_CLOUD_DEPLOYMENT.md) for deployment to an **Oracle Cloud Always-Free** compute instance.

## Project Structure

mvn test -DsuiteXmlFile=FunctionalTests.xml

````

## API Methods

The `ThinkOrSwinAPIs` class provides the following methods for interacting with Schwab's Market Data API:

### Option Chain
```java
// Get full option chain for a symbol
OptionChainResponse chain = ThinkOrSwinAPIs.getOptionChainResponse("AAPL");
````

### Price History

```java
// Get yearly price history with daily frequency
PriceHistoryResponse history = ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1);

// Get price history with custom parameters
PriceHistoryResponse history = ThinkOrSwinAPIs.getPriceHistory(
    "AAPL", "year", 1, "daily", 1, null, null, false, true);
```

### Quotes

```java
// Get quotes for multiple symbols
Map<String, QuotesResponse.QuoteData> quotes = ThinkOrSwinAPIs.getQuotes(
    List.of("AAPL", "TSLA", "AMZN"));

// Get quote for a single symbol
QuotesResponse.QuoteData quote = ThinkOrSwinAPIs.getQuote("TSLA");

// Access quote data
long volume = quote.getQuote().getTotalVolume();
double lastPrice = quote.getQuote().getLastPrice();
```

### Expiration Chain

```java
// Get all available expiration dates for a symbol
ExpirationChainResponse expirations = ThinkOrSwinAPIs.getExpirationChain("AAPL");

// Access expiration dates
expirations.getExpirationList().forEach(exp -> {
    System.out.println(exp.getExpirationDate() + " - DTE: " + exp.getDaysToExpiration());
});
```

### Market Hours

```java
// Get market hours for all equity and option markets
MarketHoursResponse hours = ThinkOrSwinAPIs.getMarketHours();

// Get market hours for a single market type
String equityHours = ThinkOrSwinAPIs.getMarketHour("equity", "2024-04-15");
```

### Movers

```java
// Get top 10 movers for the S&P 500
String movers = ThinkOrSwinAPIs.getMovers("$SPX", "PERCENT_CHANGE_UP", 0);
```

### Instruments

```java
// Search for instruments by symbol
String results = ThinkOrSwinAPIs.getInstruments("AAPL", "symbol-search");

// Get instrument by CUSIP
String instrument = ThinkOrSwinAPIs.getInstrumentByCusip("037833100");
```

> 📄 For full API documentation, see [`SchwabAPI/schwab-market-data-api.md`](SchwabAPI/schwab-market-data-api.md)

## Strategy Dashboard

The execution results dashboard is now maintained in a separate repository. It is a static HTML/JS application deployed to GitHub Pages that fetches live strategy results from Supabase.

### Setup (Separate Repo)

1. Initialize the dashboard repository from the provided standalone export.
2. Configure GitHub Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) in the new repo.
3. Enable GitHub Pages to deploy from the new repo.

## Deployment

### Authentication (Supabase Auth)

The app uses **Supabase Auth** with Google and Apple OAuth for user authentication. API endpoints (`/api/*`) are protected with JWT verification using the **Supabase JWKS public key endpoint** — no shared secret is required.

#### How It Works

1. Users sign in via Google or Apple on the `/login.html` page
2. Supabase issues a JWT (signed with ECC P-256 / ES256)
3. The frontend automatically attaches the JWT to all API requests
4. The backend `BearerTokenFilter` fetches Supabase's public key from the JWKS endpoint (cached 24 hours) and verifies the JWT signature — **no secret needed**
5. Optionally, access is restricted to specific emails via `ALLOWED_EMAILS`

> **JWKS endpoint**: `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` (auto-derived from `SUPABASE_URL`)

#### Supabase Dashboard Setup

1. **Enable Google OAuth**: Authentication → Providers → Google → Enable → Add OAuth credentials from [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. **Enable Apple OAuth** (optional): Authentication → Providers → Apple → Enable
3. **Set Site URL**: Authentication → URL Configuration → Site URL → `https://your-cloud-run-url`
4. **Set Redirect URLs**: Authentication → URL Configuration → Add `https://your-cloud-run-url/login.html`

#### Local Development

For local development, authentication is **bypassed** when `SUPABASE_URL` is not set in the environment. All API requests pass through without JWT verification.

---

### Google Cloud Run (CI/CD via GitHub Actions)

The web app is deployed to **Google Cloud Run** automatically on every push to `main` via `.github/workflows/deploy-cloud-run.yml`.

#### First-Time GCP Setup

1. **Create a GCP project** at [console.cloud.google.com](https://console.cloud.google.com)
2. **Enable APIs** — In the GCP Console, enable:
   - Cloud Run API
   - Artifact Registry API
   - Cloud Build API
3. **Create Artifact Registry repository:**
   ```bash
   gcloud artifacts repositories create trading-bot \
     --repository-format=docker \
     --location=us-central1
   ```
4. **Create a Service Account** with the following roles:
   - Cloud Run Admin
   - Artifact Registry Writer
   - Service Account User
5. **Download the Service Account JSON key** and save its contents

For detailed GCP console screenshots and instructions, see [`CLOUD_RUN_DEPLOYMENT.md`](CLOUD_RUN_DEPLOYMENT.md).

#### Configure GitHub Secrets

Go to your repo → **Settings → Secrets and variables → Actions** → **New repository secret** and add:

| Secret                      | How to Get It                                                      |
| --------------------------- | ------------------------------------------------------------------ |
| `GCP_PROJECT_ID`            | GCP Console → Dashboard → Project ID (e.g., `my-trading-bot-123`)  |
| `GCP_SA_KEY`                | Full JSON content of the Service Account key file downloaded above |
| `SUPABASE_URL`              | Supabase Dashboard → Settings → API → Project URL                  |
| `SUPABASE_ANON_KEY`         | Supabase Dashboard → Settings → API → `anon` / `public` key        |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Dashboard → Settings → API → `service_role` key           |
| `ALLOWED_EMAILS`            | Comma-separated list of authorized Google/Apple email addresses    |

#### Deploy

After configuring secrets:

1. Push to `main` branch — GitHub Actions automatically builds, pushes, and deploys
2. Or trigger manually: **Actions → Deploy to Google Cloud Run → Run workflow**

The deployed app URL is printed at the end of the workflow:

```
https://trading-bot-<hash>-uc.a.run.app
```

#### How the Pipeline Works

```
Push to main → GitHub Actions → Docker Build → Artifact Registry → Cloud Run
                                                                    ↓
                                              HTTPS URL with env vars injected
```

1. Builds a Docker image using the multi-stage `Dockerfile`
2. Pushes to **Google Artifact Registry** (tagged with commit SHA + `latest`)
3. Deploys to Cloud Run with:
   - 512Mi RAM, 1 CPU
   - `min-instances=1` (always-on, no cold starts)
   - HTTPS enabled automatically
   - Environment variables injected from GitHub Secrets

#### Production Environment Variables

These are injected into the Cloud Run container at deploy time:

| Variable                    | Purpose                                                                                 |
| --------------------------- | --------------------------------------------------------------------------------------- |
| `SUPABASE_URL`              | Supabase REST API base URL (also used to derive the JWKS endpoint for JWT verification) |
| `SUPABASE_ANON_KEY`         | Public key for frontend auth initialization                                             |
| `SUPABASE_SERVICE_ROLE_KEY` | Admin key for backend write operations                                                  |
| `ALLOWED_EMAILS`            | Comma-separated list of authorized email addresses                                      |

---

### Oracle Cloud Free Tier

See [`ORACLE_CLOUD_DEPLOYMENT.md`](ORACLE_CLOUD_DEPLOYMENT.md) for deployment to an **Oracle Cloud Always-Free** compute instance.

## Project Structure

```
src/
├── main/java/com/hemasundar/
│   ├── api/            # REST API controllers (StrategyController)
│   ├── apis/           # External API integrations (Schwab, FinnHub)
│   ├── config/         # Spring config (BearerTokenFilter, ServiceConfig)
│   ├── dto/            # Data Transfer Objects (Trade, StrategyResult, etc.)
│   ├── pojos/          # Data models
│   │   └── technicalfilters/  # Technical indicator filters
│   ├── services/       # Service layer (StrategyExecutionService, SupabaseService)
│   ├── strategies/     # Trading strategy implementations
│   └── utils/          # Utility classes (TelegramUtils, TechnicalIndicators, etc.)
├── main/resources/
│   └── static/         # Frontend (HTML, CSS, JS — served by Spring Boot)
└── test/
    ├── java/           # Test classes
    └── resources/      # Configuration files
```

## Logging

The application uses **Logback** (Spring Boot's native logging framework) for logging with XML configuration.

### Log Levels

| Level   | Usage                                               |
| ------- | --------------------------------------------------- |
| `DEBUG` | Technical indicators, cache operations, API details |
| `INFO`  | Strategy execution, trade signals, API calls        |
| `WARN`  | Skipped symbols, missing configurations             |
| `ERROR` | API failures, exceptions                            |

### Configuration

Logs are configured in `src/main/resources/logback-spring.xml`:

- **Console**: Displays only `WARN` level logs or higher to declutter terminal output.
- **RollingFile**: Writes `INFO` level logs and above to `logs/trading-bot.log` with daily rotation (max 7 files, 10MB each).

To change log levels for local development, edit the `logback-spring.xml` file.

Check individual service coverage locally via:

```bash
mvn clean test
# Report: target/site/jacoco/index.html
```

## Technology Stack & Architecture

- **Core**: Java 17, Spring Boot 3.2.2
- **Persistence**: Supabase (PostgreSQL)
- **Market Data**: Charles Schwab Market Data API
- **Architecture**: Domain-Driven Design (DDD) with Constructor-based Dependency Injection
- **Testing**: TestNG, Mockito, JaCoCo (85% Coverage Gate)
- **Configuration**: Standardized via `AppConfig.java`

## Dependencies

- **Spring Boot** 3.2.2 - Web framework (REST APIs + static file serving)
- **SpringDoc OpenAPI** - Swagger UI for API documentation
- RestAssured - HTTP client (also used for Supabase REST API)
- TestNG - Testing framework
- Lombok - Boilerplate reduction
- Jackson - JSON/YAML processing
- ta4j-core - Technical analysis library (RSI, Bollinger Bands, etc.)
- **Logback** - Logging framework (via `spring-boot-starter-logging`)

- **java-jwt** (Auth0) - JWT verification for Supabase Auth tokens
