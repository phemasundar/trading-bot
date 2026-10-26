/**
 * Trading Bot — Configuration Viewer
 * Logic for rendering strategy-config.json and securities file lists on config.html.
 */

async function initConfigPage() {
    const authed = await initAuth();
    if (!authed) return;
    const container = document.getElementById('config-container');
    if (!container) return;
    try {
        await loadFilterDescriptions();
        const [config, securitiesMaps] = await Promise.all([
            API.get('/api/config'),
            API.get('/api/securities').catch(() => ({}))
        ]);
        container.innerHTML = '';
        renderConfig(config, container, securitiesMaps);
        fetchAndRenderMarketStatus();
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load config: ${e.message}</div>`;
    }
}

function appendConfigSectionHeading(container, text) {
    const heading = document.createElement('h3');
    heading.textContent = text;
    heading.className = 'section-heading';
    container.appendChild(heading);
}

function appendAccordionConfigCard(container, html) {
    const card = document.createElement('div');
    card.className = 'config-card';
    card.innerHTML = html;
    card.querySelector('.config-card-header').addEventListener('click', function () {
        this.querySelector('.card-arrow').classList.toggle('open');
        this.nextElementSibling.classList.toggle('open');
    });
    container.appendChild(card);
}

function renderEnabledPill(enabled) {
    return enabled
        ? '<span class="pill pill-enabled">Enabled</span>'
        : '<span class="pill pill-disabled">Disabled</span>';
}

function formatCamelLabel(key) {
    return key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
}

function renderConfig(config, container, securitiesMaps = {}) {
    if (config.optionsStrategies) {
        appendConfigSectionHeading(container, 'Options Strategies');

        config.optionsStrategies.forEach((strategy) => {
            const enabledPill = renderEnabledPill(strategy.enabled);
            const infoBtn = renderDocInfoBtn(strategy.descriptionFile, strategy.alias || strategy.strategyType);

            appendAccordionConfigCard(container, `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm flex-wrap">
                        <span class="card-arrow">▶</span>
                        <strong>${strategy.alias || strategy.strategyType}</strong>
                        ${infoBtn}
                        <span class="card-badge">${strategy.strategyType} ${renderFilterHelpBtn('strategyType', 'Strategy Type', 'font-size: 0.7rem; padding: 0; color: inherit')}</span>
                        ${enabledPill}
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderFilterGrid(strategy.filter || {})}
                    ${renderTechFiltersGrid(strategy.technicalFilters)}
                    ${strategy.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File ${renderFilterHelpBtn('securitiesFile', 'Securities File', 'font-size: 0.8rem; padding: 0')}</span> <span class="config-item-value">${strategy.securitiesFile}</span></div>` : ''}
                    ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities ${renderFilterHelpBtn('securities', 'Securities', 'font-size: 0.8rem; padding: 0')}</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
                </div>`);
        });
    }

    if (securitiesMaps && Object.keys(securitiesMaps).length > 0) {
        appendConfigSectionHeading(container, 'Securities');

        for (const [fileName, symbols] of Object.entries(securitiesMaps)) {
            const displaySymbols = symbols.length > 0 ? symbols.join(', ') : 'No securities found';

            appendAccordionConfigCard(container, `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm">
                        <span class="card-arrow">▶</span>
                        <strong>${fileName}</strong>
                        <span class="card-badge">${symbols.length} symbols</span>
                    </div>
                </div>
                <div class="config-card-body">
                    <div class="mt-sm">
                        <span class="config-item-value" style="line-height: 1.6;">${displaySymbols}</span>
                    </div>
                </div>`);
        }
    }

    if (config.technicalScreeners) {
        appendConfigSectionHeading(container, 'Technical Screeners');

        config.technicalScreeners.forEach(screener => {
            const enabledPill = renderEnabledPill(screener.enabled);
            const infoBtn = renderDocInfoBtn(screener.descriptionFile, screener.alias || screener.screenerType);

            appendAccordionConfigCard(container, `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm flex-wrap">
                        <span class="card-arrow">▶</span>
                        <strong>${screener.alias || screener.screenerType || 'Screener'}</strong>
                        ${infoBtn}
                        <span class="card-badge">${screener.screenerType} ${renderFilterHelpBtn('screenerType', 'Screener Type', 'font-size: 0.7rem; padding: 0; color: inherit')}</span>
                        ${enabledPill}
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderTechFiltersGrid(screener.technicalFilters)}
                    ${renderFundamentalFiltersGrid(screener.fundamentalFilters)}
                    ${screener.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File</span> <span class="config-item-value">${screener.securitiesFile}</span></div>` : ''}
                    ${screener.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${screener.securities}</span></div>` : ''}
                </div>`);
        });
    }
}

function renderInternalFilterGrid(filter) {
    const entries = Object.entries(filter).filter(([k, v]) => v !== null && typeof v !== 'object');
    if (entries.length === 0 && !Object.entries(filter).some(([k, v]) => v !== null && typeof v === 'object')) {
        return '<span class="text-muted">No filters configured</span>';
    }

    let html = '';
    if (entries.length > 0) {
        html += '<div class="config-grid">';
        for (const [key, value] of entries) {
            const label = formatCamelLabel(key);
            const displayVal = typeof value === 'boolean'
                ? (value ? '✅ Yes' : '❌ No')
                : value;
            const infoBtn = renderFilterHelpBtn(key, label, 'font-size: 0.8rem; padding: 0');
            html += `<div class="config-item">
                <span class="config-item-label">${label} ${infoBtn}</span>
                <span class="config-item-value">${displayVal}</span>
            </div>`;
        }
        html += '</div>';
    }

    const nested = Object.entries(filter).filter(([k, v]) => v !== null && typeof v === 'object' && !Array.isArray(v));
    for (const [key, value] of nested) {
        const label = formatCamelLabel(key);
        html += `<div class="nested-section"><h4 class="nested-heading">${label}</h4>${renderInternalFilterGrid(value)}</div>`;
    }

    const arrays = Object.entries(filter).filter(([k, v]) => Array.isArray(v));
    for (const [key, value] of arrays) {
        const label = formatCamelLabel(key);
        const infoBtn = renderFilterHelpBtn(key, label, 'font-size: 0.8rem; padding: 0');
        html += `<div class="mt-sm"><span class="config-item-label">${label} ${infoBtn}</span> `;
        html += value.map(v => `<span class="card-badge" style="margin:2px">${v}</span>`).join('');
        html += '</div>';
    }

    return html;
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    const dashboard = require('./dashboard');
    Object.assign(global, utils, authApi, dashboard);

    module.exports = {
        initConfigPage,
        renderConfig,
        renderInternalFilterGrid
    };
}
