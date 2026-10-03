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
    reexecuteCustomScreener,
    reexecuteAllCustomScreeners,
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

    test('onScreenerTypeChange updates screener-type-info-btn title and inputs', () => {
        document.body.innerHTML = `
            <button id="screener-type-info-btn"></button>
            <select id="screener-type">
                <option value="PRICE_DROP" selected>Price Drop</option>
            </select>
            <div id="sc-priceDropRules-group" style="display:none"></div>
            <div id="sc-lookbackDays-group" style="display:none"></div>
            <div id="screener-templates"></div>
        `;
        window.FILTER_DESCRIPTIONS = { PRICE_DROP: 'Drop filter desc' };
        onScreenerTypeChange();

        const btn = document.getElementById('screener-type-info-btn');
        expect(btn.title).toBe('Drop filter desc');
        expect(document.getElementById('sc-priceDropRules-group').style.display).toBe('');
    });

    test('initExecuteScreenerPage initializes authed and loads screener results', async () => {
        window.supabase = {
            createClient: () => ({
                auth: {
                    getSession: () => Promise.resolve({ data: { session: { access_token: 'tok' } } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };
        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/results/custom/screeners') return Promise.resolve([]);
            if (url === '/api/status') return Promise.resolve({ running: false });
            return Promise.resolve([]);
        });

        document.body.innerHTML = `
            <select id="screener-type"><option value="RSI_OVERSOLD" selected>RSI Oversold</option></select>
            <div id="screener-custom-results"></div>
            <div id="screener-templates"></div>
        `;

        await initExecuteScreenerPage();
        expect(API.get).toHaveBeenCalledWith('/api/results/custom/screeners');
    });

    test('reexecuteCustomScreener loads parameters, disables button, executes with customResultId, and restores button', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP">Price Drop</option></select>
            <input id="screener-alias-input" value="">
            <input id="screener-securities-file-input" value="">
            <input id="screener-securities-input" value="">
            <div id="screener-custom-progress" style="display: none;"></div>
            <div id="screener-custom-results"></div>
            <div id="screener-custom-timer"></div>
        `;

        const btn = document.createElement('button');
        btn.innerHTML = '▶ Execute';
        const params = {
            screenerType: 'PRICE_DROP',
            alias: 'Drop Test',
            securitiesFile: '',
            securities: 'AAPL, MSFT',
            technicalFilters: {}
        };
        btn.dataset.requestParams = JSON.stringify(params);
        btn.dataset.screenerName = 'Drop Test';

        API.post = jest.fn().mockResolvedValue({ message: 'Execution started' });
        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/status') return Promise.resolve({ running: false });
            if (url === '/api/results/custom/screeners') return Promise.resolve([]);
            return Promise.resolve([]);
        });

        await reexecuteCustomScreener(btn, '123');

        expect(document.getElementById('screener-type').value).toBe('PRICE_DROP');
        expect(document.getElementById('screener-securities-input').value).toBe('AAPL, MSFT');
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom-screener', expect.objectContaining({
            customResultId: 123,
            screenerType: 'PRICE_DROP',
            securities: 'AAPL, MSFT'
        }));
    });

    test('reexecuteCustomScreener prevents duplicate run if progress is already active', async () => {
        document.body.innerHTML = `
            <div id="screener-custom-progress" class="active" style="display: block;"></div>
        `;
        const btn = document.createElement('button');
        btn.innerHTML = '▶ Execute';
        API.post = jest.fn();

        await reexecuteCustomScreener(btn, '123');
        expect(API.post).not.toHaveBeenCalled();
    });

    test('reexecuteCustomScreener restores button if execution fails', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="">Select</option></select>
            <div id="screener-custom-progress" style="display: none;"></div>
        `;
        const btn = document.createElement('button');
        btn.dataset.requestParams = JSON.stringify({ screenerType: '' });
        btn.innerHTML = '▶ Execute';

        await reexecuteCustomScreener(btn, '123');
        expect(btn.disabled).toBe(false);
        expect(btn.innerHTML).toBe('▶ Execute');
    });

    test('loadScreenerFiltersFromResult works with button element passing data attributes', () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP">Price Drop</option></select>
            <input id="screener-alias-input">
            <input id="screener-securities-file-input">
            <input id="screener-securities-input">
        `;
        const btn = document.createElement('button');
        btn.dataset.requestParams = JSON.stringify({
            screenerType: 'PRICE_DROP',
            alias: 'Drop Re-run',
            securitiesFile: 'sp500.txt',
            securities: 'GOOGL'
        });
        btn.dataset.screenerName = 'Drop Re-run';

        loadScreenerFiltersFromResult(btn);
        expect(document.getElementById('screener-alias-input').value).toBe('Drop Re-run');
        expect(document.getElementById('screener-securities-input').value).toBe('GOOGL');
    });

    test('loadCustomScreenerResults preserves open cards and opens updatedResultId', async () => {
        document.body.innerHTML = `
            <select id="screener-type"></select>
            <div id="screener-custom-results">
                <div class="card">
                    <div class="card-content open" id="content-100"></div>
                </div>
            </div>
        `;

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/results/custom/screeners') {
                return Promise.resolve([
                    {
                        screenerId: '100',
                        screenerName: 'Screener 100',
                        requestParams: { screenerType: 'PRICE_DROP' },
                        results: [{ symbol: 'AAPL' }]
                    },
                    {
                        screenerId: '200',
                        screenerName: 'Screener 200',
                        requestParams: { screenerType: 'PRICE_DROP' },
                        results: [{ symbol: 'MSFT' }]
                    }
                ]);
            }
            return Promise.resolve({});
        });

        await loadCustomScreenerResults('200');

        const card100Content = document.getElementById('content-100');
        const card200Content = document.getElementById('content-200');
        expect(card100Content.classList.contains('open')).toBe(true);
        expect(card200Content.classList.contains('open')).toBe(true);
    });

    test('loadCustomScreenerResults updates execute-all button and count badge correctly', async () => {
        document.body.innerHTML = `
            <div id="screener-custom-results"></div>
            <button id="execute-all-screener-btn" style="display:none;"></button>
            <span id="screener-custom-results-count-badge" style="display:none;"></span>
        `;

        API.get = jest.fn().mockResolvedValueOnce([
            { screenerId: 'sc-1', screenerName: 'Screener 1', requestParams: { screenerType: 'PRICE_DROP' }, results: [] },
            { screenerId: 'sc-2', screenerName: 'Screener 2', requestParams: { screenerType: 'BOLLINGER_BAND' }, results: [] }
        ]);

        await loadCustomScreenerResults();

        const btn = document.getElementById('execute-all-screener-btn');
        const badge = document.getElementById('screener-custom-results-count-badge');
        expect(btn.style.display).toBe('inline-flex');
        expect(badge.style.display).toBe('inline-block');
        expect(badge.textContent).toBe('2');

        // Test empty results
        API.get = jest.fn().mockResolvedValueOnce([]);
        await loadCustomScreenerResults();
        expect(btn.style.display).toBe('none');
        expect(badge.style.display).toBe('none');
    });

    test('executeCustomScreener with isBatch=true calls pollUntilComplete and returns true', async () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="PRICE_DROP" selected>Price Drop</option></select>
            <input id="screener-securities-input" value="QQQ">
            <input id="screener-securities-file-input" value="">
            <input id="screener-alias-input" value="Batch Drop">
            <div id="screener-custom-progress"></div>
        `;

        API.post = jest.fn().mockResolvedValueOnce({ message: 'Screener execution started' });
        window.pollUntilComplete = jest.fn().mockResolvedValueOnce({ running: false });

        const started = await executeCustomScreener('123', true);
        expect(started).toBe(true);
        expect(window.pollUntilComplete).toHaveBeenCalled();
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom-screener', expect.objectContaining({
            customResultId: 123
        }));
    });

    test('reexecuteAllCustomScreeners delegates to executeAllCustomCards', async () => {
        document.body.innerHTML = `
            <button id="execute-all-screener-btn">▶ Execute All</button>
            <div id="screener-custom-results">
                <div class="card">
                    <button class="btn btn-sm btn-ghost" 
                        data-request-params="{&quot;screenerType&quot;:&quot;PRICE_DROP&quot;,&quot;alias&quot;:&quot;Drop 1&quot;,&quot;securities&quot;:&quot;AAPL&quot;}"
                        data-screener-name="Drop 1"
                        onclick="reexecuteCustomScreener(this, '201')">▶ Execute</button>
                </div>
            </div>
            <select id="screener-type"><option value="PRICE_DROP">Price Drop</option></select>
            <input id="screener-securities-input" value="AAPL">
            <input id="screener-alias-input" value="Drop 1">
            <div id="screener-custom-progress"></div>
        `;

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/status') return Promise.resolve({ running: false });
            if (url === '/api/results/custom/screeners') return Promise.resolve([]);
            return Promise.resolve({});
        });
        API.post = jest.fn().mockResolvedValue({ message: 'Execution started' });
        window.pollUntilComplete = jest.fn().mockResolvedValue({ running: false });

        await reexecuteAllCustomScreeners();

        const execAllBtn = document.getElementById('execute-all-screener-btn');
        expect(execAllBtn.disabled).toBe(false);
        expect(execAllBtn.innerHTML).toBe('▶ Execute All');
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom-screener/batch', expect.any(Array));
    });

    test('getCustomScreenerPayload returns null if type is missing or neither file nor tickers provided', () => {
        document.body.innerHTML = `
            <select id="screener-type"><option value="">Select</option></select>
            <input id="screener-securities-input" value="">
            <input id="screener-securities-file-input" value="">
        `;
        expect(getCustomScreenerPayload()).toBeNull();

        document.getElementById('screener-type').innerHTML = `<option value="PRICE_DROP" selected>Price Drop</option>`;
        expect(getCustomScreenerPayload()).toBeNull();

        document.getElementById('screener-securities-input').value = 'AAPL';
        const payload = getCustomScreenerPayload(42);
        expect(payload).toEqual(expect.objectContaining({
            screenerType: 'PRICE_DROP',
            securities: 'AAPL',
            customResultId: 42
        }));
    });

    test('checkCustomScreenerExecutionStatus disables buttons and reconnects polling when running is true', async () => {
        document.body.innerHTML = `
            <button id="execute-all-screener-btn">▶ Execute All</button>
            <div id="screener-custom-results">
                <div class="card">
                    <button onclick="reexecuteCustomScreener(this, '201')">▶ Execute</button>
                </div>
            </div>
            <div id="screener-custom-progress" style="display:none;"></div>
            <div id="elapsed-text"></div>
        `;

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/status') {
                return Promise.resolve({ running: true, startTimeMs: Date.now() - 5000, currentTask: 'Custom Screener: Test (1 of 2)' });
            }
            if (url === '/api/results/custom/screeners') {
                return Promise.resolve([]);
            }
            return Promise.resolve({});
        });

        window.startPolling = jest.fn((cb) => {
            cb(); // trigger completion callback
        });

        await checkCustomScreenerExecutionStatus();

        const execAllBtn = document.getElementById('execute-all-screener-btn');
        expect(execAllBtn.disabled).toBe(false);
        expect(execAllBtn.innerHTML).toBe('▶ Execute All');
        expect(window.startPolling).toHaveBeenCalled();
    });
});
