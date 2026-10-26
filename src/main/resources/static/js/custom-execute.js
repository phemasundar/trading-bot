/**
 * Trading Bot — Custom Options Execution
 * Options custom execution form logic, leg filters, strategy templates, and custom results loading.
 */

// In Node.js testing environment, load shared utilities, dashboard helpers, and technical condition builders
if (typeof window === 'undefined' && typeof require === 'function') {
    const _utils = require('./utils');
    const _dashboard = require('./dashboard');
    const _screener = require('./screener-execute');
    Object.assign(global, _utils, _dashboard, _screener);
}

const STRATEGY_TYPES = [
    { value: 'PUT_CREDIT_SPREAD', label: 'Put Credit Spread', group: 'credit_spread' },
    { value: 'CALL_CREDIT_SPREAD', label: 'Call Credit Spread', group: 'credit_spread' },
    { value: 'IRON_CONDOR', label: 'Iron Condor', group: 'iron_condor' },
    { value: 'LONG_CALL_LEAP', label: 'Long Call LEAP', group: 'leap' },
    { value: 'BULLISH_BROKEN_WING_BUTTERFLY', label: 'Bullish Broken Wing Butterfly', group: 'bwb' },
    { value: 'BULLISH_ZEBRA', label: 'Bullish ZEBRA', group: 'zebra' },
    { value: 'SHORT_PUT', label: 'Short Put', group: 'short_put' },
    { value: 'SHORT_STRANGLE', label: 'Short Strangle', group: 'short_strangle' },
];

const TRADE_VARIABLES = [
    { value: 'DTE', label: 'DTE' },
    { value: 'MAX_LOSS', label: 'MAX_LOSS ($)' },
    { value: 'RETURN_ON_RISK', label: 'RETURN_ON_RISK (%)' },
    { value: 'CAGR', label: 'CAGR (%)' },
    { value: 'IV_PERCENTILE', label: 'IV_PERCENTILE' },
    { value: 'IV_RANK', label: 'IV_RANK' },
    { value: 'BREAK_EVEN_PCT', label: 'BREAK_EVEN_PCT (%)' },
    { value: 'UPPER_BREAK_EVEN_DELTA', label: 'UPPER_BREAK_EVEN_DELTA' },
    { value: 'TOTAL_DEBIT', label: 'TOTAL_DEBIT ($)' },
    { value: 'NET_CREDIT', label: 'NET_CREDIT ($)' },
    { value: 'OPTION_PRICE_PCT', label: 'OPTION_PRICE_PCT (%)' },
    { value: 'BREAKEVEN_CAGR', label: 'BREAKEVEN_CAGR (%)' },
    { value: 'COST_SAVINGS_PCT', label: 'COST_SAVINGS_PCT (%)' },
    { value: 'ANNUALIZED_EXTRINSIC_PCT', label: 'ANNUALIZED_EXTRINSIC_PCT (%)' },
    { value: 'MAX_LOSS_UPSIDE', label: 'MAX_LOSS_UPSIDE ($)' },
    { value: 'MAX_LOSS_DOWNSIDE', label: 'MAX_LOSS_DOWNSIDE ($)' },
    {
        value: 'BOLLINGER_BAND',
        label: 'Bollinger Band',
        operators: ['==', '<=', '>='],
        allowCustom: false,
        predefinedValues: [
            { value: 'LOWER_BAND', label: 'At/below lower band (LOWER_BAND)' },
            { value: 'UPPER_BAND', label: 'At/above upper band (UPPER_BAND)' },
        ]
    },
];


const LEG_VARIABLES = [
    { value: 'DELTA', label: 'DELTA' },
    { value: 'OPEN_INTEREST', label: 'OPEN_INTEREST' },
    { value: 'VOLUME', label: 'VOLUME' },
    { value: 'IV', label: 'IV' },
    { value: 'MARK', label: 'MARK (Premium)' },
    { value: 'BID', label: 'BID' },
    { value: 'ASK', label: 'ASK' },
    { value: 'STRIKE_PRICE', label: 'STRIKE_PRICE' },
];


const STRATEGY_LEG_CONFIG = {
    credit_spread: [
        { prefix: 'shortLeg', title: 'Short Leg' },
        { prefix: 'longLeg', title: 'Long Leg' }
    ],
    iron_condor: [
        { prefix: 'putShortLeg', title: 'Put Short Leg' },
        { prefix: 'putLongLeg', title: 'Put Long Leg' },
        { prefix: 'callShortLeg', title: 'Call Short Leg' },
        { prefix: 'callLongLeg', title: 'Call Long Leg' }
    ],
    leap: [
        { prefix: 'longCall', title: 'Long Call' }
    ],
    bwb: [
        { prefix: 'leg1Long', title: 'Lower Strike Long Leg' },
        { prefix: 'leg2Short', title: 'Middle Short Legs' },
        { prefix: 'leg3Long', title: 'Upper Strike Long Leg' }
    ],
    zebra: [
        { prefix: 'shortCall', title: 'Short Call' },
        { prefix: 'longCall', title: 'Long Call' }
    ],
    short_put: [
        { prefix: 'shortLeg', title: 'Short Put Leg' }
    ],
    short_strangle: [
        { prefix: 'putShortLeg', title: 'Short Put Leg' },
        { prefix: 'callShortLeg', title: 'Short Call Leg' }
    ],
};

const STRATEGY_SCALAR_FIELDS = {
    credit_spread: [],
    iron_condor: [
        { key: 'minCombinedCredit', label: 'Min Combined Credit', placeholder: '100' },
    ],
    leap: [
        { key: 'marginInterestRate', label: 'Margin Interest Rate', placeholder: '6.0', step: '0.1' },
        { key: 'savingsInterestRate', label: 'Savings Interest Rate', placeholder: '10.0', step: '0.1' },
        { key: 'relaxationPriority', label: 'Relaxation Priority (comma separated)', placeholder: 'maxCAGRForBreakEven, maxOptionPricePercent', type: 'text' },
        { key: 'sortPriority', label: 'Sort Priority (comma separated)', placeholder: 'daysToExpiration, costSavingsPercent', type: 'text' },
    ],
    bwb: [
        { key: 'priceVsMaxDebitRatio', label: 'Price/Debit Ratio', placeholder: '2.0', step: '0.1' },
    ],
    zebra: [],
    short_put: [],
    short_strangle: [],
};

const EARNINGS_PRESETS = [
    { value: '', label: '— None (Ignore Earnings) —', conditions: [] },
    { value: 'DAYS_TO_NEXT_EARNINGS >= DTE', label: 'No Earnings Before Expiration', conditions: ['DAYS_TO_NEXT_EARNINGS >= DTE'] },
    { value: 'EARNINGS_NEAREST_TO_DTE <= DTE - 10', label: 'Safe Close (≤ DTE - 10)', conditions: ['EARNINGS_NEAREST_TO_DTE <= DTE - 10'] },
    { value: 'EARNINGS_NEAREST_TO_DTE <= DTE - 5', label: 'Safe Close (≤ DTE - 5)', conditions: ['EARNINGS_NEAREST_TO_DTE <= DTE - 5'] },
    { value: 'DAYS_TO_NEXT_EARNINGS >= DTE, EARNINGS_NEAREST_TO_DTE <= DTE - 10', label: 'No Earnings & Safe Close', conditions: ['DAYS_TO_NEXT_EARNINGS >= DTE', 'EARNINGS_NEAREST_TO_DTE <= DTE - 10'] },
    { value: 'DAYS_TO_NEXT_EARNINGS <= 14', label: 'Capture Earnings Premium (≤ 14d)', conditions: ['DAYS_TO_NEXT_EARNINGS <= 14'] },
    { value: 'CUSTOM', label: 'Custom Condition', conditions: null }
];

function onEarningsPresetChange(selectEl) {
    const val = selectEl ? selectEl.value : '';
    const condInput = document.getElementById('earnings-conditions-input');
    if (!condInput) return;
    const preset = EARNINGS_PRESETS.find(p => p.value === val);
    if (preset && preset.conditions !== null) {
        condInput.value = preset.conditions.join(', ');
    }
}

function syncEarningsPresetFromInput() {
    const condInput = document.getElementById('earnings-conditions-input');
    const selectEl = document.getElementById('earnings-preset-select');
    if (!condInput || !selectEl) return;
    const rawVal = condInput.value.trim();
    if (!rawVal) {
        selectEl.value = '';
        return;
    }
    const currentRules = rawVal.split(',').map(s => s.trim()).filter(Boolean);
    const matched = EARNINGS_PRESETS.find(p => {
        if (!p.conditions || p.conditions.length !== currentRules.length) return false;
        return p.conditions.every((c, i) => c === currentRules[i]);
    });
    selectEl.value = matched ? matched.value : 'CUSTOM';
}

async function initExecutePage() {
    const authed = await initAuth();
    if (!authed) return;
    const select = document.getElementById('strategy-type');
    if (!select) return;

    STRATEGY_TYPES.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.value;
        opt.textContent = s.label;
        opt.dataset.group = s.group;
        select.appendChild(opt);
    });

    if (typeof loadStrategyColumnsConfig === 'function') {
        await loadStrategyColumnsConfig();
    }
    loadFilterDescriptions();
    syncEarningsPresetFromInput();

    select.addEventListener('change', () => {
        renderSpecificFilters(select.value);
        renderStrategyTemplates(select.value);
    });

    const urlParams = new URLSearchParams(window.location.search);
    const preselected = urlParams.get('strategy') || urlParams.get('type');
    if (preselected) {
        const found = STRATEGY_TYPES.find(s =>
            s.value.toLowerCase() === preselected.toLowerCase() ||
            s.value.replace(/_/g, '').toLowerCase() === preselected.replace(/_/g, '').replace('.md', '').toLowerCase()
        );
        if (found) {
            select.value = found.value;
            renderSpecificFilters(select.value);
            renderStrategyTemplates(select.value);
        }
    }

    loadCustomResults();
    checkCustomExecutionStatus();
    fetchAndRenderMarketStatus();

    document.querySelectorAll('.condition-row .form-select').forEach(sel => {
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        sel.addEventListener('change', () => {
            if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        });
    });

    window.addEventListener('resize', () => {
        document.querySelectorAll('.condition-row .form-select').forEach(sel => {
            if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        });
    });
}


async function checkCustomExecutionStatus() {
    try {
        const progress = document.getElementById('custom-progress');
        if (!progress) return;

        const status = await API.get('/api/status');
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || "";
            progress.className = 'progress-container active';
            startTimer(status.startTimeMs);
            const execAllBtn = document.getElementById('execute-all-custom-btn');
            if (execAllBtn) {
                execAllBtn.disabled = true;
                execAllBtn.innerHTML = '⏳ Executing...';
            }
            document.querySelectorAll('button[onclick*="reexecuteCustomStrategy"]').forEach(b => b.disabled = true);
            startPolling(() => {
                progress.className = 'progress-container';
                stopTimer();
                if (execAllBtn) {
                    execAllBtn.disabled = false;
                    execAllBtn.innerHTML = '▶ Execute All';
                }
                document.querySelectorAll('button[onclick*="reexecuteCustomStrategy"]').forEach(b => b.disabled = false);
                loadCustomResults();
                showToast('Strategy execution completed!');
            });
        } else if (status.alerts && status.alerts.length > 0) {
            showErrorPanel(status.alerts);
        }
    } catch (e) { /* ignore */ }
}

function flattenFilterObject(ob, prefix = '') {
    const res = {};
    for (const [k, v] of Object.entries(ob || {})) {
        if (prefix === '' && k === 'conditions') continue;
        if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
            Object.assign(res, flattenFilterObject(v, prefix + k + '.'));
        } else {
            res[prefix + k] = v;
        }
    }
    return res;
}

function resetScalarFilterInputs() {
    document.querySelectorAll('[data-filter]').forEach(inp => {
        if (inp.type === 'checkbox') {
            inp.checked = false;
        } else {
            inp.value = '';
        }
    });
}

function populateScalarFilterInputs(flatFilters) {
    for (const [k, v] of Object.entries(flatFilters)) {
        const el = document.querySelector(`[data-filter="${k}"]`);
        if (el) {
            if (el.type === 'checkbox') {
                el.checked = !!v;
            } else if (Array.isArray(v)) {
                el.value = v.join(', ');
            } else if (v !== null && v !== undefined) {
                el.value = v;
            }
        }
    }
}

async function renderStrategyTemplates(strategyType) {
    return renderTemplateAccordionList({
        containerId: 'strategy-templates',
        configKey: 'optionsStrategies',
        filterFn: s => s.strategyType === strategyType,
        buildHeaderContentFn: (strategy, enabledPill) => {
            const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadTemplateParams('${escapeAttr(JSON.stringify(strategy))}')">Load Filters</button>`;
            const baseName = strategy.alias || strategy.name || (STRATEGY_TYPES.find(t => t.value === strategy.strategyType)?.label || strategy.strategyType);
            const detailedName = (strategy.termType && !baseName.toLowerCase().includes(strategy.termType.toLowerCase()))
                ? `${baseName} - ${strategy.termType}`
                : baseName;
            return `
                <span class="card-arrow">▶</span>
                <strong>${detailedName}</strong>
                <span class="card-badge">${strategy.securitiesFile || 'Custom'}</span>
                ${enabledPill}
                ${loadBtn}`;
        },
        buildBodyContentFn: (strategy) => `
            ${renderFilterGrid(strategy.filter || {})}
            ${renderTechFiltersGrid(strategy.technicalFilters)}
            ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}`
    });
}

function loadTemplateParams(strategyJson) {
    try {
        const strategy = JSON.parse(decodeAttr(strategyJson));

        const baseName = strategy.alias || strategy.name || (STRATEGY_TYPES.find(t => t.value === strategy.strategyType)?.label || strategy.strategyType || '');
        const detailedName = (strategy.termType && !baseName.toLowerCase().includes(strategy.termType.toLowerCase()))
            ? `${baseName} - ${strategy.termType}`
            : baseName;

        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) aliasEl.value = detailedName ? (detailedName + ' (Custom)') : '';

        const secInput = document.getElementById('securities-input');
        if (secInput) {
            secInput.value = strategy.securities || '';
        }

        const secFileInput = document.getElementById('securities-file-input');
        if (secFileInput) {
            secFileInput.value = strategy.securitiesFile || '';
        }

        resetScalarFilterInputs();

        // Reset and populate trade conditions
        const tradeCondContainer = document.getElementById('trade-conditions');
        if (tradeCondContainer) {
            tradeCondContainer.innerHTML = '';
        }

        const filter = strategy.filter || {};

        if (Array.isArray(filter.conditions)) {
            filter.conditions.forEach(cond => {
                addConditionRow('trade-conditions', TRADE_VARIABLES, cond);
            });
        }

        // Reset and populate leg conditions
        const stratType = strategy.strategyType;
        const typeObj = STRATEGY_TYPES.find(s => s.value === stratType);
        const legs = (typeObj && STRATEGY_LEG_CONFIG[typeObj.group]) || [];
        legs.forEach(leg => {
            const legContainer = document.getElementById(`${leg.prefix}-conditions`);
            if (legContainer) {
                legContainer.innerHTML = '';
                const legObj = filter[leg.prefix];
                if (legObj && Array.isArray(legObj.conditions)) {
                    legObj.conditions.forEach(cond => {
                        addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, cond);
                    });
                }
            }
        });

        populateScalarFilterInputs(flattenFilterObject(filter));
        syncEarningsPresetFromInput();

        fillTechFiltersForm(strategy.technicalFilters);

        showToast('Template load complete. Verify inputs before execution.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading template:', e);
        showToast('Failed to load template', 'error');
    }
}

function loadFiltersFromResult(btn, isReexecute = false) {
    try {
        const filterConfigStr = decodeAttr(btn.dataset.filterConfig);
        const strategyName = decodeAttr(btn.dataset.strategyName || '');
        const filterConfig = JSON.parse(filterConfigStr);

        const typeSelect = document.getElementById('strategy-type');
        let selectedStratType = null;
        if (typeSelect) {
            let matched = false;
            for (const st of STRATEGY_TYPES) {
                if (strategyName.toUpperCase().includes(st.value) ||
                    strategyName.toLowerCase().includes(st.label.toLowerCase())) {
                    typeSelect.value = st.value;
                    selectedStratType = st.value;
                    renderSpecificFilters(st.value);
                    renderStrategyTemplates(st.value);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (filterConfig.strategyType) {
                    typeSelect.value = filterConfig.strategyType;
                    selectedStratType = filterConfig.strategyType;
                    renderSpecificFilters(filterConfig.strategyType);
                    renderStrategyTemplates(filterConfig.strategyType);
                }
            }
        }

        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) {
            if (isReexecute) {
                aliasEl.value = strategyName ? strategyName.replace(/\s*\(Reload\)$/, '') : '';
            } else {
                aliasEl.value = strategyName ? (strategyName.endsWith('(Reload)') ? strategyName : strategyName + ' (Reload)') : '';
            }
        }

        resetScalarFilterInputs();

        // Reset trade conditions
        const tradeCondContainer = document.getElementById('trade-conditions');
        if (tradeCondContainer) {
            tradeCondContainer.innerHTML = '';
        }

        // Populate trade conditions
        if (Array.isArray(filterConfig.conditions)) {
            filterConfig.conditions.forEach(cond => {
                addConditionRow('trade-conditions', TRADE_VARIABLES, cond);
            });
        }

        // Reset and populate leg conditions
        const stratType = selectedStratType || filterConfig.strategyType;
        const typeObj = STRATEGY_TYPES.find(s => s.value === stratType);
        const legs = (typeObj && STRATEGY_LEG_CONFIG[typeObj.group]) || [];
        legs.forEach(leg => {
            const legContainer = document.getElementById(`${leg.prefix}-conditions`);
            if (legContainer) {
                legContainer.innerHTML = '';
                const legObj = filterConfig[leg.prefix];
                if (legObj && Array.isArray(legObj.conditions)) {
                    legObj.conditions.forEach(cond => {
                        addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, cond);
                    });
                }
            }
        });

        const flatFilters = flattenFilterObject(filterConfig);

        if (flatFilters.securitiesFile !== undefined) {
            const secFileEl = document.getElementById('securities-file-input');
            if (secFileEl) secFileEl.value = flatFilters.securitiesFile || '';
        }
        if (flatFilters.securities !== undefined) {
            const secEl = document.getElementById('securities-input');
            if (secEl) {
                secEl.value = Array.isArray(flatFilters.securities) ? flatFilters.securities.join(', ') : flatFilters.securities || '';
            }
        }

        populateScalarFilterInputs(flatFilters);
        syncEarningsPresetFromInput();

        fillTechFiltersForm(filterConfig.technicalFilters);

        if (!isReexecute) {
            showToast('Filters loaded from previous execution. Verify inputs before running.');
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    } catch (e) {
        console.error('Error loading filters from result:', e);
        showToast('Failed to load filters', 'error');
    }
}
function renderSpecificFilters(strategyValue) {
    const container = document.getElementById('specific-filters');
    if (!container) return;

    const type = STRATEGY_TYPES.find(s => s.value === strategyValue);
    if (!type) { container.innerHTML = ''; return; }

    const legs = STRATEGY_LEG_CONFIG[type.group] || [];
    const scalarFields = STRATEGY_SCALAR_FIELDS[type.group] || [];

    if (legs.length === 0 && scalarFields.length === 0) {
        container.innerHTML = '';
        return;
    }

    let html = '<h4 style="font-size:0.8rem; color:var(--text-secondary); margin: 16px 0 8px; grid-column: 1 / -1">' +
        `${type.label} Specific Leg Filters & Options</h4>`;

    // Render scalar fields if any (e.g. marginInterestRate, priceVsMaxDebitRatio)
    for (const f of scalarFields) {
        const infoBtn = renderFilterHelpBtn(f.key, f.label);
        if (f.type === 'text') {
            html += `<div class="form-group">
                <label class="form-label">${f.label} ${infoBtn}</label>
                <input type="text" class="form-input" data-filter="${f.key}"
                       placeholder="${f.placeholder}">
            </div>`;
        } else {
            html += `<div class="form-group">
                <label class="form-label">${f.label} ${infoBtn}</label>
                <input type="number" class="form-input" data-filter="${f.key}"
                       placeholder="${f.placeholder}" step="${f.step || '1'}">
            </div>`;
        }
    }

    // Render leg condition sections
    for (const leg of legs) {
        html += `<div class="condition-section">
            <div class="condition-section-header">
                <label class="condition-section-title">${escapeHtml(leg.title)} Conditions ${renderFilterHelpBtn('legConditions', `${leg.title} Conditions`)}</label>
                <button type="button" class="btn btn-ghost btn-sm condition-add-btn" onclick="addConditionRow('${leg.prefix}-conditions', LEG_VARIABLES)">+ Add Condition</button>
            </div>
            <div id="${leg.prefix}-conditions" class="conditions-list"></div>
        </div>`;
    }

    container.innerHTML = html;
}

async function reexecuteCustomStrategy(btn, strategyId) {
    return reexecuteSingleCustomCard({
        btn,
        resultId: strategyId,
        isProgressActiveFn: () => {
            const progress = document.getElementById('custom-progress');
            return progress && progress.classList.contains('active');
        },
        loadFiltersFn: loadFiltersFromResult,
        executeFn: executeCustom,
        logLabel: 'custom strategy'
    });
}

function getCustomStrategyPayload(customResultId = null) {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const securitiesFileEl = document.getElementById('securities-file-input');
    const aliasEl = document.getElementById('alias-input');

    if (!typeEl || !typeEl.value) { return null; }

    const hasFile = securitiesFileEl && securitiesFileEl.value.trim();
    const hasTickers = securitiesEl && securitiesEl.value.trim();
    if (!hasFile && !hasTickers) {
        return null;
    }

    const filter = {};

    // 1. Collect scalar inputs with data-filter
    document.querySelectorAll('[data-filter]').forEach(input => {
        const key = input.dataset.filter;
        let value = null;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.value.trim()) {
            if (key === 'relaxationPriority' || key === 'sortPriority' || key === 'earningsFilters.conditions') {
                value = input.value.split(',').map(s => s.trim()).filter(Boolean);
            } else if (input.type === 'number') {
                value = parseFloat(input.value);
            } else {
                value = input.value.trim();
            }
        }

        if (value !== null) {
            if (key.includes('.')) {
                const parts = key.split('.');
                let current = filter;
                for (let i = 0; i < parts.length - 1; i++) {
                    if (!current[parts[i]]) current[parts[i]] = {};
                    current = current[parts[i]];
                }
                current[parts[parts.length - 1]] = value;
            } else {
                filter[key] = value;
            }
        }
    });

    // 2. Collect trade-level conditions
    const tradeConditions = getConditionsFromContainer('trade-conditions');
    if (tradeConditions.length > 0) {
        filter.conditions = tradeConditions;
    }

    // 3. Collect leg-level conditions
    const stratType = typeEl.value;
    const typeObj = STRATEGY_TYPES.find(s => s.value === stratType);
    const legs = (typeObj && STRATEGY_LEG_CONFIG[typeObj.group]) || [];
    legs.forEach(leg => {
        const legConditions = getConditionsFromContainer(`${leg.prefix}-conditions`);
        if (legConditions.length > 0) {
            if (!filter[leg.prefix]) filter[leg.prefix] = {};
            filter[leg.prefix].conditions = legConditions;
        }
    });

    let technicalFilters;
    try {
        technicalFilters = getTechnicalFiltersFromDOM();
    } catch (e) {
        return null;
    }

    const body = {
        strategyType: typeEl.value,
        securitiesFile: securitiesFileEl ? securitiesFileEl.value.trim() : '',
        securities: securitiesEl ? securitiesEl.value.trim() : '',
        alias: aliasEl ? aliasEl.value : '',
        maxTradesToSend: 30,
        filter,
        technicalFilters
    };

    if (customResultId) {
        body.customResultId = Number(customResultId);
    }

    return body;
}

async function executeCustom(customResultId = null, isBatch = false) {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const securitiesFileEl = document.getElementById('securities-file-input');

    if (!typeEl || !typeEl.value) { showToast('Select a strategy type', 'error'); return false; }

    const hasFile = securitiesFileEl && securitiesFileEl.value.trim();
    const hasTickers = securitiesEl && securitiesEl.value.trim();
    if (!hasFile && !hasTickers) {
        showToast('Provide a securities file, inline tickers, or both', 'error');
        return false;
    }

    const body = getCustomStrategyPayload(customResultId);
    if (!body) {
        showToast('Failed to build strategy parameters', 'error');
        return false;
    }

    try {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container active';

        const res = await API.post('/api/execute/custom', body);
        showToast(res.message);
        startTimer(Date.now());
        if (isBatch) {
            await pollUntilComplete();
            return true;
        }
        startPolling(() => {
            if (progress) progress.className = 'progress-container';
            stopTimer();
            loadCustomResults(customResultId);
            showToast('Custom execution completed!');
        });
        return true;
    } catch (e) {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container';
        showToast(e.message, 'error');
        if (isBatch) throw e;
        return false;
    }
}

async function loadCustomResults(updatedResultId = null) {
    return loadCustomCardsList({
        containerId: 'custom-results',
        endpoint: '/api/results/custom',
        executeAllBtnId: 'execute-all-custom-btn',
        countBadgeId: 'custom-results-count-badge',
        emptyMessage: 'No custom executions yet',
        errorPrefix: 'Failed to load',
        updatedResultId,
        buildCardFn: r => buildResultCard(r, 'Custom'),
        getIdFn: r => r.strategyId || r.screenerId || '',
        hasItemsFn: r => r.trades && r.trades.length > 0,
        onLoadedFn: container => fetchAndInjectTodayPerformance(container),
        onErrorFn: () => {
            if (typeof checkExecutionStatus === 'function') {
                checkExecutionStatus();
            }
        }
    });
}

function reexecuteAllCustomStrategies() {
    return executeAllCustomCards({
        containerId: 'custom-results',
        executeAllBtnId: 'execute-all-custom-btn',
        cardBtnSelector: 'button[onclick*="reexecuteCustomStrategy"]',
        batchApiEndpoint: '/api/execute/custom/batch',
        buildRequestFn: (card, execBtn) => {
            loadFiltersFromResult(execBtn, true);
            const onclickAttr = execBtn.getAttribute('onclick') || '';
            const match = onclickAttr.match(/reexecuteCustomStrategy\(\s*this\s*,\s*['"]?([^'")]+)['"]?\s*\)/);
            const customResultId = (execBtn.dataset && execBtn.dataset.resultId) || (match ? match[1] : null);
            return getCustomStrategyPayload(customResultId);
        },
        reloadResultsFn: () => loadCustomResults(),
        entityNamePlural: 'custom strategies',
        isProgressActiveFn: () => {
            const p = document.getElementById('custom-progress');
            return p && p.classList.contains('active');
        }
    });
}

if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    const dashboard = require('./dashboard');
    const screenerExecute = require('./screener-execute');
    Object.assign(global, utils, authApi, dashboard, screenerExecute);
}

const CUSTOM_EXECUTE_EXPORTS = {
    STRATEGY_TYPES,
    TRADE_VARIABLES,
    LEG_VARIABLES,
    MA_VARIABLES: typeof MA_VARIABLES !== 'undefined' ? MA_VARIABLES : undefined,
    VOLUME_VARIABLES: typeof VOLUME_VARIABLES !== 'undefined' ? VOLUME_VARIABLES : undefined,
    HV_VARIABLES: typeof HV_VARIABLES !== 'undefined' ? HV_VARIABLES : undefined,
    PRICE_DROP_VARIABLES: typeof PRICE_DROP_VARIABLES !== 'undefined' ? PRICE_DROP_VARIABLES : undefined,
    CONDITION_OPERATORS: typeof CONDITION_OPERATORS !== 'undefined' ? CONDITION_OPERATORS : undefined,
    STRATEGY_LEG_CONFIG,
    STRATEGY_SCALAR_FIELDS,
    parseConditionString: typeof parseConditionString !== 'undefined' ? parseConditionString : undefined,
    createConditionValElement: typeof createConditionValElement !== 'undefined' ? createConditionValElement : undefined,
    addConditionRow: typeof addConditionRow !== 'undefined' ? addConditionRow : undefined,
    getConditionsFromContainer: typeof getConditionsFromContainer !== 'undefined' ? getConditionsFromContainer : undefined,
    initExecutePage,
    checkCustomExecutionStatus,
    renderStrategyTemplates,
    loadTemplateParams,
    loadFiltersFromResult,
    reexecuteCustomStrategy,
    reexecuteAllCustomStrategies,
    fillTechFiltersForm: typeof fillTechFiltersForm !== 'undefined' ? fillTechFiltersForm : undefined,
    renderSpecificFilters,
    getCustomStrategyPayload,
    executeCustom,
    loadCustomResults,
    EARNINGS_PRESETS,
    onEarningsPresetChange,
    syncEarningsPresetFromInput,
    escapeHtml
};

registerModuleExports(CUSTOM_EXECUTE_EXPORTS, typeof module !== 'undefined' ? module : null);


