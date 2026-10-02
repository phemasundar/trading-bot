# Deployment & CI/CD Configurations

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

1. **Run on schedule** - Maintains existing cron schedules
2. **Checkout develop branch** - Uses `ref: develop` in checkout action
3. **Manual dispatch** - Can still be triggered manually from GitHub UI

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

