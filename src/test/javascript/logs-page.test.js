const {
    handleLogSort,
    initLogsPage,
    startLogPolling,
    loadLogs,
    clearLogs,
    isStockLevelEntry,
    aggregateStockScreening,
    renderStockScreeningSection,
    renderStrategyGroupContent,
    renderLogGroups,
    renderLogSymbolGroups,
    renderLogSymbolContent,
    renderLogFilterRow,
    toggleLogGroup,
    focusSymbol,
    API
} = require('../../main/resources/static/app');

describe('Logs Page Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window.logSortColumn = null;
        window.logSortAsc = true;
    });

    test('handleLogSort toggles sort column and direction', () => {
        document.body.innerHTML = '<div id="logsContainer"></div>';
        const dummyEvent = { stopPropagation: jest.fn() };
        handleLogSort('filterStage', dummyEvent);
        expect(window.logSortColumn).toBe('filterStage');
        expect(window.logSortAsc).toBe(true);

        handleLogSort('filterStage', dummyEvent);
        expect(window.logSortAsc).toBe(false);

        handleLogSort('filterStage', dummyEvent);
        expect(window.logSortColumn).toBeNull();
    });

    test('loadLogs populates log groups or displays empty state', async () => {
        document.body.innerHTML = '<div id="logsContainer"></div><div id="logsEmpty"></div>';
        const logs = [
            { strategyName: 'Short PCS', symbol: 'AAPL', expiry: '2026-04-17', filterStage: 'Delta Filter', tradesIn: 10, tradesOut: 2 }
        ];

        API.get = jest.fn().mockResolvedValueOnce(logs);
        await loadLogs();
        const container = document.getElementById('logsContainer');
        expect(container.innerHTML).toContain('Short PCS');
        expect(container.innerHTML).toContain('AAPL');

        API.get = jest.fn().mockResolvedValueOnce([]);
        await loadLogs();
        expect(document.getElementById('logsEmpty').style.display).toBe('flex');
    });

    test('clearLogs posts clear command and reloads logs', async () => {
        document.body.innerHTML = '<div id="logsContainer"></div><div id="logsEmpty"></div>';
        API.post = jest.fn().mockResolvedValueOnce({ success: true });
        API.get = jest.fn().mockResolvedValueOnce([]);
        await clearLogs();
        expect(API.post).toHaveBeenCalledWith('/api/filter-logs/clear');
    });

    test('toggleLogGroup toggles open class on body and arrow', () => {
        document.body.innerHTML = `
            <div id="arrow-test-group"></div>
            <div id="test-group"></div>
        `;
        toggleLogGroup('test-group');
        expect(document.getElementById('test-group').classList.contains('open')).toBe(true);
        expect(document.getElementById('arrow-test-group').classList.contains('open')).toBe(true);

        toggleLogGroup('test-group');
        expect(document.getElementById('test-group').classList.contains('open')).toBe(false);
    });

    test('renderLogSymbolContent with per-expiry and symbol-level entries', () => {
        const otherEntries = [
            { filterStage: 'Historical Volatility', tradesIn: 100, tradesOut: 80 }
        ];
        const byExpiry = {
            '2026-04-17': [
                { filterStage: 'Generated Candidates', tradesIn: 50, tradesOut: 50 },
                { filterStage: 'Delta Filter', tradesIn: 50, tradesOut: 10 }
            ]
        };
        const html = renderLogSymbolContent(otherEntries, byExpiry, 'pcs', 'aapl', new Set());
        expect(html).toContain('Expiry: 2026-04-17');
        expect(html).toContain('Other (symbol-level)');
        expect(html).not.toContain('log-reduction');
        expect(html).not.toContain('% filtered');
    });

    test('renderLogSymbolGroups correctly sums trades across expiries and excludes filtered percentage', () => {
        const entries = [
            { strategyName: 'Bullish ZEBRA', symbol: 'AAPL', expiry: '2026-11-06', filterStage: 'Generated Candidates', tradesIn: 1830, tradesOut: 1830 },
            { strategyName: 'Bullish ZEBRA', symbol: 'AAPL', expiry: '2026-11-06', filterStage: 'Delta Filter', tradesIn: 1830, tradesOut: 3 },
            { strategyName: 'Bullish ZEBRA', symbol: 'AAPL', expiry: '2026-11-13', filterStage: 'Generated Candidates', tradesIn: 1830, tradesOut: 1830 },
            { strategyName: 'Bullish ZEBRA', symbol: 'AAPL', expiry: '2026-11-13', filterStage: 'Delta Filter', tradesIn: 1830, tradesOut: 0 },
            { strategyName: 'Bullish ZEBRA', symbol: 'AAPL', expiry: null, filterStage: 'Earnings Filter', tradesIn: 1, tradesOut: 1 }
        ];

        const html = renderLogSymbolGroups(entries, 'Bullish ZEBRA', new Set(), new Set());
        expect(html).toContain('3 trades remaining');
        expect(html).not.toContain('0 trades remaining');
        expect(html).not.toContain('log-reduction');
        expect(html).not.toContain('% filtered');
    });

    test('renderLogFilterRow renders details under filter stage when provided', () => {
        const rowWithDetails = renderLogFilterRow({
            filterStage: 'IV Percentile Filter',
            details: 'IV Percentile: 75.0% [bounds: 25.0 - 75.0]',
            tradesIn: 1,
            tradesOut: 1
        });
        expect(rowWithDetails).toContain('IV Percentile Filter');
        expect(rowWithDetails).toContain('log-entry-details');
        expect(rowWithDetails).toContain('IV Percentile: 75.0% [bounds: 25.0 - 75.0]');

        const rowWithoutDetails = renderLogFilterRow({
            filterStage: 'Delta Filter',
            tradesIn: 10,
            tradesOut: 5
        });
        expect(rowWithoutDetails).toContain('Delta Filter');
        expect(rowWithoutDetails).not.toContain('log-entry-details');
    });

    test('isStockLevelEntry accurately identifies stock vs option filter entries', () => {
        expect(isStockLevelEntry({ filterStage: 'Technical: HV_RANK >= 25', expiry: null })).toBe(true);
        expect(isStockLevelEntry({ filterStage: 'Fundamental: PE_RATIO < 20', expiry: null })).toBe(true);
        expect(isStockLevelEntry({ filterStage: 'Technical Data Error', expiry: null })).toBe(true);
        expect(isStockLevelEntry({ filterStage: 'IV_RANK >= 30', expiry: null })).toBe(true);
        expect(isStockLevelEntry({ filterStage: 'Quote Data Missing', expiry: null })).toBe(true);

        // Option levels or entries with expiry must return false
        expect(isStockLevelEntry({ filterStage: 'Technical: HV_RANK >= 25', expiry: '2026-04-17' })).toBe(false);
        expect(isStockLevelEntry({ filterStage: 'Delta Filter', expiry: null })).toBe(false);
        expect(isStockLevelEntry({ filterStage: 'Generated Candidates', expiry: '2026-04-17' })).toBe(false);
        expect(isStockLevelEntry({ filterStage: 'targetDTE = 30', expiry: null })).toBe(false);
    });

    test('aggregateStockScreening correctly calculates funnel metrics, qualified and eliminated symbols', () => {
        const stockEntries = [
            { symbol: 'NVDA', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 5.4 (target: 25.0)', tradesIn: 1, tradesOut: 0 },
            { symbol: 'AAPL', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 23.1 (target: 25.0)', tradesIn: 1, tradesOut: 0 },
            { symbol: 'AMD', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 35.0 (target: 25.0)', tradesIn: 1, tradesOut: 1 },
            { symbol: 'AMD', filterStage: 'Technical: RSI <= 70', details: 'RSI: 55.0 (target: <= 70)', tradesIn: 1, tradesOut: 1 },
            { symbol: 'MSFT', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 28.0 (target: 25.0)', tradesIn: 1, tradesOut: 1 },
            { symbol: 'MSFT', filterStage: 'Technical: RSI <= 70', details: 'RSI: 75.0 (target: <= 70)', tradesIn: 1, tradesOut: 0 }
        ];

        const result = aggregateStockScreening(stockEntries);
        expect(result.totalEvaluated).toBe(4);
        expect(result.qualifiedSymbols).toEqual(['AMD']);
        expect(result.eliminatedList).toHaveLength(3);

        const nvdaElim = result.eliminatedList.find(e => e.symbol === 'NVDA');
        expect(nvdaElim.details).toBe('HV_RANK: 5.4 (target: 25.0)');

        const msftElim = result.eliminatedList.find(e => e.symbol === 'MSFT');
        expect(msftElim.stage).toBe('Technical: RSI <= 70');

        // Check stage aggregations
        expect(result.stages).toHaveLength(2);
        expect(result.stages[0].filterStage).toBe('Technical: HV_RANK >= 25');
        expect(result.stages[0].tradesIn).toBe(4);
        expect(result.stages[0].tradesOut).toBe(2);

        expect(result.stages[1].filterStage).toBe('Technical: RSI <= 70');
        expect(result.stages[1].tradesIn).toBe(2);
        expect(result.stages[1].tradesOut).toBe(1);
    });

    test('renderLogGroups renders stock screening funnel and isolates options to qualified symbols', () => {
        const container = document.createElement('div');
        const entries = [
            // NVDA fails technical filter
            { strategyName: 'PCS - Short Term', symbol: 'NVDA', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 5.4 (target: 25.0)', tradesIn: 1, tradesOut: 0, expiry: null },
            // AMD passes technical filter and has option logs
            { strategyName: 'PCS - Short Term', symbol: 'AMD', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 40.0 (target: 25.0)', tradesIn: 1, tradesOut: 1, expiry: null },
            { strategyName: 'PCS - Short Term', symbol: 'AMD', filterStage: 'Generated Candidates', expiry: '2026-04-17', tradesIn: 20, tradesOut: 20 },
            { strategyName: 'PCS - Short Term', symbol: 'AMD', filterStage: 'Delta Filter', expiry: '2026-04-17', tradesIn: 20, tradesOut: 4 }
        ];

        renderLogGroups(entries, container);
        const html = container.innerHTML;

        // Stock screening funnel
        expect(html).toContain('Stock Screening Funnel');
        expect(html).toContain('Technical: HV_RANK &gt;= 25');
        expect(html).toContain('1 qualified');
        expect(html).toContain('✓ AMD');

        // Eliminated stock breakdown contains NVDA
        expect(html).toContain('Eliminated Stocks (1)');
        expect(html).toContain('NVDA');
        expect(html).toContain('HV_RANK: 5.4 (target: 25.0)');

        // Options analysis only renders for AMD, not NVDA
        expect(html).toContain('Options Analysis (1 Qualified Symbol)');
        expect(html).toContain('sym-PCS-Short-Term-AMD');
        expect(html).not.toContain('sym-PCS-Short-Term-NVDA');
    });

    test('renderLogGroups shows friendly message when no stocks pass screening', () => {
        const container = document.createElement('div');
        const entries = [
            { strategyName: 'PCS - Short Term', symbol: 'NVDA', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 5.4 (target: 25.0)', tradesIn: 1, tradesOut: 0, expiry: null },
            { strategyName: 'PCS - Short Term', symbol: 'AAPL', filterStage: 'Technical: HV_RANK >= 25', details: 'HV_RANK: 23.1 (target: 25.0)', tradesIn: 1, tradesOut: 0, expiry: null }
        ];

        renderLogGroups(entries, container);
        const html = container.innerHTML;

        expect(html).toContain('Stock Screening Funnel');
        expect(html).toContain('0 qualified');
        expect(html).toContain('No stocks qualified for options analysis');
        expect(html).not.toContain('sym-PCS---Short-Term-NVDA');
        expect(html).not.toContain('sym-PCS---Short-Term-AAPL');
    });

    test('focusSymbol opens symbol accordion and scrolls smoothly', () => {
        document.body.innerHTML = `
            <div class="log-symbol-block">
                <div class="card-arrow" id="arrow-sym-test-strat-aapl"></div>
                <div class="log-symbol-body" id="sym-test-strat-aapl"></div>
            </div>
        `;
        const bodyEl = document.getElementById('sym-test-strat-aapl');
        bodyEl.closest('.log-symbol-block').scrollIntoView = jest.fn();

        window.focusSymbol('test-strat', 'aapl');
        expect(bodyEl.classList.contains('open')).toBe(true);
        expect(document.getElementById('arrow-sym-test-strat-aapl').classList.contains('open')).toBe(true);
        expect(bodyEl.closest('.log-symbol-block').scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
    });
});
