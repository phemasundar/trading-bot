# Supabase Setup Guide for IV Data Collection

This guide walks you through setting up Supabase as a database backend for storing daily Implied Volatility (IV) data collected by the Trading Bot's `IVDataCollectionTest`.

## Table of Contents
1. [Overview](#overview)
2. [Create Supabase Account](#step-1-create-supabase-account)
3. [Create New Project](#step-2-create-new-project)
4. [Create Database Table](#step-3-create-database-table)
5. [Configure Security](#step-4-configure-security)
6. [Get API Credentials](#step-5-get-api-credentials)
7. [Configure Local Environment](#step-6-configure-local-environment)
8. [Configure CI/CD Environment](#step-7-configure-cicd-environment-optional)
9. [Test Connection](#step-8-test-connection)
10. [Troubleshooting](#troubleshooting)

---

## Overview

**What is Supabase?**
Supabase is an open-source Firebase alternative that provides:
- PostgreSQL database (powerful relational database)
- Auto-generated REST API
- Real-time subscriptions
- Authentication (not needed for our use case)
- Row Level Security (RLS)

**Why use it for IV data?**
- ✅ Free tier: 500 MB database, 2 GB bandwidth/month (sufficient for IV data)
- ✅ PostgreSQL: Superior querying and analytics compared to Google Sheets
- ✅ REST API: Easy to integrate with Java
- ✅ Automatic indexes and constraints
- ✅ Built-in dashboard for viewing data

---

## Step 1: Create Supabase Account

1. Go to [https://supabase.com](https://supabase.com)
2. Click **"Start your project"** or **"Sign Up"**
3. Sign up using one of these methods:
   - **GitHub** (recommended for developers)
   - **Google**
   - **Email**
4. Verify your email if required
5. You'll be redirected to the Supabase dashboard

---

## Step 2: Create New Project

1. In the Supabase dashboard, click **"New Project"**
2. Fill in the project details:
   - **Organization**: Select or create an organization (e.g., "Personal")
   - **Name**: `trading-bot-iv-data` (or your preferred name)
   - **Database Password**: Generate a strong password
     - ⚠️ **IMPORTANT**: Save this password securely (you'll need it for direct database access)
     - You can use the auto-generated password
   - **Region**: Choose nearest to your location (e.g., `us-east-1`)
   - **Pricing Plan**: Select **"Free"** tier
3. Click **"Create new project"**
4. Wait 1-2 minutes for project provisioning

---

## Step 3: Create Database Table

### Option A: Using SQL Editor (Recommended)

1. In your project dashboard, click **"SQL Editor"** in the left sidebar
2. Click **"New Query"**
3. Copy and paste this SQL script:

```sql
-- Create IV data table
CREATE TABLE IF NOT EXISTS public.iv_data (
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
    
    -- Unique constraint: one entry per symbol per date
    CONSTRAINT unique_symbol_date UNIQUE (symbol, date)
);

-- Create indexes for better query performance
CREATE INDEX idx_iv_data_symbol ON public.iv_data (symbol);
CREATE INDEX idx_iv_data_date ON public.iv_data (date);
CREATE INDEX idx_iv_data_symbol_date ON public.iv_data (symbol, date);

-- Create function to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to call the function
CREATE TRIGGER update_iv_data_updated_at
    BEFORE UPDATE ON public.iv_data
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add comment to table for documentation
COMMENT ON TABLE public.iv_data IS 'Daily Implied Volatility data for stock options (ATM PUT and CALL)';
```

4. Click **"Run"** (or press `Ctrl+Enter`)
5. Verify success message appears

### Option B: Using Table Editor (Alternative)

If you prefer a GUI approach:
1. Click **"Table Editor"** in the left sidebar
2. Click **"Create a new table"**
3. Configure:
   - Name: `iv_data`
   - Enable RLS: ✅ (we'll configure policies next)
4. Add columns manually (match the SQL script above)

---

## Step 4: Configure Security

Supabase uses Row Level Security (RLS) for fine-grained access control.

### Enable RLS and Create Policies

1. In the SQL Editor, run this script to allow API access:

```sql
-- Enable Row Level Security
ALTER TABLE public.iv_data ENABLE ROW LEVEL SECURITY;

-- Policy 1: Allow INSERT for authenticated and anon users
CREATE POLICY "Allow insert for all users"
ON public.iv_data
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Policy 2: Allow SELECT for authenticated and anon users
CREATE POLICY "Allow select for all users"
ON public.iv_data
FOR SELECT
TO anon, authenticated
USING (true);

-- Policy 3: Allow UPDATE for authenticated and anon users
CREATE POLICY "Allow update for all users"
ON public.iv_data
FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);
```

> [!WARNING]
> **Security Note**: These policies allow all users with valid API keys to read/write data. This is acceptable for a personal trading bot, but in production, you should implement stricter policies based on user authentication.

### Alternative: Disable RLS (Not Recommended)

If you want to disable RLS entirely (easier but less secure):
```sql
ALTER TABLE public.iv_data DISABLE ROW LEVEL SECURITY;
```

---

## Step 5: Get API Credentials

You need two values from Supabase:

### 5.1 Get Project URL

1. In your project dashboard, click **"Project Settings"** (gear icon) in the left sidebar
2. Click **"API"** in the settings menu
3. Under the **"Configuration"** section, find **"URL"** or **"Project URL"**
   - Example: `https://abcdefghijklmnop.supabase.co`
4. Copy this URL

> [!TIP]
> The Project URL is also visible in your browser's address bar when you're in the dashboard. It follows the pattern: `https://supabase.com/dashboard/project/YOUR_PROJECT_ID`

### 5.2 Get API Key (Publishable/Anon Key)

1. On the same **Project Settings → API** page
2. Look for the **"Publishable Key"** or **"API Keys"** section
3. Find and copy the **"Publishable Key"** (also called "anon" or "public" key)
   - This is a long JWT token string starting with `eyJ...`
   - It should be clearly labeled as "Publishable Key" or "anon key"
   - Example: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFiY2RlZmdoaWprbG1ub3AiLCJyb2xlIjoiYW5vbiIsImlhdCI6MTYxMjM0NTY3OCwiZXhwIjoxOTI3OTIxNjc4fQ.1234567890abcdefghijklmnopqrstuvwxyz`
4. Click the **copy icon** next to the key to copy it

> [!TIP]
> **Key Naming**: Supabase may show this as "Publishable Key", "anon key", or "public key" - they all refer to the same thing. This is the safe-to-use client-side key.

> [!NOTE]
> **Can't find Project Settings?**
> - Look for a **gear/cog icon** (⚙️) in the left sidebar
> - Or look for text that says **"Project Settings"** or just **"Settings"**
> - The direct URL format is: `https://supabase.com/dashboard/project/YOUR_PROJECT_ID/settings/api`
> - Once you create your project, you can also find these values under **Home → [Your Project Name] → Settings → API**

> [!NOTE]
> **Which key to use?**
> - **Publishable Key (anon/public key)**: Safe to use on client-side, has RLS restrictions - **Use this one** ✅
> - **Secret Key (service_role key)**: Bypasses RLS, full admin access (⚠️ keep secret, don't use unless necessary!)
>
> For the trading bot, use the **Publishable Key** since it's sufficient and safer.

---

## Step 6: Configure Local Environment

### 6.1 Update test.properties

1. Open `src/test/resources/test.properties`
2. Add these lines at the end:

```properties
# Database Configuration for IV Data Collection
google_sheets_enabled=true
supabase_enabled=true

# Supabase Configuration
supabase_url=https://YOUR_PROJECT_ID.supabase.co
supabase_anon_key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.YOUR_ACTUAL_KEY
```

3. Replace:
   - `YOUR_PROJECT_ID` with your actual project URL from Step 5.1
   - `YOUR_ACTUAL_KEY` with your actual Publishable Key from Step 5.2

### 6.2 Security Reminder

> [!IMPORTANT]
> **Do NOT commit API keys to Git!**
> - The `test.properties` file should already be in `.gitignore`
> - Never commit real credentials to version control
> - Use environment variables for CI/CD (see next step)

---

## Step 7: Configure CI/CD Environment (Optional)

If you're running IV data collection in GitHub Actions or Jenkins:

### GitHub Actions

1. Go to your repository on GitHub
2. Click **Settings → Secrets and variables → Actions**
3. Click **"New repository secret"**
4. Add two secrets:
   - **Name**: `SUPABASE_URL`  
     **Value**: Your Supabase project URL
   - **Name**: `SUPABASE_ANON_KEY`  
     **Value**: Your Supabase anon key

5. Update your workflow YAML (e.g., `.github/workflows/iv-collection.yml`):

```yaml
- name: Run IV Data Collection
  env:
    SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
    SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
  run: mvn test -DsuiteXmlFile=iv-data-collection.xml
```

### Jenkins

Add credentials in Jenkins:
1. Go to **Manage Jenkins → Credentials**
2. Add **Secret text** entries for `SUPABASE_URL` and `SUPABASE_ANON_KEY`
3. Use in pipeline:

```groovy
withCredentials([
    string(credentialsId: 'supabase-url', variable: 'SUPABASE_URL'),
    string(credentialsId: 'supabase-anon-key', variable: 'SUPABASE_ANON_KEY')
]) {
    sh 'mvn test -DsuiteXmlFile=iv-data-collection.xml'
}
```

---

## Step 8: Test Connection

### 8.1 Verify API Access with curl

Test your credentials using curl (or Postman):

```bash
curl "https://YOUR_PROJECT_ID.supabase.co/rest/v1/iv_data?select=*&limit=5" \
  -H "apikey: YOUR_PUBLISHABLE_KEY" \
  -H "Authorization: Bearer YOUR_PUBLISHABLE_KEY"
```

Replace:
- `YOUR_PROJECT_ID` with your project ID from the URL
- `YOUR_PUBLISHABLE_KEY` with your Publishable Key from Step 5.2

Expected response:
- **HTTP 200** with empty array `[]` (if table is empty)
- **HTTP 200** with data array (if data exists)

### 8.2 Insert Test Data

Insert a test row:

```bash
curl -X POST "https://YOUR_PROJECT_ID.supabase.co/rest/v1/iv_data" \
  -H "apikey: YOUR_PUBLISHABLE_KEY" \
  -H "Authorization: Bearer YOUR_PUBLISHABLE_KEY" \
  -H "Content-Type: application/json" \
  -H "Prefer: return=representation" \
  -d '{
    "symbol": "AAPL",
    "date": "2026-02-03",
    "strike": 225.00,
    "dte": 30,
    "expiry_date": "2026-03-05",
    "put_iv": 32.45,
    "call_iv": 31.89,
    "underlying_price": 226.50
  }'
```

### 8.3 View Data in Dashboard

1. Go to **Table Editor** in Supabase dashboard
2. Click on `iv_data` table
3. You should see your test row

### 8.4 Delete Test Data

After verification, delete the test row:
1. In Table Editor, click the checkbox next to the AAPL row
2. Click **"Delete"** at the top

---

## Troubleshooting

### Can't Find Project Settings or API Section

**Problem**: Unable to locate "Project Settings" or the "API" section in the dashboard

**Solution**:
1. Make sure you're in your **project dashboard**, not the organization home page
2. Look for these visual cues in the left sidebar:
   - A **gear icon** (⚙️) labeled "Project Settings" or just "Settings"
   - It should be near the bottom of the sidebar
3. Alternative: Use the direct URL:
   - Go to your browser address bar
   - Navigate to: `https://supabase.com/dashboard/project/YOUR_PROJECT_ID/settings/api`
   - Replace `YOUR_PROJECT_ID` with your actual project ID
4. If you just created the project:
   - Wait ~30 seconds for full provisioning
   - Refresh the page
   - Check if "Settings" now appears

---

### Error: "relation 'public.iv_data' does not exist"

**Solution**: The table wasn't created. Go back to [Step 3](#step-3-create-database-table) and run the SQL script again.

---

### Can't Find "Project API Keys" - Only See "Publishable Key"

**This is correct!** Supabase updated their UI terminology. What you're looking for:

- ✅ **"Publishable Key"** = This is the correct key to use (same as the old "anon key")
- ⚠️ You may also see a **"Secret Key"** or **"service_role key"** - don't use this one

**Just copy the Publishable Key** - that's all you need!

---

### Error: "401 Unauthorized"

**Cause**: Invalid or expired API key

**Solution**:
1. Verify you copied the **anon key** correctly (no extra spaces)
2. Check if key is still valid in **Settings → API**
3. Try regenerating the API key if needed

---

### Error: "new row violates row-level security policy"

**Cause**: RLS is enabled but no policies allow the operation

**Solution**: 
1. Go to [Step 4](#step-4-configure-security)
2. Run the RLS policy SQL script
3. Or temporarily disable RLS for testing (not recommended for production)

---

### Error: "duplicate key value violates unique constraint"

**Cause**: Trying to insert data for a symbol+date combination that already exists

**Solution**: This is expected behavior. The Java code will use UPSERT to handle this automatically.

---

### Rate Limits

**Free Tier Limits**:
- 500 MB database storage
- 2 GB bandwidth/month
- ~100 requests/second (generous for trading bot)

**If you exceed limits**: Consider upgrading to Pro plan ($25/month) or optimizing data collection frequency.

---

### Connection Timeout

**Cause**: Network issues or project paused (free projects pause after 1 week of inactivity)

**Solution**:
1. Check project status in dashboard
2. If paused, click **"Restore"** to reactivate
3. Free projects automatically pause but data is preserved

---

## Next Steps

After completing this setup:

1. ✅ You have a Supabase project with `iv_data` table
2. ✅ API credentials are configured in `test.properties`
3. ✅ Connection tested successfully

Now you can run the IV data collection test:

```bash
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

The test will automatically save data to both Google Sheets (if enabled) and Supabase!

---

## Viewing Your Data

### Supabase Dashboard

1. Go to **Table Editor**
2. Click `iv_data` table
3. View, filter, and sort data

### SQL Queries

Use the SQL Editor to run analytical queries:

```sql
-- Get latest IV data for all symbols
SELECT symbol, date, put_iv, call_iv, underlying_price
FROM public.iv_data
WHERE date = (SELECT MAX(date) FROM public.iv_data WHERE symbol = iv_data.symbol)
ORDER BY symbol;

-- Calculate IV percentile rank for a symbol (last 30 days)
WITH recent_iv AS (
    SELECT put_iv, call_iv
    FROM public.iv_data
    WHERE symbol = 'AAPL'
      AND date >= CURRENT_DATE - INTERVAL '30 days'
)
SELECT 
    symbol,
    put_iv,
    call_iv,
    PERCENT_RANK() OVER (ORDER BY put_iv) * 100 AS put_iv_rank,
    PERCENT_RANK() OVER (ORDER BY call_iv) * 100 AS call_iv_rank
FROM public.iv_data
WHERE symbol = 'AAPL'
  AND date = CURRENT_DATE;
```

### Export Data

Export to CSV:
1. Run a query in SQL Editor
2. Click **"Download CSV"** button

---

## Security Best Practices

1. ✅ **Never commit API keys** to Git
2. ✅ Use **environment variables** for CI/CD
3. ✅ Use **anon key** (not service_role key) for trading bot
4. ✅ Keep RLS enabled with appropriate policies
5. ✅ Rotate API keys periodically (Settings → API → "Generate new anon key")
6. ✅ Monitor usage in Dashboard → Settings → Usage

---

## Additional Resources

- [Supabase Documentation](https://supabase.com/docs)
- [PostgREST API Reference](https://postgrest.org/en/stable/api.html)
- [Row Level Security Guide](https://supabase.com/docs/guides/auth/row-level-security)
- [Supabase Java Client Examples](https://github.com/supabase-community/supabase-java)
