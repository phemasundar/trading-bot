const {
    buildResultCard,
    renderTermGroups,
    computeTermDteRange,
    confirmDeleteCustomResult,
    deleteCustomResult,
    promptDeleteCustomScreenerResult,
    deleteCustomScreenerResult,
    buildScreenerCard,
    buildScreenerTable,
    buildDropScreenerTable,
    buildTradeTable,
    handleTableSort,
    injectTodayPerformance,
    fetchAndInjectTodayPerformance,
    renderOptionDataTable,
    initTradeRowClicks,
    showTradeHistoryModal,
    renderFilterGrid,
    renderTechFiltersGrid,
    renderFundamentalFiltersGrid,
    initDashboard,
    loadOptionsStrategies,
    loadStrategies,
    loadOptionsResults,
    loadResults,
    checkExecutionStatus,
    executeSelected,
    cancelExecution,
    fetchAndRenderMarketStatus,
    showDashboardFilterBar,
    hideDashboardFilterBar,
    resetDashboardFilter,
    onFilterColumnChange,
    getDashboardItemValue,
    matchesDashboardFilter,
    applyDashboardFilter,
    clearDashboardFilter,
    updateToggleAllTermsButtonState,
    toggleAllTermGroups,
    toggleCard,
    toggleSection,
    selectAll,
    selectAllScreeners,
    setDashboardBusy,
    API
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();
window.scrollTo = jest.fn();

describe('Dashboard & Table Rendering Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window.tableSortState = {};
        window.tradeDataMap = {};
        global.fetch = jest.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    });

    test('buildResultCard should render strategy card header, badge, and trade table', () => {
        const mockResult = {
            strategyId: 'pcs-1',
            strategyName: 'Short PCS',
            filterConfig: { minDTE: 30, maxDTE: 45, technicalFilterSummary: 'OVERSOLD' },
            trades: [
                {
                    symbol: 'AAPL',
                    returnOnRisk: 15.5,
                    maxProfit: 75,
                    expirationDate: '2026-04-17',
                    optionType: 'PUT',
                    underlyingPrice: 150.0,
                    legs: [{ optionType: 'PUT', strikePrice: 145, delta: 0.20 }]
                }
            ]
        };

        const cardElement = buildResultCard(mockResult, 'Custom');
        expect(cardElement.innerHTML).toContain('Short PCS');
        expect(cardElement.innerHTML).toContain('Custom');
        expect(cardElement.innerHTML).toContain('AAPL');
        expect(cardElement.innerHTML).toContain('Min D T E');
        expect(cardElement.innerHTML).toContain('OVERSOLD');
    });

    test('buildScreenerCard should render technical screener card with table', () => {
        const mockScreenerResult = {
            screenerId: 'rsi-bb-1',
            screenerName: 'RSI Bullish Crossover',
            results: [
                { symbol: 'AAPL', currentPrice: 150.0, rsi: 28.5, marketCapB: 2500 }
            ]
        };

        const card = buildScreenerCard(mockScreenerResult);
        expect(card.innerHTML).toContain('RSI Bullish Crossover');
        expect(card.innerHTML).toContain('AAPL');
    });

    test('buildDropScreenerTable should render multi-day price drop table', () => {
        const dropResults = [
            { symbol: 'TSLA', dropType: 'PERCENT_DROP', currentPrice: 200.0, dropPercent: 12.5, referencePrice: 228.5 }
        ];

        const html = buildDropScreenerTable(dropResults, 'drop-card');
        expect(html).toContain('TSLA');
        expect(html).toContain('12.50%');
    });

    test('handleTableSort should re-sort trade data map and re-render table', () => {
        document.body.innerHTML = `
            <div id="content-test-card">
                <div class="table-container"></div>
            </div>
        `;

        window.tradeDataMap['test-card'] = [
            { symbol: 'MSFT', returnOnRisk: 0.10 },
            { symbol: 'AAPL', returnOnRisk: 0.25 }
        ];

        handleTableSort('test-card', 'symbol');
        const content = document.querySelector('#content-test-card');
        expect(content.innerHTML).toContain('AAPL');
        expect(content.innerHTML).toContain('MSFT');
    });

    test('dashboard cross-table filter functionality', () => {
        document.body.innerHTML = `
            <input id="options-filter-text" value="AAPL">
            <select id="options-filter-column"><option value="ticker" selected>Ticker</option></select>
            <div id="options-filter-summary"></div>
            <div id="options-filter-clear" style="display:none"></div>
            <div id="results-container">
                <div class="card" id="card-1" data-symbol="AAPL">
                    <div class="card-arrow" id="arrow-card-1"></div>
                    <div class="card-content" id="content-card-1"></div>
                </div>
            </div>
        `;

        applyDashboardFilter('options');
        const summary = document.getElementById('options-filter-summary');
        expect(summary.textContent).toContain('match');

        clearDashboardFilter('options');
        expect(document.getElementById('options-filter-text').value).toBe('');
    });

    test('injectTodayPerformance should render net change from API data', async () => {
        document.body.innerHTML = `
            <div class="today-perf" data-symbol="AAPL"></div>
        `;

        API.get = jest.fn().mockResolvedValueOnce([
            { symbol: 'AAPL', netChange: 2.5, netPercentChange: 1.67 }
        ]);

        await injectTodayPerformance(['AAPL']);
        expect(document.querySelector('.today-perf').innerHTML).toContain('+$2.50');
    });

    test('toggleCard and toggleSection toggles open class', () => {
        document.body.innerHTML = `
            <div id="content-card-1"></div>
            <div id="arrow-card-1"></div>
            <div id="sec-1"></div>
            <div id="arrow-sec-1"></div>
        `;

        toggleCard('card-1');
        expect(document.getElementById('content-card-1').classList.contains('open')).toBe(true);

        toggleSection('sec-1');
        expect(document.getElementById('sec-1').classList.contains('open')).toBe(true);
    });

    test('selectAll and selectAllScreeners toggle checkboxes', () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes">
                <input type="checkbox" id="c1" data-type="strategy">
                <input type="checkbox" id="c2" data-type="strategy">
            </div>
            <div id="screener-checkboxes">
                <input type="checkbox" id="s1" data-type="screener">
            </div>
        `;

        selectAll(true);
        expect(document.getElementById('c1').checked).toBe(true);
        expect(document.getElementById('c2').checked).toBe(true);

        selectAllScreeners(true);
        expect(document.getElementById('s1').checked).toBe(true);
    });

    test('renderFilterGrid and renderTechFiltersGrid format grids', () => {
        const filterHtml = renderFilterGrid({ minDTE: 30, maxDTE: 45 });
        expect(filterHtml).toContain('Min D T E');
        expect(filterHtml).toContain('30');

        const techHtml = renderTechFiltersGrid({ RSI: { condition: 'OVERSOLD' } });
        expect(techHtml).toContain('RSI');
        expect(techHtml).toContain('OVERSOLD');
    });

    test('renderFundamentalFiltersGrid formats fundamental grid', () => {
        const fundHtml = renderFundamentalFiltersGrid({ MARKET_CAP: 'market_cap_b > 100' });
        expect(fundHtml).toContain('MARKET_CAP');
        expect(fundHtml).toContain('market_cap_b > 100');
    });

    test('setDashboardBusy toggles busy state on buttons', () => {
        document.body.innerHTML = `
            <button id="execute-btn">Execute</button>
            <button id="cancel-btn"></button>
            <div id="progress-container"></div>
        `;

        setDashboardBusy(true);
        expect(document.getElementById('execute-btn').disabled).toBe(true);

        setDashboardBusy(false);
        expect(document.getElementById('execute-btn').disabled).toBe(false);
    });

    test('deleteCustomResult prompts confirmation and calls API.delete', async () => {
        document.body.innerHTML = '<div><div class="card">Card</div></div>';
        const card = document.querySelector('.card');
        API.delete = jest.fn().mockResolvedValueOnce({ success: true });

        confirmDeleteCustomResult('123', { stopPropagation: jest.fn(), target: card });
        const confirmBtn = document.getElementById('confirm-delete-btn');
        expect(confirmBtn).not.toBeNull();

        confirmBtn.click();
        await new Promise(resolve => setTimeout(resolve, 10));
        expect(API.delete).toHaveBeenCalledWith('/api/results/custom/123');
    });

    test('initDashboard populates strategy & screener checkboxes and results', async () => {
        global.fetch = jest.fn().mockImplementation(url => {
            if (url === '/api/auth/config') return Promise.resolve({ ok: true, json: () => Promise.resolve({ supabaseUrl: 'https://test.supabase.co', supabaseAnonKey: 'anon' }) });
            return Promise.resolve({ ok: true, json: () => Promise.resolve({}) });
        });
        window.supabase = {
            createClient: () => ({
                auth: {
                    getSession: () => Promise.resolve({ data: { session: { access_token: 'tok', user: { email: 'test@user.com' } } } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/strategies') return Promise.resolve([{ index: 0, name: 'Strategy 1', descriptionFile: 's1.md' }]);
            if (url === '/api/screeners') return Promise.resolve([{ index: 0, name: 'Screener 1', descriptionFile: 'sc1.md' }]);
            if (url === '/api/results/options' || url === '/api/results/screeners') return Promise.resolve([]);
            if (url === '/api/status') return Promise.resolve({ running: false, alerts: [] });
            if (url === '/api/market-status') return Promise.resolve({ equityStatus: 'OPEN', optionsStatus: 'OPEN' });
            return Promise.resolve([]);
        });

        document.body.innerHTML = `
            <div class="main-content">
                <div class="sidebar"><div class="sidebar-brand">Brand</div></div>
                <div id="strategy-checkboxes"></div>
                <div id="screener-checkboxes"></div>
                <div id="results-container"></div>
                <span id="strategy-count-badge"></span>
                <span id="screener-count-badge"></span>
            </div>
        `;

        await initDashboard();
        expect(document.getElementById('strategy-checkboxes').innerHTML).toContain('Strategy 1');
    });

    test('checkExecutionStatus handles running status and alerts', async () => {
        API.get = jest.fn().mockResolvedValueOnce({
            running: true,
            currentTask: 'Execute All',
            startTimeMs: Date.now() - 10000,
            percent: 40
        });

        document.body.innerHTML = `
            <button id="execute-btn">Execute</button>
            <button id="cancel-btn"></button>
            <div id="progress-container"></div>
        `;

        await checkExecutionStatus();
        expect(document.getElementById('progress-container').className).toContain('active');
    });

    test('executeSelected submits selected strategies and screeners', async () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes"><input type="checkbox" value="0" checked data-type="strategy"></div>
            <div id="screener-checkboxes"><input type="checkbox" value="1" checked data-type="screener"></div>
            <button id="execute-btn">Execute</button>
            <div id="progress-container"></div>
        `;

        API.post = jest.fn().mockResolvedValueOnce({ message: 'Execution started' });
        await executeSelected();
        expect(API.post).toHaveBeenCalledWith('/api/execute', { strategyIndices: [0], screenerIndices: [1] });
    });

    test('cancelExecution posts cancel command', async () => {
        API.post = jest.fn().mockResolvedValueOnce({ message: 'Cancel requested' });
        await cancelExecution();
        expect(API.post).toHaveBeenCalledWith('/api/cancel');
    });

    test('fetchAndRenderMarketStatus renders open/closed badges', async () => {
        API.get = jest.fn().mockResolvedValueOnce({ equityStatus: 'OPEN', optionsStatus: 'CLOSED' });
        document.body.innerHTML = '<div class="main-content"><div class="sidebar"><div class="sidebar-brand">Brand</div></div></div>';

        await fetchAndRenderMarketStatus();
        expect(document.querySelector('.main-content').innerHTML).toContain('OPEN');
        expect(document.querySelector('.main-content').innerHTML).toContain('CLOSED');
    });

    test('buildTradeTable renders History column and history icon button', () => {
        const trades = [{ symbol: 'AAPL', returnOnRisk: 12.5, maxLoss: 100, expiryDate: '2026-09-18', dte: 30 }];
        const html = buildTradeTable(trades, 'card-pcs');
        expect(html).toContain('<th>History</th>');
        expect(html).toContain('btn-history-icon');
        expect(html).toContain('🕒');
    });

    test('showTradeHistoryModal renders modal and displays empty state when no historical matches', async () => {
        API.post = jest.fn().mockResolvedValueOnce([]);
        const trade = { symbol: 'AAPL', expiryDate: '2026-09-18' };

        showTradeHistoryModal('pcs-123', trade);
        expect(document.getElementById('history-modal-overlay')).not.toBeNull();
        expect(document.getElementById('historyModal').innerHTML).toContain('AAPL');

        await new Promise(resolve => setTimeout(resolve, 20));
        expect(API.post).toHaveBeenCalledWith('/api/strategies/history/similar-trades', { strategyId: 'pcs', trade: trade, limit: 20 });
        expect(document.getElementById('history-modal-body').innerHTML).toContain('No matching historical trades found');
    });

    test('showTradeHistoryModal renders similar trade results table with Date Found and without History button on API response', async () => {
        const mockMatches = [{ symbol: 'AAPL', returnOnRisk: 14.2, maxLoss: 120, expiryDate: '2026-08-21', dte: 10, foundDate: '2026-08-14' }];
        API.post = jest.fn().mockResolvedValueOnce(mockMatches);
        const tradeStr = JSON.stringify({ symbol: 'AAPL', expiryDate: '2026-09-18' });

        showTradeHistoryModal('pcs-123', tradeStr);
        await new Promise(resolve => setTimeout(resolve, 20));

        const body = document.getElementById('history-modal-body');
        expect(body.innerHTML).toContain('AAPL');
        expect(body.innerHTML).toContain('Date Found');
        expect(body.innerHTML).toContain('2026-08-14');
        expect(body.innerHTML).not.toContain('btn-history-icon');
    });

    test('showTradeHistoryModal handles API error and overlay close click', async () => {
        API.post = jest.fn().mockRejectedValueOnce(new Error('Network failure'));
        const trade = { symbol: 'AAPL' };

        showTradeHistoryModal('pcs-123', trade);
        await new Promise(resolve => setTimeout(resolve, 20));

        expect(document.getElementById('history-modal-body').innerHTML).toContain('Failed to load trade history');

        const overlay = document.getElementById('history-modal-overlay');
        overlay.click();
        expect(document.getElementById('history-modal-overlay')).toBeNull();
    });

    test('term groups render collapsed by default and toggleAllTermGroups expands/collapses all', () => {
        document.body.innerHTML = `
            <div id="options-filter-bar"></div>
            <div class="term-actions-bar" id="term-actions-bar" style="display:none;">
                <button class="btn btn-sm btn-ghost" id="toggle-all-terms-btn" style="display:none;">
                    <span class="card-arrow" id="arrow-toggle-all-terms">▶</span>
                    <span id="text-toggle-all-terms">Expand All</span>
                </button>
            </div>
            <div id="results-container"></div>
        `;
        const container = document.getElementById('results-container');
        const bar = document.getElementById('term-actions-bar');
        const results = [
            { strategyName: 'Strat 1', filterConfig: { termType: 'Short' }, trades: [] },
            { strategyName: 'Strat 2', filterConfig: { termType: 'Long' }, trades: [] }
        ];

        renderTermGroups(container, results);

        const btn = document.getElementById('toggle-all-terms-btn');
        const btnText = document.getElementById('text-toggle-all-terms');
        const btnArrow = document.getElementById('arrow-toggle-all-terms');
        const bodies = container.querySelectorAll('.term-group-body');

        expect(btn.style.display).toBe('inline-flex');
        expect(bar.style.display).toBe('flex');
        expect(btnText.textContent).toBe('Expand All');
        expect(btnArrow.classList.contains('open')).toBe(false);
        expect(bodies.length).toBe(2);
        bodies.forEach(b => expect(b.classList.contains('hidden')).toBe(true));

        // Single click toggle expands all
        toggleAllTermGroups(undefined, container);
        bodies.forEach(b => expect(b.classList.contains('hidden')).toBe(false));
        expect(btnText.textContent).toBe('Collapse All');
        expect(btnArrow.classList.contains('open')).toBe(true);

        // Single click toggle collapses all
        toggleAllTermGroups(undefined, container);
        bodies.forEach(b => expect(b.classList.contains('hidden')).toBe(true));
        expect(btnText.textContent).toBe('Expand All');
        expect(btnArrow.classList.contains('open')).toBe(false);

        // Explicit expand all
        toggleAllTermGroups(true, container);
        bodies.forEach(b => expect(b.classList.contains('hidden')).toBe(false));
        expect(btnText.textContent).toBe('Collapse All');

        // Explicit collapse all
        toggleAllTermGroups(false, container);
        bodies.forEach(b => expect(b.classList.contains('hidden')).toBe(true));
        expect(btnText.textContent).toBe('Expand All');
    });

    test('updateToggleAllTermsButtonState responds to manual individual group clicks', () => {
        document.body.innerHTML = `
            <div id="term-actions-bar" style="display:none;">
                <button id="toggle-all-terms-btn" style="display:none;">
                    <span id="arrow-toggle-all-terms">▶</span>
                    <span id="text-toggle-all-terms">Expand All</span>
                </button>
            </div>
            <div id="results-container"></div>
        `;
        const container = document.getElementById('results-container');
        const results = [
            { strategyName: 'Strat 1', filterConfig: { termType: 'Short' }, trades: [] },
            { strategyName: 'Strat 2', filterConfig: { termType: 'Long' }, trades: [] }
        ];

        renderTermGroups(container, results);

        const btnText = document.getElementById('text-toggle-all-terms');
        const headers = container.querySelectorAll('.term-group-header');

        // Click first header to expand 1 group (partial expansion)
        headers[0].click();
        expect(btnText.textContent).toBe('Expand All');

        // Click second header to expand all groups
        headers[1].click();
        expect(btnText.textContent).toBe('Collapse All');

        // Click second header again to collapse 1 group
        headers[1].click();
        expect(btnText.textContent).toBe('Expand All');
    });

    test('updateToggleAllTermsButtonState hides button when container is empty', () => {
        document.body.innerHTML = `
            <div id="term-actions-bar" style="display:flex;">
                <button id="toggle-all-terms-btn" style="display:inline-flex;">
                    <span id="arrow-toggle-all-terms">▶</span>
                    <span id="text-toggle-all-terms">Expand All</span>
                </button>
            </div>
            <div id="results-container"></div>
        `;
        const container = document.getElementById('results-container');
        updateToggleAllTermsButtonState(container);
        expect(document.getElementById('toggle-all-terms-btn').style.display).toBe('none');
        expect(document.getElementById('term-actions-bar').style.display).toBe('none');
    });

    describe('computeTermDteRange & DTE header display', () => {
        test('computeTermDteRange returns empty string when no DTE filter exists', () => {
            expect(computeTermDteRange([])).toBe('');
            expect(computeTermDteRange([{ strategyName: 'S1' }])).toBe('');
            expect(computeTermDteRange([{ strategyName: 'S1', filterConfig: {} }])).toBe('');
        });

        test('computeTermDteRange formats unbounded range (e.g. Extra Long Term 300+)', () => {
            const results = [
                { filterConfig: { minDTE: 300 } },
                { filterConfig: JSON.stringify({ minDTE: 330 }) }
            ];
            expect(computeTermDteRange(results)).toBe('300+');
        });

        test('computeTermDteRange formats bounded range across multiple strategies (e.g. Medium Term 40 - 185)', () => {
            const results = [
                { filterConfig: { minDTE: 55, maxDTE: 185 } },
                { filterConfig: { minDTE: 40, maxDTE: 65 } }
            ];
            expect(computeTermDteRange(results)).toBe('40 - 185');
        });

        test('computeTermDteRange formats targetDTE correctly', () => {
            const results = [
                { filterConfig: { targetDTE: 30 } }
            ];
            expect(computeTermDteRange(results)).toBe('30');
        });

        test('computeTermDteRange formats mixed targetDTE and min/max DTE', () => {
            const results = [
                { filterConfig: { targetDTE: 30 } },
                { filterConfig: { minDTE: 25, maxDTE: 50 } }
            ];
            expect(computeTermDteRange(results)).toBe('25 - 50');
        });

        test('computeTermDteRange formats 0-DTE Daily correctly', () => {
            const results = [
                { filterConfig: { minDTE: 0, maxDTE: 1 } }
            ];
            expect(computeTermDteRange(results)).toBe('0 - 1');
        });

        test('renderTermGroups renders term DTE range in header', () => {
            const container = document.createElement('div');
            const results = [
                { strategyName: 'LEAP', filterConfig: { termType: 'Extra Long Term', minDTE: 300 }, trades: [] },
                { strategyName: 'PCS', filterConfig: { termType: 'Medium Term', minDTE: 55, maxDTE: 185 }, trades: [] },
                { strategyName: 'IC', filterConfig: { termType: 'Medium Term', minDTE: 40, maxDTE: 65 }, trades: [] }
            ];

            renderTermGroups(container, results);

            const groups = container.querySelectorAll('.term-group');
            expect(groups.length).toBe(2);

            // Group 0: Extra Long Term
            const group0Label = groups[0].querySelector('.term-group-label').textContent;
            const group0Dte = groups[0].querySelector('.term-group-dte').textContent;
            expect(group0Label).toBe('Extra Long Term');
            expect(group0Dte).toBe('| 300+');

            // Group 1: Medium Term
            const group1Label = groups[1].querySelector('.term-group-label').textContent;
            const group1Dte = groups[1].querySelector('.term-group-dte').textContent;
            expect(group1Label).toBe('Medium Term');
            expect(group1Dte).toBe('| 40 - 185');
        });
    });
});

