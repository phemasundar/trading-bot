const {
    SCREENER_TYPE_META,
    initScreenerDashboard,
    loadScreenerStrategies,
    loadScreenerResults,
    checkScreenerExecutionStatus,
    executeScreenersSelected,
    initExecuteScreenerPage,
    onScreenerTypeChange,
    renderScreenerTemplates,
    loadScreenerTemplateParams,
    loadScreenerFiltersFromResult,
    executeCustomScreener,
    getTechnicalFiltersFromDOM,
    loadCustomScreenerResults,
    checkCustomScreenerExecutionStatus,
    setCustomScreenerBusy,
    promptDeleteCustomScreenerResult,
    deleteCustomScreenerResult,
    API,
    showToast,
    escapeAttr
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();

describe('Technical Screener Execution Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window.appConfig = null;
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            json: () => Promise.resolve({ supabaseUrl: 'http://localhost', supabaseAnonKey: 'key' })
        });
    });

    test('initScreenerDashboard completes setup', async () => {
        window.supabase = {
            createClient: () => ({
                auth: {
                    getSession: () => Promise.resolve({ data: { session: { access_token: 'tok' } } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };
        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/screeners') return Promise.resolve([{ index: 0, name: 'Screener 1', descriptionFile: 'desc.md' }]);
            if (url === '/api/results/screeners') return Promise.resolve([{ screenerId: 's1', screenerName: 'Screener 1', results: [{ symbol: 'AAPL' }] }]);
            if (url === '/api/status') return Promise.resolve({ running: false, alerts: [] });
            if (url === '/api/market-status') return Promise.resolve({ equityStatus: 'OPEN', optionsStatus: 'OPEN' });
            return Promise.resolve([]);
        });

        document.body.innerHTML = `
            <div class="sidebar"><div class="sidebar-brand">Brand</div></div>
            <div class="main-content"></div>
            <div id="screener-checkboxes"></div>
            <div id="screener-results-container"></div>
            <span id="screener-count-badge"></span>
        `;

        await initScreenerDashboard();
        expect(document.getElementById('screener-checkboxes').innerHTML).toContain('Screener 1');
    });

    test('loadScreenerStrategies error path', async () => {
        document.body.innerHTML = '<div id="screener-checkboxes"></div>';
        API.get = jest.fn().mockRejectedValueOnce(new Error('Network error'));
        await loadScreenerStrategies();
        expect(document.getElementById('screener-checkboxes').innerHTML).toContain('Failed to load screeners');
    });

    test('loadScreenerResults empty state', async () => {
        document.body.innerHTML = '<div id="screener-results-container"></div>';
        API.get = jest.fn().mockResolvedValueOnce([]);
        await loadScreenerResults();
        expect(document.getElementById('screener-results-container').innerHTML).toContain('No technical screener results yet');
    });

    test('loadScreenerResults error path', async () => {
        document.body.innerHTML = '<div id="screener-results-container"></div>';
        API.get = jest.fn().mockRejectedValueOnce(new Error('Results fail'));
        await loadScreenerResults();
        expect(document.getElementById('screener-results-container').innerHTML).toContain('No technical screener results yet');
    });

    test('executeScreenersSelected toast error when no screeners checked', async () => {
        document.body.innerHTML = '<div id="screener-checkboxes"></div>';
        await executeScreenersSelected();
        expect(document.querySelector('.toast-error')).not.toBeNull();
    });

    test('executeScreenersSelected success execution', async () => {
        document.body.innerHTML = `
            <div id="screener-checkboxes">
                <input type="checkbox" value="0" checked data-type="screener">
            </div>
            <button id="execute-btn">Execute</button>
            <div id="progress-container"></div>
        `;
        API.post = jest.fn().mockResolvedValueOnce({ message: 'Execution started' });
        await executeScreenersSelected();
        expect(API.post).toHaveBeenCalledWith('/api/execute', { strategyIndices: [], screenerIndices: [0] });
    });

    test('onScreenerTypeChange handles PRICE_DROP and HIGH_52W_DROP metadata', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP">Price Drop</option></select>
            <div id="sc-priceDropRules-group" style="display:none"></div>
            <div id="sc-lookbackDays-group" style="display:none"></div>
            <select id="sc-rsiCondition"><option value=""></option></select>
            <select id="sc-bollingerCondition"><option value=""></option></select>
            <div id="screener-templates"></div>
        `;
        onScreenerTypeChange();
        expect(document.getElementById('sc-priceDropRules-group').style.display).toBe('');
        expect(document.getElementById('sc-lookbackDays-group').style.display).toBe('');
    });

    test('renderScreenerTemplates and loadScreenerTemplateParams', async () => {
        document.body.innerHTML = `
            <div id="screener-templates"></div>
            <input id="screener-alias-input">
            <input id="screener-securities-input">
            <input id="screener-securities-file-input">
            <input id="sc-rsiCondition">
            <input id="sc-bollingerCondition">
            <input id="sc-volumeRules">
            <input id="sc-priceDropRules">
            <input id="sc-lookbackDays">
            <input id="sc-movingAverageRules">
            <input id="sc-hvPeriod">
            <input id="sc-hvRules">
        `;
        window.appConfig = {
            technicalScreeners: [{
                screenerType: 'RSI_BB_BULLISH_CROSSOVER',
                alias: 'RSI Bull',
                enabled: true,
                securitiesFile: 'tech.txt',
                technicalFilters: {
                    RSI: { condition: 'OVERSOLD' },
                    VOLUME: { conditions: [{ type: 'MIN_VOLUME', min: 1000 }] }
                }
            }]
        };

        await renderScreenerTemplates('RSI_BB_BULLISH_CROSSOVER');
        expect(document.getElementById('screener-templates').innerHTML).toContain('RSI Bull');

        const screenerData = JSON.stringify(window.appConfig.technicalScreeners[0]);
        loadScreenerTemplateParams(escapeAttr(screenerData));
        expect(document.getElementById('screener-alias-input').value).toContain('RSI Bull (Custom)');
    });

    test('loadScreenerFiltersFromResult populates inputs', () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP">Price Drop</option></select>
            <input id="screener-alias-input">
            <input id="screener-securities-file-input">
            <input id="screener-securities-input">
            <div class="main-content"><div class="card"></div></div>
        `;
        const params = {
            screenerType: 'PRICE_DROP',
            alias: 'Drop Test',
            securitiesFile: 'sp500.txt',
            securities: 'AAPL, MSFT'
        };
        const evt = { stopPropagation: jest.fn() };
        loadScreenerFiltersFromResult(escapeAttr(JSON.stringify(params)), evt);
        expect(evt.stopPropagation).toHaveBeenCalled();
        expect(document.getElementById('screener-alias-input').value).toBe('Drop Test');
    });

    test('executeCustomScreener validates missing tickers/files', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP" selected>Price Drop</option></select>
            <input id="screener-alias-input" value="">
            <input id="screener-securities-file-input" value="">
            <input id="screener-securities-input" value="">
        `;
        await executeCustomScreener();
        expect(document.querySelector('.toast-error')).not.toBeNull();
    });

    test('getTechnicalFiltersFromDOM parses rules and validates RSI custom range', () => {
        document.body.innerHTML = `
            <input data-tech-filter="RSI" data-tech-field="condition" value="CUSTOM_RANGE">
            <input data-tech-filter="RSI" data-tech-field="min" value="30">
        `;
        expect(() => getTechnicalFiltersFromDOM()).toThrow('Min RSI and Max RSI are mandatory');

        document.body.innerHTML = `
            <input data-tech-filter="SIMPLE_MOVING_AVERAGE" data-tech-field="rules" value="SMA20 > SMA50">
            <input data-tech-filter="HISTORICAL_VOLATILITY" data-tech-field="period" value="20">
        `;
        const res = getTechnicalFiltersFromDOM();
        expect(res.SIMPLE_MOVING_AVERAGE.conditions).toEqual(['SMA20 > SMA50']);
        expect(res.HISTORICAL_VOLATILITY.config.period).toBe(20);
    });

    test('promptDeleteCustomScreenerResult and deleteCustomScreenerResult', async () => {
        document.body.innerHTML = '<div><div class="card">Screener Card</div></div>';
        const card = document.querySelector('.card');
        API.delete = jest.fn().mockResolvedValueOnce({ success: true });

        promptDeleteCustomScreenerResult('scr-123', { stopPropagation: jest.fn(), target: card });
        const confirmBtn = document.getElementById('confirm-delete-btn');
        expect(confirmBtn).not.toBeNull();

        confirmBtn.click();
        await new Promise(resolve => setTimeout(resolve, 10));
        expect(API.delete).toHaveBeenCalledWith('/api/results/custom/screeners/scr-123');
    });
});
