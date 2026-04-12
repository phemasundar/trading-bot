# Trading Bot

A Java-based options trading analysis bot that integrates with the Schwab API to analyze various options strategies.

## Features

- **Options Chain Analysis**: Fetch and analyze options data from Schwab API
- **Multiple Trading Strategies**:
  - Put Credit Spread (PCS)
  - Call Credit Spread (CCS)
  - Iron Condor
  - Long Call LEAP
  - RSI Bollinger Bull Put Spread (oversold signal-based)
  - RSI Bollinger Bear Call Spread (overbought signal-based)
  - Bullish Broken Wing Butterfly (3-leg directional strategy)
  - Bullish ZEBRA (Zero Extrinsic Back Ratio Spread)
- **Technical Screeners**: 
  - RSI Bollinger Crossovers (Bullish/Bearish)
  - Moving Average Crossovers
  - Multi-day Price Drop (Selectable lookback from 0-N days)
  - 52-Week High Drop (Percentage decline from yearly high)
- **Object-Oriented Filter System**: Extensible filter hierarchy with strategy-specific and leg-specific filters
- **Technical Indicators**: RSI, Bollinger Bands, and Volume analysis using ta4j library
- **Telegram Notifications**: Receive trade alerts directly to your Telegram
- **Interactive UI Dashboard**: Execute custom strategy instances, explore strategy configurations with user-defined technical screener aliases, and view data-rich technical screening results with customizable indicator tables. Integrated Charles Schwab API provides live Market Hours status directly on the dashboard. Sort results by any column (Price, Volume, RSI, Drop %, etc.) with persistent state.
- **Robust Architecture**: Strictly immutable Data Transfer Objects (DTOs) via Lombok `@Value` and `@Jacksonized` for thread-safe concurrent execution.

## Prerequisites

- Java 17+
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

### IV Data Tracking

The bot can automatically collect and store daily Implied Volatility (IV) data for your securities. This data is used for calculating IV rank and percentile for better trade timing.

**Supported Databases:**
- **Google Sheets**: Store data in a Google Spreadsheet (see `SETUP_GUIDE.md`)
- **Supabase**: Store data in a PostgreSQL database (see `SUPABASE_SETUP_GUIDE.md`)

Both databases can be enabled simultaneously or individually configured via `test.properties`:

```properties
# Database Configuration for IV Data Collection
google_sheets_enabled=true
supabase_enabled=false

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
- Google Sheets: See `SETUP_GUIDE.md`
- Supabase Database: See `SUPABASE_SETUP_GUIDE.md` (Updated for Service Role Key security)
- Authentication (Used for dashboard login): See `AUTH_SETUP_GUIDE.md`

## Usage

### Running Strategy Analysis

#### 1. Via the Web Interface (Static HTML/JS)
Run `mvn spring-boot:run` to start the Spring Boot application with the built-in web dashboard.
- **Dashboard (`/index.html`)**: View historical results with interactive strategy descriptions.
- **Execute Strategy (`/execute.html`)**: Build custom configurations for any strategy type, set filters, and view interactive "speech balloon" tooltips for every field. Previous execution results show collapsible filter details and a "Load Filters" button to quickly reload parameters from past runs.
- **Strategy Config (`/config.html`)**: Read-only view of all strategy configurations with full parameter descriptions.
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

Each strategy can use a different securities file. This is configured in `strategies-config.json` via the `securitiesFile` property for each enabled strategy definition.

```json
{
  "strategies": [
    {
      "enabled": true,
      "type": "PUT_CREDIT_SPREAD",
      "securitiesFile": "securities"
    }
  ]
}
```

**Available securities files:**
- `securities.yaml` - Custom watchlist
- `top100.yaml` - Top 100 stocks

**API Call Optimization:** The `OptionChainCache` ensures each symbol is fetched only once, even if used by multiple strategies. At the end of execution, cache statistics are printed showing the total API calls made.

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



## Testing & CI/CD Coverage

The project enforces a **minimum 75% instruction coverage** for all core business logic using JaCoCo. This is enforced locally during Maven verification and via GitHub Actions for any Pull Request targeting the `main` branch.

### Unit Tests
Unit tests run locally without making real external API calls (Schwab, Supabase, Telegram are mocked).
To execute all unit tests and generate the coverage report:
```bash
mvn clean verify
# OR
mvn test
```
*Coverage reports are generated at `target/site/jacoco/index.html`.*

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
// Get full option chain for a symbol
OptionChainResponse chain = ThinkOrSwinAPIs.getOptionChainResponse("AAPL");
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

| Secret | How to Get It |
|---|---|
| `GCP_PROJECT_ID` | GCP Console → Dashboard → Project ID (e.g., `my-trading-bot-123`) |
| `GCP_SA_KEY` | Full JSON content of the Service Account key file downloaded above |
| `SUPABASE_URL` | Supabase Dashboard → Settings → API → Project URL |
| `SUPABASE_ANON_KEY` | Supabase Dashboard → Settings → API → `anon` / `public` key |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Dashboard → Settings → API → `service_role` key |
| `ALLOWED_EMAILS` | Comma-separated list of authorized Google/Apple email addresses |

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

| Variable | Purpose |
|---|---|
| `SUPABASE_URL` | Supabase REST API base URL (also used to derive the JWKS endpoint for JWT verification) |
| `SUPABASE_ANON_KEY` | Public key for frontend auth initialization |
| `SUPABASE_SERVICE_ROLE_KEY` | Admin key for backend write operations |
| `ALLOWED_EMAILS` | Comma-separated list of authorized email addresses |

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

The application uses **Log4j2** for logging with JSON configuration.

### Log Levels

| Level | Usage |
|-------|-------|
| `DEBUG` | Technical indicators, cache operations, API details |
| `INFO` | Strategy execution, trade signals, API calls |
| `WARN` | Skipped symbols, missing configurations |
| `ERROR` | API failures, exceptions |

### Configuration

Logs are configured in `src/main/resources/log4j2.json`:
- **Console**: Outputs to stdout with pattern `HH:mm:ss.SSS LEVEL ClassName - message`
- **RollingFile**: Writes to `logs/trading-bot.log` with daily rotation (max 10 files, 10MB each)

To change log levels, edit the `log4j2.json` file:
```json
{
  "name": "com.hemasundar",
  "level": "debug"  // Change to "info", "warn", or "error"
}
```

## Dependencies

- **Spring Boot** 3.2.2 - Web framework (REST APIs + static file serving)
- **SpringDoc OpenAPI** - Swagger UI for API documentation
- RestAssured - HTTP client (also used for Supabase REST API)
- TestNG - Testing framework
- Lombok - Boilerplate reduction
- Jackson - JSON/YAML processing
- ta4j-core - Technical analysis library (RSI, Bollinger Bands, etc.)
- Log4j2 - Logging framework
- Google Sheets API - For IV data storage in Google Sheets
- **java-jwt** (Auth0) - JWT verification for Supabase Auth tokens

