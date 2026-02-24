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
- **Object-Oriented Filter System**: Extensible filter hierarchy with strategy-specific and leg-specific filters
- **Technical Indicators**: RSI, Bollinger Bands, and Volume analysis using ta4j library
- **Telegram Notifications**: Receive trade alerts directly to your Telegram

## Prerequisites

- Java 17+
- Maven
- Schwab Developer Account (for API access)
- Telegram Account (for notifications)

## Configuration

### Schwab API Setup

1. Register for a Schwab Developer account
2. Create an application to get your `app_key` and `pp_secret`
3. Run the main method in `SampleTestNG` to generate a refresh token

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
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

This automated test runs daily (recommended via cron/scheduler) to collect ATM IV data for PUT and CALL options (~30 DTE) for all securities in your `securities/` folder.

For detailed setup instructions:
- Google Sheets: See `SETUP_GUIDE.md`
- Supabase: See `SUPABASE_SETUP_GUIDE.md` (Updated for Service Role Key security)

## Usage

### Running Strategy Analysis

```bash
mvn test -Dtest=SampleTestNG#getOptionChainData
```

This will:
1. Fetch options chain data for configured securities
2. Analyze trades based on your strategy filters
3. Print results to console
4. Send alerts to Telegram (if configured)

### Configuring Securities Files Per Strategy

Each strategy can use a different securities file. Edit `SampleTestNG.java` to configure:

```java
// Default strategies use securities.yaml
String defaultSecuritiesFile = "securities.yaml";

// RSI Bollinger strategies can use a different file
String rsiBollingerSecuritiesFile = "top100.yaml";
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


## Strategy Dashboard

The execution results dashboard is now maintained in a separate repository. It is a static HTML/JS application deployed to GitHub Pages that fetches live strategy results from Supabase.

### Setup (Separate Repo)
1. Initialize the dashboard repository from the provided standalone export.
2. Configure GitHub Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) in the new repo.
3. Enable GitHub Pages to deploy from the new repo.


## Project Structure

```
src/
├── main/java/com/hemasundar/
│   ├── apis/           # API integrations (Schwab, FinnHub)
│   ├── dto/            # Data Transfer Objects (Trade, StrategyResult, etc.)
│   ├── pojos/          # Data models
│   │   └── technicalfilters/  # Technical indicator filters
│   ├── services/       # Service layer (StrategyExecutionService, SupabaseService)
│   ├── strategies/     # Trading strategy implementations
│   ├── ui/             # Vaadin Web UI (local-only)
│   └── utils/          # Utility classes (TelegramUtils, TechnicalIndicators, etc.)
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

- RestAssured - HTTP client (also used for Supabase REST API)
- TestNG - Testing framework
- Lombok - Boilerplate reduction
- Jackson - JSON/YAML processing
- ta4j-core - Technical analysis library (RSI, Bollinger Bands, etc.)
- Log4j2 - Logging framework
- Google Sheets API - For IV data storage in Google Sheets

