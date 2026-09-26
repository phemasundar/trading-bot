/**
 * Trading Bot — Custom Options Execution
 * Options custom execution form logic, leg filters, strategy templates, and custom results loading.
 */

const STRATEGY_TYPES = [
    { value: 'PUT_CREDIT_SPREAD', label: 'Put Credit Spread', group: 'credit_spread' },
    { value: 'TECH_PUT_CREDIT_SPREAD', label: 'Technical Put Credit Spread', group: 'credit_spread' },
    { value: 'BULLISH_LONG_PUT_CREDIT_SPREAD', label: 'Bullish Long Put Credit Spread', group: 'credit_spread' },
    { value: 'CALL_CREDIT_SPREAD', label: 'Call Credit Spread', group: 'credit_spread' },
    { value: 'TECH_CALL_CREDIT_SPREAD', label: 'Technical Call Credit Spread', group: 'credit_spread' },
    { value: 'IRON_CONDOR', label: 'Iron Condor', group: 'iron_condor' },
    { value: 'BULLISH_LONG_IRON_CONDOR', label: 'Bullish Long Iron Condor', group: 'iron_condor' },
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
    { value: 'OPTION_PRICE_PERCENT', label: 'OPTION_PRICE_PERCENT (%)' },
    { value: 'BREAKEVEN_CAGR', label: 'BREAKEVEN_CAGR (%)' },
    { value: 'COST_SAVINGS_PERCENT', label: 'COST_SAVINGS_PERCENT (%)' },
    { value: 'ANNUALIZED_EXTRINSIC_PCT', label: 'ANNUALIZED_EXTRINSIC_PCT (%)' },
    { value: 'MAX_LOSS_UPSIDE', label: 'MAX_LOSS_UPSIDE ($)' },
    { value: 'MAX_LOSS_DOWNSIDE', label: 'MAX_LOSS_DOWNSIDE ($)' },
];

const LEG_VARIABLES = [
    { value: 'DELTA', label: 'DELTA' },
    { value: 'OPEN_INTEREST', label: 'OPEN_INTEREST' },
    { value: 'VOLUME', label: 'VOLUME' },
    { value: 'IV', label: 'IV' },
    { value: 'MARK', label: 'MARK (Premium)' },
    { value: 'BID', label: 'BID' },
    { value: 'ASK', label: 'ASK' },
    { value: 'STRIKE', label: 'STRIKE' },
];

const CONDITION_OPERATORS = ['>=', '<=', '>', '<'];

function parseConditionString(str) {
    if (!str || typeof str !== 'string') return null;
    str = str.trim();
    const match = str.match(/^([A-Za-z0-9_.]+)\s*(>=|<=|>|<)\s*(.+)$/);
    if (!match) return null;
    return {
        variable: match[1].trim(),
        operator: match[2].trim(),
        value: match[3].trim()
    };
}

function addConditionRow(containerId, variables, prefill = null) {
    const container = document.getElementById(containerId);
    if (!container) return null;

    const row = document.createElement('div');
    row.className = 'condition-row';

    const parsed = typeof prefill === 'string' ? parseConditionString(prefill) : prefill;

    // Variable Select
    const varSelect = document.createElement('select');
    varSelect.className = 'form-select condition-var-select';

    const prefillVar = parsed ? parsed.variable.toUpperCase() : '';
    const hasVar = variables.some(v => v.value.toUpperCase() === prefillVar);
    if (prefillVar && !hasVar) {
        const customOpt = document.createElement('option');
        customOpt.value = parsed.variable;
        customOpt.textContent = parsed.variable;
        varSelect.appendChild(customOpt);
    }

    variables.forEach(v => {
        const opt = document.createElement('option');
        opt.value = v.value;
        opt.textContent = v.label;
        if (typeof FILTER_DESCRIPTIONS !== 'undefined' && FILTER_DESCRIPTIONS[v.value]) {
            opt.title = FILTER_DESCRIPTIONS[v.value];
        }
        if (prefillVar === v.value.toUpperCase()) {
            opt.selected = true;
        }
        varSelect.appendChild(opt);
    });
    if (parsed && hasVar) {
        varSelect.value = variables.find(v => v.value.toUpperCase() === prefillVar).value;
    }

    // Info Button for Selected Variable
    const infoBtn = document.createElement('button');
    infoBtn.type = 'button';
    infoBtn.className = 'info-btn condition-info-btn';
    infoBtn.innerHTML = `<svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>`;

    const updateInfoBtn = () => {
        const selectedVal = varSelect.value;
        const vObj = variables.find(v => v.value === selectedVal);
        const label = vObj ? vObj.label : selectedVal;
        infoBtn.dataset.key = selectedVal;
        infoBtn.dataset.label = label;
        if (typeof FILTER_DESCRIPTIONS !== 'undefined' && FILTER_DESCRIPTIONS[selectedVal]) {
            infoBtn.title = FILTER_DESCRIPTIONS[selectedVal];
        } else {
            infoBtn.title = `${label} details`;
        }
    };

    infoBtn.onclick = (e) => {
        const key = infoBtn.dataset.key || varSelect.value;
        const label = infoBtn.dataset.label || key;
        showFilterHelp(e, key, label);
    };

    const onVarChange = () => {
        updateInfoBtn();
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(varSelect);
    };
    varSelect.addEventListener('change', onVarChange);
    updateInfoBtn();
    if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(varSelect);

    // Operator Select
    const opSelect = document.createElement('select');
    opSelect.className = 'form-select condition-op-select';
    CONDITION_OPERATORS.forEach(op => {
        const opt = document.createElement('option');
        opt.value = op;
        opt.textContent = op;
        if (parsed && parsed.operator === op) {
            opt.selected = true;
        }
        opSelect.appendChild(opt);
    });
    if (parsed && parsed.operator) {
        opSelect.value = parsed.operator;
    }
    opSelect.addEventListener('change', () => {
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(opSelect);
    });
    if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(opSelect);

    // Value Input
    const valInput = document.createElement('input');
    valInput.type = 'text';
    valInput.className = 'form-input condition-val-input';
    valInput.placeholder = 'e.g. 25';
    if (parsed && parsed.value !== undefined) {
        valInput.value = parsed.value;
    }

    // Remove Button
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-ghost condition-remove-btn';
    removeBtn.innerHTML = '&times;';
    removeBtn.title = 'Remove condition';
    removeBtn.onclick = () => row.remove();

    row.appendChild(varSelect);
    row.appendChild(infoBtn);
    row.appendChild(opSelect);
    row.appendChild(valInput);
    row.appendChild(removeBtn);

    container.appendChild(row);
    return row;
}

function getConditionsFromContainer(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return [];
    const rows = container.querySelectorAll('.condition-row');
    const conditions = [];
    rows.forEach(row => {
        const varSel = row.querySelector('.condition-var-select');
        const opSel = row.querySelector('.condition-op-select');
        const valInp = row.querySelector('.condition-val-input');
        if (varSel && opSel && valInp) {
            const v = varSel.value.trim();
            const op = opSel.value.trim();
            const val = valInp.value.trim();
            if (v && op && val) {
                conditions.push(`${v} ${op} ${val}`);
            }
        }
    });
    return conditions;
}

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

function getLegFilters(prefix, title) {
    return [
        { key: `${prefix}.minDelta`, label: `${title} Min Delta`, placeholder: '0.10', step: '0.01' },
        { key: `${prefix}.maxDelta`, label: `${title} Max Delta`, placeholder: '0.30', step: '0.01' },
        { key: `${prefix}.minOpenInterest`, label: `${title} Min OI`, placeholder: '100' },
        { key: `${prefix}.minVolume`, label: `${title} Min Volume`, placeholder: '10' },
        { key: `${prefix}.minPremium`, label: `${title} Min Premium`, placeholder: '0.50', step: '0.10' },
        { key: `${prefix}.maxPremium`, label: `${title} Max Premium`, placeholder: '5.00', step: '0.10' },
        { key: `${prefix}.minVolatility`, label: `${title} Min Volatility`, placeholder: '10.0', step: '0.1' },
        { key: `${prefix}.maxVolatility`, label: `${title} Max Volatility`, placeholder: '50.0', step: '0.1' },
    ];
}

const STRATEGY_SPECIFIC_FILTERS = {
    credit_spread: [
        ...getLegFilters('shortLeg', 'Short Leg'),
        ...getLegFilters('longLeg', 'Long Leg')
    ],
    iron_condor: [
        ...getLegFilters('putShortLeg', 'Put Short'),
        ...getLegFilters('putLongLeg', 'Put Long'),
        ...getLegFilters('callShortLeg', 'Call Short'),
        ...getLegFilters('callLongLeg', 'Call Long'),
        { key: 'minCombinedCredit', label: 'Min Combined Credit', placeholder: '100' },
    ],
    leap: [
        ...getLegFilters('longCall', 'Long Call'),
        { key: 'minCostSavingsPercent', label: 'Min Cost Savings %', placeholder: '10.0', step: '0.1' },
        { key: 'maxCAGRForBreakEven', label: 'Max Breakeven CAGR', placeholder: '15.0', step: '0.1' },
        { key: 'maxOptionPricePercent', label: 'Max Option Price %', placeholder: '30.0', step: '0.1' },
        { key: 'marginInterestRate', label: 'Margin Interest Rate', placeholder: '6.0', step: '0.1' },
        { key: 'savingsInterestRate', label: 'Savings Interest Rate', placeholder: '10.0', step: '0.1' },
        { key: 'relaxationPriority', label: 'Relaxation Priority (comma separated)', placeholder: 'maxCAGRForBreakEven,maxOptionPricePercent', type: 'text' },
        { key: 'sortPriority', label: 'Sort Priority (comma separated)', placeholder: 'daysToExpiration,costSavingsPercent', type: 'text' },
    ],
    bwb: [
        ...getLegFilters('leg1Long', 'Lower Strike Long Leg'),
        ...getLegFilters('leg2Short', 'Middle Short Legs'),
        ...getLegFilters('leg3Long', 'Upper Strike Long Leg'),
        { key: 'priceVsMaxDebitRatio', label: 'Price/Debit Ratio', placeholder: '2.0', step: '0.1' },
    ],
    zebra: [
        ...getLegFilters('shortCall', 'Short Call'),
        ...getLegFilters('longCall', 'Long Call'),
    ],
    short_put: [
        ...getLegFilters('shortLeg', 'Short Put Leg'),
    ],
    short_strangle: [
        ...getLegFilters('putShortLeg', 'Short Put Leg'),
        ...getLegFilters('callShortLeg', 'Short Call Leg'),
    ],
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

    loadCustomResults();
    checkCustomExecutionStatus();
    fetchAndRenderMarketStatus();

    document.querySelectorAll('.form-select').forEach(sel => {
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        sel.addEventListener('change', () => {
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
            startPolling(() => {
                progress.className = 'progress-container';
                stopTimer();
                loadCustomResults();
                showToast('Custom execution completed!');
            });
        } else if (status.alerts && status.alerts.length > 0) {
            showErrorPanel(status.alerts);
        }
    } catch (e) { /* ignore */ }
}

async function renderStrategyTemplates(strategyType) {
    const container = document.getElementById('strategy-templates');
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const strategies = (window.appConfig && window.appConfig.optionsStrategies) || [];
    const matching = strategies.filter(s => s.strategyType === strategyType);

    if (matching.length === 0) {
        container.innerHTML = '';
        return;
    }

    const heading = document.createElement('h4');
    heading.style.fontSize = '0.8rem';
    heading.style.color = 'var(--text-secondary)';
    heading.style.marginBottom = '8px';
    heading.textContent = 'Configured Templates (Click to view, Load to edit)';

    container.innerHTML = '';
    container.appendChild(heading);

    matching.forEach(strategy => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = strategy.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadTemplateParams('${escapeAttr(JSON.stringify(strategy))}')">Load Filters</button>`;

        const baseName = strategy.alias || strategy.name || (STRATEGY_TYPES.find(t => t.value === strategy.strategyType)?.label || strategy.strategyType);
        const detailedName = (strategy.termType && !baseName.toLowerCase().includes(strategy.termType.toLowerCase()))
            ? `${baseName} - ${strategy.termType}`
            : baseName;

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${detailedName}</strong>
                    <span class="card-badge">${strategy.securitiesFile || 'Custom'}</span>
                    ${enabledPill}
                    ${loadBtn}
                </div>
            </div>
            <div class="config-card-body">
                ${renderFilterGrid(strategy.filter || {})}
                ${renderTechFiltersGrid(strategy.technicalFilters)}
                ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
            </div>`;

        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
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

        // Reset scalar filter inputs
        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

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

        // Flatten remaining scalar fields (e.g. targetDTE, topTradesCount, marginInterestRate, etc.)
        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
                if (prefix === '' && k === 'conditions') continue;
                if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
                    Object.assign(res, flattenObj(v, prefix + k + '.'));
                } else {
                    res[prefix + k] = v;
                }
            }
            return res;
        };

        const flatFilters = flattenObj(filter);

        for (const [k, v] of Object.entries(flatFilters)) {
            const el = document.querySelector(`[data-filter="${k}"]`);
            if (el) {
                if (el.type === 'checkbox') {
                    el.checked = !!v;
                } else if (Array.isArray(v)) {
                    el.value = v.join(', ');
                } else {
                    el.value = v;
                }
            }
        }

        if (flatFilters['earningsFilters.conditions'] === undefined && flatFilters.ignoreEarnings !== undefined) {
            const condEl = document.getElementById('earnings-conditions-input');
            if (condEl) {
                condEl.value = flatFilters.ignoreEarnings === false ? 'DAYS_TO_NEXT_EARNINGS >= DTE' : '';
            }
        }
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

        // Reset scalar filter inputs
        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

        // Reset trade conditions
        const tradeCondContainer = document.getElementById('trade-conditions');
        if (tradeCondContainer) {
            tradeCondContainer.innerHTML = '';
        }

        // Populate trade conditions
        if (Array.isArray(filterConfig.conditions) && filterConfig.conditions.length > 0) {
            filterConfig.conditions.forEach(cond => {
                addConditionRow('trade-conditions', TRADE_VARIABLES, cond);
            });
        } else {
            // Graceful fallback for legacy records that stored named properties
            if (filterConfig.minDTE && filterConfig.minDTE > 0) addConditionRow('trade-conditions', TRADE_VARIABLES, `DTE >= ${filterConfig.minDTE}`);
            if (filterConfig.maxDTE && filterConfig.maxDTE < 2147483647 && filterConfig.maxDTE > 0) addConditionRow('trade-conditions', TRADE_VARIABLES, `DTE <= ${filterConfig.maxDTE}`);
            if (filterConfig.maxLossLimit) addConditionRow('trade-conditions', TRADE_VARIABLES, `MAX_LOSS <= ${filterConfig.maxLossLimit}`);
            if (filterConfig.minReturnOnRisk && filterConfig.minReturnOnRisk > 0) addConditionRow('trade-conditions', TRADE_VARIABLES, `RETURN_ON_RISK >= ${filterConfig.minReturnOnRisk}`);
            if (filterConfig.minReturnOnRiskCAGR && filterConfig.minReturnOnRiskCAGR > 0) addConditionRow('trade-conditions', TRADE_VARIABLES, `CAGR >= ${filterConfig.minReturnOnRiskCAGR}`);
            if (filterConfig.minIVRank) addConditionRow('trade-conditions', TRADE_VARIABLES, `IV_RANK >= ${filterConfig.minIVRank}`);
            if (filterConfig.maxIVRank) addConditionRow('trade-conditions', TRADE_VARIABLES, `IV_RANK <= ${filterConfig.maxIVRank}`);
            if (filterConfig.minIVPercentile) addConditionRow('trade-conditions', TRADE_VARIABLES, `IV_PERCENTILE >= ${filterConfig.minIVPercentile}`);
            if (filterConfig.maxIVPercentile) addConditionRow('trade-conditions', TRADE_VARIABLES, `IV_PERCENTILE <= ${filterConfig.maxIVPercentile}`);
            if (filterConfig.maxBreakEvenPercentage) addConditionRow('trade-conditions', TRADE_VARIABLES, `BREAK_EVEN_PCT <= ${filterConfig.maxBreakEvenPercentage}`);
            if (filterConfig.maxUpperBreakevenDelta) addConditionRow('trade-conditions', TRADE_VARIABLES, `UPPER_BREAK_EVEN_DELTA <= ${filterConfig.maxUpperBreakevenDelta}`);
            if (filterConfig.maxTotalDebit) addConditionRow('trade-conditions', TRADE_VARIABLES, `TOTAL_DEBIT <= ${filterConfig.maxTotalDebit}`);
            if (filterConfig.maxTotalCredit) addConditionRow('trade-conditions', TRADE_VARIABLES, `NET_CREDIT <= ${filterConfig.maxTotalCredit}`);
            if (filterConfig.minTotalCredit) addConditionRow('trade-conditions', TRADE_VARIABLES, `NET_CREDIT >= ${filterConfig.minTotalCredit}`);
            if (filterConfig.maxNetExtrinsicValueToPricePercentage) addConditionRow('trade-conditions', TRADE_VARIABLES, `ANNUALIZED_EXTRINSIC_PCT <= ${filterConfig.maxNetExtrinsicValueToPricePercentage}`);
            if (filterConfig.minNetExtrinsicValueToPricePercentage) addConditionRow('trade-conditions', TRADE_VARIABLES, `ANNUALIZED_EXTRINSIC_PCT >= ${filterConfig.minNetExtrinsicValueToPricePercentage}`);
            if (filterConfig.maxCAGRForBreakEven) addConditionRow('trade-conditions', TRADE_VARIABLES, `BREAKEVEN_CAGR <= ${filterConfig.maxCAGRForBreakEven}`);
            if (filterConfig.maxOptionPricePercent) addConditionRow('trade-conditions', TRADE_VARIABLES, `OPTION_PRICE_PERCENT <= ${filterConfig.maxOptionPricePercent}`);
            if (filterConfig.minCostSavingsPercent) addConditionRow('trade-conditions', TRADE_VARIABLES, `COST_SAVINGS_PERCENT >= ${filterConfig.minCostSavingsPercent}`);
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
                if (legObj) {
                    if (Array.isArray(legObj.conditions) && legObj.conditions.length > 0) {
                        legObj.conditions.forEach(cond => {
                            addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, cond);
                        });
                    } else {
                        // Legacy leg properties fallback
                        if (legObj.minDelta !== undefined && legObj.minDelta !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `DELTA >= ${legObj.minDelta}`);
                        if (legObj.maxDelta !== undefined && legObj.maxDelta !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `DELTA <= ${legObj.maxDelta}`);
                        if (legObj.minOpenInterest !== undefined && legObj.minOpenInterest !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `OPEN_INTEREST >= ${legObj.minOpenInterest}`);
                        if (legObj.minVolume !== undefined && legObj.minVolume !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `VOLUME >= ${legObj.minVolume}`);
                        if (legObj.minPremium !== undefined && legObj.minPremium !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `MARK >= ${legObj.minPremium}`);
                        if (legObj.maxPremium !== undefined && legObj.maxPremium !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `MARK <= ${legObj.maxPremium}`);
                        if (legObj.minVolatility !== undefined && legObj.minVolatility !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `IV >= ${legObj.minVolatility}`);
                        if (legObj.maxVolatility !== undefined && legObj.maxVolatility !== null) addConditionRow(`${leg.prefix}-conditions`, LEG_VARIABLES, `IV <= ${legObj.maxVolatility}`);
                    }
                }
            }
        });

        // Flatten scalar properties
        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
                if (prefix === '' && k === 'conditions') continue;
                if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
                    Object.assign(res, flattenObj(v, prefix + k + '.'));
                } else {
                    res[prefix + k] = v;
                }
            }
            return res;
        };

        const flatFilters = flattenObj(filterConfig);

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

        if (flatFilters['earningsFilters.conditions'] === undefined && flatFilters.ignoreEarnings !== undefined) {
            const condEl = document.getElementById('earnings-conditions-input');
            if (condEl) {
                condEl.value = flatFilters.ignoreEarnings === false ? 'DAYS_TO_NEXT_EARNINGS >= DTE' : '';
            }
        }
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

function fillTechFiltersForm(techFilters) {
    document.querySelectorAll('[data-tech-filter]').forEach(inp => {
        inp.value = '';
    });

    if (!techFilters) return;
    if (typeof techFilters === 'string' && typeof window !== 'undefined' && window.appConfig && window.appConfig.technicalFilters) {
        techFilters = window.appConfig.technicalFilters[techFilters] || {};
    }
    if (typeof techFilters !== 'object') return;

    for (const [filterKey, val] of Object.entries(techFilters)) {
        if (filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'VOLUME' || filterKey === 'HISTORICAL_VOLATILITY') {
            if (val.conditions || Array.isArray(val)) {
                const rules = Array.isArray(val) ? val.join(', ') : (val.conditions || val);
                const rulesStr = Array.isArray(rules) ? rules.join(', ') : rules;
                const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="rules"]`);
                if (el) el.value = rulesStr;
            }
            if (filterKey === 'SIMPLE_MOVING_AVERAGE') continue;
        }

        if (val && typeof val === 'object') {
            for (const [fieldKey, fieldVal] of Object.entries(val)) {
                if (fieldKey === 'condition') {
                    if (typeof fieldVal === 'string') {
                        const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="condition"]`);
                        if (el) el.value = fieldVal;
                    } else if (typeof fieldVal === 'object') {
                        for (const [condKey, condVal] of Object.entries(fieldVal)) {
                            const mappedKey = condKey === 'type' ? 'condition' : condKey;
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${mappedKey}"]`);
                            if (el) {
                                el.value = condVal;
                                if (mappedKey === 'condition' && typeof el.onchange === 'function') {
                                    el.onchange();
                                }
                            }
                        }
                    }
                } else if (fieldKey === 'config') {
                    if (typeof fieldVal === 'object') {
                        for (const [cfgKey, cfgVal] of Object.entries(fieldVal)) {
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${cfgKey}"]`);
                            if (el) el.value = cfgVal;
                        }
                    }
                }
            }
        }
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
        const infoBtn = `<button type="button" class="info-btn" onclick="showFilterHelp(event, '${f.key}', '${escapeAttr(f.label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
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
        html += `<div class="form-group" style="grid-column: 1 / -1; margin-top: 8px;">
            <div class="flex items-center justify-between" style="margin-bottom: 8px;">
                <label class="form-label" style="margin: 0;">${leg.title} Conditions <button type="button" class="info-btn" onclick="showFilterHelp(event, 'legConditions', '${escapeAttr(leg.title)} Conditions')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></label>
                <button type="button" class="btn btn-ghost" style="padding: 2px 10px; font-size: 0.8rem;" onclick="addConditionRow('${leg.prefix}-conditions', LEG_VARIABLES)">+ Add Condition</button>
            </div>
            <div id="${leg.prefix}-conditions" class="conditions-list"></div>
        </div>`;
    }

    container.innerHTML = html;
}

async function reexecuteCustomStrategy(btn, strategyId) {
    if (!strategyId) return;

    const progress = document.getElementById('custom-progress');
    if (progress && progress.classList.contains('active')) {
        showToast('An execution is already in progress', 'error');
        return;
    }

    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '⏳ Executing...';

    try {
        loadFiltersFromResult(btn, true);
        const success = await executeCustom(strategyId);
        if (success === false) {
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    } catch (e) {
        console.error('Error re-executing custom strategy:', e);
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

async function executeCustom(customResultId = null) {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const securitiesFileEl = document.getElementById('securities-file-input');
    const aliasEl = document.getElementById('alias-input');

    if (!typeEl || !typeEl.value) { showToast('Select a strategy type', 'error'); return false; }

    const hasFile = securitiesFileEl && securitiesFileEl.value.trim();
    const hasTickers = securitiesEl && securitiesEl.value.trim();
    if (!hasFile && !hasTickers) {
        showToast('Provide a securities file, inline tickers, or both', 'error');
        return false;
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
        showToast(e.message, 'error');
        return;
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

    try {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container active';

        const res = await API.post('/api/execute/custom', body);
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            if (progress) progress.className = 'progress-container';
            stopTimer();
            loadCustomResults(customResultId);
            showToast('Custom execution completed!');
        });
    } catch (e) {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container';
        showToast(e.message, 'error');
        throw e;
    }
}

async function loadCustomResults(updatedResultId = null) {
    const container = document.getElementById('custom-results');
    if (!container) return;
    try {
        const openCardIds = new Set();
        container.querySelectorAll('.card-content.open').forEach(el => {
            const id = el.id.replace(/^content-/, '');
            openCardIds.add(id);
        });

        const results = await API.get('/api/results/custom');
        container.innerHTML = '';
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔬</div>No custom executions yet</div>';
            return;
        }
        for (const r of results) {
            const cardEl = buildResultCard(r, 'Custom');
            container.appendChild(cardEl);
            const cardId = String(r.strategyId || r.screenerId || '').replace(/\s+/g, '-');
            const shouldOpen = openCardIds.has(cardId) || (updatedResultId && String(r.strategyId) === String(updatedResultId));
            if (shouldOpen && r.trades && r.trades.length > 0) {
                const content = cardEl.querySelector(`[id="content-${cardId}"]`);
                const arrow = cardEl.querySelector(`[id="arrow-${cardId}"]`);
                if (content) content.classList.add('open');
                if (arrow) arrow.classList.add('open');
            }
        }
        fetchAndInjectTodayPerformance(container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load: ${e.message}</div>`;
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

if (typeof window !== 'undefined') {
    window.loadFiltersFromResult = loadFiltersFromResult;
    window.reexecuteCustomStrategy = reexecuteCustomStrategy;
    window.executeCustom = executeCustom;
    window.loadCustomResults = loadCustomResults;
    window.addConditionRow = addConditionRow;
    window.getConditionsFromContainer = getConditionsFromContainer;
    window.parseConditionString = parseConditionString;
    window.TRADE_VARIABLES = TRADE_VARIABLES;
    window.LEG_VARIABLES = LEG_VARIABLES;
    window.CONDITION_OPERATORS = CONDITION_OPERATORS;
    window.STRATEGY_LEG_CONFIG = STRATEGY_LEG_CONFIG;
    window.STRATEGY_SCALAR_FIELDS = STRATEGY_SCALAR_FIELDS;
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    const dashboard = require('./dashboard');
    const screenerExecute = require('./screener-execute');
    Object.assign(global, utils, authApi, dashboard, screenerExecute);

    module.exports = {
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
        reexecuteCustomStrategy,
        fillTechFiltersForm,
        renderSpecificFilters,
        executeCustom,
        loadCustomResults,
        EARNINGS_PRESETS,
        onEarningsPresetChange,
        syncEarningsPresetFromInput
    };
}

