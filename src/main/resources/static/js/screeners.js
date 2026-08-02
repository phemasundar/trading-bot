/**
 * Trading Bot — Technical Screeners View & Custom Screener Logic (screeners.html & execute-screener.html)
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

async function initScreenerDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadScreenerStrategies();
    await loadScreenerResults();
    await checkScreenerExecutionStatus();
    updateMarketStatusBadge();
}

async function loadScreenerStrategies() {
    const screenerContainer = document.getElementById('screener-checkboxes');
    if (!screenerContainer) return;

    try {
        const screeners = await API.getScreeners();
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

async function checkScreenerExecutionStatus() {
    try {
        const status = await API.getStatus();
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
        const res = await API.executeStrategies([], screenerIndices);
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

function selectAllScreeners(check) {
    document.querySelectorAll('#screener-checkboxes input[type="checkbox"]')
        .forEach(cb => cb.checked = check);
    if (check) {
        const body = document.getElementById('screener-section');
        const arrow = document.getElementById('arrow-screener-section');
        if (body && !body.classList.contains('open')) {
            body.classList.add('open');
            if (arrow) arrow.classList.add('open');
        }
    }
}

async function loadScreenerResults() {
    const screenerContainer = document.getElementById('screener-results-container');
    if (!screenerContainer) return;

    resetDashboardFilter('screeners');

    try {
        const screenerResults = await API.getScreenerResults().catch(() => []);
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

function buildScreenerCard(result, isCustom = false) {
    const card = document.createElement('div');
    const hasResults = result.results && result.results.length > 0;
    card.className = hasResults ? 'card' : 'card disabled';

    const cardId = (result.screenerId || 'screener-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    const isDropScreener = hasResults && result.results[0].dropType && result.results[0].dropType.length > 0;
    window.tradeDataMap[cardId] = result.results || [];
    window.tradeDataMap[cardId]._type = isDropScreener ? 'drop' : 'screener';
    
    const isExecuteScreenerPage = !!document.getElementById('screener-type');
    const loadFiltersBtn = (isCustom && isExecuteScreenerPage && result.requestParams)
        ? `<button class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: 4px;" onclick="loadScreenerFiltersFromResult('${escapeAttr(JSON.stringify(result.requestParams))}', event)">⬆ Load Filters</button>`
        : '';

    let deleteBtn = '';
    if (isCustom && result.screenerId) {
        deleteBtn = `<button class="btn btn-danger" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="promptDeleteCustomScreenerResult('${result.screenerId}', event)">🗑️ Delete</button>`;
    }

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                ${arrow}
                <span class="card-name">${result.screenerName || 'Screener'}</span>
                <span class="card-badge" style="background-color: var(--primary); color: #fff;">Screener</span>
                ${loadFiltersBtn}
                ${deleteBtn}
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Found: ${result.resultsFound || 0}${(() => { const d = formatDuration(result.executionTimeMs); return d ? ` · ⏱ ${d}` : ''; })()}</span>
        </div>

        <div class="card-content" id="content-${cardId}">
            ${buildScreenerTable(result.results || [], cardId)}
        </div>`;

    if (hasResults) {
        card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));
    } else {
        card.querySelector('.card-header').style.cursor = 'default';
    }

    return card;
}

function buildScreenerTable(results, cardId = null) {
    if (!results || results.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">🔎</div>No stocks found</div>';
    }

    const isDropScreener = results[0].dropType && results[0].dropType.length > 0;
    if (isDropScreener) {
        return buildDropScreenerTable(results, cardId);
    }

    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;

    const th = (key, label) => {
        if (!cardId) return `<th>${label}</th>`;
        const active = state && state.column === key;
        const arrow = active ? (state.direction === 'asc' ? ' ↑' : ' ↓') : '';
        const cls = active ? 'sort-header active' : 'sort-header';
        return `<th class="${cls}" onclick="handleTableSort('${cardId}', '${key}')" title="Sort by ${label}">${label}${arrow}</th>`;
    };

    let hasRsi = false;
    let hasBb = false;
    let hasVolume = false;
    let hasMarketCap = false;
    let maPeriodsSet = new Set();
    
    for (const r of results) {
        if (r.rsi && r.rsi !== 0) hasRsi = true;
        if (r.bollingerLower && r.bollingerLower !== 0) hasBb = true;
        if (r.volume && r.volume !== 0) hasVolume = true;
        if (r.marketCapB != null) hasMarketCap = true;
        if (r.maValues) {
            Object.keys(r.maValues).forEach(k => maPeriodsSet.add(Number(k)));
        }
    }
    const maPeriods = Array.from(maPeriodsSet).sort((a,b)=>b-a);

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            <th>Company</th>
            ${th('price', 'Price')}
            ${hasMarketCap ? th('marketCapB', 'Mkt Cap') : ''}
            ${hasVolume ? th('volume', 'Volume') : ''}
            ${hasRsi ? th('rsi', 'RSI') : ''}
            ${hasBb ? '<th>BB (L-U)</th>' : ''}
            ${maPeriods.map(p => th(`ma_${p}`, `SMA ${p}`)).join('\n            ')}
        </tr></thead><tbody>`;

    for (const r of results) {
        const typeClass = r.signalType === 'BULLISH' ? 'text-success' : (r.signalType === 'BEARISH' ? 'text-danger' : 'text-muted');
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const volume = formatLargeNumber(r.volume);
        const rsi = (typeof r.rsi === 'number') ? rsiValue(r.rsi) : '-';
        const bb = (typeof r.bollingerLower === 'number' && typeof r.bollingerUpper === 'number') 
            ? `${r.bollingerLower.toFixed(1)} - ${r.bollingerUpper.toFixed(1)}` : '-';

        html += `<tr class="trade-row" data-symbol="${escapeAttr(r.symbol || '')}">
            <td><strong class="${typeClass}">${r.symbol || ''}</strong></td>
            <td><span class="text-muted" title="${r.companyName || ''}">${formatCompanyName(r.companyName)}</span></td>
            <td class="text-mono">${price}</td>
            ${hasMarketCap ? `<td class="text-mono">${r.marketCapB != null ? formatMarketCap(r.marketCapB) : '-'}</td>` : ''}
            ${hasVolume ? `<td>${volume}</td>` : ''}
            ${hasRsi ? `<td>${rsi}</td>` : ''}
            ${hasBb ? `<td class="text-muted small">${bb}</td>` : ''}
            ${maPeriods.map(p => {
                const val = r.maValues && r.maValues[p];
                return `<td>${(typeof val === 'number' && val !== 0) ? val.toFixed(2) : '-'}</td>`;
            }).join('\n            ')}
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

function buildDropScreenerTable(results, cardId = null) {
    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;

    const th = (key, label) => {
        if (!cardId) return `<th>${label}</th>`;
        const active = state && state.column === key;
        const arrow = active ? (state.direction === 'asc' ? ' ↑' : ' ↓') : '';
        const cls = active ? 'sort-header active' : 'sort-header';
        return `<th class="${cls}" onclick="handleTableSort('${cardId}', '${key}')" title="Sort by ${label}">${label}${arrow}</th>`;
    };

    const hasMarketCapDrop = results.some(r => r.marketCapB != null);

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            <th>Company</th>
            ${th('price', 'Current Price')}
            ${hasMarketCapDrop ? th('marketCapB', 'Mkt Cap') : ''}
            ${th('refPrice', 'Ref Price')}
            ${th('dropPct', 'Drop %')}
            ${th('volume', 'Volume')}
            <th>Type</th>
        </tr></thead><tbody>`;

    let sorted = results;
    if (!state || !state.column) {
        sorted = [...results].sort((a, b) => (b.dropPercent || 0) - (a.dropPercent || 0));
    }

    for (const r of sorted) {
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const refPrice = (typeof r.referencePrice === 'number') ? `$${r.referencePrice.toFixed(2)}` : '-';
        const dropPct = (typeof r.dropPercent === 'number') ? r.dropPercent.toFixed(2) : '0';
        const volume = formatLargeNumber(r.volume);
        const dropType = r.dropType || '-';
        const dropVal = r.dropPercent || 0;
        const dropColor = dropVal >= 20 ? '#ff4444' : dropVal >= 10 ? '#ff6b6b' : dropVal >= 5 ? '#ffa07a' : '#ffcc80';

        html += `<tr class="trade-row" data-symbol="${escapeAttr(r.symbol || '')}">
            <td><strong class="text-danger">${r.symbol || ''}</strong></td>
            <td><span class="text-muted" title="${r.companyName || ''}">${formatCompanyName(r.companyName)}</span></td>
            <td class="text-mono">${price}</td>
            ${hasMarketCapDrop ? `<td class="text-mono">${r.marketCapB != null ? formatMarketCap(r.marketCapB) : '-'}</td>` : ''}
            <td class="text-muted text-mono">${refPrice}</td>
            <td><span style="color: ${dropColor}; font-weight: 700;">-${dropPct}%</span></td>
            <td>${volume}</td>
            <td class="text-muted small">${dropType}</td>
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

function rsiValue(val) {
    const cls = val < 30 ? 'text-success' : (val > 70 ? 'text-danger' : '');
    return `<span class="${cls}">${val.toFixed(1)}</span>`;
}

function promptDeleteCustomScreenerResult(resultId, event) {
    if (event) event.stopPropagation();
    const card = document.querySelector(`.card-header[data-target="${resultId}"]`)?.closest('.card');
    
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
        <div class="modal" style="text-align:center; max-width:400px">
            <h3 style="margin-bottom:12px; color:var(--text-primary)">Delete Screener Result</h3>
            <p style="margin-bottom:24px; color:var(--text-secondary)">Are you sure you want to permanently delete this custom screener execution? This action cannot be undone.</p>
            <div class="flex gap-sm justify-center">
                <button class="btn btn-secondary" id="cancel-delete-btn">Cancel</button>
                <button class="btn btn-danger" id="confirm-delete-btn">Delete</button>
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    overlay.querySelector('#cancel-delete-btn').addEventListener('click', () => overlay.remove());
    overlay.querySelector('#confirm-delete-btn').addEventListener('click', async () => {
        overlay.remove();
        await deleteCustomScreenerResult(resultId, card);
    });
}

async function deleteCustomScreenerResult(resultId, card) {
    try {
        await API.deleteCustomScreenerResult(resultId);
        if (card) {
            card.style.transition = 'opacity 0.3s, transform 0.3s';
            card.style.opacity = '0';
            card.style.transform = 'translateX(20px)';
            setTimeout(() => card.remove(), 320);
        }
        showToast('Screener execution result deleted.');
    } catch (e) {
        showToast(`Failed to delete: ${e.message}`, 'error');
    }
}
