# Phase 2 Testing Instructions: Supabase Schema & Service Extensions

## Overview
Phase 2 added database persistence for strategy execution results using Supabase PostgreSQL.

## What Was Added
1. ✅ SQL schema file: `supabase-schema.sql`
2. ✅ Three DTOs: `ExecutionResult`, `StrategyResult`, `Trade`
3. ✅ Extended `SupabaseService` with two new methods:
   - `saveExecutionResult(ExecutionResult result)`
   - `getLatestExecutionResult()`

---

## Testing Steps

### Step 1: Create Supabase Table

**1.1** Open your Supabase Dashboard:
```
https://supabase.com/dashboard
```

**1.2** Select your project (the same one used for IV data)

**1.3** Go to: **SQL Editor** (left sidebar)

**1.4** Copy the entire content from `supabase-schema.sql` and paste into the SQL  editor

**1.5** Click "Run" to execute the SQL

**Expected Result:**
- ✅ Success message showing table created
- ✅ New table `strategy_executions` appears in Table Editor

**1.6** Verify table structure:
- Go to **Table Editor** → Select `strategy_executions`
- Columns should be:
  - `id` (bigint, primary key)
  - `execution_id` (text, unique)
  - `executed_at` (timestamp with time zone)
  - `strategy_ids` (array of text)
  - `results` (jsonb)
  - `total_trades_found` (integer)
  - `execution_time_ms` (integer)
  - `telegram_sent` (boolean)
  - `created_at` (timestamp with time zone)

---

### Step 2: Verify Maven Compilation

**2.1** Clean and compile:
```bash
mvn clean compile
```

**Expected Result:**
```
[INFO] BUILD SUCCESS
[INFO] Compiling 73 source files
```

**2.2** Check for new files:
```bash
# Verify DTOs exist
dir src\main\java\com\hemasundar\dto
```

You should see:
- `ExecutionResult.java`
- `StrategyResult.java`
- `Trade.java`

---

### Step 3: Test SupabaseService Integration (Manual)

Since we haven't created the UI yet, you can test the new methods programmatically:

**3. 1** Create a simple test class (optional but recommended):

Create `src\test\java\com\hemasundar\SupabaseExecutionTest.java`:

```java
package com.hemasundar;

import com.hemasundar.dto.*;
import com.hemasundar.services.SupabaseService;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

public class SupabaseExecutionTest {
    
    @Test
    public void testSaveAndRetrieveExecution() throws Exception {
        // Initialize Supabase service
        String url = System.getenv("SUPABASE_URL");
        String key = System.getenv("SUPABASE_ANON_KEY");
        SupabaseService service = new SupabaseService(url, key);
        
        // Create test execution result
        Trade testTrade = Trade.builder()
                .symbol("AAPL")
                .underlyingPrice(150.25)
                .expiryDate("2026-03-20")
                .dte(30)
                .shortStrike(145.0)
                .longStrike(140.0)
                .netCredit(0.85)
                .maxLoss(415.0)
                .returnOnRisk(20.48)
                .breakEvenPrice(144.15)
                .breakEvenPercent(-4.06)
                .build();
        
        StrategyResult strategyResult = StrategyResult.builder()
                .strategyId("test_strategy_0")
                .strategyName("Test PCS Strategy")
                .executionTimeMs(5420)
                .tradesFound(1)
                .trades(Arrays.asList(testTrade))
                .build();
        
        ExecutionResult testResult = ExecutionResult.builder()
                .executionId("test_exec_" + System.currentTimeMillis())
                .timestamp(LocalDateTime.now())
                .results(Arrays.asList(strategyResult))
                .totalTradesFound(1)
                .totalExecutionTimeMs(5420)
                .telegramSent(false)
                .build();
        
        // Save to Supabase
        System.out.println("Saving test execution...");
        service.saveExecutionResult(testResult);
        System.out.println("✓ Saved successfully");
        
        // Retrieve from Supabase
        System.out.println("Retrieving latest execution...");
        Optional<ExecutionResult> retrieved = service.getLatestExecutionResult();
        
        if (retrieved.isPresent()) {
            ExecutionResult result = retrieved.get();
            System.out.println("✓ Retrieved successfully:");
            System.out.println("  - Execution ID: " + result.getExecutionId());
            System.out.println("  - Timestamp: " + result.getTimestamp());
            System.out.println("  - Total Trades: " + result.getTotalTradesFound());
            System.out.println("  - Strategies: " + result.getResults().size());
        } else {
            System.out.println("✗ No results found");
        }
    }
}
```

**3.2** Run the test:
```bash
mvn test -Dtest=SupabaseExecutionTest
```

**Expected Output:**
```
Saving test execution...
✓ Saved successfully
Retrieving latest execution...
✓ Retrieved successfully:
  - Execution ID: test_exec_1707789012345
  - Timestamp: 2026-02-12T21:45:00
  - Total Trades: 1
  - Strategies: 1
Tests run: 1, Failures: 0
BUILD SUCCESS
```

**3.3** Verify in Supabase Dashboard:
- Go to **Table Editor** → `strategy_executions`
- You should see 1 row with your test data
- Click on the row to expand the JSON in the `results` column

---

### Step 4: Verify Existing Functionality Still Works

**4.1** Test IV data collection (ensure we didn't break anything):
```bash
mvn test -DsuiteXmlFile=iv-data-collection.xml
```

**Expected:** Should still work as before with no errors

---

##  Phase 2 Checklist

- [ ] Supabase table `strategy_executions` created successfully
- [ ] Table has correct columns and indexes
- [ ] Maven compilation succeeds
- [ ] DTOs created (`ExecutionResult`, `StrategyResult`, `Trade`)
- [ ] `SupabaseService.saveExecutionResult()` works (test passed)
- [ ] `SupabaseService.getLatestExecutionResult()` works (test passed)  
- [ ] Test data visible in Supabase dashboard
- [ ] Existing IV collection still works

---

## Troubleshooting

**Problem:** "cannot find symbol: class ExecutionResult"
- **Solution:** Run `mvn clean compile` to ensure DTOs are compiled

**Problem:** Supabase table creation fails
- **Solution:** Check if you have permission to create tables in your Supabase project

**Problem:** saveExecutionResult() throws IOException
- **Solution:** Verify `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables are set correctly

---

Ready for Phase 3 (Service Layer)?
