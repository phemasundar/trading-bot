const {
    STRATEGY_TYPES,
    getLegFilters,
    STRATEGY_SPECIFIC_FILTERS,
    initExecutePage,
    checkCustomExecutionStatus,
    renderStrategyTemplates,
    loadTemplateParams,
    loadFiltersFromResult,
    fillTechFiltersForm,
    renderSpecificFilters,
    executeCustom,
    loadCustomResults,
    reexecuteCustomStrategy,
    EARNINGS_PRESETS,
    onEarningsPresetChange,
    syncEarningsPresetFromInput,
    API,
    escapeAttr
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();
window.scrollTo = jest.fn();

describe('Custom Options Execute Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window.appConfig = null;
    });

    test('initExecutePage populates strategy select and checks execution status', async () => {
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ supabaseUrl: 'http://localhost', supabaseAnonKey: 'key' })
        });
        window.supabase = {
            createClient: () => ({
                auth: {
                    getSession: () => Promise.resolve({ data: { session: { access_token: 'tok' } } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };
        document.body.innerHTML = `
            <select id="strategy-type"></select>
            <div id="custom-results"></div>
            <div id="specific-filters"></div>
            <div id="strategy-templates"></div>
        `;
        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/results/custom') return Promise.resolve([]);
            if (url === '/api/status') return Promise.resolve({ running: true, currentTask: 'Custom PCS', startTimeMs: Date.now() });
            if (url === '/api/market-status') return Promise.resolve({ equityStatus: 'OPEN', optionsStatus: 'OPEN' });
            return Promise.resolve([]);
        });

        await initExecutePage();
        const select = document.getElementById('strategy-type');
        expect(select.options.length).toBeGreaterThan(0);
    });

    test('renderSpecificFilters renders for all strategy groups', () => {
        document.body.innerHTML = '<div id="specific-filters"></div>';
        renderSpecificFilters('PUT_CREDIT_SPREAD');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Short Leg Min Delta');

        renderSpecificFilters('IRON_CONDOR');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Min Combined Credit');

        renderSpecificFilters('LONG_CALL_LEAP');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Min Cost Savings %');

        renderSpecificFilters('BULLISH_BROKEN_WING_BUTTERFLY');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Price/Debit Ratio');
    });

    test('renderStrategyTemplates and loadTemplateParams', async () => {
        document.body.innerHTML = `
            <div id="strategy-templates"></div>
            <input id="alias-input">
            <input id="securities-input">
            <input id="securities-file-input">
            <input data-filter="minDTE">
            <input data-filter="maxDTE">
            <div id="specific-filters"></div>
        `;
        window.appConfig = {
            optionsStrategies: [{
                strategyType: 'PUT_CREDIT_SPREAD',
                alias: 'PCS Template',
                enabled: true,
                securitiesFile: 'pcs.txt',
                filter: { minDTE: 30, maxDTE: 45, shortLeg: { minDelta: 0.15 } }
            }]
        };

        await renderStrategyTemplates('PUT_CREDIT_SPREAD');
        expect(document.getElementById('strategy-templates').innerHTML).toContain('PCS Template');

        const strategyData = JSON.stringify(window.appConfig.optionsStrategies[0]);
        loadTemplateParams(escapeAttr(strategyData));
        expect(document.getElementById('alias-input').value).toContain('PCS Template (Custom)');
        expect(document.querySelector('[data-filter="minDTE"]').value).toBe('30');
    });

    test('renderStrategyTemplates and loadTemplateParams with termType displays detailed name and populates alias', async () => {
        document.body.innerHTML = `
            <div id="strategy-templates"></div>
            <input id="alias-input">
        `;
        window.appConfig = {
            optionsStrategies: [
                {
                    strategyType: 'SHORT_PUT',
                    alias: 'Short Put',
                    termType: 'Short Term',
                    enabled: true,
                    securitiesFile: 'portfolio'
                },
                {
                    strategyType: 'SHORT_PUT',
                    alias: 'Short Put',
                    termType: 'Medium Term',
                    enabled: true,
                    securitiesFile: 'tracking'
                }
            ]
        };

        await renderStrategyTemplates('SHORT_PUT');
        const containerHtml = document.getElementById('strategy-templates').innerHTML;
        expect(containerHtml).toContain('Short Put - Short Term');
        expect(containerHtml).toContain('Short Put - Medium Term');

        const strategyData = JSON.stringify(window.appConfig.optionsStrategies[0]);
        loadTemplateParams(escapeAttr(strategyData));
        expect(document.getElementById('alias-input').value).toBe('Short Put - Short Term (Custom)');
    });

    test('loadFiltersFromResult populates inputs from button dataset', () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD">PCS</option></select>
            <input id="alias-input">
            <input id="securities-file-input">
            <input id="securities-input">
            <input data-filter="minDTE">
            <input data-filter="maxDTE">
            <div id="specific-filters"></div>
        `;

        const filterObj = {
            strategyType: 'PUT_CREDIT_SPREAD',
            minDTE: 20,
            maxDTE: 40,
            securitiesFile: 'tech.txt',
            securities: 'AAPL'
        };

        const dummyBtn = document.createElement('button');
        dummyBtn.dataset.filterConfig = escapeAttr(JSON.stringify(filterObj));
        dummyBtn.dataset.strategyName = 'Put Credit Spread';

        loadFiltersFromResult(dummyBtn);
        expect(document.getElementById('alias-input').value).toContain('Put Credit Spread (Reload)');
        expect(document.querySelector('[data-filter="minDTE"]').value).toBe('20');
    });

    test('fillTechFiltersForm populates technical filter inputs', () => {
        document.body.innerHTML = `
            <select data-tech-filter="RSI" data-tech-field="condition">
                <option value="OVERSOLD">Oversold</option>
            </select>
            <input data-tech-filter="SIMPLE_MOVING_AVERAGE" data-tech-field="rules">
        `;

        fillTechFiltersForm({
            RSI: { condition: 'OVERSOLD' },
            SIMPLE_MOVING_AVERAGE: { conditions: ['SMA20 > SMA50'] }
        });

        expect(document.querySelector('[data-tech-filter="RSI"]').value).toBe('OVERSOLD');
        expect(document.querySelector('[data-tech-filter="SIMPLE_MOVING_AVERAGE"]').value).toBe('SMA20 > SMA50');
    });

    test('executeCustom validates strategy type and submits nested filters', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD" selected>PCS</option></select>
            <input id="securities-input" value="AAPL">
            <input id="securities-file-input" value="">
            <input id="alias-input" value="My PCS">
            <input data-filter="shortLeg.minDelta" value="0.15" type="number">
            <input data-filter="relaxationPriority" value="maxCAGRForBreakEven, maxOptionPricePercent">
            <div id="custom-progress"></div>
        `;
        API.post = jest.fn().mockResolvedValueOnce({ message: 'Submitted' });
        await executeCustom();
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            strategyType: 'PUT_CREDIT_SPREAD',
            alias: 'My PCS',
            filter: expect.objectContaining({
                shortLeg: { minDelta: 0.15 },
                relaxationPriority: ['maxCAGRForBreakEven', 'maxOptionPricePercent']
            })
        }));
    });

    test('loadCustomResults error and populated paths', async () => {
        document.body.innerHTML = '<div id="custom-results"></div>';
        API.get = jest.fn().mockResolvedValueOnce([
            { strategyId: 'custom-1', strategyName: 'Custom PCS', trades: [{ symbol: 'AAPL' }] }
        ]);
        await loadCustomResults();
        expect(document.getElementById('custom-results').innerHTML).toContain('Custom PCS');

        API.get = jest.fn().mockRejectedValueOnce(new Error('Failed custom'));
        await loadCustomResults();
        expect(document.getElementById('custom-results').innerHTML).toContain('Failed to load');
    });

    test('onEarningsPresetChange updates conditions input', () => {
        document.body.innerHTML = `
            <select id="earnings-preset-select">
                <option value="DAYS_TO_NEXT_EARNINGS >= DTE">No Earnings</option>
            </select>
            <input id="earnings-conditions-input" value="" />
        `;
        const select = document.getElementById('earnings-preset-select');
        onEarningsPresetChange(select);
        expect(document.getElementById('earnings-conditions-input').value).toBe('DAYS_TO_NEXT_EARNINGS >= DTE');
    });

    test('syncEarningsPresetFromInput sets matching preset or CUSTOM', () => {
        document.body.innerHTML = `
            <select id="earnings-preset-select">
                <option value="">None</option>
                <option value="DAYS_TO_NEXT_EARNINGS >= DTE">No Earnings</option>
                <option value="CUSTOM">Custom</option>
            </select>
            <input id="earnings-conditions-input" value="DAYS_TO_NEXT_EARNINGS >= DTE" />
        `;
        syncEarningsPresetFromInput();
        expect(document.getElementById('earnings-preset-select').value).toBe('DAYS_TO_NEXT_EARNINGS >= DTE');

        document.getElementById('earnings-conditions-input').value = 'DAYS_TO_NEXT_EARNINGS >= 50';
        syncEarningsPresetFromInput();
        expect(document.getElementById('earnings-preset-select').value).toBe('CUSTOM');

        document.getElementById('earnings-conditions-input').value = '';
        syncEarningsPresetFromInput();
        expect(document.getElementById('earnings-preset-select').value).toBe('');
    });

    test('executeCustom includes earningsFilters.conditions as list', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="SHORT_STRANGLE" selected>Short Strangle</option></select>
            <input id="securities-input" value="AAPL">
            <input id="securities-file-input" value="">
            <input id="alias-input" value="My Strangle">
            <input data-filter="earningsFilters.conditions" value="DAYS_TO_NEXT_EARNINGS >= DTE, EARNINGS_NEAREST_TO_DTE <= DTE - 10">
            <div id="custom-progress"></div>
        `;
        API.post = jest.fn().mockResolvedValueOnce({ message: 'Submitted' });
        await executeCustom();
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            filter: expect.objectContaining({
                earningsFilters: {
                    conditions: [
                        'DAYS_TO_NEXT_EARNINGS >= DTE',
                        'EARNINGS_NEAREST_TO_DTE <= DTE - 10'
                    ]
                }
            })
        }));
    });

    test('executeCustom includes customResultId when passed', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD" selected>PCS</option></select>
            <input id="securities-input" value="AAPL">
            <input id="securities-file-input" value="">
            <input id="alias-input" value="Existing Run">
            <div id="custom-progress"></div>
        `;
        API.post = jest.fn().mockResolvedValueOnce({ message: 'Started' });
        await executeCustom(42);
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            customResultId: 42,
            strategyType: 'PUT_CREDIT_SPREAD'
        }));
    });

    test('loadFiltersFromResult with isReexecute=true does not append (Reload)', () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD">PCS</option></select>
            <input id="alias-input">
            <input id="securities-file-input">
            <input id="securities-input">
            <div id="specific-filters"></div>
        `;
        const filterObj = {
            strategyType: 'PUT_CREDIT_SPREAD',
            securities: 'MSFT'
        };
        const dummyBtn = document.createElement('button');
        dummyBtn.dataset.filterConfig = escapeAttr(JSON.stringify(filterObj));
        dummyBtn.dataset.strategyName = 'Put Credit Spread';

        loadFiltersFromResult(dummyBtn, true);
        expect(document.getElementById('alias-input').value).toBe('Put Credit Spread');
    });

    test('reexecuteCustomStrategy loads filters and submits executeCustom with strategyId', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD">PCS</option></select>
            <input id="alias-input">
            <input id="securities-file-input">
            <input id="securities-input">
            <div id="specific-filters"></div>
            <div id="custom-progress"></div>
        `;
        const filterObj = {
            strategyType: 'PUT_CREDIT_SPREAD',
            securities: 'NVDA'
        };
        const executeBtn = document.createElement('button');
        executeBtn.dataset.filterConfig = escapeAttr(JSON.stringify(filterObj));
        executeBtn.dataset.strategyName = 'NVDA PCS';
        executeBtn.innerHTML = 'Execute';

        API.post = jest.fn().mockResolvedValueOnce({ message: 'Execution started' });

        await reexecuteCustomStrategy(executeBtn, '99');

        expect(document.getElementById('strategy-type').value).toBe('PUT_CREDIT_SPREAD');
        expect(document.getElementById('securities-input').value).toBe('NVDA');
        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            customResultId: 99,
            strategyType: 'PUT_CREDIT_SPREAD'
        }));
    });

    test('reexecuteCustomStrategy prevents duplicate run if progress is already active', async () => {
        document.body.innerHTML = `
            <div id="custom-progress" class="progress-container active"></div>
        `;
        const btn = document.createElement('button');
        btn.innerHTML = '▶ Execute';
        API.post = jest.fn();

        await reexecuteCustomStrategy(btn, '99');
        expect(API.post).not.toHaveBeenCalled();
    });

    test('reexecuteCustomStrategy restores button if execution fails', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="">Select</option></select>
            <div id="custom-progress"></div>
        `;
        const btn = document.createElement('button');
        btn.dataset.filterConfig = escapeAttr(JSON.stringify({ strategyType: '' }));
        btn.innerHTML = '▶ Execute';

        await reexecuteCustomStrategy(btn, '99');
        expect(btn.disabled).toBe(false);
        expect(btn.innerHTML).toBe('▶ Execute');
    });

    test('loadCustomResults preserves open card state and auto-opens updated card with trades', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"></select>
            <div id="custom-results">
                <div class="card">
                    <div class="card-content open" id="content-100"></div>
                </div>
            </div>
        `;

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/results/custom') {
                return Promise.resolve([
                    {
                        strategyId: '100',
                        strategyName: 'PCS 1',
                        filterConfig: { minDTE: 30 },
                        trades: [{ symbol: 'AAPL', returnOnRisk: 10, maxProfit: 50, legs: [] }]
                    },
                    {
                        strategyId: '200',
                        strategyName: 'PCS 2',
                        filterConfig: { minDTE: 30 },
                        trades: [{ symbol: 'MSFT', returnOnRisk: 12, maxProfit: 60, legs: [] }]
                    }
                ]);
            }
            return Promise.resolve({});
        });

        await loadCustomResults('200');

        const card100Content = document.getElementById('content-100');
        const card200Content = document.getElementById('content-200');
        expect(card100Content.classList.contains('open')).toBe(true);
        expect(card200Content.classList.contains('open')).toBe(true);
    });
});
