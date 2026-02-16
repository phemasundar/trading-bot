# How to Run the Trading Bot Web UI

## Quick Start

### 1. Set Environment Variables

**Windows (PowerShell):**
```powershell
$env:SUPABASE_URL="https://your-project-id.supabase.co"
$env:SUPABASE_ANON_KEY="your-anon-key-here"
```

**Find your Supabase credentials:**
1. Go to https://supabase.com/dashboard
2. Select your project
3. Go to Settings → API
4. Copy:
   - Project URL → Use as SUPABASE_URL
   - anon/public key → Use as SUPABASE_ANON_KEY

### 2. Start the Application

```bash
mvn spring-boot:run
```

### 3. Open in Browser

```
http://localhost:8080
```

---

## What You'll See

1. **Trading Bot Dashboard** - Main page title
2. **Strategy Selector** - Checkboxes for each enabled strategy
3. **Execute Button** - Primary blue button to run selected strategies
4. **Results Area** - Shows execution results in a table

---

## How to Use

1. **Select Strategies:**
   - Check boxes next to strategies you want to execute
   - Or click "Select All" to run all strategies

2. **Execute:**
   - Click "Execute Selected Strategies"
   - Wait for progress bar to complete (may take several minutes)

3. **View Results:**
   - See trades found organized by strategy
   - Sort columns by clicking headers
   - Expand/collapse strategy details

4. **Telegram Notifications:**
   - Trade alerts are automatically sent to Telegram
   - Same behavior as command-line execution

---

## Troubleshooting

**Problem:** Application fails to start with "Supabase project URL cannot be null"
- **Solution:** Set SUPABASE_URL and SUPABASE_ANON_KEY environment variables

**Problem:** Port 8080 is already in use
- **Solution:** Stop other process on port 8080, or change port in `application.properties`:
  ```properties
  server.port=8081
  ```

**Problem:** Strategy list is empty
- **Solution:** Check that `strategies-config.json` has enabled strategies

**Problem:** No results after execution
- **Solution:** Check console logs for errors, verify securities files exist

---

## Stopping the Application

Press `Ctrl+C` in the terminal where you ran `mvn spring-boot:run`

---

## Alternative: Command Line Execution

The existing command-line execution still works:

```bash
mvn test -DsuiteXmlFile=TestNG.xml
```

Both methods (UI and command-line) save results to the same Supabase database.
