# IV Data Tracking - Setup Guide

Complete setup instructions for the IV data collection system with Google Sheets integration.

**Authentication Method**: Service Account (works for both local development and CI/CD)

---

## Part 1: Google Cloud Setup

### Step 1: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Enter project name: `Trading Bot IV Tracker`
4. Click "Create"

### Step 2: Enable Google Sheets API

1. In the Google Cloud Console, navigate to "APIs & Services" → "Library"
2. Search for "Google Sheets API"
3. Click on it and press "Enable"

### Step 3: Create Service Account

1. Navigate to "IAM & Admin" → "Service Accounts"
2. Click "Create Service Account"
3. Fill in details:
   - **Name**: `iv-tracker-service`
   - **Description**: `Service account for IV data collection`
4. Click "Create and Continue"
5. **Skip** role assignment (click "Continue" → "Done")

### Step 4: Create Service Account Key

1. Click on the newly created service account (`iv-tracker-service`)
2. Go to "Keys" tab
3. Click "Add Key" → "Create new key"
4. Choose **JSON** format
5. Click "Create"
6. A JSON file will download automatically (e.g., `trading-bot-iv-tracker-xxxxx.json`)
7. **Save this file securely** - you'll need it for both local and CI/CD

---

## Part 2: Google Sheets Setup

### Step 5: Create Google Spreadsheet

1. Go to [Google Sheets](https://sheets.google.com/)
2. Click "+" to create a new spreadsheet
3. Name it: `IV Data Tracker`
4. Copy the spreadsheet ID from the URL:
   ```
   https://docs.google.com/spreadsheets/d/COPY_THIS_PART/edit
   ```
   Example: `1SOoK_IGW3TVZlYQ_uYyIjSAUBTzh_tvk5qJghzTBLgA`

### Step 6: Share Spreadsheet with Service Account

1. Open the service account JSON file you downloaded
2. Find the `client_email` field  
   Example: `iv-tracker-service@trading-bot-iv-tracker.iam.gserviceaccount.com`
3. Copy this email address
4. In your Google Spreadsheet, click "Share" button
5. Paste the service account email
6. Set permission to **Editor**
7. **Uncheck** "Notify people" (it's a service account, not a person)
8. Click "Share"

✅ Your spreadsheet is now accessible by the service account!

---

## Part 3: Local Development Setup

### Step 7: Set Environment Variable (Local)

You have two options for local development:

#### Option A: Environment Variable (Recommended for Windows/PowerShell)

1. Open the service account JSON file
2. Copy **entire file content** (all lines, including `{` and `}`)
3. Set environment variable before running tests:

**PowerShell:**
```powershell
$env:GOOGLE_SERVICE_ACCOUNT_JSON = Get-Content "path\to\trading-bot-iv-tracker-xxxxx.json" -Raw
$env:GOOGLE_SPREADSHEET_ID = "YOUR_SPREADSHEET_ID_HERE"
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

**Command Prompt:**
```cmd
set GOOGLE_SERVICE_ACCOUNT_JSON=<paste entire JSON content here>
set GOOGLE_SPREADSHEET_ID=YOUR_SPREADSHEET_ID_HERE
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

#### Option B: Update test.properties (Simpler)

1. Open `src/test/resources/test.properties`
2. Add the service account JSON path:
   ```properties
   google_sheets_spreadsheet_id=YOUR_SPREADSHEET_ID_HERE
   # google_service_account_json_path=C:/path/to/service-account.json
   ```

**Note**: For security, environment variables are preferred over hardcoding paths.

### Step 8: Test Local Execution

Run the IV collection test:
```bash
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

**Expected output:**
```
[INFO] Using Service Account authentication for automated environment
[INFO] Google Sheets Service initialized for spreadsheet: 1SOoK...
[INFO] [AAPL] Collected IV - PUT: 28.5%, CALL: 29.1% (Strike: 150.0, DTE: 30, Market Date: 2026-01-31)
[INFO] [AAPL] Appended new entry for 2026-01-31 - PUT: 28.5%, CALL: 29.1%
```

### Step 9: Verify in Google Sheets

1. Open your spreadsheet
2. You should see:
   - One tab per stock symbol
   - Headers: Date, Strike, DTE, Expiry Date, PUT IV (%), CALL IV (%), Underlying Price
   - Data populated for each symbol

---

## Part 4: GitHub Actions Setup (CI/CD)

### Step 10: Create GitHub Secrets

1. Go to your GitHub repository
2. Navigate to: **Settings** → **Secrets and variables** → **Actions**
3. Click "New repository secret" for each of the following:

#### Required Secrets:

**1. GOOGLE_SERVICE_ACCOUNT_JSON**
- **Value**: Entire contents of your service account JSON file
- Open `trading-bot-iv-tracker-xxxxx.json` → Copy ALL content → Paste

**2. GOOGLE_SPREADSHEET_ID**
- **Value**: Spreadsheet ID from Step 5
- Example: `1SOoK_IGW3TVZlYQ_uYyIjSAUBTzh_tvk5qJghzTBLgA`

**3. REFRESH_TOKEN**
- **Value**: ThinkOrSwim API refresh token
- Copy from your `test.properties` file

**4. APP_KEY**
- **Value**: ThinkOrSwim API app key
- Copy from your `test.properties` file

**5. PP_SECRET**
- **Value**: ThinkOrSwim API app secret
- Copy from your `test.properties` file (field name `pp_secret`)

**6. FINNHUB_API_KEY**
- **Value**: Finnhub API key (for earnings data)
- Copy from your `test.properties` file

**7. FMP_API_KEY**
- **Value**: Financial Modeling Prep API key
- Copy from your `test.properties` file

#### Optional Secrets (if using Telegram notifications):

**8. TELEGRAM_BOT_TOKEN**
- **Value**: Your Telegram bot token
- Copy from your `test.properties` file

**9. TELEGRAM_CHAT_ID**
- **Value**: Your Telegram chat ID
- Copy from your `test.properties` file

### Step 11: Verify Workflow Configuration

The workflow file `.github/workflows/daily-iv-collection.yml` is already configured.

**Schedule**: Monday-Friday at 5:00 PM EST (22:00 UTC)

### Step 12: Manual Test Run (Optional)

1. Go to GitHub repository → **Actions** tab
2. Select "Daily IV Data Collection" workflow
3. Click "Run workflow"
4. Select branch: `main`
5. Click "Run workflow"

**Monitor the execution:**
- The workflow should complete successfully
- Check the Google Sheet for new data

---

## Data Format

Each symbol's tab will contain:

| Date       | Strike | DTE | Expiry Date | PUT IV (%) | CALL IV (%) | Underlying Price |
|------------|--------|-----|-------------|------------|-------------|------------------|
| 2026-01-31 | 150.0  | 30  | 2026-03-21  | 28.5       | 29.1        | 151.25           |
| 2026-02-03 | 155.0  | 29  | 2026-03-21  | 27.8       | 28.4        | 156.50           |

**Note**: Date reflects the actual market close date (not collection date), so weekend/holiday runs still use Friday's market date.

---

## IV Rank Calculation (Future)

After collecting 52 weeks of data, calculate IV Rank:

```
IV Rank = (Current IV - Min IV 52w) / (Max IV 52w - Min IV 52w) × 100
```

**Google Sheets Formula Example (for PUT IV):**
```
=IF(MAX(E:E)-MIN(E:E)=0, 50, (E2-MIN(E:E))/(MAX(E:E)-MIN(E:E))*100)
```
Where column E contains PUT IV (%) values.

---

## Troubleshooting

### Local Execution Issues

**Error: "Credentials file not found"**
- Ensure `GOOGLE_SERVICE_ACCOUNT_JSON` environment variable is set
- Verify JSON content is valid (copy entire file including braces)

**Error: "Invalid spreadsheet ID"**
- Verify spreadsheet ID is correct
- Ensure spreadsheet is shared with service account email

**Error: "Permission denied"**
- Check spreadsheet sharing settings
- Ensure service account has "Editor" permission

### GitHub Actions Issues

**Error: "userName cannot be null"**
- Verify `GOOGLE_SERVICE_ACCOUNT_JSON` secret is set in GitHub
- Ensure secret contains the entire JSON file content

**Error: "Spreadsheet not found"**
- Verify `GOOGLE_SPREADSHEET_ID` secret is correct
- Ensure spreadsheet is shared with service account email from the JSON

**Rate Limit Errors:**
- The code has built-in retry logic and 1.5s delays
- If errors persist, reduce the number of securities in YAML files

---

## Security Best Practices

✅ **DO:**
- Keep service account JSON file secure
- Add `*.json` to `.gitignore`
- Use GitHub Secrets for CI/CD
- Rotate service account keys annually

❌ **DON'T:**
- Commit service account JSON to Git
- Share JSON file publicly
- Hardcode credentials in code
- Use personal Google accounts for automation

---

## Next Steps

1. ✅ Complete Google Cloud setup
2. ✅ Create and share spreadsheet
3. ✅ Test local execution
4. ✅ Configure GitHub Secrets
5. ✅ Trigger first GitHub Actions run
6. 📊 Monitor daily data collection
7. 📈 Implement IV Rank calculations (after 52 weeks)
