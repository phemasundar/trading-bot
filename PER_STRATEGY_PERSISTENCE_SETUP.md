# Per-Strategy Result Persistence - Setup Guide

## Problem Solved
Previously, executing Strategy 2 would clear Strategy 1's results. Now each strategy maintains its own latest result independently in the database.

## Database Setup Required

You must create the new Supabase table before using this feature.

### Step 1: Login to Supabase Dashboard
1. Go to https://supabase.com
2. Open your project: `snjbkdqbmlmwjllnoyqy`

### Step 2: Create the Table
1. Click **SQL Editor** in the left sidebar
2. Click **New Query**
3. Copy and paste the contents from `supabase-latest-strategy-results-schema.sql`
4. Click **Run** (or press Ctrl/Cmd + Enter)
5. Verify the table was created successfully

### Step 3: Test the Application
1. Restart your Spring Boot application (if running)
2. Execute Strategy 1 from the UI
3. Verify results are displayed
4. Execute Strategy 2 from the UI
5. **Verify BOTH Strategy 1 and Strategy 2 results are displayed**
6. Refresh the browser page
7. **Verify both results are still visible**

## How It Works

### Database Level
- New table: `latest_strategy_results`
- Primary key: `strategy_id` (automatically upserts on each execution)
- Stores: strategy name, execution time, trade count, and trades (JSONB)

### Application Level
- `SupabaseService.saveStrategyResult()` - Saves/updates individual strategy result
- `SupabaseService.getAllLatestStrategyResults()` - Retrieves all strategy results
- `StrategyExecutionService` - Automatically saves each strategy result
- `MainView` - Loads and displays all strategy results on startup

### UI Behavior
- **On Startup**: Loads all latest strategy results from database
- **After Execution**: New results appear alongside existing results
- **After Refresh**: All results persist (no data loss)

## Troubleshooting

### "Table not found" Error
- You haven't run the SQL schema yet
- Run the SQL from `supabase-latest-strategy-results-schema.sql` in Supabase SQL Editor

### "401 Unauthorized" Error  
- Check `application.properties` has correct `supabase.url` and `supabase.anon.key`

### Results Not Persisting
- Check browser console for errors
- Check application logs for `"Failed to save strategy result"`
- Verify Supabase table was created successfully

## Reverting (If Needed)

If you want to go back to the old behavior:
1. Comment out the `saveStrategyResult()` call in `StrategyExecutionService.executeStrategy()`
2. Revert the changes to `MainView.loadLatestResults()`
