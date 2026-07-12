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
