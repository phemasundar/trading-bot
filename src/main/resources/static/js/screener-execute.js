/**
 * Trading Bot — Technical Screeners Dashboard & Custom Execution
 * Screener dashboard management and custom technical screener execution.
 */

const SCREENER_TYPE_META = {
    RSI_BB_BULLISH_CROSSOVER: { rsi: 'BULLISH_CROSSOVER', bollinger: 'LOWER_BAND', hasDrop: false },
    RSI_BB_BEARISH_CROSSOVER: { rsi: 'BEARISH_CROSSOVER', bollinger: 'UPPER_BAND', hasDrop: false },
    RSI_OVERSOLD:             { rsi: 'OVERSOLD',          bollinger: '',            hasDrop: false },
    BB_LOWER:                 { rsi: '',                  bollinger: 'LOWER_BAND',  hasDrop: false },
    BELOW_200_DAY_MA:         { rsi: '',                  bollinger: '',            hasDrop: false },
    PRICE_DROP:               { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: true  },
    HIGH_52W_DROP:            { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: false },
};

// ── Screeners Dashboard (screeners.html) ──

async function initScreenerDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadScreenerStrategies();
    await loadScreenerResults();
    await checkScreenerExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadScreenerStrategies() {
    const screenerContainer = document.getElementById('screener-checkboxes');
    if (!screenerContainer) return;

    try {
        const screeners = await API.get('/api/screeners');
        if (screeners.length === 0) {
            screenerContainer.innerHTML = `<span class="text-muted">No screeners configured</span>`;
        } else {
            screenerContainer.innerHTML = screeners.map(s => {
                const infoBtn = s.descriptionFile
                    ? `<button type="button" class="info-btn" style="margin-left: 4px;" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(s.name)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
                    : '';
                return `
                <div class="flex items-center" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="screener">
                        <span>${s.name}</span>
                    </label>
                    ${infoBtn}
                </div>`;
            }).join('');
        }
        const badge = document.getElementById('screener-count-badge');
        if (badge) badge.textContent = `(${screeners.length})`;
    } catch (e) {
        screenerContainer.innerHTML = `<span class="text-muted">Failed to load screeners</span>`;
    }
}

async function loadScreenerResults() {
    const screenerContainer = document.getElementById('screener-results-container');
    if (!screenerContainer) return;

    resetDashboardFilter('screeners');

    try {
        const screenerResults = await API.get('/api/results/screeners').catch(() => []);
        screenerContainer.innerHTML = '';
        if (!screenerResults || screenerResults.length === 0) {
            screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet. Execute a screener to see results.</div>';
            hideDashboardFilterBar('screeners');
        } else {
            for (const r of screenerResults) {
                screenerContainer.appendChild(buildScreenerCard(r));
            }
            showDashboardFilterBar('screeners');
        }
    } catch (e) {
        screenerContainer.innerHTML = `<div class="empty-state text-danger">Failed to load screener results: ${e.message}</div>`;
        hideDashboardFilterBar('screeners');
    }
}

async function checkScreenerExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.alerts && status.alerts.length > 0) {
            showErrorPanel(status.alerts);
        }
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || "";
            setDashboardBusy(true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setDashboardBusy(false);
                loadScreenerResults();
                showToast('Execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

async function executeScreenersSelected() {
    const checkedScreeners = document.querySelectorAll('#screener-checkboxes input[type="checkbox"]:checked');
    const screenerIndices = Array.from(checkedScreeners).map(c => parseInt(c.value));
    if (screenerIndices.length === 0) {
        showToast('Select at least one screener', 'error');
        return;
    }
    try {
        setDashboardBusy(true);
        const res = await API.post('/api/execute', { strategyIndices: [], screenerIndices });
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setDashboardBusy(false);
            loadScreenerResults();
            showToast('Screener execution completed!');
        });
    } catch (e) {
        setDashboardBusy(false);
        showToast(e.message, 'error');
    }
}

// ── Custom Screener Execution Page (execute-screener.html) ──

async function initExecuteScreenerPage() {
    const authed = await initAuth();
    if (!authed) return;
    await loadCustomScreenerResults();
    await checkCustomScreenerExecutionStatus();
    
    const select = document.getElementById('screener-type');
    if (select && select.value) {
        onScreenerTypeChange();
    }
}

function onScreenerTypeChange() {
    const type = document.getElementById('screener-type').value;
    const meta = SCREENER_TYPE_META[type];

    const dropGroup = document.getElementById('sc-priceDropRules-group');
    const lookbackGroup = document.getElementById('sc-lookbackDays-group');
    if (dropGroup) dropGroup.style.display = (meta && meta.hasDrop) ? '' : 'none';
    if (lookbackGroup) lookbackGroup.style.display = (meta && meta.hasLookback) ? '' : 'none';

    if (!meta) {
        renderScreenerTemplates('');
        return;
    }

    const rsiSel = document.getElementById('sc-rsiCondition');
    const bbSel  = document.getElementById('sc-bollingerCondition');
    if (rsiSel && meta.rsi !== undefined) rsiSel.value = meta.rsi;
    if (bbSel  && meta.bollinger !== undefined) bbSel.value = meta.bollinger;

    renderScreenerTemplates(type);
}

async function renderScreenerTemplates(screenerType) {
    const container = document.getElementById('screener-templates');
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const screeners = (window.appConfig && window.appConfig.technicalScreeners) || [];
    const matching = screeners.filter(s => s.screenerType === screenerType);

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

    matching.forEach(screener => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = screener.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadScreenerTemplateParams('${escapeAttr(JSON.stringify(screener))}')">Load Filters</button>`;

        const infoBtn = screener.descriptionFile
            ? `<button type="button" class="info-btn" onclick="showInfo(event, '${screener.descriptionFile}', '${escapeAttr(screener.alias || screener.screenerType)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
            : '';

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${screener.alias || screener.screenerType}</strong>
                    ${infoBtn}
                    <span class="card-badge">${screener.screenerType} <button type="button" class="info-btn" style="font-size: 0.7rem; padding: 0; color: inherit" onclick="showFilterHelp(event, 'screenerType', 'Screener Type')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span>
                    <span class="card-badge">${screener.securitiesFile || 'Custom'}</span>
                    ${enabledPill}
                    ${loadBtn}
                </div>
            </div>
            <div class="config-card-body">
                ${renderTechFiltersGrid(screener.technicalFilters)}
                ${screener.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${screener.securities}</span></div>` : ''}
            </div>`;

        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
    });
}

function loadScreenerTemplateParams(screenerJson) {
    try {
        const screener = JSON.parse(decodeAttr(screenerJson));

        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) aliasEl.value = (screener.alias || '') + ' (Custom)';

        const secInput = document.getElementById('screener-securities-input');
        if (secInput) secInput.value = screener.securities || '';

        const secFileInput = document.getElementById('screener-securities-file-input');
        if (secFileInput) secFileInput.value = screener.securitiesFile || '';

        const techFilters = screener.technicalFilters || {};

        const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val !== undefined && val !== null ? val : ''; };
        
        const extractField = (filterKey, fieldType, prop) => {
            if (!techFilters[filterKey]) return undefined;
            if (fieldType === 'root') return techFilters[filterKey];
            if (!techFilters[filterKey][fieldType]) return undefined;
            if (typeof techFilters[filterKey][fieldType] !== 'object') {
                return prop ? undefined : techFilters[filterKey][fieldType];
            }
            return techFilters[filterKey][fieldType][prop];
        };

        setVal('sc-rsiCondition', extractField('RSI', 'condition'));
        setVal('sc-bollingerCondition', extractField('BOLLINGER_BAND', 'condition'));
        const volConditions = extractField('VOLUME', 'conditions');
        if (volConditions && Array.isArray(volConditions) && volConditions.length > 0) {
            const rulesStrs = volConditions.map(vc => {
                if (typeof vc === 'string') return vc;
                if (vc.type === 'MIN_VOLUME') return `>= ${vc.min}`;
                if (vc.type === 'SMA_COMPARISON') return `SMA${vc.volumeShortSmaPeriod || 20} >= SMA${vc.volumeLongSmaPeriod || 50} * ${vc.volumeThresholdPercent || 90}%`;
                return '';
            }).filter(Boolean);
            setVal('sc-volumeRules', rulesStrs.join(', '));
        }
        let pdRules = extractField('PRICE_DROP', 'root');
        if (pdRules && pdRules.conditions) pdRules = pdRules.conditions;
        setVal('sc-priceDropRules', pdRules && Array.isArray(pdRules) ? pdRules.join(', ') : pdRules || '');
        setVal('sc-lookbackDays', extractField('PRICE_DROP', 'config', 'lookbackDays'));

        let maRules = extractField('SIMPLE_MOVING_AVERAGE', 'root');
        if (maRules && maRules.conditions) maRules = maRules.conditions;
        setVal('sc-movingAverageRules', maRules && Array.isArray(maRules) ? maRules.join(', ') : maRules || '');
        setVal('sc-hvPeriod', extractField('HISTORICAL_VOLATILITY', 'config', 'period'));
        let hvRules = extractField('HISTORICAL_VOLATILITY', 'root');
        if (hvRules && hvRules.conditions) hvRules = hvRules.conditions;
        setVal('sc-hvRules', hvRules && Array.isArray(hvRules) ? hvRules.join(', ') : hvRules || '');

        showToast('Template filters loaded!');
    } catch (e) {
        showToast('Error loading template', 'error');
    }
}

function loadScreenerFiltersFromResult(paramsJson, event) {
    if (event) event.stopPropagation();
    try {
        const params = JSON.parse(decodeAttr(paramsJson));

        const typeEl = document.getElementById('screener-type');
        if (typeEl && params.screenerType) {
            typeEl.value = params.screenerType;
            onScreenerTypeChange();
        }

        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) aliasEl.value = params.alias || '';

        const secFileEl = document.getElementById('screener-securities-file-input');
        if (secFileEl) secFileEl.value = params.securitiesFile || '';

        const secEl = document.getElementById('screener-securities-input');
        if (secEl) secEl.value = params.securities || '';

        fillTechFiltersForm(params.technicalFilters);

        const firstCard = document.querySelector('.main-content .card');
        if (firstCard) firstCard.scrollIntoView({ behavior: 'smooth', block: 'start' });

        showToast('Filters loaded from history!');
    } catch (e) {
        console.error('loadScreenerFiltersFromResult error:', e);
        showToast('Error loading filters from result', 'error');
    }
}

async function executeCustomScreener() {
    const type = document.getElementById('screener-type').value;
    if (!type) { showToast('Select a screener type', 'error'); return; }

    const alias            = document.getElementById('screener-alias-input').value.trim() || null;
    const securitiesFile   = document.getElementById('screener-securities-file-input').value.trim() || null;
    const securities       = document.getElementById('screener-securities-input').value.trim() || null;

    if (!securitiesFile && !securities) {
        showToast('Provide a securities file or tickers', 'error');
        return;
    }

    let technicalFilters;
    try {
        const container = document.getElementById('screener-tech-filters-container');
        technicalFilters = getTechnicalFiltersFromDOM(container || document);
    } catch (e) {
        showToast(e.message, 'error');
        return;
    }

    const payload = {
        screenerType: type,
        alias,
        securitiesFile,
        securities,
        technicalFilters
    };

    Object.keys(payload).forEach(k => {
        if (payload[k] === null || payload[k] === false || payload[k] === '') delete payload[k];
    });

    try {
        setCustomScreenerBusy(true);
        const res = await API.post('/api/execute/custom-screener', payload);
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setCustomScreenerBusy(false);
            loadCustomScreenerResults();
            showToast('Screener execution completed!');
        });
    } catch (e) {
        setCustomScreenerBusy(false);
        showToast(e.message, 'error');
    }
}

function getTechnicalFiltersFromDOM(container = document) {
    const technicalFilters = {};
    container.querySelectorAll('[data-tech-filter]').forEach(input => {
        const filterKey = input.dataset.techFilter;
        const fieldKey = input.dataset.techField;
        const rawVal = (input.tagName === 'SELECT' ? input.value : input.value.trim());
        if (!rawVal) return;

        if (!technicalFilters[filterKey]) technicalFilters[filterKey] = {};

        if ((filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'VOLUME' || filterKey === 'HISTORICAL_VOLATILITY' || filterKey === 'PRICE_DROP') && fieldKey === 'rules') {
            technicalFilters[filterKey] = { conditions: rawVal.split(',').map(s => s.trim()).filter(Boolean) };
        } else if (fieldKey === 'condition') {
            if (typeof technicalFilters[filterKey].condition === 'object') {
                technicalFilters[filterKey].condition.type = rawVal;
            } else {
                technicalFilters[filterKey].condition = rawVal;
            }
        } else if (fieldKey === 'period') {
            const num = parseInt(rawVal);
            if (!isNaN(num)) {
                if (!technicalFilters[filterKey].config) technicalFilters[filterKey].config = {};
                technicalFilters[filterKey].config.period = num;
            }
        } else if (['min', 'max', 'lookbackDays'].includes(fieldKey)) {
            const num = parseFloat(rawVal);
            if (!isNaN(num)) {
                if (filterKey === 'VOLUME') {
                    if (!technicalFilters[filterKey].conditions) {
                        technicalFilters[filterKey].conditions = [ { type: 'MIN_VOLUME' } ];
                    }
                    technicalFilters[filterKey].conditions[0][fieldKey] = num;
                } else {
                    if (!technicalFilters[filterKey].condition || typeof technicalFilters[filterKey].condition === 'string') {
                        const existingType = typeof technicalFilters[filterKey].condition === 'string' ? technicalFilters[filterKey].condition : null;
                        technicalFilters[filterKey].condition = {};
                        if (existingType) technicalFilters[filterKey].condition.type = existingType;
                    }
                    technicalFilters[filterKey].condition[fieldKey] = num;
                }
            }
        }
    });

    if (Object.keys(technicalFilters).length === 0) {
        return undefined;
    }

    if (technicalFilters.RSI && technicalFilters.RSI.condition && technicalFilters.RSI.condition.type === 'CUSTOM_RANGE') {
        if (technicalFilters.RSI.condition.min === undefined || technicalFilters.RSI.condition.max === undefined) {
            throw new Error('Min RSI and Max RSI are mandatory for Custom Range condition.');
        }
    }

    return technicalFilters;
}

async function loadCustomScreenerResults() {
    const container = document.getElementById('screener-custom-results');
    if (!container) return;
    try {
        const results = await API.get('/api/results/custom/screeners');
        container.innerHTML = '';
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔬</div>No screener results yet. Run a custom screener above.</div>';
        } else {
            for (const r of results) {
                container.appendChild(buildScreenerCard(r, true));
            }
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
    }
}

async function checkCustomScreenerExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.alerts && status.alerts.length > 0) showErrorPanel(status.alerts);
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || '';
            setCustomScreenerBusy(true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setCustomScreenerBusy(false);
                loadCustomScreenerResults();
                showToast('Screener execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

function setCustomScreenerBusy(busy) {
    const progress = document.getElementById('screener-custom-progress');
    if (progress) progress.style.display = busy ? 'block' : 'none';
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    const dashboard = require('./dashboard');
    Object.assign(global, utils, authApi, dashboard);

    module.exports = {
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
        setCustomScreenerBusy
    };
}
