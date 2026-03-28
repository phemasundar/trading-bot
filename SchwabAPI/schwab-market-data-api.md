# Schwab Market Data API - Comprehensive Documentation

> **Source**: Extracted from the saved Swagger HTML at  
> `SchwabAPI/Trader API - Individual _ Products _ Charles Schwab Developer Portal.html`  
> **Base URL**: `https://api.schwabapi.com/marketdata/v1`  
> **Auth**: OAuth 2.0 Bearer Token (`Authorization: Bearer <access_token>`)

> [!NOTE]
> Despite the filename referencing "Trader API", the saved HTML contains the **Market Data API** Swagger documentation.
> The actual Trader API (Accounts, Orders, Transactions) is a separate specification.

---

## Table of Contents

1. [Quotes](#1-quotes)
2. [Option Chains](#2-option-chains)
3. [Option Expiration Chain](#3-option-expiration-chain)
4. [Price History](#4-price-history)
5. [Movers](#5-movers)
6. [Market Hours](#6-market-hours)
7. [Instruments](#7-instruments)
8. [Schemas](#8-schemas)
9. [Gap Analysis vs ThinkOrSwinAPIs.java](#9-gap-analysis)
10. [Error Response Format](#10-error-response-format)

---

## 1. Quotes

### `GET /quotes` — Get Quotes for Multiple Symbols

Get quote information for one or more symbols.

| Parameter    | Type    | In    | Required | Description                                                                                                    |
| ------------ | ------- | ----- | -------- | -------------------------------------------------------------------------------------------------------------- |
| `symbols`    | string  | query | ✅ Yes   | Comma-separated list of symbols (e.g., `AAPL,TSLA,AMZN`)                                                       |
| `fields`     | string  | query | ❌ No    | Comma-separated fields: `quote`, `fundamental`, `extended`, `reference`, `regular`, or `all`. Defaults to all. |
| `indicative` | boolean | query | ❌ No    | Include indicative symbol quotes (e.g., `$SPX.X`)                                                              |

**Response**: `200 OK` — `application/json`  
Returns a map of `{symbol: QuoteResponseObject}`.

**Example Request**:

```
GET /marketdata/v1/quotes?symbols=AAPL,MSFT&fields=quote,reference
```

---

### `GET /{symbol_id}/quotes` — Get Quote for a Single Symbol

Get quote information for a single symbol.

| Parameter   | Type   | In    | Required | Description                                                                                  |
| ----------- | ------ | ----- | -------- | -------------------------------------------------------------------------------------------- |
| `symbol_id` | string | path  | ✅ Yes   | Symbol to get quote for (e.g., `AAPL`)                                                       |
| `fields`    | string | query | ❌ No    | Comma-separated fields: `quote`, `fundamental`, `extended`, `reference`, `regular`, or `all` |

**Response**: `200 OK` — `application/json`  
Returns a map of `{symbol: QuoteResponseObject}`.

**Example Request**:

```
GET /marketdata/v1/AAPL/quotes?fields=quote,reference
```

---

## 2. Option Chains

### `GET /chains` — Get Option Chain

Get option chain for an optionable symbol.

| Parameter                | Type    | In    | Required | Description                                                                                                                                              |
| ------------------------ | ------- | ----- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `symbol`                 | string  | query | ✅ Yes   | Underlying symbol (e.g., `AAPL`)                                                                                                                         |
| `contractType`           | string  | query | ❌ No    | `CALL`, `PUT`, or `ALL` (default: `ALL`)                                                                                                                 |
| `strikeCount`            | integer | query | ❌ No    | Number of strikes above/below ATM price                                                                                                                  |
| `includeUnderlyingQuote` | boolean | query | ❌ No    | Include underlying quote data                                                                                                                            |
| `strategy`               | string  | query | ❌ No    | Options strategy: `SINGLE`, `ANALYTICAL`, `COVERED`, `VERTICAL`, `CALENDAR`, `STRANGLE`, `STRADDLE`, `BUTTERFLY`, `CONDOR`, `DIAGONAL`, `COLLAR`, `ROLL` |
| `interval`               | number  | query | ❌ No    | Strike interval                                                                                                                                          |
| `strike`                 | number  | query | ❌ No    | Specific strike price                                                                                                                                    |
| `range`                  | string  | query | ❌ No    | Strike range: `ITM`, `NTM`, `OTM`, `SAK`, `SBK`, `SNK`, `ALL`                                                                                            |
| `fromDate`               | string  | query | ❌ No    | From expiration date (format: `YYYY-MM-DD`)                                                                                                              |
| `toDate`                 | string  | query | ❌ No    | To expiration date (format: `YYYY-MM-DD`)                                                                                                                |
| `volatility`             | number  | query | ❌ No    | Volatility to use in ANALYTICAL strategy                                                                                                                 |
| `underlyingPrice`        | number  | query | ❌ No    | Price to use in ANALYTICAL strategy                                                                                                                      |
| `interestRate`           | number  | query | ❌ No    | Interest rate for ANALYTICAL strategy                                                                                                                    |
| `daysToExpiration`       | integer | query | ❌ No    | DTE for ANALYTICAL strategy                                                                                                                              |
| `expMonth`               | string  | query | ❌ No    | Expiration month: `JAN`, `FEB`, ... `DEC`, `ALL`                                                                                                         |
| `optionType`             | string  | query | ❌ No    | `S` (Standard), `NS` (Non-Standard), `ALL`                                                                                                               |
| `entitlement`            | string  | query | ❌ No    | Entitlement: `PN`, `NP`, `PP`                                                                                                                            |

**Response**: `200 OK` — `application/json`  
Returns an `OptionChain` object with `callExpDateMap` and `putExpDateMap`.

**Example Request**:

```
GET /marketdata/v1/chains?symbol=AAPL&contractType=CALL&strategy=SINGLE
```

---

## 3. Option Expiration Chain

### `GET /expirationchain` — Get Option Expiration Chain

Get all available expiration dates for options on a symbol.

| Parameter | Type   | In    | Required | Description                      |
| --------- | ------ | ----- | -------- | -------------------------------- |
| `symbol`  | string | query | ✅ Yes   | Underlying symbol (e.g., `AAPL`) |

**Response**: `200 OK` — `application/json`  
Returns an `ExpirationChain` object with a list of `Expiration` items.

**Example Request**:

```
GET /marketdata/v1/expirationchain?symbol=AAPL
```

---

## 4. Price History

### `GET /pricehistory` — Get Price History for a Symbol

Get historical price data (OHLCV candles) for a symbol.

| Parameter               | Type    | In    | Required | Description                                  |
| ----------------------- | ------- | ----- | -------- | -------------------------------------------- |
| `symbol`                | string  | query | ✅ Yes   | Symbol to get history for                    |
| `periodType`            | string  | query | ❌ No    | `day`, `month`, `year`, `ytd`                |
| `period`                | integer | query | ❌ No    | Number of periods                            |
| `frequencyType`         | string  | query | ❌ No    | `minute`, `daily`, `weekly`, `monthly`       |
| `frequency`             | integer | query | ❌ No    | Frequency value (e.g., 1, 5, 15 for minutes) |
| `startDate`             | long    | query | ❌ No    | Start date (Unix timestamp in ms)            |
| `endDate`               | long    | query | ❌ No    | End date (Unix timestamp in ms)              |
| `needExtendedHoursData` | boolean | query | ❌ No    | Include extended hours candles               |
| `needPreviousClose`     | boolean | query | ❌ No    | Include previous close price                 |

**Response**: `200 OK` — `application/json`  
Returns a `CandleList` object containing `candles[]` with OHLCV data.

**Valid Period/Frequency Combinations**:

| periodType | Valid periods          | Valid frequencyTypes                     |
| ---------- | ---------------------- | ---------------------------------------- |
| `day`      | 1, 2, 3, 4, 5, 10      | `minute` (1, 5, 10, 15, 30)              |
| `month`    | 1, 2, 3, 6             | `daily` (1), `weekly` (1)                |
| `year`     | 1, 2, 3, 5, 10, 15, 20 | `daily` (1), `weekly` (1), `monthly` (1) |
| `ytd`      | 1                      | `daily` (1), `weekly` (1)                |

**Example Request**:

```
GET /marketdata/v1/pricehistory?symbol=AAPL&periodType=year&period=1&frequencyType=daily&frequency=1
```

---

## 5. Movers

### `GET /movers/{symbol_id}` — Get Top Movers

Get top 10 movers for a specific index.

| Parameter   | Type    | In    | Required | Description                                                                                                                             |
| ----------- | ------- | ----- | -------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `symbol_id` | string  | path  | ✅ Yes   | Index symbol: `$DJI`, `$COMPX`, `$SPX`, `NYSE`, `NASDAQ`, `OTCBB`, `INDEX_ALL`, `EQUITY_ALL`, `OPTION_ALL`, `OPTION_PUT`, `OPTION_CALL` |
| `sort`      | string  | query | ❌ No    | Sort direction: `VOLUME`, `TRADES`, `PERCENT_CHANGE_UP`, `PERCENT_CHANGE_DOWN`                                                          |
| `frequency` | integer | query | ❌ No    | Frequency: `0` (zero), `1`, `5`, `10`, `30`, `60`                                                                                       |

**Response**: `200 OK` — `application/json`  
Returns a `Screener` object with mover data.

**Example Request**:

```
GET /marketdata/v1/movers/$DJI?sort=PERCENT_CHANGE_UP&frequency=0
```

---

## 6. Market Hours

### `GET /markets` — Get Market Hours for Multiple Markets

Get market hours/status for all requested market types.

| Parameter | Type   | In    | Required | Description                                                                               |
| --------- | ------ | ----- | -------- | ----------------------------------------------------------------------------------------- |
| `markets` | string | query | ✅ Yes   | Repeatable. Values: `equity`, `option`, `bond`, `future`, `forex`                         |
| `date`    | string | query | ❌ No    | Date in `YYYY-MM-DD` format. Defaults to current day. Valid range: today to 1 year ahead. |

**Response**: `200 OK` — `application/json`

**Example Response** (abbreviated):

```json
{
  "equity": {
    "EQ": {
      "date": "2022-04-14",
      "marketType": "EQUITY",
      "product": "EQ",
      "productName": "equity",
      "isOpen": true,
      "sessionHours": {
        "preMarket": [{"start": "2022-04-14T07:00:00-04:00", "end": "2022-04-14T09:30:00-04:00"}],
        "regularMarket": [{"start": "2022-04-14T09:30:00-04:00", "end": "2022-04-14T16:00:00-04:00"}],
        "postMarket": [{"start": "2022-04-14T16:00:00-04:00", "end": "2022-04-14T20:00:00-04:00"}]
      }
    }
  },
  "option": {
    "EQO": { ... },
    "IND": { ... }
  }
}
```

**Example Request**:

```
GET /marketdata/v1/markets?markets=equity&markets=option
```

---

### `GET /markets/{market_id}` — Get Market Hours for a Single Market

Get market hours for a single specific market.

| Parameter   | Type   | In    | Required | Description                                                |
| ----------- | ------ | ----- | -------- | ---------------------------------------------------------- |
| `market_id` | string | path  | ✅ Yes   | Market type: `equity`, `option`, `bond`, `future`, `forex` |
| `date`      | string | query | ❌ No    | Date in `YYYY-MM-DD` format                                |

**Response**: `200 OK` — `application/json`  
Returns market hours for the specified market only.

**Example Request**:

```
GET /marketdata/v1/markets/equity?date=2024-04-15
```

---

## 7. Instruments

### `GET /instruments` — Search Instruments

Get instrument details by symbols and projections.

| Parameter    | Type   | In    | Required | Description                                                                                        |
| ------------ | ------ | ----- | -------- | -------------------------------------------------------------------------------------------------- |
| `symbol`     | string | query | ✅ Yes   | Symbol of a security (e.g., `AAPL`)                                                                |
| `projection` | string | query | ✅ Yes   | Search type: `symbol-search`, `symbol-regex`, `desc-search`, `desc-regex`, `search`, `fundamental` |

**Response**: `200 OK` — `application/json`

**Example Response**:

```json
{
  "instruments": [
    {
      "cusip": "037833100",
      "symbol": "AAPL",
      "description": "Apple Inc",
      "exchange": "NASDAQ",
      "assetType": "EQUITY"
    }
  ]
}
```

**Example Request**:

```
GET /marketdata/v1/instruments?symbol=AAPL&projection=symbol-search
```

---

### `GET /instruments/{cusip_id}` — Get Instrument by CUSIP

Get basic instrument details by CUSIP identifier.

| Parameter  | Type   | In   | Required | Description                             |
| ---------- | ------ | ---- | -------- | --------------------------------------- |
| `cusip_id` | string | path | ✅ Yes   | CUSIP of a security (e.g., `037833100`) |

**Response**: `200 OK` — `application/json`

**Example Response**:

```json
{
  "cusip": "037833100",
  "symbol": "AAPL",
  "description": "Apple Inc",
  "exchange": "NASDAQ",
  "assetType": "EQUITY"
}
```

**Example Request**:

```
GET /marketdata/v1/instruments/037833100
```

---

## 8. Schemas

The API defines the following data models (collapsed in Swagger UI):

| Schema                   | Description                        |
| ------------------------ | ---------------------------------- |
| `Bond`                   | Bond instrument data               |
| `FundamentalInst`        | Fundamental instrument data        |
| `Instrument`             | Basic instrument info              |
| `InstrumentResponse`     | Instrument search response wrapper |
| `Hours`                  | Market hours data                  |
| `Interval`               | Time interval (start/end)          |
| `Screener`               | Movers/screener data               |
| `Candle`                 | Single OHLCV candle                |
| `CandleList`             | List of candles with metadata      |
| `EquityResponse`         | Equity quote response              |
| `QuoteError`             | Quote error details                |
| `ExtendedMarket`         | Extended hours market data         |
| `ForexResponse`          | Forex quote response               |
| `Fundamental`            | Fundamental analysis data          |
| `FutureOptionResponse`   | Future option quote                |
| `FutureResponse`         | Future quote                       |
| `IndexResponse`          | Index quote                        |
| `MutualFundResponse`     | Mutual fund quote                  |
| `OptionResponse`         | Option quote                       |
| `QuoteEquity`            | Equity quote data                  |
| `QuoteForex`             | Forex quote data                   |
| `QuoteFuture`            | Future quote data                  |
| `QuoteFutureOption`      | Future option quote data           |
| `QuoteIndex`             | Index quote data                   |
| `QuoteMutualFund`        | Mutual fund quote data             |
| `QuoteOption`            | Option quote data                  |
| `QuoteRequest`           | Quote request parameters           |
| `QuoteResponse`          | Quote response wrapper             |
| `QuoteResponseObject`    | Individual quote response          |
| `ReferenceEquity`        | Equity reference data              |
| `ReferenceForex`         | Forex reference data               |
| `ReferenceFuture`        | Future reference data              |
| `ReferenceFutureOption`  | Future option reference data       |
| `ReferenceIndex`         | Index reference data               |
| `ReferenceMutualFund`    | Mutual fund reference data         |
| `ReferenceOption`        | Option reference data              |
| `RegularMarket`          | Regular market data                |
| `AssetMainType`          | Asset type enum                    |
| `EquityAssetSubType`     | Equity sub-type enum               |
| `MutualFundAssetSubType` | Mutual fund sub-type enum          |
| `ContractType`           | Option contract type enum          |
| `SettlementType`         | Settlement type enum               |
| `ExpirationType`         | Expiration type enum               |
| `FundStrategy`           | Fund strategy enum                 |
| `ExerciseType`           | Exercise type enum                 |
| `DivFreq`                | Dividend frequency enum            |
| `QuoteType`              | Quote type enum                    |
| `ErrorResponse`          | Error response wrapper             |
| `Error`                  | Error detail                       |
| `ErrorSource`            | Error source info                  |
| `OptionChain`            | Full option chain                  |
| `OptionContractMap`      | Option contract map                |
| `Underlying`             | Underlying stock data              |
| `OptionDeliverables`     | Option deliverable details         |
| `OptionContract`         | Individual option contract         |
| `ExpirationChain`        | Expiration chain list              |
| `Expiration`             | Individual expiration date         |

---

## 9. Gap Analysis

### Current Implementation (`ThinkOrSwinAPIs.java`)

| #   | Method                               | API Endpoint                         | Status         |
| --- | ------------------------------------ | ------------------------------------ | -------------- |
| 1   | `getQuotes(List, String, Boolean)`   | `GET /quotes`                        | ✅ Implemented |
| 2   | `getQuotes(List)`                    | `GET /quotes` (overload)             | ✅ Implemented |
| 3   | `getQuote(String, String)`           | `GET /{symbol_id}/quotes`            | ✅ Implemented |
| 4   | `getQuote(String)`                   | `GET /{symbol_id}/quotes` (overload) | ✅ Implemented |
| 5   | `getOptionChain(String)`             | `GET /chains`                        | ✅ Implemented |
| 6   | `getExpirationChain(String)`         | `GET /expirationchain`               | ✅ Implemented |
| 7   | `getPriceHistory(...)`               | `GET /pricehistory`                  | ✅ Implemented |
| 8   | `getYearlyPriceHistory(String, int)` | `GET /pricehistory` (convenience)    | ✅ Implemented |
| 9   | `getMarketHours()`                   | `GET /markets`                       | ✅ Implemented |

### Missing Endpoints

| #   | API Endpoint                  | Status                 | Priority                                     |
| --- | ----------------------------- | ---------------------- | -------------------------------------------- |
| 1   | `GET /movers/{symbol_id}`     | ❌ **Not implemented** | Medium — useful for market screening         |
| 2   | `GET /markets/{market_id}`    | ❌ **Not implemented** | Low — current multi-market call suffices     |
| 3   | `GET /instruments`            | ❌ **Not implemented** | Medium — useful for symbol lookup/validation |
| 4   | `GET /instruments/{cusip_id}` | ❌ **Not implemented** | Low — CUSIP lookup rarely needed             |

---

## 10. Error Response Format

All endpoints return consistent error responses for non-200 status codes.

**Response Headers**: Every response includes:

- `Schwab-Client-CorrelId` — Unique correlation ID for tracking support requests
- `Schwab-Resource-Version` — API version used

**Error Codes**: `400`, `401`, `404`, `500`

**Error Response Body**:

```json
{
  "errors": [
    {
      "id": "6808262e-52bb-4421-9d31-6c0e762e7dd5",
      "status": "400",
      "title": "Bad Request",
      "detail": "Missing header",
      "source": {
        "header": "Authorization"
      }
    }
  ]
}
```
