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

function renderConfig(config, container, securitiesMaps = {}) {
    if (config.optionsStrategies) {
        const heading = document.createElement('h3');
        heading.textContent = 'Options Strategies';
        heading.className = 'section-heading';
        container.appendChild(heading);

        config.optionsStrategies.forEach((strategy, i) => {
            const card = document.createElement('div');
            card.className = 'config-card';

            const enabledPill = strategy.enabled
                ? '<span class="pill pill-enabled">Enabled</span>'
                : '<span class="pill pill-disabled">Disabled</span>';

            const infoBtn = strategy.descriptionFile
                ? `<button type="button" class="info-btn" onclick="showInfo(event, '${strategy.descriptionFile}', '${escapeAttr(strategy.alias || strategy.strategyType)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
                : '';

            card.innerHTML = `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm flex-wrap">
                        <span class="card-arrow">▶</span>
                        <strong>${strategy.alias || strategy.strategyType}</strong>
                        ${infoBtn}
                        <span class="card-badge">${strategy.strategyType} <button type="button" class="info-btn" style="font-size: 0.7rem; padding: 0; color: inherit" onclick="showFilterHelp(event, 'strategyType', 'Strategy Type')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span>
                        ${enabledPill}
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderFilterGrid(strategy.filter || {})}
                    ${renderTechFiltersGrid(strategy.technicalFilters)}
                    ${strategy.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File <button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, 'securitiesFile', 'Securities File')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span> <span class="config-item-value">${strategy.securitiesFile}</span></div>` : ''}
                    ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities <button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, 'securities', 'Securities')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
                </div>`;

            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });

            container.appendChild(card);
        });
    }

    if (securitiesMaps && Object.keys(securitiesMaps).length > 0) {
        const heading = document.createElement('h3');
        heading.textContent = 'Securities';
        heading.className = 'section-heading';
        container.appendChild(heading);

        for (const [fileName, symbols] of Object.entries(securitiesMaps)) {
            const card = document.createElement('div');
            card.className = 'config-card';
            const displaySymbols = symbols.length > 0 ? symbols.join(', ') : 'No securities found';

            card.innerHTML = `
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
                </div>`;

            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });

            container.appendChild(card);
        }
    }

    if (config.technicalScreeners) {
        const heading = document.createElement('h3');
        heading.textContent = 'Technical Screeners';
        heading.className = 'section-heading';
        container.appendChild(heading);

        config.technicalScreeners.forEach(screener => {
            const card = document.createElement('div');
            card.className = 'config-card';

            const enabledPill = screener.enabled
                ? '<span class="pill pill-enabled">Enabled</span>'
                : '<span class="pill pill-disabled">Disabled</span>';

            const infoBtn = screener.descriptionFile
                ? `<button type="button" class="info-btn" onclick="showInfo(event, '${screener.descriptionFile}', '${escapeAttr(screener.alias || screener.screenerType)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
                : '';

            card.innerHTML = `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm flex-wrap">
                        <span class="card-arrow">▶</span>
                        <strong>${screener.alias || screener.screenerType || 'Screener'}</strong>
                        ${infoBtn}
                        <span class="card-badge">${screener.screenerType} <button type="button" class="info-btn" style="font-size: 0.7rem; padding: 0; color: inherit" onclick="showFilterHelp(event, 'screenerType', 'Screener Type')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span>
                        ${enabledPill}
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderTechFiltersGrid(screener.technicalFilters)}
                    ${renderFundamentalFiltersGrid(screener.fundamentalFilters)}
                    ${screener.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File</span> <span class="config-item-value">${screener.securitiesFile}</span></div>` : ''}
                    ${screener.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${screener.securities}</span></div>` : ''}
                </div>`;
            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });
            container.appendChild(card);
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
            const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
            const displayVal = typeof value === 'boolean'
                ? (value ? '✅ Yes' : '❌ No')
                : value;
            const infoBtn = `<button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, '${key}', '${escapeAttr(label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
            html += `<div class="config-item">
                <span class="config-item-label">${label} ${infoBtn}</span>
                <span class="config-item-value">${displayVal}</span>
            </div>`;
        }
        html += '</div>';
    }

    const nested = Object.entries(filter).filter(([k, v]) => v !== null && typeof v === 'object' && !Array.isArray(v));
    for (const [key, value] of nested) {
        const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
        html += `<div class="nested-section"><h4 class="nested-heading">${label}</h4>${renderInternalFilterGrid(value)}</div>`;
    }

    const arrays = Object.entries(filter).filter(([k, v]) => Array.isArray(v));
    for (const [key, value] of arrays) {
        const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
        const infoBtn = `<button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, '${key}', '${escapeAttr(label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
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
