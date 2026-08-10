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
        { key: 'minCostEfficiencyPercent', label: 'Min Cost Efficiency %', placeholder: '90.0', step: '0.1' },
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

    loadFilterDescriptions();

    select.addEventListener('change', () => {
        renderSpecificFilters(select.value);
        renderStrategyTemplates(select.value);
    });

    loadCustomResults();
    checkCustomExecutionStatus();
    fetchAndRenderMarketStatus();
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

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${strategy.alias || strategy.strategyType}</strong>
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

        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) aliasEl.value = (strategy.alias || '') + ' (Custom)';

        const secInput = document.getElementById('securities-input');
        if (secInput) {
            secInput.value = strategy.securities || '';
        }

        const secFileInput = document.getElementById('securities-file-input');
        if (secFileInput) {
            secFileInput.value = strategy.securitiesFile || '';
        }

        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

        const filter = strategy.filter || {};

        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
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

        fillTechFiltersForm(strategy.technicalFilters);

        showToast('Template load complete. Verify inputs before execution.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading template:', e);
        showToast('Failed to load template', 'error');
    }
}

function loadFiltersFromResult(btn) {
    try {
        const filterConfigStr = decodeAttr(btn.dataset.filterConfig);
        const strategyName = decodeAttr(btn.dataset.strategyName || '');
        const filterConfig = JSON.parse(filterConfigStr);

        const typeSelect = document.getElementById('strategy-type');
        if (typeSelect) {
            let matched = false;
            for (const st of STRATEGY_TYPES) {
                if (strategyName.toUpperCase().includes(st.value) ||
                    strategyName.toLowerCase().includes(st.label.toLowerCase())) {
                    typeSelect.value = st.value;
                    renderSpecificFilters(st.value);
                    renderStrategyTemplates(st.value);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (filterConfig.strategyType) {
                    typeSelect.value = filterConfig.strategyType;
                    renderSpecificFilters(filterConfig.strategyType);
                    renderStrategyTemplates(filterConfig.strategyType);
                }
            }
        }

        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) aliasEl.value = strategyName ? strategyName + ' (Reload)' : '';

        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
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
            if (k === 'maxDTE' && v === 2147483647) continue;
            if ((k === 'targetDTE' || k === 'minDTE' || k === 'minReturnOnRisk' || k === 'minReturnOnRiskCAGR') && v === 0) continue;

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

        fillTechFiltersForm(filterConfig.technicalFilters);

        showToast('Filters loaded from previous execution. Verify inputs before running.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading filters from result:', e);
        showToast('Failed to load filters', 'error');
    }
}

function fillTechFiltersForm(techFilters) {
    document.querySelectorAll('[data-tech-filter]').forEach(inp => {
        inp.value = '';
    });

    if (!techFilters || typeof techFilters !== 'object') return;

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

    const filters = STRATEGY_SPECIFIC_FILTERS[type.group] || [];
    if (filters.length === 0) { container.innerHTML = ''; return; }

    let html = '<h4 style="font-size:0.8rem; color:var(--text-secondary); margin: 16px 0 8px; grid-column: 1 / -1">' +
        `${type.label} Specific Leg Filters & Options</h4>`;
    for (const f of filters) {
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
    container.innerHTML = html;
}

async function executeCustom() {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const securitiesFileEl = document.getElementById('securities-file-input');
    const aliasEl = document.getElementById('alias-input');

    if (!typeEl.value) { showToast('Select a strategy type', 'error'); return; }

    const hasFile = securitiesFileEl && securitiesFileEl.value.trim();
    const hasTickers = securitiesEl && securitiesEl.value.trim();
    if (!hasFile && !hasTickers) {
        showToast('Provide a securities file, inline tickers, or both', 'error');
        return;
    }

    const filter = {};
    document.querySelectorAll('[data-filter]').forEach(input => {
        const key = input.dataset.filter;
        let value = null;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.value.trim()) {
            if (key === 'relaxationPriority' || key === 'sortPriority') {
                value = input.value.split(',').map(s => s.trim()).filter(s => s);
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

    try {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container active';

        const res = await API.post('/api/execute/custom', body);
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            if (progress) progress.className = 'progress-container';
            stopTimer();
            loadCustomResults();
            showToast('Custom execution completed!');
        });
    } catch (e) {
        const progress = document.getElementById('custom-progress');
        if (progress) progress.className = 'progress-container';
        showToast(e.message, 'error');
    }
}

async function loadCustomResults() {
    const container = document.getElementById('custom-results');
    if (!container) return;
    try {
        const results = await API.get('/api/results/custom');
        container.innerHTML = '';
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔬</div>No custom executions yet</div>';
            return;
        }
        for (const r of results) {
            container.appendChild(buildResultCard(r, 'Custom'));
        }
        fetchAndInjectTodayPerformance(container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load: ${e.message}</div>`;
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
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
        loadCustomResults
    };
}
