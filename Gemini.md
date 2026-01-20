# Project Updates

## Bullish Broken Wing Butterfly Strategy Implementation

### Overview
Implemented a new "Bullish Broken Wing Butterfly" options strategy with 3 legs for directional bullish trades.

### Strategy Structure
- **Leg 1 (Long Call)**: Buy 1 Call at lower strike with max delta parameter
- **Leg 2 (Short Calls)**: Sell 2 Calls at middle strike with max delta parameter  
- **Leg 3 (Protection)**: Buy 1 Call at higher strike to hedge the short calls

### Max Loss Calculation
- **Upside Loss**: (Upper Wing Width − Lower Wing Width) + Debit Paid
- **Downside Loss**: Debit Paid (total debit)
- **Actual Max Loss**: Maximum of Upside and Downside Loss

### Filter Parameters (Configurable in SampleTestNG)
- `targetDTE`: Days to expiration (default: 45)
- `longCallMaxDelta`: Max delta for Leg 1 long call (default: 0.5)
- `shortCallsMaxDelta`: Max delta for Leg 2 short calls (default: 0.2)
- `maxTotalDebit`: Maximum total debit allowed (default: $100)
- `maxLossLimit`: Maximum loss limit

### Files Modified/Created
- **Created**: `BrokenWingButterfly.java` - Model class for the strategy
- **Created**: `BrokenWingButterflyStrategy.java` - Strategy implementation
- **Modified**: `OptionsStrategyFilter.java` - Added new filter fields
- **Modified**: `StrategyType.java` - Added `BULLISH_BROKEN_WING_BUTTERFLY` enum
- **Modified**: `runtime-config.json` - Added strategy configuration
- **Modified**: `SampleTestNG.java` - Added strategy to options strategies list
- **Modified**: `README.md` - Added strategy to features list

### Code Pattern Alignment
The implementation follows the same patterns as other strategies in the repository:
- **Strategy class**: Follows `CallCreditSpreadStrategy` pattern with:
  - Constructor accepting `StrategyType` for flexibility
  - `findValidTrades` delegating to private method with `currentPrice` parameter
  - Inline option retrieval with `CollectionUtils.isEmpty` checks
  - No-brace single-statement style for null checks
  - Filter checks without redundant `> 0` conditions where applicable
- **Model class**: Follows `CallCreditSpread` pattern with:
  - `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` annotations
  - Implements `TradeSetup` interface methods
  - `returnOnRisk` as a calculated field
  - Consistent `toString()` format
