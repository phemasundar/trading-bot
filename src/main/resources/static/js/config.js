/**
 * Trading Bot — Configuration Viewer (config.html)
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
            API.getSecuritiesMaps().catch(() => ({}))
        ]);
        container.innerHTML = '';
        renderConfig(config, container, securitiesMaps);
        updateMarketStatusBadge();
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

        config.optionsStrategies.forEach((strategy) => {
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
                        <span class="card-badge">${strategy.strategyType}</span>
                        ${enabledPill}
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderFilterGrid(strategy.filter || {})}
                    ${renderTechFiltersGrid(strategy.technicalFilters)}
                    ${strategy.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File</span> <span class="config-item-value">${strategy.securitiesFile}</span></div>` : ''}
                    ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
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
                        <span class="card-badge">${screener.screenerType}</span>
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
