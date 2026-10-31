# Deployment & CI/CD Configurations

## Heap & Memory Optimization for Screener & Strategy Execution (2026-10-31)

Resolved `java.lang.OutOfMemoryError: Java heap space` during full-universe strategy and screener execution:

- **Inverted Micro-Batched Option Chain Streaming (`StrategyExecutionService`)**: Replaced the upfront 550-symbol `OptionChainCache.prewarm` with a two-phase pipeline that runs technical screening first, builds a symbol-to-strategy routing map, and streams surviving symbols in micro-batches of 16 (`OPTION_CHAIN_BATCH_SIZE = 16`). Each symbol's `OptionChainResponse` is fetched at most once across all strategies and immediately evicted at the end of its micro-batch.
- **Bounded Caches (`AbstractApiCache`, `OptionChainCache`, `PriceHistoryCache`, `QuotesCache`, `TechnicalIndicatorCache`)**: Added explicit batch eviction (`evict`, `evictAll`), chunked parallel pre-warming (`PREWARM_BATCH_SIZE = 50`), and capacity ceilings (`100` for `OptionChainCache`, `750` for singleton caches).
- **Iron Condor Mathematical Wing Pre-Pruning & Candidate-First Filtering (`IronCondorStrategy`, `BrokenWingButterflyStrategy`)**: Pre-prunes Put and Call credit spreads using `OptionsStrategyFilter.getEffectiveMaxLossLimit()` before the 4-leg Cartesian loop, and evaluates candidate filters on lightweight `IronCondorCandidate` records before instantiating `IronCondor` POJOs.
- **Selective Option Chain Deserialization (`OptionChainResponse`)**: Retained all Schwab API fields in `OptionChainResponse` and `OptionData` for developer schema visibility while annotating unused `String`, `List<OptionDeliverable>`, and primitive fields with `@JsonIgnore` to prevent Jackson from allocating ~45,000 unused heap objects per deep option chain.
- **Bounded Filter Log Store (`FilterLogStore`)**: Replaced `CopyOnWriteArrayList` with a bounded synchronized `ArrayList` (`MAX_ENTRIES = 50,000`) to eliminate $O(N^2)$ array-copy heap churn.
- **JVM Heap Sizing**: Configured `-Xmx3g -Xms512m -XX:+UseG1GC` in GitHub Actions workflows (`ci.yml`, `daily-iv-collection.yml`), `-Xmx2g` in `pom.xml` (`spring-boot-maven-plugin`), and `-Xmx768m` in `Dockerfile`.

## Unit Test Infrastructure & CI Coverage Resolution (2026-03-08)

Resolved the CI pipeline coverage failure through comprehensive test suite optimization and architectural realignment.

### Improvements

- **Coverage Restoration**: Expanded `UnitTests.xml` to include all unit test packages, restoring instruction coverage to >60% (from 27%).
- **Package Realignment**: Refactored the test directory structure to perfectly mirror the `src/main/java` packages, resolving protected-access compilation issues.
- **CI Environment Isolation**: Automated the injection of `TELEGRAM_ENABLED=false` in GitHub Actions to ensure non-blocking test execution in PR environments.
- **Test Stability**: Fixed `UnsupportedOperationException` in `IronCondorStrategyTest` by utilizing mutable collections for expiration mappings.

## Unit Testing & CI/CD Coverage Isolation (2026-03-07)

Expanded unit tests to achieve >60% instruction coverage enforcing a robust CI/CD gate.

### Testing Architecture

- **Suite Separation**: Cleanly separated unit tests from functional tests using `UnitTests.xml` and `FunctionalTests.xml`.
- **Default Behavior**: Running `mvn test` or `mvn clean verify` runs only Unit Tests by default to prevent rate limits and database bloat.
- **JaCoCo Enforcement**: Configured `jacoco-maven-plugin` to mandate 85% instruction coverage on all subsequent builds.

### CI/CD PR Gate

Created `.github/workflows/pr-gate.yml` to automatically execute unit tests and JaCoCo coverage checks on all pull requests targeting the `main` branch.

## GitHub Actions Workflow Updates for Develop Branch (2026-02-08)

Updated GitHub Actions workflows to run scheduled jobs against the develop branch instead of main.

### Changes Made

- **ci.yml**: Added `ref: develop` to checkout step
- **daily-iv-collection.yml**: Added `ref: develop` to checkout step

### Workflow Configuration

Both workflows now:

1. **External Webhook Dispatches** - Triggerable via `workflow_dispatch` and `repository_dispatch` (replacing congested GitHub free-tier scheduled crons)
2. **Checkout develop branch** - Uses `ref: develop` in checkout action
3. **Manual dispatch** - Can still be triggered manually from GitHub UI or via external webhook pings

### External Webhook Triggers (cron-job.org / Webhook Schedulers)

Due to GitHub Actions free tier queuing delays during high-traffic market hours, internal `schedule: cron` triggers are replaced by external webhook triggers. These hit GitHub's REST API at exact market times, dispatching runners instantly.

- **Screener & Strategies (`ci.yml`)**:
  - `workflow_dispatch`: `POST https://api.github.com/repos/{owner}/{repo}/actions/workflows/ci.yml/dispatches` with `{"ref": "develop"}`
  - `repository_dispatch`: `POST https://api.github.com/repos/{owner}/{repo}/dispatches` with `{"event_type": "run-screener"}`
- **Daily IV Collection (`daily-iv-collection.yml`)**:
  - `workflow_dispatch`: `POST https://api.github.com/repos/{owner}/{repo}/actions/workflows/daily-iv-collection.yml/dispatches` with `{"ref": "develop"}`
  - `repository_dispatch`: `POST https://api.github.com/repos/{owner}/{repo}/dispatches` with `{"event_type": "collect-iv"}`

### Files Modified

- `.github/workflows/ci.yml`: Checkout step now uses develop branch
- `.github/workflows/daily-iv-collection.yml`: Checkout step now uses develop branch

### Benefits

- **Development Testing**: Scheduled runs test the latest development code
- **Early Detection**: Issues are caught in develop before merging to main
- **No CI Noise**: Workflows only run on schedule, not on every push

## Windows Service Setup & Auto-Start (Local Deployment)

To run the application automatically as a background service on laptop startup:

### Option 1: True Windows Service via NSSM (Recommended)

1. **Install Service**:
   Right-click `scripts/install-service.bat` and select **Run as administrator**.
   - Registers service name `TradingBot` set to automatic startup (`SERVICE_AUTO_START`).
   - Automatically starts every time Windows boots.
   - Redirects stdout and stderr to `logs/service-stdout.log` and `logs/service-stderr.log` with 10MB auto-rotation.

2. **Restarting the Service**:
   - Double-click `scripts/restart-service.bat` (prompts for admin elevation automatically).
   - Or PowerShell (Admin): `Restart-Service TradingBot`
   - Or Command Prompt (Admin): `nssm restart TradingBot`
   - Or Windows GUI: Press `Win + R` -> type `services.msc` -> right-click **Trading Bot Service** -> **Restart**.

3. **Status & Logs**:
   - Run `scripts/status-service.bat` to verify running state, port 8080 binding, and latest logs.
   - Logs are located at `logs/trading-bot.log` and `logs/service-stdout.log`.

4. **Stopping / Uninstalling**:
   - Stop: `scripts/stop-service.bat` or `Stop-Service TradingBot`
   - Uninstall: `scripts/uninstall-service.bat`

### Option 2: Windows Task Scheduler (No Admin Rights Required)

1. **Install**: Run `scripts/install-task.bat`.
   - Schedules a logon task `TradingBotTask` that launches `scripts/run-bot.bat` when logging into Windows.
2. **Restart**: Run `scripts/restart-task.bat`.
