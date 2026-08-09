const {
    formatMarketCap,
    formatLargeNumber,
    formatDuration,
    timeAgo,
    formatCompanyName,
    toggleSidebar,
    escapeAttr,
    showToast,
    renderGreeksPills,
    formatLegs,
    formatBreakeven,
    formatExpiryDate,
    escapeHtmlContent,
    decodeAttr,
    formatRevenue,
    formatHourBadge,
    rsiValue,
    buildTradeTable,
    buildResultCard,
    renderTermGroups,
    toggleCard,
    handleTableSort,
    buildScreenerCard,
    buildScreenerTable,
    buildDropScreenerTable,
    renderFilterGrid,
    renderTechFiltersGrid,
    renderFundamentalFiltersGrid,
    showErrorPanel,
    dismissErrorPanel,
    dismissSingleAlert,
    startTimer,
    stopTimer,
    API,
    injectUserInfo,
    loadOptionsStrategies,
    loadOptionsResults,
    loadResults,
    setDashboardBusy,
    showInfo,
    checkExecutionStatus,
    executeSelected,
    cancelExecution,
    toggleSection,
    selectAll,
    selectAllScreeners,
    loadScreenerStrategies,
    loadScreenerResults,
    showDashboardFilterBar,
    hideDashboardFilterBar,
    resetDashboardFilter,
    onFilterColumnChange,
    getDashboardItemValue,
    matchesDashboardFilter,
    applyDashboardFilter,
    clearDashboardFilter,
    checkScreenerExecutionStatus,
    executeScreenersSelected,
    STRATEGY_TYPES,
    getLegFilters,
    STRATEGY_SPECIFIC_FILTERS,
    checkCustomExecutionStatus,
    renderStrategyTemplates,
    loadTemplateParams,
    loadFiltersFromResult,
    fillTechFiltersForm,
    renderSpecificFilters,
    executeCustom,
    loadCustomResults,
    renderConfig,
    renderInternalFilterGrid,
    fetchAndRenderMarketStatus,
    loadLogs,
    clearLogs,
    renderLogGroups,
    renderLogSymbolGroups,
    renderLogSymbolContent,
    renderLogFilterRow,
    toggleLogGroup,
    SCREENER_TYPE_META,
    onScreenerTypeChange,
    renderScreenerTemplates,
    loadScreenerTemplateParams,
    loadScreenerFiltersFromResult,
    executeCustomScreener,
    getTechnicalFiltersFromDOM,
    loadCustomScreenerResults,
    initEarningsCalendar,
    renderCalendar,
    calNavigate,
    calGoToday,
    onDayClick,
    formatDateKey,
    createDayCell,
    confirmDeleteCustomResult,
    deleteCustomResult,
    promptDeleteCustomScreenerResult,
    deleteCustomScreenerResult,
    renderOptionDataTable,
    initTradeRowClicks,
    loadStrategies,
    showFilterHelp,
    loadFilterDescriptions
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();

describe('App Utility Functions', () => {
    test('formatMarketCap should format billions correctly', () => {
        expect(formatMarketCap(1.5)).toBe('$1.50B');
        expect(formatMarketCap(0.5)).toBe('$0.50B');
        expect(formatMarketCap(null)).toBe('-');
        expect(formatMarketCap(undefined)).toBe('-');
    });

    test('formatLargeNumber should format numbers appropriately', () => {
        expect(formatLargeNumber(1500000000)).toBe('1500.0M');
        expect(formatLargeNumber(1500000)).toBe('1.5M');
        expect(formatLargeNumber(1500)).toBe('1.5K');
        expect(formatLargeNumber(null)).toBe('-');
    });

    test('formatCompanyName should truncate and format names', () => {
        expect(formatCompanyName('Apple Inc.')).toBe('Apple Inc.');
        expect(formatCompanyName('A very long company name that exceeds the limit')).toBe('A very lon...');
        expect(formatCompanyName(null)).toBe('-');
    });

    test('formatDuration should handle milliseconds', () => {
        expect(formatDuration(1500)).toBe('1.5s');
        expect(formatDuration(0)).toBeNull();
    });

    test('timeAgo should calculate relative time', () => {
        const now = new Date();
        const tenSecondsAgo = new Date(now.getTime() - 10000);
        const oneHourAgo = new Date(now.getTime() - 3600000);
        
        expect(timeAgo(tenSecondsAgo.toISOString())).toBe('Just now');
        expect(timeAgo(oneHourAgo.toISOString())).toMatch(/1h|60m/); // Depends on exact rounding
    });
});

describe('DOM and String Utilities', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('escapeAttr should escape HTML entities', () => {
        expect(escapeAttr('<script>alert("xss & \'")</script>'))
            .toBe('&lt;script&gt;alert(&quot;xss &amp; &#39;&quot;)&lt;/script&gt;');
        expect(escapeAttr(null)).toBe('');
    });

    test('toggleSidebar should toggle open class', () => {
        document.body.innerHTML = '<div class="sidebar"></div>';
        toggleSidebar();
        expect(document.querySelector('.sidebar').classList.contains('open')).toBe(true);
        toggleSidebar();
        expect(document.querySelector('.sidebar').classList.contains('open')).toBe(false);
    });

    test('showToast should render success toast and auto-dismiss', () => {
        showToast('Operation successful', 'success');
        const toast = document.querySelector('.toast');
        expect(toast).not.toBeNull();
        expect(toast.classList.contains('toast-success')).toBe(true);
        expect(toast.textContent).toBe('Operation successful');
        
        jest.advanceTimersByTime(4000);
        expect(document.querySelector('.toast')).toBeNull(); // it removes itself
    });

    test('showToast should render error toast that persists', () => {
        showToast('Operation failed', 'error');
        const toast = document.querySelector('.toast');
        expect(toast).not.toBeNull();
        expect(toast.classList.contains('toast-error')).toBe(true);
        expect(toast.innerHTML).toContain('Operation failed');
        expect(toast.innerHTML).toContain('<button'); // has dismiss button
        
        jest.advanceTimersByTime(5000);
        expect(document.querySelector('.toast')).not.toBeNull(); // does not auto-dismiss
    });

    test('renderGreeksPills should render HTML based on greeks data', () => {
        const greeks = { delta: 'positive', gamma: 'negative', theta: 'neutral' };
        const html = renderGreeksPills(greeks);
        expect(html).toContain('greek-positive');
        expect(html).toContain('greek-negative');
        expect(html).toContain('greek-neutral');
        expect(renderGreeksPills(null)).toBe('');
    });
});

describe('Trade Formatting Functions', () => {
    test('formatExpiryDate should extract date from ISO string', () => {
        expect(formatExpiryDate('2026-04-17T20:00:00.000+00:00')).toBe('2026-04-17');
        expect(formatExpiryDate(null)).toBe('');
        expect(formatExpiryDate('')).toBe('');
    });

    test('formatLegs should format option legs array', () => {
        const trade = {
            legs: [
                { action: 'BUY', quantity: 2, strike: 150.5, optionType: 'CALL' },
                { action: 'SELL', quantity: 1, strike: 160, optionType: 'PUT' }
            ]
        };
        const html = formatLegs(trade);
        expect(html).toContain('leg-buy');
        expect(html).toContain('BUY 2x 151 CALL');
        expect(html).toContain('leg-sell');
        expect(html).toContain('SELL 160 PUT');
        expect(html).toContain('<br>');
    });

    test('formatLegs should return - if no legs', () => {
        expect(formatLegs({})).toBe('-');
        expect(formatLegs({ legs: [] })).toBe('-');
    });

    test('formatBreakeven should handle single and double breakevens', () => {
        const singleBreakeven = { breakEvenPrice: 155.50, breakEvenPercent: 2.5 };
        const html1 = formatBreakeven(singleBreakeven);
        expect(html1).toContain('$155.50');
        expect(html1).toContain('(2.5%)');
        
        const doubleBreakeven = { 
            breakEvenPrice: 155.50, breakEvenPercent: 2.5,
            upperBreakEvenPrice: 165.50, upperBreakEvenPercent: 8.5
        };
        const html2 = formatBreakeven(doubleBreakeven);
        expect(html2).toContain('$155.50');
        expect(html2).toContain('$165.50');
        expect(html2).toContain('<br>');
    });

    test('formatBreakeven should include CAGR if present', () => {
        const trade = { breakEvenPrice: 155.50, breakevenCAGR: 15.4 };
        const html = formatBreakeven(trade);
        expect(html).toContain('CAGR: 15.4%');
    });

    test('formatBreakeven should return - if no data', () => {
        expect(formatBreakeven({})).toBe('-');
    });

    test('rsiValue should color-code based on value', () => {
        expect(rsiValue(25)).toContain('text-success');
        expect(rsiValue(25)).toContain('25.0');
        
        expect(rsiValue(75)).toContain('text-danger');
        expect(rsiValue(75)).toContain('75.0');
        
        expect(rsiValue(50)).not.toContain('text-success');
        expect(rsiValue(50)).not.toContain('text-danger');
        expect(rsiValue(50)).toContain('50.0');
    });

    test('formatRevenue should format numbers properly', () => {
        expect(formatRevenue(1500000000)).toBe('$1.50B');
        expect(formatRevenue(1500000)).toBe('$1.5M');
        expect(formatRevenue(1500)).toBe('$1,500');
        expect(formatRevenue(null)).toBe('—');
        expect(formatRevenue('invalid')).toBe('—');
    });

    test('formatHourBadge should return HTML span based on hour', () => {
        expect(formatHourBadge('bmo')).toContain('cal-hour-badge bmo');
        expect(formatHourBadge('amc')).toContain('cal-hour-badge amc');
        expect(formatHourBadge('other')).toContain('cal-hour-badge unknown');
    });

    test('escapeHtmlContent and decodeAttr should be inverses', () => {
        const original = '<div class="test">& \' "</div>';
        const escaped = escapeHtmlContent(original);
        expect(escaped).toBe('&lt;div class="test"&gt;&amp; \' "&lt;/div&gt;');
        const decoded = decodeAttr(escaped);
        expect(decoded).toBe(original);
    });
});

describe('UI Builder Functions', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        window.tableSortState = {};
        window.tradeDataMap = {};
    });

    test('buildTradeTable should return empty state if no trades', () => {
        expect(buildTradeTable(null)).toContain('No trades found');
        expect(buildTradeTable([])).toContain('No trades found');
    });

    test('buildTradeTable should render table with trades', () => {
        const trades = [
            {
                symbol: 'AAPL',
                companyName: 'Apple Inc.',
                underlyingPrice: 150.0,
                todayPct: 1.5,
                strategyType: 'IRON_CONDOR',
                maxLoss: -50.0,
                extrinsicValue: 20.0,
                returnOnRisk: 15.0,
                netCredit: 10.0,
                legs: [
                    { action: 'SELL', strike: 155, optionType: 'CALL', expirationDate: '2026-04-17T20:00:00.000+00:00' }
                ],
                breakEvenPrice: 156.0
            }
        ];
        
        const html = buildTradeTable(trades, 'card123');
        expect(html).toContain('<table');
        expect(html).toContain('AAPL');
        expect(html).toContain('Apple Inc.');
        expect(html).toContain('$150.00');
        expect(html).toContain('SELL');
        expect(html).toContain('text-success'); // for positive ROR
        expect(html).toContain('handleTableSort');
    });

    test('buildResultCard should create disabled card if no trades', () => {
        const result = {
            strategyId: 'strat1',
            strategyName: 'Test Strategy',
            trades: [],
            updatedAt: new Date().toISOString()
        };
        const card = buildResultCard(result, 'Standard');
        expect(card.tagName).toBe('DIV');
        expect(card.className).toContain('card disabled');
        expect(card.querySelector('.card-name').textContent).toBe('Test Strategy');
    });

    test('buildResultCard should create active card with trades', () => {
        const result = {
            strategyId: 'strat2',
            strategyName: 'Active Strategy',
            trades: [{ symbol: 'AAPL' }],
            updatedAt: new Date().toISOString(),
            tradesFound: 1
        };
        const card = buildResultCard(result, 'Custom');
        expect(card.className).toBe('card');
        expect(card.querySelector('.card-name').textContent).toBe('Active Strategy');
        expect(card.querySelector('.card-badge').textContent).toBe('Custom');
        expect(card.querySelector('.card-stats').textContent).toContain('Trades: 1');
        
        // Trades map should be populated
        expect(window.tradeDataMap['strat2']).toEqual([{ symbol: 'AAPL' }]);
    });

    test('toggleCard should toggle open class and content visibility', () => {
        document.body.innerHTML = `
            <div id="arrow-card1"></div>
            <div id="content-card1"></div>
        `;
        toggleCard('card1');
        expect(document.getElementById('content-card1').classList.contains('open')).toBe(true);
        expect(document.getElementById('arrow-card1').classList.contains('open')).toBe(true);
        
        toggleCard('card1');
        expect(document.getElementById('content-card1').classList.contains('open')).toBe(false);
        expect(document.getElementById('arrow-card1').classList.contains('open')).toBe(false);
    });

    test('handleTableSort should update state and re-render table', () => {
        // Setup initial DOM and state
        document.body.innerHTML = '<div id="content-card1"></div>';
        window.tradeDataMap['card1'] = [
            { symbol: 'AAPL', underlyingPrice: 150 },
            { symbol: 'MSFT', underlyingPrice: 300 }
        ];
        
        handleTableSort('card1', 'symbol');
        
        // Check state updated
        expect(window.tableSortState['card1'].column).toBe('symbol');
        expect(window.tableSortState['card1'].direction).toBe('asc');
        
        // Re-sorting same column flips direction
        handleTableSort('card1', 'symbol');
        expect(window.tableSortState['card1'].direction).toBe('desc');
    });

    test('renderTermGroups should group results and sort by termWeight', () => {
        const results = [
            { strategyName: 'Strat 1', filterConfig: { termType: 'Short' }, trades: [] },
            { strategyName: 'Strat 2', filterConfig: '{"termType": "Long"}', trades: [] },
            { strategyName: 'Strat 3', trades: [] } // Defaults to Other
        ];
        
        const container = document.createElement('div');
        renderTermGroups(container, results, 'Standard');
        
        const groups = container.querySelectorAll('.term-group');
        expect(groups.length).toBe(3);
        
        // Long (weight 60) should be first, Short (30) second, Other (-1) last
        expect(groups[0].querySelector('.term-group-label').textContent).toBe('Long');
        expect(groups[1].querySelector('.term-group-label').textContent).toBe('Short');
        expect(groups[2].querySelector('.term-group-label').textContent).toBe('Other');
        
        // Header click should toggle visibility
        const header = groups[0].querySelector('.term-group-header');
        const body = groups[0].querySelector('.term-group-body');
        
        expect(body.classList.contains('hidden')).toBe(true);
        header.click();
        expect(body.classList.contains('hidden')).toBe(false);
    });

    test('buildScreenerTable should render HTML table for screener results', () => {
        const results = [
            {
                symbol: 'NVDA',
                companyName: 'NVIDIA Corp',
                marketCapB: 2000,
                price: 850.50,
                rsi: 35.4,
                bollingerBandsPosition: 0.1,
                ttmRevenue: 50000000000,
                debtToEquity: 0.5
            }
        ];
        
        const html = buildScreenerTable(results, 'scr1');
        expect(html).toContain('<table');
        expect(html).toContain('NVDA');
        expect(html).toContain('NVIDIA Corp');
        expect(html).toContain('$2000.00B'); // formatMarketCap output
        expect(html).toContain('35.4'); // rsi output
        expect(html).toContain('handleTableSort(\'scr1\'');
    });

    test('buildDropScreenerTable should render HTML table for drop results', () => {
        const results = [
            {
                symbol: 'TSLA',
                companyName: 'Tesla Inc',
                dropPercent: 5.5,
                volume: 50000000,
                dropType: 'Significant Drop'
            }
        ];
        
        const html = buildDropScreenerTable(results, 'drop1');
        expect(html).toContain('<table');
        expect(html).toContain('TSLA');
        expect(html).toContain('Tesla Inc');
        expect(html).toContain('-5.50%');
        expect(html).toContain('50.0M');
        expect(html).toContain('Significant Drop');
        expect(html).toContain('handleTableSort(\'drop1\'');
    });

    test('buildScreenerCard should create DOM element for screener result', () => {
        const result = {
            screenerId: 'sc123',
            screenerName: 'Value Stocks',
            results: [{ symbol: 'AAPL' }],
            resultsFound: 1,
            updatedAt: new Date().toISOString()
        };
        
        const card = buildScreenerCard(result, false);
        expect(card.tagName).toBe('DIV');
        expect(card.className).toBe('card');
        expect(card.querySelector('.card-name').textContent).toBe('Value Stocks');
        expect(card.querySelector('.card-stats').textContent).toContain('Found: 1');
        
        // Trades map should be populated as 'screener' type
        expect(window.tradeDataMap['sc123'][0]).toEqual({ symbol: 'AAPL' });
        expect(window.tradeDataMap['sc123']._type).toBe('screener');
    });

    test('renderFilterGrid should format config object into grid HTML', () => {
        const config = {
            minReturnOnRisk: 15.5,
            excludeEarnings: true,
            allowedSectors: ['Tech', 'Health'],
            nestedObject: {
                innerKey: 'Inner Value'
            },
            technicalFilterSummary: 'Strong Buy'
        };
        
        const html = renderFilterGrid(config);
        
        // Assert label formatting (e.g. minReturnOnRisk -> Min Return On Risk)
        expect(html).toContain('Min Return On Risk');
        expect(html).toContain('15.5');
        
        // Assert boolean and array formatting
        expect(html).toContain('Exclude Earnings');
        expect(html).toContain('Yes');
        expect(html).toContain('Allowed Sectors');
        expect(html).toContain('Tech, Health');
        
        // Assert nested object section
        expect(html).toContain('Nested Object');
        expect(html).toContain('Inner Key');
        expect(html).toContain('Inner Value');
        
        // Assert technicalFilterSummary special handling
        expect(html).toContain('🔬 Tech Filters');
        expect(html).toContain('Strong Buy');
    });

    test('renderTechFiltersGrid should render technical filters properly', () => {
        const strHtml = renderTechFiltersGrid('PresetName');
        expect(strHtml).toContain('Preset');
        expect(strHtml).toContain('PresetName');

        const mapHtml = renderTechFiltersGrid({
            'RSI': { condition: 'RSI < 30' },
            'SMA': ['SMA_50', 'SMA_200']
        });
        expect(mapHtml).toContain('🔬 Technical Filters');
        expect(mapHtml).toContain('RSI');
        expect(mapHtml).toContain('RSI < 30');
        expect(mapHtml).toContain('SMA');
        expect(mapHtml).toContain('SMA_50, SMA_200');
    });

    test('renderFundamentalFiltersGrid should render fundamental filters properly', () => {
        const mapHtml = renderFundamentalFiltersGrid({
            'MARKET_CAP': { conditions: ['> 10B'] },
            'PE_RATIO': '< 20'
        });
        expect(mapHtml).toContain('📊 Fundamental Filters');
        expect(mapHtml).toContain('MARKET_CAP');
        expect(mapHtml).toContain('> 10B');
        expect(mapHtml).toContain('PE_RATIO');
        expect(mapHtml).toContain('< 20');
    });

    test('showErrorPanel should render errors and warnings', () => {
        const alerts = [
            { severity: 'ERROR', source: 'Test', message: 'Critical failure', timestamp: 1234567890 },
            { severity: 'WARNING', source: 'Test', message: 'Minor issue', timestamp: 1234567890 }
        ];
        
        showErrorPanel(alerts);
        
        const panel = document.getElementById('error-panel');
        expect(panel).not.toBeNull();
        expect(panel.innerHTML).toContain('1 error, 1 warning');
        expect(panel.innerHTML).toContain('Critical failure');
        expect(panel.innerHTML).toContain('Minor issue');
    });

    test('dismissSingleAlert should remove item and update count', () => {
        const alerts = [
            { severity: 'ERROR', source: 'Test', message: 'Critical failure', timestamp: 1234567890 },
            { severity: 'WARNING', source: 'Test', message: 'Minor issue', timestamp: 1234567890 }
        ];
        showErrorPanel(alerts);
        
        const panel = document.getElementById('error-panel');
        const warnItem = panel.querySelector('.error-panel-item-warning');
        const dismissBtn = warnItem.querySelector('.error-item-dismiss');
        
        // Mock the closest() function in JSDOM if needed, but it works natively in JSDOM
        dismissSingleAlert(dismissBtn);
        
        // Warning should be gone, error remains
        expect(panel.querySelector('.error-panel-item-warning')).toBeNull();
        expect(panel.querySelector('.error-panel-item-error')).not.toBeNull();
        
        // Count should update
        expect(panel.querySelector('.error-panel-count').textContent).toBe('1 error');
    });

    test('dismissErrorPanel should remove the entire panel', async () => {
        const alerts = [{ severity: 'ERROR', source: 'Test', message: 'Fail', timestamp: 123 }];
        showErrorPanel(alerts);
        expect(document.getElementById('error-panel')).not.toBeNull();
        
        await dismissErrorPanel();
        expect(document.getElementById('error-panel')).toBeNull();
    });

    test('startTimer and stopTimer should manage elapsed text element', () => {
        jest.useFakeTimers();
        document.body.innerHTML = '<div id="elapsed-text"></div>';
        const startTime = Date.now() - 65000; // 1 min 5 seconds ago
        
        startTimer(startTime);
        
        // Fast forward 1 second to trigger interval
        jest.advanceTimersByTime(1000);
        
        const el = document.getElementById('elapsed-text');
        expect(el.textContent).toContain('Elapsed: 1m 6s');
        
        stopTimer();
        
        // Fast forward another 5 seconds, text should not change
        jest.advanceTimersByTime(5000);
        expect(el.textContent).toContain('Elapsed: 1m 6s');
    });
});

describe('API and Auth Functions', () => {
    let originalFetch;

    beforeEach(() => {
        originalFetch = global.fetch;
        global.fetch = jest.fn();
        API._accessToken = 'test-token';
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    test('API.get should perform successful GET request', async () => {
        global.fetch.mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({ success: true })
        });
        
        const data = await API.get('/api/test');
        expect(global.fetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
            method: 'GET',
            headers: expect.objectContaining({
                'Authorization': 'Bearer test-token'
            })
        }));
        expect(data).toEqual({ success: true });
    });

    test('API.post should perform successful POST request with body', async () => {
        global.fetch.mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({ created: true })
        });
        
        const data = await API.post('/api/test', { name: 'test' });
        expect(global.fetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ name: 'test' })
        }));
        expect(data).toEqual({ created: true });
    });

    test('API request should handle 401/403 unauthorized and throw Unauthorized', async () => {
        global.fetch.mockResolvedValueOnce({
            ok: false,
            status: 401,
            json: async () => ({ error: 'Token expired' })
        });
        
        await expect(API.get('/api/test')).rejects.toThrow('Unauthorized');
        expect(localStorage.getItem('authError')).toBe('Token expired');
    });

    test('API request should handle 503 Service Unavailable', async () => {
        global.fetch.mockResolvedValueOnce({
            ok: false,
            status: 503
        });
        
        await expect(API.get('/api/test')).rejects.toThrow('Service unavailable');
    });

    test('injectUserInfo should update sidebar DOM with user info', () => {
        document.body.innerHTML = `
            <div class="sidebar">
                <div class="sidebar-brand">Brand</div>
            </div>
        `;
        
        const user = {
            email: 'test@example.com',
            user_metadata: {
                full_name: 'Test User',
                avatar_url: 'http://example.com/avatar.jpg'
            }
        };
        
        injectUserInfo(user);
        
        const sidebar = document.querySelector('.sidebar');
        expect(sidebar.querySelector('.user-info')).not.toBeNull();
        expect(sidebar.querySelector('img').src).toBe('http://example.com/avatar.jpg');
        expect(sidebar.innerHTML).toContain('Test User');
        expect(sidebar.querySelector('.nav-link-logout')).not.toBeNull();
    });
});

describe('Dashboard Page Functions', () => {
    let originalFetch;

    beforeEach(() => {
        originalFetch = global.fetch;
        global.fetch = jest.fn();
        jest.restoreAllMocks();
        // Setup global helpers used in app.js
        window.resetDashboardFilter = jest.fn();
        window.showDashboardFilterBar = jest.fn();
        window.hideDashboardFilterBar = jest.fn();
        window.fetchAndInjectTodayPerformance = jest.fn();
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    test('setDashboardBusy should toggle DOM elements busy state', () => {
        document.body.innerHTML = `
            <button id="execute-btn"></button>
            <button id="stop-btn" style="display: none;"></button>
            <div id="progress-container" class="progress-container"></div>
        `;
        
        setDashboardBusy(true);
        expect(document.getElementById('execute-btn').disabled).toBe(true);
        expect(document.getElementById('stop-btn').style.display).toBe('inline-flex');
        expect(document.getElementById('progress-container').className).toContain('active');

        setDashboardBusy(false);
        expect(document.getElementById('execute-btn').disabled).toBe(false);
        expect(document.getElementById('stop-btn').style.display).toBe('none');
        expect(document.getElementById('progress-container').className).not.toContain('active');
    });

    test('loadOptionsStrategies should populate strategy checkboxes container', async () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes"></div>
            <span id="strategy-count-badge"></span>
        `;
        
        const mockStrategies = [
            { index: 1, name: 'Iron Condor', termType: 'Short', securitiesFile: 'spy.txt', descriptionFile: 'ic.md' }
        ];
        
        jest.spyOn(API, 'get').mockResolvedValueOnce(mockStrategies);

        await loadOptionsStrategies();

        const container = document.getElementById('strategy-checkboxes');
        expect(container.innerHTML).toContain('Iron Condor - Short - spy.txt');
        expect(container.innerHTML).toContain('data-type="strategy"');
        expect(document.getElementById('strategy-count-badge').textContent).toBe('(1)');
    });

    test('loadOptionsResults should render empty state when no results', async () => {
        document.body.innerHTML = `<div id="results-container"></div>`;
        
        jest.spyOn(API, 'get').mockResolvedValueOnce([]);

        await loadOptionsResults();

        const container = document.getElementById('results-container');
        expect(container.innerHTML).toContain('No option strategy results yet');
    });

    test('loadOptionsResults should render option results using renderTermGroups', async () => {
        document.body.innerHTML = `<div id="results-container"></div>`;
        
        const mockResults = [
            { strategyName: 'Strat 1', trades: [] }
        ];
        
        jest.spyOn(API, 'get').mockResolvedValueOnce(mockResults);

        await loadOptionsResults();

        const container = document.getElementById('results-container');
        expect(container.querySelector('.term-group')).not.toBeNull();
    });

    test('checkExecutionStatus should trigger startTimer and startPolling when running', async () => {
        jest.spyOn(API, 'get').mockResolvedValueOnce({
            running: true,
            currentTask: 'Running Strategy 1',
            startTimeMs: Date.now() - 10000,
            alerts: [{ severity: 'WARNING', message: 'Test Warn' }]
        });

        document.body.innerHTML = '<div id="elapsed-text"></div>';

        await checkExecutionStatus();

        expect(window.currentExecutionTaskName).toBe('Running Strategy 1');
        expect(document.getElementById('error-panel')).not.toBeNull();
    });

    test('showInfo should create modal overlay and fetch description file', async () => {
        global.fetch.mockResolvedValueOnce({
            ok: true,
            text: async () => '## Strategy Overview\nThis is a test strategy.'
        });

        await showInfo(null, 'ic.md', 'Iron Condor Strategy');

        const overlay = document.querySelector('.info-modal-overlay');
        expect(overlay).not.toBeNull();
        expect(overlay.innerHTML).toContain('Iron Condor Strategy');
        expect(overlay.innerHTML).toContain('This is a test strategy.');

        // Clean up DOM
        overlay.remove();
    });

    test('executeSelected should post selected strategy indices', async () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes">
                <input type="checkbox" value="0" checked>
                <input type="checkbox" value="1">
            </div>
            <div id="screener-checkboxes">
                <input type="checkbox" value="2" checked>
            </div>
        `;

        jest.spyOn(API, 'post').mockResolvedValueOnce({ message: 'Execution started' });

        await executeSelected();

        expect(API.post).toHaveBeenCalledWith('/api/execute', {
            strategyIndices: [0],
            screenerIndices: [2]
        });
    });

    test('executeSelected should toast error if nothing is selected', async () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes"><input type="checkbox" value="0"></div>
            <div id="screener-checkboxes"></div>
        `;

        const postSpy = jest.spyOn(API, 'post');

        await executeSelected();

        expect(postSpy).not.toHaveBeenCalled();
    });

    test('cancelExecution should post cancel request', async () => {
        jest.spyOn(API, 'post').mockResolvedValueOnce({ success: true });

        await cancelExecution();

        expect(API.post).toHaveBeenCalledWith('/api/cancel');
    });

    test('toggleSection, selectAll, and selectAllScreeners should manipulate checkbox DOM', () => {
        document.body.innerHTML = `
            <div id="sec1"></div>
            <div id="arrow-sec1"></div>
            <div id="strategy-section"></div>
            <div id="arrow-strategy-section"></div>
            <div id="strategy-checkboxes">
                <input type="checkbox">
                <input type="checkbox">
            </div>
            <div id="screener-section"></div>
            <div id="arrow-screener-section"></div>
            <div id="screener-checkboxes">
                <input type="checkbox">
            </div>
        `;

        toggleSection('sec1');
        expect(document.getElementById('sec1').classList.contains('open')).toBe(true);

        selectAll(true);
        const stratCbs = document.querySelectorAll('#strategy-checkboxes input');
        expect(stratCbs[0].checked).toBe(true);
        expect(stratCbs[1].checked).toBe(true);
        expect(document.getElementById('strategy-section').classList.contains('open')).toBe(true);

        selectAllScreeners(true);
        const scrCbs = document.querySelectorAll('#screener-checkboxes input');
        expect(scrCbs[0].checked).toBe(true);
        expect(document.getElementById('screener-section').classList.contains('open')).toBe(true);
    });

    test('loadScreenerStrategies should populate screener checkboxes container', async () => {
        document.body.innerHTML = `
            <div id="screener-checkboxes"></div>
            <span id="screener-count-badge"></span>
        `;
        
        const mockScreeners = [
            { index: 0, name: 'Dip Buyer' }
        ];
        
        jest.spyOn(API, 'get').mockResolvedValueOnce(mockScreeners);

        await loadScreenerStrategies();

        const container = document.getElementById('screener-checkboxes');
        expect(container.innerHTML).toContain('Dip Buyer');
        expect(document.getElementById('screener-count-badge').textContent).toBe('(1)');
    });

    test('loadScreenerResults should render screener cards', async () => {
        document.body.innerHTML = `<div id="screener-results-container"></div>`;
        
        const mockScreenerResults = [
            { screenerId: 's1', screenerName: 'RSI Screener', results: [] }
        ];
        
        jest.spyOn(API, 'get').mockResolvedValueOnce(mockScreenerResults);

        await loadScreenerResults();

        const container = document.getElementById('screener-results-container');
        expect(container.querySelector('.card')).not.toBeNull();
        expect(container.innerHTML).toContain('RSI Screener');
    });

    test('showDashboardFilterBar and hideDashboardFilterBar should toggle bar display', () => {
        document.body.innerHTML = `
            <div id="options-filter-bar" style="display: none;"></div>
        `;

        showDashboardFilterBar('options');
        expect(document.getElementById('options-filter-bar').style.display).toBe('');

        hideDashboardFilterBar('options');
        expect(document.getElementById('options-filter-bar').style.display).toBe('none');
    });

    test('resetDashboardFilter should reset inputs and buttons to clean state', () => {
        document.body.innerHTML = `
            <select id="options-filter-column"><option value="ticker">Ticker</option><option value="expiry">Expiry</option></select>
            <input id="options-filter-text" value="some text" style="display: none;">
            <div id="options-filter-date-group" style="display: flex;"></div>
            <input id="options-filter-date" value="2026-04-17">
            <button id="options-filter-btn" style="display: none;"></button>
            <button id="options-filter-clear" style="display: block;"></button>
            <div id="options-filter-summary">Filtered</div>
        `;

        resetDashboardFilter('options');

        expect(document.getElementById('options-filter-column').value).toBe('ticker');
        expect(document.getElementById('options-filter-text').value).toBe('');
        expect(document.getElementById('options-filter-text').style.display).toBe('inline-block');
        expect(document.getElementById('options-filter-date-group').style.display).toBe('none');
        expect(document.getElementById('options-filter-date').value).toBe('');
        expect(document.getElementById('options-filter-btn').style.display).toBe('inline-block');
        expect(document.getElementById('options-filter-clear').style.display).toBe('none');
        expect(document.getElementById('options-filter-summary').innerHTML).toBe('');
    });

    test('onFilterColumnChange should display date group when expiry is selected', () => {
        jest.useFakeTimers();
        document.body.innerHTML = `
            <select id="options-filter-column"><option value="expiry" selected>Expiry</option></select>
            <input id="options-filter-text" style="display: inline-block;">
            <div id="options-filter-date-group" style="display: none;"></div>
            <button id="options-filter-btn" style="display: none;"></button>
            <button id="options-filter-clear" style="display: block;"></button>
            <div id="options-filter-summary">Summary</div>
        `;

        onFilterColumnChange('options');

        expect(document.getElementById('options-filter-text').style.display).toBe('none');
        expect(document.getElementById('options-filter-date-group').style.display).toBe('');
        expect(document.getElementById('options-filter-btn').style.display).toBe('');
    });

    test('getDashboardItemValue should return formatted lowercase ticker or YYYY-MM-DD date', () => {
        const tradeItem = { symbol: 'AAPL', expiryDate: '2026-04-17T20:00:00.000+00:00' };

        expect(getDashboardItemValue(tradeItem, 'ticker')).toBe('aapl');
        expect(getDashboardItemValue(tradeItem, 'expiry')).toBe('2026-04-17');
    });

    test('matchesDashboardFilter should match ticker substring and expiry date before/after', () => {
        const item = { symbol: 'MSFT', expiryDate: '2026-04-17' };

        expect(matchesDashboardFilter(item, 'ticker', 'ms')).toBe(true);
        expect(matchesDashboardFilter(item, 'ticker', 'aapl')).toBe(false);

        expect(matchesDashboardFilter(item, 'expiry', '2026-05-01', 'before')).toBe(true);
        expect(matchesDashboardFilter(item, 'expiry', '2026-04-01', 'before')).toBe(false);
        expect(matchesDashboardFilter(item, 'expiry', '2026-04-01', 'after')).toBe(true);
    });

    test('applyDashboardFilter should filter trade rows in DOM and update match count', () => {
        document.body.innerHTML = `
            <select id="options-filter-column"><option value="ticker" selected>Ticker</option></select>
            <input id="options-filter-text" value="AAPL">
            <button id="options-filter-clear" style="display: none;"></button>
            <div id="options-filter-summary"></div>
            <div id="results-container">
                <div class="card">
                    <div class="card-header" data-target="card1"></div>
                    <div id="content-card1">
                        <table>
                            <tbody>
                                <tr class="trade-row" data-symbol="AAPL"><td><strong>AAPL</strong></td></tr>
                                <tr class="trade-row" data-symbol="MSFT"><td><strong>MSFT</strong></td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;

        window.tradeDataMap['card1'] = [
            { symbol: 'AAPL' },
            { symbol: 'MSFT' }
        ];

        applyDashboardFilter('options');

        const rows = document.querySelectorAll('.trade-row');
        expect(rows[0].classList.contains('filter-match')).toBe(true);
        expect(rows[1].classList.contains('filter-hidden')).toBe(true);

        const summary = document.getElementById('options-filter-summary');
        expect(summary.innerHTML).toContain('1 match');
    });

    test('clearDashboardFilter should reset input values and restore hidden rows', () => {
        document.body.innerHTML = `
            <input id="options-filter-text" value="AAPL">
            <button id="options-filter-clear" style="display: block;"></button>
            <div id="options-filter-summary">1 match</div>
            <div id="results-container">
                <div class="card">
                    <div class="card-header" data-target="card1"></div>
                    <div id="content-card1" class="open">
                        <table>
                            <tbody>
                                <tr class="trade-row filter-hidden"><td>AAPL</td></tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;

        clearDashboardFilter('options');

        expect(document.getElementById('options-filter-text').value).toBe('');
        expect(document.getElementById('options-filter-clear').style.display).toBe('none');
        expect(document.getElementById('options-filter-summary').innerHTML).toBe('');
        expect(document.querySelector('.trade-row').classList.contains('filter-hidden')).toBe(false);
    });

    test('checkScreenerExecutionStatus should trigger startTimer when running', async () => {
        jest.spyOn(API, 'get').mockResolvedValueOnce({
            running: true,
            currentTask: 'Screener Task',
            startTimeMs: Date.now() - 5000,
            alerts: []
        });

        document.body.innerHTML = '<div id="elapsed-text"></div>';

        await checkScreenerExecutionStatus();

        expect(window.currentExecutionTaskName).toBe('Screener Task');
    });

    test('executeScreenersSelected should post selected screener indices', async () => {
        document.body.innerHTML = `
            <div id="screener-checkboxes">
                <input type="checkbox" value="5" checked>
            </div>
        `;

        jest.spyOn(API, 'post').mockResolvedValueOnce({ message: 'Screeners started' });

        await executeScreenersSelected();

        expect(API.post).toHaveBeenCalledWith('/api/execute', {
            strategyIndices: [],
            screenerIndices: [5]
        });
    });
});

describe('Execute Strategy Configuration', () => {
    beforeEach(() => {
        window.scrollTo = jest.fn();
    });
    test('STRATEGY_TYPES and getLegFilters should generate valid filter schemas', () => {
        expect(STRATEGY_TYPES.length).toBeGreaterThan(0);
        const legFilters = getLegFilters('shortLeg', 'Short Leg');
        expect(legFilters.length).toBe(8);
        expect(legFilters[0].key).toBe('shortLeg.minDelta');
        expect(STRATEGY_SPECIFIC_FILTERS['credit_spread'].length).toBe(16);
    });

    test('checkCustomExecutionStatus should handle running status', async () => {
        document.body.innerHTML = '<div id="custom-progress"></div>';

        jest.spyOn(API, 'get').mockResolvedValueOnce({
            running: true,
            currentTask: 'Custom Strategy',
            startTimeMs: Date.now() - 1000
        });

        await checkCustomExecutionStatus();

        expect(document.getElementById('custom-progress').className).toContain('active');
    });

    test('renderStrategyTemplates should render config cards into container', async () => {
        document.body.innerHTML = '<div id="strategy-templates"></div>';

        window.appConfig = {
            optionsStrategies: [
                { name: 'My Iron Condor', strategyType: 'IRON_CONDOR', enabled: true }
            ]
        };

        await renderStrategyTemplates('IRON_CONDOR');

        const container = document.getElementById('strategy-templates');
        expect(container.innerHTML).toContain('Configured Templates');
        expect(container.innerHTML).toContain('My Iron Condor');
        expect(container.innerHTML).toContain('pill-enabled');
    });

    test('loadTemplateParams should fill inputs from strategy JSON', () => {
        document.body.innerHTML = `
            <input id="alias-input">
            <input id="securities-input">
            <input id="securities-file-input">
            <input data-filter="minDTE">
        `;

        const strategy = {
            alias: 'Test Strategy',
            securities: 'AAPL, MSFT',
            filter: { minDTE: 30 }
        };

        loadTemplateParams(escapeAttr(JSON.stringify(strategy)));

        expect(document.getElementById('alias-input').value).toContain('Test Strategy (Custom)');
        expect(document.getElementById('securities-input').value).toBe('AAPL, MSFT');
        expect(document.querySelector('[data-filter="minDTE"]').value).toBe('30');
    });

    test('loadFiltersFromResult should fill inputs from result dataset button', () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="IRON_CONDOR">Iron Condor</option></select>
            <input id="alias-input">
            <input id="securities-input">
            <input data-filter="targetDTE">
        `;

        const filterConfig = {
            strategyType: 'IRON_CONDOR',
            targetDTE: 45,
            securities: ['SPY']
        };

        const btn = document.createElement('button');
        btn.dataset.filterConfig = escapeAttr(JSON.stringify(filterConfig));
        btn.dataset.strategyName = 'Iron Condor Strategy';

        loadFiltersFromResult(btn);

        expect(document.getElementById('alias-input').value).toBe('Iron Condor Strategy (Reload)');
        expect(document.getElementById('securities-input').value).toBe('SPY');
        expect(document.querySelector('[data-filter="targetDTE"]').value).toBe('45');
    });

    test('fillTechFiltersForm should set technical filter inputs in DOM', () => {
        document.body.innerHTML = `
            <input data-tech-filter="RSI" data-tech-field="condition">
            <input data-tech-filter="RSI" data-tech-field="period">
        `;

        const techFilters = {
            RSI: {
                condition: 'LESS_THAN',
                config: { period: 14 }
            }
        };

        fillTechFiltersForm(techFilters);

        expect(document.querySelector('[data-tech-filter="RSI"][data-tech-field="condition"]').value).toBe('LESS_THAN');
        expect(document.querySelector('[data-tech-filter="RSI"][data-tech-field="period"]').value).toBe('14');
    });

    test('renderSpecificFilters should populate specific filters for selected strategy type', () => {
        document.body.innerHTML = '<div id="specific-filters"></div>';

        renderSpecificFilters('IRON_CONDOR');

        const container = document.getElementById('specific-filters');
        expect(container.innerHTML).toContain('Iron Condor Specific Leg Filters');
        expect(container.innerHTML).toContain('data-filter="putShortLeg.minDelta"');
    });

    test('executeCustom should post custom strategy execution body', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="IRON_CONDOR" selected>Iron Condor</option></select>
            <input id="securities-input" value="AAPL">
            <input id="securities-file-input" value="">
            <input id="alias-input" value="My Custom Execution">
            <input data-filter="targetDTE" value="45" type="number">
            <div id="custom-progress"></div>
        `;

        jest.spyOn(API, 'post').mockResolvedValueOnce({ message: 'Custom execution started' });

        await executeCustom();

        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            strategyType: 'IRON_CONDOR',
            securities: 'AAPL',
            alias: 'My Custom Execution',
            filter: expect.objectContaining({ targetDTE: 45 })
        }));
    });

    test('loadCustomResults should fetch and render custom execution cards', async () => {
        document.body.innerHTML = '<div id="custom-results"></div>';

        const mockCustomResults = [
            { strategyName: 'Custom Iron Condor', trades: [] }
        ];

        jest.spyOn(API, 'get').mockResolvedValueOnce(mockCustomResults);

        await loadCustomResults();

        const container = document.getElementById('custom-results');
        expect(container.querySelector('.card')).not.toBeNull();
    });
});

describe('Config Viewer Functions', () => {
    test('renderInternalFilterGrid should generate HTML grid for scalar and nested filter properties', () => {
        const filter = {
            targetDTE: 45,
            ignoreEarnings: true,
            legs: {
                minDelta: 0.15
            }
        };

        const html = renderInternalFilterGrid(filter);

        expect(html).toContain('Target D T E');
        expect(html).toContain('45');
        expect(html).toContain('✅ Yes');
        expect(html).toContain('Legs');
        expect(html).toContain('Min Delta');
    });

    test('renderConfig should render strategy cards and securities cards into container', () => {
        const container = document.createElement('div');
        const config = {
            optionsStrategies: [
                { alias: 'My Bullish Strategy', strategyType: 'PUT_CREDIT_SPREAD', enabled: true }
            ],
            technicalScreeners: [
                { alias: 'RSI Watchlist', screenerType: 'RSI', enabled: false }
            ]
        };
        const securitiesMaps = {
            'watch.txt': ['AAPL', 'GOOGL']
        };

        renderConfig(config, container, securitiesMaps);

        expect(container.innerHTML).toContain('Options Strategies');
        expect(container.innerHTML).toContain('My Bullish Strategy');
        expect(container.innerHTML).toContain('Securities');
        expect(container.innerHTML).toContain('watch.txt');
        expect(container.innerHTML).toContain('Technical Screeners');
        expect(container.innerHTML).toContain('RSI Watchlist');
    });
});

describe('Market Status and Execution Logs', () => {
    test('fetchAndRenderMarketStatus should fetch status and append badge to main-content', async () => {
        document.body.innerHTML = '<div class="main-content"></div>';

        jest.spyOn(API, 'get').mockResolvedValueOnce({
            equityStatus: 'OPEN',
            optionsStatus: 'OPEN'
        });

        await fetchAndRenderMarketStatus();

        const badge = document.querySelector('.market-status-container');
        expect(badge).not.toBeNull();
        expect(badge.textContent).toContain('Equity: OPEN');
        expect(badge.textContent).toContain('Options: OPEN');
    });

    test('loadLogs should fetch filter-logs and call renderLogGroups', async () => {
        document.body.innerHTML = `
            <div id="logsContainer"></div>
            <div id="logsEmpty" style="display: flex;"></div>
        `;

        const mockLogs = [
            { strategyName: 'Iron Condor', symbol: 'AAPL', filterName: 'Min Delta', inputCount: 10, outputCount: 5 }
        ];

        jest.spyOn(API, 'get').mockResolvedValueOnce(mockLogs);

        await loadLogs();

        expect(document.getElementById('logsEmpty').style.display).toBe('none');
        expect(document.getElementById('logsContainer').innerHTML).toContain('Iron Condor');
    });

    test('clearLogs should invoke clear endpoint and reload logs', async () => {
        document.body.innerHTML = `
            <div id="logsContainer"></div>
            <div id="logsEmpty"></div>
        `;

        jest.spyOn(API, 'post').mockResolvedValueOnce({});
        jest.spyOn(API, 'get').mockResolvedValueOnce([]);

        await clearLogs();

        expect(API.post).toHaveBeenCalledWith('/api/filter-logs/clear');
    });

    test('renderLogFilterRow should render table row with flow bar and percentage', () => {
        const entry = { filterStage: 'Min Delta', tradesIn: 10, tradesOut: 5 };
        const row = renderLogFilterRow(entry);
        expect(row).toContain('Min Delta');
        expect(row).toContain('50%');
        expect(row).toContain('-5');
    });

    test('toggleLogGroup should toggle open class on body and arrow', () => {
        document.body.innerHTML = `
            <div id="test-group"></div>
            <div id="arrow-test-group"></div>
        `;

        toggleLogGroup('test-group');

        expect(document.getElementById('test-group').className).toContain('open');
        expect(document.getElementById('arrow-test-group').className).toContain('open');
    });

    test('onScreenerTypeChange should adjust drop rule input visibility', () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP" selected>Price Drop</option></select>
            <div id="sc-priceDropRules-group" style="display: none;"></div>
            <div id="sc-lookbackDays-group" style="display: none;"></div>
        `;

        onScreenerTypeChange();

        expect(document.getElementById('sc-priceDropRules-group').style.display).toBe('');
        expect(document.getElementById('sc-lookbackDays-group').style.display).toBe('');
    });
});

describe('Execute Screener Configuration', () => {
    test('renderScreenerTemplates should render matching technical screeners from config', async () => {
        document.body.innerHTML = '<div id="screener-templates"></div>';

        window.appConfig = {
            technicalScreeners: [
                { alias: 'My Drop Screener', screenerType: 'PRICE_DROP', enabled: true }
            ]
        };

        await renderScreenerTemplates('PRICE_DROP');

        const container = document.getElementById('screener-templates');
        expect(container.innerHTML).toContain('My Drop Screener');
        expect(container.innerHTML).toContain('pill-enabled');
    });

    test('loadScreenerTemplateParams should set inputs from screener JSON', () => {
        document.body.innerHTML = `
            <input id="screener-alias-input">
            <input id="screener-securities-input">
            <input id="screener-securities-file-input">
            <input id="sc-rsiCondition">
        `;

        const screener = {
            alias: 'RSI Oversold',
            securities: 'AAPL',
            technicalFilters: {
                RSI: { condition: 'OVERSOLD' }
            }
        };

        loadScreenerTemplateParams(escapeAttr(JSON.stringify(screener)));

        expect(document.getElementById('screener-alias-input').value).toContain('RSI Oversold (Custom)');
        expect(document.getElementById('screener-securities-input').value).toBe('AAPL');
        expect(document.getElementById('sc-rsiCondition').value).toBe('OVERSOLD');
    });

    test('executeCustomScreener should post payload to /api/execute/screener/custom', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP" selected>Price Drop</option></select>
            <input id="screener-alias-input" value="Custom Drop Test">
            <input id="screener-securities-input" value="TSLA">
            <input id="screener-securities-file-input" value="">
            <div id="screener-custom-progress"></div>
        `;

        jest.spyOn(API, 'post').mockResolvedValueOnce({ message: 'Screener started' });

        await executeCustomScreener();

        expect(API.post).toHaveBeenCalledWith('/api/execute/custom-screener', expect.objectContaining({
            screenerType: 'PRICE_DROP',
            securities: 'TSLA',
            alias: 'Custom Drop Test'
        }));
    });
});

describe('Technical Filters DOM Parser', () => {
    test('getTechnicalFiltersFromDOM should build technicalFilters object', () => {
        document.body.innerHTML = `
            <input data-tech-filter="RSI" data-tech-field="condition" value="OVERSOLD">
            <input data-tech-filter="RSI" data-tech-field="period" value="14">
            <input data-tech-filter="SIMPLE_MOVING_AVERAGE" data-tech-field="rules" value="SMA20 > SMA50">
        `;

        const tf = getTechnicalFiltersFromDOM();

        expect(tf.RSI.condition).toBe('OVERSOLD');
        expect(tf.RSI.config.period).toBe(14);
        expect(tf.SIMPLE_MOVING_AVERAGE.conditions).toEqual(['SMA20 > SMA50']);
    });

    test('getTechnicalFiltersFromDOM should throw error for CUSTOM_RANGE RSI missing bounds', () => {
        document.body.innerHTML = `
            <input data-tech-filter="RSI" data-tech-field="condition" value="CUSTOM_RANGE">
            <input data-tech-filter="RSI" data-tech-field="min" value="20">
        `;

        expect(() => getTechnicalFiltersFromDOM()).toThrow('Min RSI and Max RSI are mandatory');
    });
});

describe('Table Sorting', () => {
    test('handleTableSort should sort data and re-render table content', () => {
        const cardId = 'testCard';
        const data = [
            { symbol: 'MSFT', dte: 30, maxLoss: 500, returnOnRisk: 10, rsi: 40, dropPercent: 5 },
            { symbol: 'AAPL', dte: 45, maxLoss: 300, returnOnRisk: 15, rsi: 60, dropPercent: 10 }
        ];
        data._type = 'trades';

        window.tradeDataMap = window.tradeDataMap || {};
        window.tableSortState = window.tableSortState || {};
        window.tradeDataMap[cardId] = data;

        document.body.innerHTML = `
            <div id="content-${cardId}"></div>
        `;

        // 1st click on 'ticker' -> ASC ('AAPL' first in HTML)
        handleTableSort(cardId, 'ticker');
        const contentDiv = document.getElementById(`content-${cardId}`);
        expect(contentDiv.innerHTML).toContain('AAPL');

        // 1st click on 'expiry' -> ASC (dte 30 first)
        handleTableSort(cardId, 'expiry');
        expect(contentDiv.innerHTML).toContain('30d');

        // 2nd click on 'expiry' -> DESC (dte 45 first)
        handleTableSort(cardId, 'expiry');
        expect(contentDiv.innerHTML).toContain('45d');
    });
});

describe('Earnings Calendar Functions', () => {
    test('initEarningsCalendar and calendar navigation should render grid', async () => {
        document.body.innerHTML = `
            <div id="cal-month-label"></div>
            <div id="cal-grid">
                <div>Sun</div><div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div>
            </div>
        `;

        jest.spyOn(API, 'get').mockResolvedValueOnce({
            events: {
                '2026-08-15': [{ symbol: 'AAPL', title: 'Q3 Earnings' }]
            }
        });

        await initEarningsCalendar();

        expect(document.getElementById('cal-month-label').textContent).toBeTruthy();
        expect(document.getElementById('cal-grid').children.length).toBeGreaterThan(7);

        calNavigate(1);
        expect(document.getElementById('cal-month-label').textContent).toBeTruthy();

        calGoToday();
        expect(document.getElementById('cal-month-label').textContent).toBeTruthy();
    });

    test('formatDateKey should format date to YYYY-MM-DD', () => {
        const d = new Date(2026, 7, 9); // Month is 0-indexed (August)
        expect(formatDateKey(d)).toBe('2026-08-09');
    });

    test('onDayClick should display earnings detail panel for clicked day', () => {
        document.body.innerHTML = `
            <div id="cal-detail-panel" style="display: none;"></div>
            <div id="cal-detail-date"></div>
            <div id="cal-detail-body"></div>
        `;

        const dateStr = '2026-08-09';
        const date = new Date(2026, 7, 9);

        onDayClick(dateStr, date);

        expect(document.getElementById('cal-detail-panel').style.display).toBe('block');
        expect(document.getElementById('cal-detail-body').innerHTML).toContain('No events on this day');
    });
});

describe('Delete Result Modals and Actions', () => {
    test('deleteCustomResult should call API.delete and remove card element', async () => {
        const card = document.createElement('div');
        jest.spyOn(API, 'delete').mockResolvedValueOnce({});

        await deleteCustomResult('res-123', card);

        expect(API.delete).toHaveBeenCalledWith('/api/results/custom/res-123');
    });

    test('confirmDeleteCustomResult should insert confirmation overlay', () => {
        document.body.innerHTML = '';
        confirmDeleteCustomResult('res-123', document.createElement('div'));

        const overlay = document.querySelector('.delete-confirm-overlay');
        expect(overlay).not.toBeNull();
        expect(overlay.innerHTML).toContain('Delete Execution Result?');
    });

    test('deleteCustomScreenerResult should call API.delete for screener result', async () => {
        const card = document.createElement('div');
        jest.spyOn(API, 'delete').mockResolvedValueOnce({});

        await deleteCustomScreenerResult('scr-456', card);

        expect(API.delete).toHaveBeenCalledWith('/api/results/custom/screeners/scr-456');
    });

    test('promptDeleteCustomScreenerResult should insert screener delete overlay', () => {
        document.body.innerHTML = '';
        promptDeleteCustomScreenerResult('scr-456', { stopPropagation: jest.fn() });

        const overlay = document.querySelector('.modal-overlay');
        expect(overlay).not.toBeNull();
        expect(overlay.innerHTML).toContain('Delete Screener Result');
    });
});

describe('Option Chain Data Table and Row Details', () => {
    test('renderOptionDataTable should generate HTML grid for leg option data', () => {
        const legsOptionData = [
            {
                action: 'SELL',
                optionType: 'PUT',
                optionData: {
                    symbol: 'AAPL 260821P00200000',
                    bid: 2.5,
                    ask: 2.7,
                    mark: 2.6,
                    delta: -0.25,
                    volatility: 35.5
                }
            }
        ];

        const html = renderOptionDataTable(legsOptionData);

        expect(html).toContain('Option Chain Data');
        expect(html).toContain('SELL PUT');
        expect(html).toContain('AAPL 260821P00200000');
        expect(html).toContain('35.5%');
    });

    test('initTradeRowClicks should expand detail panel on row click', async () => {
        document.body.innerHTML = `
            <table>
                <tbody>
                    <tr class="trade-row" data-details="Details Text" data-symbol="AAPL">
                        <td><strong>AAPL</strong></td>
                    </tr>
                </tbody>
            </table>
        `;

        initTradeRowClicks();

        jest.spyOn(API, 'get').mockResolvedValueOnce({
            currentIV: 30,
            ivRank: 45,
            minIV: 20,
            maxIV: 50
        });

        const row = document.querySelector('.trade-row');
        row.click();

        const panel = document.querySelector('.trade-detail-panel');
        expect(panel).not.toBeNull();
        expect(panel.innerHTML).toContain('AAPL — Trade Details');
    });
});

describe('Legacy Strategy and Screener Loaders', () => {
    test('loadStrategies should fetch strategies and screeners and render checkbox items', async () => {
        document.body.innerHTML = `
            <div id="strategy-checkboxes"></div>
            <div id="screener-checkboxes"></div>
            <span id="strategy-count-badge"></span>
            <span id="screener-count-badge"></span>
        `;

        jest.spyOn(API, 'get')
            .mockResolvedValueOnce([{ index: 0, name: 'Bull Put Spread', termType: 'SHORT_TERM' }])
            .mockResolvedValueOnce([{ index: 0, name: 'RSI Oversold' }]);

        await loadStrategies();

        expect(document.getElementById('strategy-checkboxes').innerHTML).toContain('Bull Put Spread');
        expect(document.getElementById('screener-checkboxes').innerHTML).toContain('RSI Oversold');
        expect(document.getElementById('strategy-count-badge').textContent).toBe('(1)');
        expect(document.getElementById('screener-count-badge').textContent).toBe('(1)');
    });
});

describe('Filter Help Tooltip Balloon', () => {
    test('showFilterHelp should create tooltip element on target element click', () => {
        document.body.innerHTML = '<button id="btn-info"></button>';
        const btn = document.getElementById('btn-info');
        const event = {
            stopPropagation: jest.fn(),
            preventDefault: jest.fn(),
            currentTarget: btn
        };

        showFilterHelp(event, 'minDelta', 'Min Delta');

        const tooltip = document.querySelector('.tooltip-balloon');
        expect(tooltip).not.toBeNull();
        expect(tooltip.innerHTML).toContain('Min Delta');
    });
});

