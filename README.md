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

Technical filters can be composed using the builder pattern:

```java
TechnicalFilterChain filterChain = TechnicalFilterChain.builder()
    .withRSI(RSIFilter.builder()
        .period(14)
        .oversoldThreshold(30.0)
        .overboughtThreshold(70.0)
        .build())
    .withBollingerBands(BollingerBandsFilter.builder()
        .period(20)
        .standardDeviations(2.0)
        .build())
    .withVolume(VolumeFilter.builder()
        .minVolume(100_000L)  // Minimum 100K shares
        .build())
    .build();
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

## Project Structure

```
src/
├── main/java/com/hemasundar/
│   ├── apis/           # API integrations (Schwab, FinnHub)
│   ├── pojos/          # Data models
│   │   └── technicalfilters/  # Technical indicator filters
│   ├── strategies/     # Trading strategy implementations
│   └── utils/          # Utility classes (TelegramUtils, TechnicalIndicators, etc.)
└── test/
    ├── java/           # Test classes
    └── resources/      # Configuration files
```

## Dependencies

- RestAssured - HTTP client
- TestNG - Testing framework
- Lombok - Boilerplate reduction
- Jackson - JSON/YAML processing
- ta4j-core - Technical analysis library (RSI, Bollinger Bands, etc.)

