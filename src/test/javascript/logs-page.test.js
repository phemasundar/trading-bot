const {
    handleLogSort,
    initLogsPage,
    startLogPolling,
    loadLogs,
    clearLogs,
    renderLogGroups,
    renderLogSymbolGroups,
    renderLogSymbolContent,
    renderLogFilterRow,
    toggleLogGroup,
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
    });
});
