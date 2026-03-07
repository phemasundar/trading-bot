## v2.4.0 — Execute Screen Templates

### Key Changes
- **Comprehensive Strategy Filters**: Added a full suite of 8 filter fields (Delta, Premium, OI, Volume, Volatility) for every leg in all strategies (PCS, CCS, IC, Zebra, LEAP).
- **Backend Filter Implementation**: Implemented missing filter logic in `AbstractTradingStrategy.java` and all concrete strategy classes for `maxTotalDebit`, `maxTotalCredit`, and `minTotalCredit`.
- **LEAP Specifics**: Added `marginInterestRate`, `savingsInterestRate`, `minCostSavingsPercent`, `minCostEfficiencyPercent`, and versioned priority lists (`sortPriority`, `relaxationPriority`) to LEAP strategies.
- **UI Robustness**: Updated `app.js` to handle nested filter objects (e.g., `shortLeg.minDelta`) and robustly parse comma-separated array inputs.
- **Securities Visibility**: Users can now view the actual list of stock symbols for each security file directly on the Configuration page.
- **Strategy Info Modals**: Integrated `marked.js` to display detailed strategy descriptions from markdown files via an info button on both the Dashboard and Config pages.
- **Execute Screen Templates**: Added dynamic configuration template loading to the Custom Execute screen (`/execute.html`), allowing users to "Load Filters" to quickly pre-populate parameters.

---
**Branch**: main
**Merged from**: develop
