# Project Updates

## Filter Refactoring - Object-Oriented Approach

### Overview
Refactored the filter system to use an Object-Oriented approach with strategy-specific filter classes. This provides better scalability, type safety, and clearer semantics for leg-specific filtering.

### New Filter Hierarchy

```
OptionsStrategyFilter (base - common filters)
    ├── CreditSpreadFilter (for Put/Call Credit Spreads, Iron Condor)
    │       ├── shortLeg: LegFilter
    │       └── longLeg: LegFilter
    │
    ├── BrokenWingButterflyFilter (Call or Put variants)
    │       ├── leg1Long: LegFilter
    │       ├── leg2Short: LegFilter
    │       └── leg3Long: LegFilter
    │
    └── LongCallLeapFilter
            └── longCall: LegFilter
```

### New Classes Created
- `LegFilter.java` - Reusable filter for any single leg (minDelta, maxDelta, etc.)
- `CreditSpreadFilter.java` - For Put/Call Credit Spreads and Iron Condor
- `BrokenWingButterflyFilter.java` - For Broken Wing Butterfly strategy
- `LongCallLeapFilter.java` - For LEAP strategy

### Modified Classes
- `OptionsStrategyFilter.java` - Now base class using `@SuperBuilder`
- `CallCreditSpreadStrategy.java` - Uses `CreditSpreadFilter`
- `PutCreditSpreadStrategy.java` - Uses `CreditSpreadFilter`
- `IronCondorStrategy.java` - Uses `CreditSpreadFilter`
- `LongCallLeapStrategy.java` - Uses `LongCallLeapFilter`
- `BrokenWingButterflyStrategy.java` - Uses `BrokenWingButterflyFilter`
- `SampleTestNG.java` - Updated all filter configurations
- `SampleTestNG1.java` - Updated all filter configurations

### Key Features
1. **Optional Filters**: All leg filters are optional. If null, no restriction is applied.
2. **Named Fields**: Each leg has a named filter field (e.g., `leg1LongCall`, `leg2ShortCalls`)
3. **Type Safety**: Compile-time type checking for filter classes
4. **Helper Methods**: `LegFilter` includes null-safe helper methods like `passesMinDelta()`, `passesMaxDelta()`

### Usage Example
```java
.filter(BrokenWingButterflyFilter.builder()
    .targetDTE(45)
    .maxLossLimit(1000)
    .leg1LongCall(LegFilter.builder().minDelta(0.5).build())
    .leg2ShortCalls(LegFilter.builder().maxDelta(0.2).build())
    // leg3LongCall not set - no filter applied
    .build())
```
