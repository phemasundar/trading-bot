const {
    STRATEGY_TYPES,
    TRADE_VARIABLES,
    LEG_VARIABLES,
    CONDITION_OPERATORS,
    STRATEGY_LEG_CONFIG,
    STRATEGY_SCALAR_FIELDS,
    parseConditionString,
    addConditionRow,
    getConditionsFromContainer,
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
        expect(document.getElementById('specific-filters').innerHTML).toContain('Short Leg Conditions');

        renderSpecificFilters('IRON_CONDOR');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Min Combined Credit');

        renderSpecificFilters('LONG_CALL_LEAP');
        expect(document.getElementById('specific-filters').innerHTML).toContain('Long Call Conditions');

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

    test('parseConditionString parses valid rules and handles invalid inputs', () => {
        expect(parseConditionString('DTE >= 25')).toEqual({ variable: 'DTE', operator: '>=', value: '25' });
        expect(parseConditionString('MAX_LOSS <= 1000')).toEqual({ variable: 'MAX_LOSS', operator: '<=', value: '1000' });
        expect(parseConditionString('EARNINGS_NEAREST_TO_DTE <= DTE - 10')).toEqual({
            variable: 'EARNINGS_NEAREST_TO_DTE',
            operator: '<=',
            value: 'DTE - 10'
        });
        expect(parseConditionString('INVALID')).toBeNull();
        expect(parseConditionString('')).toBeNull();
        expect(parseConditionString(null)).toBeNull();
    });

    test('addConditionRow and getConditionsFromContainer manage dynamic rows', () => {
        document.body.innerHTML = '<div id="trade-conditions"></div>';
        
        // Non-existent container returns null / empty
        expect(addConditionRow('non-existent', TRADE_VARIABLES)).toBeNull();
        expect(getConditionsFromContainer('non-existent')).toEqual([]);

        // Add row with prefill string
        const row1 = addConditionRow('trade-conditions', TRADE_VARIABLES, 'DTE >= 30');
        expect(row1).not.toBeNull();
        expect(row1.querySelector('.condition-var-select').value).toBe('DTE');
        expect(row1.querySelector('.condition-op-select').value).toBe('>=');
        expect(row1.querySelector('.condition-val-input').value).toBe('30');

        // Condition info button
        const infoBtn1 = row1.querySelector('.condition-info-btn');
        expect(infoBtn1).not.toBeNull();
        expect(infoBtn1.dataset.key).toBe('DTE');
        expect(infoBtn1.dataset.label).toBe('DTE');

        // Changing variable updates info button key and label
        const varSel1 = row1.querySelector('.condition-var-select');
        varSel1.value = 'MAX_LOSS';
        varSel1.dispatchEvent(new Event('change'));
        expect(infoBtn1.dataset.key).toBe('MAX_LOSS');
        expect(infoBtn1.dataset.label).toBe('MAX_LOSS ($)');

        // Clicking info button invokes showFilterHelp
        const origShowFilterHelp = window.showFilterHelp;
        window.showFilterHelp = jest.fn();
        infoBtn1.click();
        expect(window.showFilterHelp).toHaveBeenCalledWith(expect.anything(), 'MAX_LOSS', 'MAX_LOSS ($)');
        window.showFilterHelp = origShowFilterHelp;

        // Add row with custom variable
        const row2 = addConditionRow('trade-conditions', TRADE_VARIABLES, 'CUSTOM_VAR <= 50');
        expect(row2.querySelector('.condition-var-select').value).toBe('CUSTOM_VAR');

        // Add empty row
        const row3 = addConditionRow('trade-conditions', TRADE_VARIABLES);
        expect(row3.querySelector('.condition-var-select').value).toBe(TRADE_VARIABLES[0].value);
        expect(row3.querySelector('.condition-op-select').value).toBe(CONDITION_OPERATORS[0]);
        expect(row3.querySelector('.condition-val-input').value).toBe('');

        // getConditionsFromContainer ignores row with empty value
        let conds = getConditionsFromContainer('trade-conditions');
        expect(conds).toEqual(['MAX_LOSS >= 30', 'CUSTOM_VAR <= 50']);

        // Remove button removes row
        const removeBtn = row2.querySelector('.condition-remove-btn');
        removeBtn.click();
        conds = getConditionsFromContainer('trade-conditions');
        expect(conds).toEqual(['MAX_LOSS >= 30']);
    });

    test('loadTemplateParams with conditions populates condition rows', () => {
        document.body.innerHTML = `
            <input id="alias-input">
            <input id="securities-input">
            <input id="securities-file-input">
            <input data-filter="targetDTE">
            <input id="earnings-conditions-input" data-filter="earningsFilters.conditions">
            <select id="earnings-preset-select"><option value=""></option></select>
            <div id="trade-conditions"></div>
            <div id="specific-filters"></div>
            <div id="shortLeg-conditions"></div>
            <div id="longLeg-conditions"></div>
        `;

        const template = {
            strategyType: 'PUT_CREDIT_SPREAD',
            alias: 'PCS Conditions Template',
            securities: 'AAPL, MSFT',
            securitiesFile: 'portfolio',
            filter: {
                targetDTE: 45,
                conditions: ['DTE >= 25', 'MAX_LOSS <= 1000', 'RETURN_ON_RISK >= 12'],
                shortLeg: {
                    conditions: ['DELTA <= 0.2', 'OPEN_INTEREST >= 500']
                },
                earningsFilters: {
                    conditions: ['DAYS_TO_NEXT_EARNINGS >= DTE']
                }
            }
        };

        loadTemplateParams(escapeAttr(JSON.stringify(template)));

        expect(document.getElementById('alias-input').value).toBe('PCS Conditions Template (Custom)');
        expect(document.getElementById('securities-input').value).toBe('AAPL, MSFT');
        expect(document.querySelector('[data-filter="targetDTE"]').value).toBe('45');
        expect(document.getElementById('earnings-conditions-input').value).toBe('DAYS_TO_NEXT_EARNINGS >= DTE');

        const tradeConds = getConditionsFromContainer('trade-conditions');
        expect(tradeConds).toEqual(['DTE >= 25', 'MAX_LOSS <= 1000', 'RETURN_ON_RISK >= 12']);

        const legConds = getConditionsFromContainer('shortLeg-conditions');
        expect(legConds).toEqual(['DELTA <= 0.2', 'OPEN_INTEREST >= 500']);
    });

    test('loadFiltersFromResult populates condition rows directly and supports legacy fallback', () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD">PCS</option></select>
            <input id="alias-input">
            <input id="securities-file-input">
            <input id="securities-input">
            <input data-filter="targetDTE">
            <input id="earnings-conditions-input" data-filter="earningsFilters.conditions">
            <select id="earnings-preset-select"><option value=""></option></select>
            <div id="trade-conditions"></div>
            <div id="specific-filters"></div>
            <div id="shortLeg-conditions"></div>
            <div id="longLeg-conditions"></div>
        `;

        // Case A: Result with conditions array
        const modernResult = {
            strategyType: 'PUT_CREDIT_SPREAD',
            targetDTE: 30,
            conditions: ['DTE >= 20', 'MAX_LOSS <= 800'],
            shortLeg: {
                conditions: ['DELTA <= 0.15']
            }
        };

        const btnA = document.createElement('button');
        btnA.dataset.filterConfig = escapeAttr(JSON.stringify(modernResult));
        btnA.dataset.strategyName = 'PCS Modern Run';

        loadFiltersFromResult(btnA);
        expect(getConditionsFromContainer('trade-conditions')).toEqual(['DTE >= 20', 'MAX_LOSS <= 800']);
        expect(getConditionsFromContainer('shortLeg-conditions')).toEqual(['DELTA <= 0.15']);

        // Case B: Legacy result without conditions array (historical fallback)
        const legacyResult = {
            strategyType: 'PUT_CREDIT_SPREAD',
            minDTE: 25,
            maxDTE: 50,
            maxLossLimit: 600,
            minReturnOnRisk: 15,
            minReturnOnRiskCAGR: 40,
            minIVRank: 20,
            maxIVRank: 85,
            minIVPercentile: 30,
            maxIVPercentile: 90,
            maxBreakEvenPercentage: 4.5,
            maxUpperBreakevenDelta: 0.2,
            maxTotalDebit: 500,
            maxTotalCredit: 1500,
            minTotalCredit: 100,
            maxNetExtrinsicValueToPricePercentage: 0.6,
            minNetExtrinsicValueToPricePercentage: 0.1,
            maxCAGRForBreakEven: 12.0,
            maxOptionPricePercent: 25.0,
            minCostSavingsPercent: 15.0,
            shortLeg: {
                minDelta: 0.12,
                maxDelta: 0.25,
                minOpenInterest: 200,
                minVolume: 20,
                minPremium: 0.75,
                maxPremium: 4.00,
                minVolatility: 15.0,
                maxVolatility: 60.0
            }
        };

        const btnB = document.createElement('button');
        btnB.dataset.filterConfig = escapeAttr(JSON.stringify(legacyResult));
        btnB.dataset.strategyName = 'PCS Legacy Run';

        loadFiltersFromResult(btnB);
        const legacyTradeConds = getConditionsFromContainer('trade-conditions');
        expect(legacyTradeConds).toContain('DTE >= 25');
        expect(legacyTradeConds).toContain('DTE <= 50');
        expect(legacyTradeConds).toContain('MAX_LOSS <= 600');
        expect(legacyTradeConds).toContain('RETURN_ON_RISK >= 15');
        expect(legacyTradeConds).toContain('CAGR >= 40');
        expect(legacyTradeConds).toContain('IV_RANK >= 20');
        expect(legacyTradeConds).toContain('IV_RANK <= 85');
        expect(legacyTradeConds).toContain('IV_PERCENTILE >= 30');
        expect(legacyTradeConds).toContain('IV_PERCENTILE <= 90');
        expect(legacyTradeConds).toContain('BREAK_EVEN_PCT <= 4.5');
        expect(legacyTradeConds).toContain('UPPER_BREAK_EVEN_DELTA <= 0.2');
        expect(legacyTradeConds).toContain('TOTAL_DEBIT <= 500');
        expect(legacyTradeConds).toContain('NET_CREDIT <= 1500');
        expect(legacyTradeConds).toContain('NET_CREDIT >= 100');
        expect(legacyTradeConds).toContain('ANNUALIZED_EXTRINSIC_PCT <= 0.6');
        expect(legacyTradeConds).toContain('ANNUALIZED_EXTRINSIC_PCT >= 0.1');
        expect(legacyTradeConds).toContain('BREAKEVEN_CAGR <= 12');
        expect(legacyTradeConds).toContain('OPTION_PRICE_PERCENT <= 25');
        expect(legacyTradeConds).toContain('COST_SAVINGS_PERCENT >= 15');

        const legacyLegConds = getConditionsFromContainer('shortLeg-conditions');
        expect(legacyLegConds).toContain('DELTA >= 0.12');
        expect(legacyLegConds).toContain('DELTA <= 0.25');
        expect(legacyLegConds).toContain('OPEN_INTEREST >= 200');
        expect(legacyLegConds).toContain('VOLUME >= 20');
        expect(legacyLegConds).toContain('MARK >= 0.75');
        expect(legacyLegConds).toContain('MARK <= 4');
        expect(legacyLegConds).toContain('IV >= 15');
        expect(legacyLegConds).toContain('IV <= 60');
    });

    test('executeCustom collects trade and leg condition containers into payload', async () => {
        document.body.innerHTML = `
            <select id="strategy-type"><option value="PUT_CREDIT_SPREAD" selected>PCS</option></select>
            <input id="securities-input" value="AAPL">
            <input id="securities-file-input" value="">
            <input id="alias-input" value="Condition PCS Run">
            <input data-filter="targetDTE" value="45" type="number">
            <div id="trade-conditions"></div>
            <div id="shortLeg-conditions"></div>
            <div id="longLeg-conditions"></div>
            <div id="custom-progress"></div>
        `;

        addConditionRow('trade-conditions', TRADE_VARIABLES, 'DTE >= 30');
        addConditionRow('trade-conditions', TRADE_VARIABLES, 'MAX_LOSS <= 1000');
        addConditionRow('shortLeg-conditions', LEG_VARIABLES, 'DELTA <= 0.20');

        API.post = jest.fn().mockResolvedValueOnce({ message: 'Started' });

        await executeCustom();

        expect(API.post).toHaveBeenCalledWith('/api/execute/custom', expect.objectContaining({
            strategyType: 'PUT_CREDIT_SPREAD',
            alias: 'Condition PCS Run',
            filter: expect.objectContaining({
                targetDTE: 45,
                conditions: ['DTE >= 30', 'MAX_LOSS <= 1000'],
                shortLeg: {
                    conditions: ['DELTA <= 0.20']
                }
            })
        }));
    });
});

