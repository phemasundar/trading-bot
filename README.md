# Trading Bot

A Java-based options trading analysis bot that integrates with the Schwab API to analyze various options strategies.

## Features

- **Options Chain Analysis**: Fetch and analyze options data from Schwab API
- **Multiple Trading Strategies**:
  - Put Credit Spread (PCS)
  - Call Credit Spread (CCS)
  - Iron Condor
  - Long Call LEAP
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

## Project Structure

```
src/
├── main/java/com/hemasundar/
│   ├── apis/           # API integrations (Schwab, etc.)
│   ├── pojos/          # Data models
│   ├── strategies/     # Trading strategy implementations
│   └── utils/          # Utility classes (TelegramUtils, etc.)
└── test/
    ├── java/           # Test classes
    └── resources/      # Configuration files
```

## Dependencies

- RestAssured - HTTP client
- TestNG - Testing framework
- Lombok - Boilerplate reduction
- Jackson - JSON/YAML processing
