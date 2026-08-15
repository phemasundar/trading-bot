/**
 * Trading Bot — Dashboard & Table Rendering
 * Dashboard initialization, results loading, card/table builders, sorting, live quote performance, and filter bar.
 */

// ── Global State for Sorting & Strategy Mapping ──
window.tableSortState = window.tableSortState || {};
window.tradeDataMap = window.tradeDataMap || {};
window.tradeStrategyIdMap = window.tradeStrategyIdMap || {};

// ── Card Builder ──

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    const hasTrades = result.trades && result.trades.length > 0;
    card.className = hasTrades ? 'card' : 'card disabled';

    const cardId = String(result.strategyId || result.screenerId || 'card-' + Math.random()).replace(/\s+/g, '-');
    const actualStrategyId = result.strategyId || result.strategyName || '';
    window.tradeStrategyIdMap[cardId] = actualStrategyId;
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;
    const filterId = `filters-${cardId}`;

    const isExecutePage = !!document.getElementById('strategy-type');

    const loadFiltersBtn = (isExecutePage && result.filterConfig)
        ? `<button type="button" class="btn btn-primary btn-sm" style="margin-left: auto;" onclick="event.stopPropagation(); loadFiltersFromResult(this)" data-filter-config="${escapeAttr(typeof result.filterConfig === 'string' ? result.filterConfig : JSON.stringify(result.filterConfig))}" data-strategy-name="${escapeAttr(result.strategyName || '')}">⬆ Load Filters</button>`
        : '';

    let filterDetailsHtml = '';
    if (result.filterConfig) {
        try {
            const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig;
            const filterGrid = renderFilterGrid(cfg);
            if (filterGrid && !filterGrid.includes('No filters configured')) {
                filterDetailsHtml = `
                    <div class="filter-details-section">
                        <div class="filter-details-toggle" data-target="${filterId}">
                            <span class="card-arrow" id="arrow-${filterId}">▶</span>
                            <span>Filter Details</span>
                        </div>
                        <div class="filter-details-body" id="${filterId}">
                            ${filterGrid}
                        </div>
                    </div>`;
            }
        } catch (e) { /* ignore parse errors */ }
    }

    const deleteBtn = (isExecutePage && badgeText === 'Custom' && result.strategyId && !isNaN(result.strategyId))
        ? `<button type="button" class="btn btn-danger btn-sm" style="margin-left: 4px;" onclick="event.stopPropagation(); confirmDeleteCustomResult('${escapeAttr(result.strategyId)}', this.closest('.card'))">🗑 Delete</button>`
        : '';

    let displayName = result.strategyName || 'Unknown';
    if (result.filterConfig) {
        try {
            const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig;
            if (cfg && cfg.termType) displayName += ` - ${cfg.termType}`;
            if (cfg && cfg.securitiesFile) displayName += ` - ${cfg.securitiesFile}`;
        } catch (e) { /* ignore parse errors */ }
    }

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                ${arrow}
                <span class="card-name">${displayName}</span>
                ${result.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${result.descriptionFile}', '${escapeAttr(displayName)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>` : ''}
                <span class="card-badge">${badgeText}</span>
                ${(() => { try { const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig; return renderGreeksPills(cfg && cfg.greeks); } catch(e) { return ''; } })()}
                ${loadFiltersBtn}
                ${deleteBtn}
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Trades: ${result.tradesFound || 0}${(() => { const d = formatDuration(result.executionTimeMs); return d ? ` · ⏱ ${d}` : ''; })()}</span>
        </div>

        ${filterDetailsHtml}
        <div class="card-content" id="content-${cardId}">
            ${buildTradeTable(result.trades || [], cardId, actualStrategyId)}
        </div>`;

    window.tradeDataMap[cardId] = result.trades || [];

    if (hasTrades) {
        card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));
    } else {
        card.querySelector('.card-header').style.cursor = 'default';
        if(card.querySelector('.info-btn')) {
             card.querySelector('.info-btn').style.opacity = '0.5';
             card.querySelector('.info-btn').style.cursor = 'default';
             card.querySelector('.info-btn').onclick = (e) => { e.stopPropagation(); e.preventDefault(); };
        }
    }

    const filterToggle = card.querySelector('.filter-details-toggle');
    if (filterToggle) {
        filterToggle.addEventListener('click', (e) => {
            e.stopPropagation();
            const targetId = filterToggle.dataset.target;
            const body = card.querySelector('#' + CSS.escape(targetId));
            const arrowEl = card.querySelector('#' + CSS.escape('arrow-' + targetId));
            if (body) body.classList.toggle('open');
            if (arrowEl) arrowEl.classList.toggle('open');
        });
    }

    return card;
}

function renderTermGroups(container, results, badgeText = 'Standard') {
    const groups = new Map();

    for (const r of results) {
        let term = 'Other';
        if (r.filterConfig) {
            try {
                const cfg = typeof r.filterConfig === 'string' ? JSON.parse(r.filterConfig) : r.filterConfig;
                if (cfg && cfg.termType) term = cfg.termType;
            } catch (_) { /* ignore */ }
        }
        if (!groups.has(term)) groups.set(term, []);
        groups.get(term).push(r);
    }

    function termWeight(label) {
        if (label === 'Other') return -1;
        const l = label.toLowerCase();
        if (l.includes('extra long')) return 70;
        if (l.includes('long'))       return 60;
        if (l.includes('medium term 2') || l.includes('medium 2')) return 45;
        if (l.includes('up to'))      return 35;
        if (l.includes('medium'))     return 40;
        if (l.includes('short'))      return 30;
        if (l.includes('daily'))      return 20;
        return 10;
    }

    const orderedKeys = [...groups.keys()].sort((a, b) => {
        const wa = termWeight(a), wb = termWeight(b);
        if (wb !== wa) return wb - wa;
        return b.localeCompare(a);
    });

    for (const term of orderedKeys) {
        const termResults = groups.get(term);
        const groupId = 'term-group-' + term.replace(/\s+/g, '-').toLowerCase();

        const groupEl = document.createElement('div');
        groupEl.className = 'term-group';

        const header = document.createElement('div');
        header.className = 'term-group-header';
        header.innerHTML = `
            <span class="card-arrow" id="arrow-${groupId}">▶</span>
            <span class="term-group-label">${term}</span>
            <span class="term-group-count">(${termResults.length})</span>`;

        const body = document.createElement('div');
        body.className = 'term-group-body hidden';
        body.id = groupId;

        for (const r of termResults) {
            body.appendChild(buildResultCard(r, badgeText));
        }

        header.addEventListener('click', () => {
            body.classList.toggle('hidden');
            const arrowEl = document.getElementById(`arrow-${groupId}`);
            if (arrowEl) arrowEl.classList.toggle('open');
        });

        groupEl.appendChild(header);
        groupEl.appendChild(body);
        container.appendChild(groupEl);
    }
}

// ── Delete Custom Result ──

function confirmDeleteCustomResult(resultId, card) {
    if (document.querySelector('.delete-confirm-overlay')) return;

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay delete-confirm-overlay';
    overlay.innerHTML = `
        <div class="modal" style="max-width: 420px; text-align: center;">
            <div style="font-size: 2.5rem; margin-bottom: 12px;">🗑</div>
            <h2 style="margin: 0 0 8px;">Delete Execution Result?</h2>
            <p style="color: var(--text-secondary); margin: 0 0 24px; font-size: 0.9rem;">
                This action cannot be undone. The record will be permanently removed from the database.
            </p>
            <div class="flex gap-sm" style="justify-content: center;">
                <button class="btn btn-secondary" onclick="this.closest('.modal-overlay').remove()">Cancel</button>
                <button class="btn btn-danger" id="confirm-delete-btn">🗑 Delete</button>
            </div>
        </div>`;

    overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });
    document.body.appendChild(overlay);

    overlay.querySelector('#confirm-delete-btn').addEventListener('click', async () => {
        overlay.remove();
        await deleteCustomResult(resultId, card);
    });
}

async function deleteCustomResult(resultId, card) {
    try {
        await API.delete(`/api/results/custom/${resultId}`);
        if (card) {
            card.style.transition = 'opacity 0.3s, transform 0.3s';
            card.style.opacity = '0';
            card.style.transform = 'translateX(20px)';
            setTimeout(() => card.remove(), 320);
        }
        showToast('Execution result deleted.');
    } catch (e) {
        showToast(`Failed to delete: ${e.message}`, 'error');
    }
}

function promptDeleteCustomScreenerResult(resultId, event) {
    event.stopPropagation();
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
        await API.delete(`/api/results/custom/screeners/${resultId}`);
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

// ── Screener Card & Table Builders ──

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
        
        const detailLines = [];
        detailLines.push(`💰 Price: $${(typeof r.currentPrice === 'number') ? r.currentPrice.toFixed(2) : '-'}`);
        detailLines.push(`📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`);

        if (typeof r.rsi === 'number' && r.rsi !== 0) {
            let rsiLine = `📈 RSI: ${r.rsi.toFixed(2)}`;
            if (typeof r.previousRsi === 'number' && r.previousRsi !== 0) rsiLine += ` (prev: ${r.previousRsi.toFixed(2)})`;
            if (r.rsiBullishCrossover) rsiLine += ' ⬆️ CROSSOVER';
            else if (r.rsiBearishCrossover) rsiLine += ' ⬇️ CROSSOVER';
            else if (r.rsiOversold) rsiLine += ' 🔴 OVERSOLD';
            else if (r.rsiOverbought) rsiLine += ' 🟢 OVERBOUGHT';
            detailLines.push(rsiLine);
        }

        if (typeof r.bollingerLower === 'number' && r.bollingerLower !== 0) {
            let bbLine = '📉 BB: ';
            if (r.priceTouchingLowerBand) bbLine += `Touching Lower ($${r.bollingerLower.toFixed(2)})`;
            else if (r.priceTouchingUpperBand) bbLine += `Touching Upper ($${r.bollingerUpper.toFixed(2)})`;
            else bbLine += `Within bands ($${r.bollingerLower.toFixed(2)} - $${r.bollingerUpper.toFixed(2)})`;
            detailLines.push(bbLine);
        }

        const belowMAs = [];
        const aboveMAs = [];
        if (r.maValues) {
            maPeriods.forEach(p => {
                const val = r.maValues[p];
                if (typeof val === 'number' && val !== 0) {
                    if (r.currentPrice < val) belowMAs.push(`SMA${p}`);
                    else aboveMAs.push(`SMA${p}`);
                }
            });
        }
        if (belowMAs.length > 0 || aboveMAs.length > 0) {
            let maLine = '📊 MAs: ';
            if (belowMAs.length > 0) maLine += `Below ${belowMAs.join(', ')}`;
            if (belowMAs.length > 0 && aboveMAs.length > 0) maLine += ' | ';
            if (aboveMAs.length > 0) maLine += `Above ${aboveMAs.join(', ')}`;
            detailLines.push(maLine);
        }

        const detailStr = escapeAttr(detailLines.join('\n'));
        const techIndicatorsAttr = escapeAttr(r.allTechnicalIndicatorsSummary || r.formattedSummary || '');

        html += `<tr class="trade-row" data-details="${detailStr}" data-tech-indicators="${techIndicatorsAttr}">
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

        const detailLines = [
            `📉 Drop: ${dropPct}% (${dropType})`,
            `💰 Current: ${price}`,
            `📌 Reference: ${refPrice}`,
            `📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`,
            ...(r.marketCapB != null ? [`🏢 Mkt Cap: ${formatMarketCap(r.marketCapB)}`] : [])
        ];
        const detailStr = escapeAttr(detailLines.join('\n'));
        const techIndicatorsAttr = escapeAttr(r.allTechnicalIndicatorsSummary || r.formattedSummary || '');

        html += `<tr class="trade-row" data-details="${detailStr}" data-tech-indicators="${techIndicatorsAttr}">
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

function buildTradeTable(trades, cardId = null, strategyId = null, isHistoryModal = false) {
    if (!trades || trades.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">📊</div>No trades found</div>';
    }

    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;

    const th = (key, label) => {
        if (!cardId) return `<th>${label}</th>`;
        const active = state && state.column === key;
        const arrow = active ? (state.direction === 'asc' ? ' ↑' : ' ↓') : '';
        const cls = active ? 'sort-header active' : 'sort-header';
        return `<th class="${cls}" onclick="handleTableSort('${cardId}', '${key}')" title="Sort by ${label}">${label}${arrow}</th>`;
    };

    let html = `<table class="data-table">
        <thead><tr>
            ${isHistoryModal ? '<th>Date Found</th>' : ''}
            ${th('ticker', 'Ticker')}
            <th>Company</th>
            <th>Price</th>
            ${th('todayPct', 'Today')}
            <th>Type</th>
            ${th('expiry', 'Expiry')}
            <th>Credit/Debit</th>
            ${th('maxLoss', 'Max Loss')}
            ${th('extrinsic', 'Extrinsic')}
            ${th('breakeven', 'Breakeven')}
            ${th('ror', 'ROR%')}
            ${!isHistoryModal ? '<th>History</th>' : ''}
        </tr></thead><tbody>`;

    for (const t of trades) {
        const rorClass = (t.returnOnRisk || 0) >= 0 ? 'text-success' : 'text-danger';
        const credit = t.netCredit || 0;
        const creditStr = credit >= 0
            ? `<span class="text-success">$${credit.toFixed(2)}</span>`
            : `<span class="text-danger">-$${Math.abs(credit).toFixed(2)}</span>`;

        const detailsEscaped = escapeAttr(t.tradeDetails || '');
        const techIndicatorsAttr = t.techIndicators ? escapeAttr(t.techIndicators) : '';
        const sym = t.symbol || '';
        const legsOptionData = (t.legs || []).map(l => ({ action: l.action, optionType: l.optionType, optionData: l.optionData || null }));
        const legsAttr = escapeAttr(JSON.stringify(legsOptionData));
        const tradeEscaped = escapeAttr(JSON.stringify(t));

        let rorCagr = t.returnOnRiskCAGR;
        if (typeof rorCagr === 'string') rorCagr = parseFloat(rorCagr);
        if (rorCagr == null && t.returnOnRisk != null && t.dte > 0 && t.maxLoss > 0) {
            const rawRoR = t.returnOnRisk / 100.0;
            rorCagr = (Math.pow(1.0 + rawRoR, 365.0 / t.dte) - 1.0) * 100.0;
        }
        
        let rorCagrDisplay = '';
        if (rorCagr != null && !isNaN(rorCagr) && isFinite(rorCagr)) {
            rorCagrDisplay = ` <span class="text-muted">(${rorCagr.toFixed(1)}% CAGR)</span>`;
        }

        const effectiveStrategyId = strategyId || (cardId && window.tradeStrategyIdMap ? window.tradeStrategyIdMap[cardId] : '') || cardId || '';
        const foundDateDisplay = (t.foundDate && t.foundDate !== '1969-12-31' && t.foundDate !== '1970-01-01' && t.foundDate !== 'null')
            ? t.foundDate
            : (t.executionTimeMs && Number(t.executionTimeMs) > 86400000
                ? new Date(Number(t.executionTimeMs)).toISOString().slice(0, 10)
                : '--');

        html += `<tr class="trade-row" data-details="${detailsEscaped}" data-tech-indicators="${techIndicatorsAttr}" data-legs-option-data="${legsAttr}" data-symbol="${escapeAttr(sym)}">
            ${isHistoryModal ? `<td><span class="text-muted text-mono">${escapeHtmlContent(foundDateDisplay)}</span></td>` : ''}
            <td><strong>${sym}</strong></td>
            <td><span class="text-muted" title="${t.companyName || ''}">${formatCompanyName(t.companyName)}</span></td>
            <td class="text-mono">$${(t.underlyingPrice || 0).toFixed(2)}</td>
            <td class="today-perf" data-symbol="${escapeAttr(sym)}"><span class="text-muted">--</span></td>
            <td>${formatLegs(t)}</td>
            <td>${formatExpiryDate(t.expiryDate)} <span class="text-muted">(${t.dte || 0}d)</span></td>
            <td>${creditStr}</td>
            <td class="text-danger">$${(t.maxLoss || 0).toFixed(2)}</td>
            <td>$${(t.netExtrinsicValue || 0).toFixed(2)} <span class="text-muted">(${(t.anulizedNetExtrinsicValueToCapitalPercentage || 0).toFixed(1)}%)</span></td>
            <td>${formatBreakeven(t)}</td>
            <td class="${rorClass}">${(t.returnOnRisk || 0).toFixed(1)}%${rorCagrDisplay}</td>
            ${!isHistoryModal ? `<td onclick="event.stopPropagation(); showTradeHistoryModal('${escapeAttr(effectiveStrategyId)}', this.dataset.trade, event)" data-trade="${tradeEscaped}">
                <button class="btn-history-icon" title="View Similar Historical Trades" style="background:none;border:none;cursor:pointer;font-size:1.1rem;padding:2px 6px;">🕒</button>
            </td>` : ''}
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

function handleTableSort(cardId, column) {
    const originalData = window.tradeDataMap[cardId];
    if (!originalData || originalData.length === 0) return;

    if (!window.tableSortState[cardId] || window.tableSortState[cardId].column !== column) {
        window.tableSortState[cardId] = { column: column, direction: 'asc' };
    } else {
        const state = window.tableSortState[cardId];
        if (state.direction === 'asc') {
            state.direction = 'desc';
        } else {
            window.tableSortState[cardId] = { column: null, direction: null };
        }
    }

    let data = [...originalData];
    const state = window.tableSortState[cardId];

    if (state && state.column && state.direction) {
        const dirMultiplier = state.direction === 'asc' ? 1 : -1;

        data.sort((a, b) => {
            let valA, valB;
            switch (state.column) {
                case 'ticker':
                    valA = a.symbol || '';
                    valB = b.symbol || '';
                    return valA.localeCompare(valB) * dirMultiplier;
                case 'volume':
                    valA = a.volume || a.totalVolume || 0;
                    valB = b.volume || b.totalVolume || 0;
                    break;

                case 'expiry':
                    valA = a.dte || 0;
                    valB = b.dte || 0;
                    break;
                case 'maxLoss':
                    valA = a.maxLoss || 0;
                    valB = b.maxLoss || 0;
                    break;
                case 'extrinsic':
                    valA = a.anulizedNetExtrinsicValueToCapitalPercentage || 0;
                    valB = b.anulizedNetExtrinsicValueToCapitalPercentage || 0;
                    break;
                case 'breakeven':
                    const hasCagr = a.breakevenCAGR != null && b.breakevenCAGR != null;
                    valA = hasCagr ? a.breakevenCAGR : (a.breakEvenPercent || 0);
                    valB = hasCagr ? b.breakevenCAGR : (b.breakEvenPercent || 0);
                    break;
                case 'ror':
                    const getRoRCagr = (x) => {
                        let c = x.returnOnRiskCAGR;
                        if (typeof c === 'string') c = parseFloat(c);
                        if (c == null && x.returnOnRisk != null && x.dte > 0 && x.maxLoss > 0) {
                            c = (Math.pow(1.0 + (x.returnOnRisk/100.0), 365.0/x.dte) - 1.0) * 100.0;
                        }
                        if (c == null || isNaN(c) || !isFinite(c)) {
                            c = x.maxReturnOnRiskPercentage || x.returnOnRisk || 0;
                        }
                        return c;
                    };
                    valA = getRoRCagr(a);
                    valB = getRoRCagr(b);
                    break;
                case 'marketCapB':
                    valA = a.marketCapB || 0;
                    valB = b.marketCapB || 0;
                    break;

                case 'todayPct':
                    valA = a._todayPct != null ? a._todayPct : -Infinity;
                    valB = b._todayPct != null ? b._todayPct : -Infinity;
                    break;

                case 'price':
                    valA = a.currentPrice || a.underlyingPrice || 0;
                    valB = b.currentPrice || b.underlyingPrice || 0;
                    break;
                case 'rsi':
                    valA = a.rsi || 0;
                    valB = b.rsi || 0;
                    break;
                case 'ma200':
                    valA = a.ma200 || 0;
                    valB = b.ma200 || 0;
                    break;
                case 'ma100':
                    valA = a.ma100 || 0;
                    valB = b.ma100 || 0;
                    break;
                case 'ma50':
                    valA = a.ma50 || 0;
                    valB = b.ma50 || 0;
                    break;
                case 'ma20':
                    valA = a.ma20 || 0;
                    valB = b.ma20 || 0;
                    break;

                case 'refPrice':
                    valA = a.referencePrice || 0;
                    valB = b.referencePrice || 0;
                    break;
                case 'dropPct':
                    valA = a.dropPercent || 0;
                    valB = b.dropPercent || 0;
                    break;

                default:
                    return 0;
            }
            return (valA - valB) * dirMultiplier;
        });
    }

    const contentDiv = document.getElementById(`content-${cardId}`);
    if (contentDiv) {
        const tableType = originalData._type;
        if (tableType === 'drop') {
            contentDiv.innerHTML = buildDropScreenerTable(data, cardId);
        } else if (tableType === 'screener') {
            contentDiv.innerHTML = buildScreenerTable(data, cardId);
        } else {
            const actualStrategyId = window.tradeStrategyIdMap ? window.tradeStrategyIdMap[cardId] : null;
            contentDiv.innerHTML = buildTradeTable(data, cardId, actualStrategyId);
            const symbols = [...new Set(data.map(t => t.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, contentDiv);
            }
        }
    }
}

// ── Today's Performance Injection ──

async function injectTodayPerformance(symbols, scope = document) {
    if (!symbols || symbols.length === 0) return;
    try {
        const data = await API.get(`/api/quotes?symbols=${symbols.join(',')}`);
        if (!Array.isArray(data)) return;

        const quoteMap = {};
        for (const q of data) {
            quoteMap[q.symbol] = q;
        }

        const cells = scope.querySelectorAll('.today-perf[data-symbol]');
        cells.forEach(cell => {
            const sym = cell.getAttribute('data-symbol') || (cell.dataset && cell.dataset.symbol);
            const q = quoteMap[sym];
            if (!q || q.netChange == null || q.netPercentChange == null) {
                cell.innerHTML = '<span class="text-muted">N/A</span>';
                return;
            }
            const chg = q.netChange;
            const pct = q.netPercentChange;
            const sign = chg >= 0 ? '+' : '';
            const cls = chg >= 0 ? 'text-success' : 'text-danger';
            const chgStr = `${sign}$${Math.abs(chg).toFixed(2)}`;
            const pctStr = `(${sign}${pct.toFixed(2)}%)`;
            cell.innerHTML = `<span class="${cls}" style="font-size:0.82rem; white-space:nowrap;">${chgStr} ${pctStr}</span>`;
        });

        for (const [cardId, trades] of Object.entries(window.tradeDataMap || {})) {
            if (!Array.isArray(trades)) continue;
            trades.forEach(t => {
                const q = quoteMap[t.symbol];
                if (q && q.netPercentChange != null) {
                    t._todayPct = q.netPercentChange;
                }
            });
        }
    } catch (e) {
        console.warn('Today performance fetch failed:', e.message);
    }
}

async function fetchAndInjectTodayPerformance(container) {
    if (!container) return;
    const symbols = [...new Set(
        [...container.querySelectorAll('.today-perf[data-symbol]')]
            .map(el => el.dataset.symbol)
            .filter(Boolean)
    )];
    if (symbols.length > 0) {
        await injectTodayPerformance(symbols, container);
    }
}

// ── Trade Detail Popup ──

function renderOptionDataTable(legsOptionData) {
    if (!legsOptionData || legsOptionData.length === 0) return '';

    const fmt = (v, digits = 2) => (v != null && v !== 0) ? (typeof v === 'number' ? v.toFixed(digits) : v) : '—';
    const fmtDollar = (v) => (v != null && v !== 0) ? `$${Math.abs(v).toFixed(2)}` : '—';
    const fmtInt = (v) => (v != null && v !== 0) ? v.toLocaleString() : '—';

    let html = `<div style="margin-top: 10px; border: 1px solid var(--border); border-radius: 6px; overflow: hidden;">
        <div style="padding: 7px 12px; background: var(--bg-secondary); border-bottom: 1px solid var(--border); font-size: 0.78rem; font-weight: 600; letter-spacing: 0.06em; color: var(--text-muted); text-transform: uppercase;">
            Option Chain Data
        </div>`;

    for (const leg of legsOptionData) {
        const d = leg.optionData;
        const actionCls = leg.action === 'SELL' ? 'leg-sell' : 'leg-buy';
        const legLabel = `<span class="leg-chip ${actionCls}" style="font-size:0.75rem; padding:2px 7px;">${leg.action} ${leg.optionType}</span>`;

        if (!d) {
            html += `<div style="padding: 8px 12px; font-size:0.82rem; color: var(--text-muted);">${legLabel} <span style="margin-left:8px;">No option data available (re-run strategy to populate)</span></div>`;
            continue;
        }

        const rows = [
            ['Symbol', escapeHtmlContent(d.symbol || '—')],
            ['Bid / Ask', `${fmtDollar(d.bid)} / ${fmtDollar(d.ask)}`],
            ['Mark', fmtDollar(d.mark)],
            ['Last', fmtDollar(d.last)],
            ['Bid×Ask Size', escapeHtmlContent(d.bidAskSize || '—')],
            ['Volume', fmtInt(d.totalVolume)],
            ['Open Interest', fmtInt(d.openInterest)],
            ['IV (Volatility)', d.volatility != null ? `${fmt(d.volatility, 1)}%` : '—'],
            ['Delta', fmt(d.delta, 4)],
            ['Gamma', fmt(d.gamma, 4)],
            ['Theta', fmt(d.theta, 4)],
            ['Vega', fmt(d.vega, 4)],
            ['Rho', fmt(d.rho, 4)],
            ['Intrinsic Value', fmtDollar(d.intrinsicValue)],
            ['Extrinsic Value', fmtDollar(d.extrinsicValue)],
            ['Time Value', fmtDollar(d.timeValue)],
            ['Theoretical Value', fmtDollar(d.theoreticalOptionValue)],
            ['% Change', d.percentChange != null ? `${fmt(d.percentChange, 2)}%` : '—'],
            ['In The Money', d.inTheMoney ? 'Yes' : 'No'],
            ['Days to Expiry', d.daysToExpiration != null ? d.daysToExpiration : '—'],
            ['Expiration Date', escapeHtmlContent(d.expirationDate || '—')],
            ['Strike', fmtDollar(d.strikePrice)],
            ['52W High', fmtDollar(d.high52Week)],
            ['52W Low', fmtDollar(d.low52Week)],
        ];

        html += `<div style="padding: 8px 12px 6px; border-bottom: 1px solid var(--border);">
            <div style="margin-bottom: 6px;">${legLabel} <span style="font-size:0.78rem; color:var(--text-muted); margin-left:6px;">${escapeHtmlContent(d.description || '')}</span></div>
            <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 3px 16px;">`;

        for (const [label, val] of rows) {
            html += `<div style="display:flex; justify-content:space-between; font-size:0.8rem; padding: 2px 0; border-bottom: 1px solid var(--border); gap:8px;">
                <span style="color:var(--text-muted); white-space:nowrap;">${label}</span>
                <span style="color:var(--text-primary); font-family:var(--font-mono); text-align:right;">${val}</span>
            </div>`;
        }

        html += `</div></div>`;
    }

    html += `</div>`;
    return html;
}

function initTradeRowClicks() {
    document.addEventListener('click', (e) => {
        const row = e.target.closest('.trade-row');
        if (!row) return;

        const existing = document.querySelector('.trade-detail-panel');
        if (existing) {
            if (existing.dataset.rowId === row.dataset.details) {
                existing.remove();
                row.classList.remove('selected');
                return;
            }
            existing.remove();
            document.querySelectorAll('.trade-row.selected').forEach(r => r.classList.remove('selected'));
        }

        const details = row.dataset.details;
        if (!details) return;

        const techIndicators = row.dataset.techIndicators;

        row.classList.add('selected');

        const symbol = row.querySelector('td strong')?.textContent || '';
        let legsOptionData = null;
        try {
            const raw = row.dataset.legsOptionData ? decodeAttr(row.dataset.legsOptionData) : null;
            legsOptionData = raw ? JSON.parse(raw) : null;
        } catch(e) {}
        
        const panel = document.createElement('tr');
        panel.className = 'trade-detail-panel';
        panel.dataset.rowId = details;
        panel.innerHTML = `
            <td colspan="10">
                <div class="trade-detail">
                    <div class="trade-detail-header">
                        ▶ ${symbol} — Trade Details
                    </div>
                    <pre class="trade-detail-body">${decodeAttr(details)}</pre>
                    ${techIndicators ? `
                    <div class="trade-detail-header" style="margin-top: 15px;">
                        ▶ ${symbol} — Tech Indicators
                    </div>
                    <pre class="trade-detail-body">${decodeAttr(techIndicators)}</pre>
                    ` : ''}
                    ${legsOptionData ? renderOptionDataTable(legsOptionData) : ''}
                    <div class="iv-data-panel" style="margin-top: 10px; font-family: var(--font-mono); font-size: 0.85rem; padding: 8px; background: var(--bg-alt); border-radius: 4px; border: 1px solid var(--border);">
                        <span class="text-muted">Loading Volatility Data...</span>
                    </div>
                </div>
            </td>`;
        row.after(panel);

        if (symbol) {
            const ivPanel = panel.querySelector('.iv-data-panel');
            API.get(`/api/iv-rank?symbol=${symbol}`)
                .then(data => {
                    if (data && data.currentIV !== undefined) {
                        ivPanel.innerHTML = `
                            <div style="font-weight: 600; margin-bottom: 4px; color: var(--text-primary);">Volatility Context (1Y)</div>
                            <div class="flex gap-md flex-wrap">
                                <div><span class="text-muted">IV Rank:</span> <strong>${data.ivRank.toFixed(1)}</strong></div>
                                <div><span class="text-muted">Current IV:</span> <strong>${data.currentIV.toFixed(1)}%</strong></div>
                                <div><span class="text-muted">52W Low:</span> ${data.minIV.toFixed(1)}%</div>
                                <div><span class="text-muted">52W High:</span> ${data.maxIV.toFixed(1)}%</div>
                            </div>
                        `;
                    } else {
                        ivPanel.innerHTML = `<span class="text-muted">No IV data available</span>`;
                    }
                })
                .catch(e => {
                    ivPanel.innerHTML = `<span class="text-muted">IV Data not available</span>`;
                });
        }
    });
}

function showTradeHistoryModal(cardId, tradeRaw, event) {
    if (event) event.stopPropagation();

    let trade = tradeRaw;
    if (typeof tradeRaw === 'string') {
        try {
            trade = JSON.parse(decodeAttr(tradeRaw));
        } catch (e) {
            trade = tradeRaw;
        }
    }

    if (!trade || !trade.symbol) return;

    let overlay = document.getElementById('history-modal-overlay');
    if (overlay) overlay.remove();

    overlay = document.createElement('div');
    overlay.id = 'history-modal-overlay';
    overlay.className = 'modal-overlay';
    overlay.style.zIndex = '1000';

    const symbolEscaped = escapeHtmlContent(trade.symbol || '');
    const expiryEscaped = escapeHtmlContent(trade.expiryDate || '');

    overlay.innerHTML = `
        <div class="modal" id="historyModal" style="max-width: 960px; width: 92%; max-height: 85vh; overflow-y: auto;">
            <div class="flex items-center justify-between" style="margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 12px;">
                <div>
                    <h3 style="margin: 0; font-size: 1.25rem; color: var(--text-primary);">🕒 Similar Historical Trades</h3>
                    <span class="text-muted small">Target: <strong>${symbolEscaped}</strong> (${expiryEscaped})</span>
                </div>
                <button class="btn btn-secondary" onclick="document.getElementById('history-modal-overlay').remove()">✕ Close</button>
            </div>
            <div id="history-modal-body">
                <div class="empty-state"><div class="spinner"></div>Loading similar historical trades...</div>
            </div>
        </div>
    `;

    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.remove();
    });

    document.body.appendChild(overlay);

    const strategyId = cardId ? cardId.replace(/-[0-9a-fA-F-]+$/, '') : '';
    API.post('/api/strategies/history/similar-trades', { strategyId: strategyId, trade: trade, limit: 20 })
        .then(matches => {
            const bodyEl = document.getElementById('history-modal-body');
            if (!bodyEl) return;

            if (!matches || !Array.isArray(matches) || matches.length === 0) {
                bodyEl.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📜</div>No matching historical trades found in database</div>';
                return;
            }

            bodyEl.innerHTML = buildTradeTable(matches, 'history-modal-table', null, true);
            const symbols = [...new Set(matches.map(m => m.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, bodyEl);
            }
        })
        .catch(err => {
            const bodyEl = document.getElementById('history-modal-body');
            if (bodyEl) {
                bodyEl.innerHTML = `<div class="empty-state text-danger">Failed to load trade history: ${escapeHtmlContent(err.message || 'Error')}</div>`;
            }
        });
}

if (typeof document !== 'undefined') {
    document.addEventListener('DOMContentLoaded', initTradeRowClicks);
}

// ── Filter Params Formatter ──

function renderFilterGrid(cfg) {
    if (!cfg || typeof cfg !== 'object') return '';
    const entries = Object.entries(cfg);
    if (entries.length === 0) return '';

    const formatLabel = (key) => key
        .replace(/([A-Z])/g, ' $1')
        .replace(/^./, s => s.toUpperCase())
        .replace(/([a-z])(\d)/g, '$1 $2')
        .trim();

    const formatValue = (v) => {
        if (v === null || v === undefined || v === '') return null;
        if (typeof v === 'boolean') return v ? 'Yes' : 'No';
        if (Array.isArray(v)) return v.length > 0 ? v.join(', ') : null;
        return String(v);
    };

    let html = '';
    let rootHtml = '';
    const nested = [];
    const SKIP_KEYS = new Set(['greeks', 'strategyType', 'securitiesFile', 'securities', 'strategyId']);

    for (const [key, val] of entries) {
        if (SKIP_KEYS.has(key)) continue;
        if (key === 'maxDTE' && val === 2147483647) continue;
        if ((key === 'targetDTE' || key === 'minDTE' || key === 'minReturnOnRisk' || key === 'minReturnOnRiskCAGR') && val === 0) continue;
        if (key === 'technicalFilterSummary' && val) {
            rootHtml += `<div class="config-item" style="grid-column: 1 / -1"><span class="config-item-label" style="color:var(--accent)">🔬 Tech Filters</span><span class="config-item-value">${formatValue(val) || String(val)}</span></div>`;
            continue;
        }
        if (val !== null && typeof val === 'object' && !Array.isArray(val)) {
            nested.push([key, val]);
        } else {
            const formattedVal = formatValue(val);
            if (formattedVal !== null) {
                rootHtml += `<div class="config-item"><span class="config-item-label">${formatLabel(key)}</span><span class="config-item-value">${formattedVal}</span></div>`;
            }
        }
    }
    
    if (rootHtml) {
        html += `<div class="config-grid">${rootHtml}</div>`;
    }

    for (const [key, obj] of nested) {
        const nestedEntries = Object.entries(obj);
        if (nestedEntries.length === 0) continue;
        let nestedHtml = '';
        for (const [k, v] of nestedEntries) {
            const formattedVal = formatValue(v);
            if (formattedVal !== null) {
                nestedHtml += `<div class="config-item"><span class="config-item-label">${formatLabel(k)}</span><span class="config-item-value">${formattedVal}</span></div>`;
            }
        }
        if (nestedHtml) {
            html += `<div class="nested-section"><div class="nested-heading">${formatLabel(key)}</div><div class="config-grid">${nestedHtml}</div></div>`;
        }
    }

    return html;
}

function renderTechFiltersGrid(technicalFilters) {
    if (!technicalFilters) return '';
    if (typeof technicalFilters === 'string') {
        return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters (Preset)</div><div class="config-grid"><div class="config-item"><span class="config-item-label">Preset</span><span class="config-item-value">${technicalFilters}</span></div></div></div>`;
    }
    if (typeof technicalFilters !== 'object') return '';

    const parts = [];
    for (const [key, val] of Object.entries(technicalFilters)) {
        if (Array.isArray(val)) {
            if (val.length > 0) {
                parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val.join(', ')}</span></div>`);
            }
        } else if (val && typeof val === 'object') {
            let condStr = '';
            if (val.conditions && Array.isArray(val.conditions) && val.conditions.length > 0) {
                condStr = val.conditions.join(', ');
            } else if (val.condition !== undefined && val.condition !== null && val.condition !== '') {
                condStr = String(val.condition);
            }
            if (condStr) {
                parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${condStr}</span></div>`);
            }
        } else {
            if (val !== null && val !== undefined && val !== '') {
                parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val}</span></div>`);
            }
        }
    }
    if (parts.length === 0) return '';
    return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters</div><div class="config-grid">${parts.join('')}</div></div>`;
}

function renderFundamentalFiltersGrid(fundamentalFilters) {
    if (!fundamentalFilters || typeof fundamentalFilters !== 'object') return '';
    const parts = [];
    for (const [key, val] of Object.entries(fundamentalFilters)) {
        if (val && typeof val === 'object' && Array.isArray(val.conditions)) {
            if (val.conditions.length > 0) {
                parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val.conditions.join(', ')}</span></div>`);
            }
        } else {
            if (val !== null && val !== undefined && val !== '') {
                parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val}</span></div>`);
            }
        }
    }
    if (parts.length === 0) return '';
    return `<div class="nested-section"><div class="nested-heading">📊 Fundamental Filters</div><div class="config-grid">${parts.join('')}</div></div>`;
}

// ── Dashboard Page Initialization ──

async function initDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadFilterDescriptions();
    await loadOptionsStrategies();
    await loadOptionsResults();
    await checkExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadOptionsStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');

    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
            strategyContainer.innerHTML = strategies.map(s => {
                const parts = [s.name, s.termType, s.securitiesFile].filter(Boolean);
                const displayName = parts.join(' - ');
                return `
                <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="strategy">
                        <span>${displayName}</span>
                    </label>
                    ${s.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(displayName)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>` : ''}
                </div>`;
            }).join('');
            const badge = document.getElementById('strategy-count-badge');
            if (badge) badge.textContent = `(${strategies.length})`;
        } catch (e) {
            strategyContainer.innerHTML = `<span class="text-muted">Failed to load strategies</span>`;
        }
    }
}

async function loadStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');
    const screenerContainer = document.getElementById('screener-checkboxes');

    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
            strategyContainer.innerHTML = strategies.map(s => {
                const parts = [s.name, s.termType, s.securitiesFile].filter(Boolean);
                const displayName = parts.join(' - ');
                return `
                <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="strategy">
                        <span>${displayName}</span>
                    </label>
                    ${s.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(displayName)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>` : ''}
                </div>`;
            }).join('');
            const badge = document.getElementById('strategy-count-badge');
            if (badge) badge.textContent = `(${strategies.length})`;
        } catch (e) {
            strategyContainer.innerHTML = `<span class="text-muted">Failed to load strategies</span>`;
        }
    }

    if (screenerContainer) {
        try {
            const screeners = await API.get('/api/screeners');
            if (screeners.length === 0) {
                screenerContainer.innerHTML = `<span class="text-muted">No screeners configured</span>`;
            } else {
                screenerContainer.innerHTML = screeners.map(s => `
                    <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                        <label class="checkbox-label" style="margin: 0;">
                            <input type="checkbox" value="${s.index}" data-type="screener">
                            <span>${s.name}</span>
                        </label>
                    </div>`).join('');
            }
            const badge = document.getElementById('screener-count-badge');
            if (badge) badge.textContent = `(${screeners.length})`;
        } catch (e) {
            screenerContainer.innerHTML = `<span class="text-muted">Failed to load screeners</span>`;
        }
    }
}

async function loadOptionsResults() {
    const optionsContainer = document.getElementById('results-container');
    if (!optionsContainer) return;

    resetDashboardFilter('options');

    try {
        const optionResults = await API.get('/api/results');

        optionsContainer.innerHTML = '';
        if (!optionResults || optionResults.length === 0) {
            optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
            hideDashboardFilterBar('options');
        } else {
            renderTermGroups(optionsContainer, optionResults);
            fetchAndInjectTodayPerformance(optionsContainer);
            showDashboardFilterBar('options');
        }
    } catch (e) {
        optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        hideDashboardFilterBar('options');
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

async function loadResults() {
    const optionsContainer = document.getElementById('results-container');
    const screenerContainer = document.getElementById('screener-results-container');
    
    if (!optionsContainer && !screenerContainer) return;

    try {
        const [optionResults, screenerResults] = await Promise.all([
            optionsContainer ? API.get('/api/results') : Promise.resolve([]),
            screenerContainer ? API.get('/api/results/screeners').catch(() => []) : Promise.resolve([])
        ]);

        if (optionsContainer) {
            optionsContainer.innerHTML = '';
            if (!optionResults || optionResults.length === 0) {
                optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
            } else {
                renderTermGroups(optionsContainer, optionResults);
                fetchAndInjectTodayPerformance(optionsContainer);
            }
        }

        if (screenerContainer) {
            screenerContainer.innerHTML = '';
            if (!screenerResults || screenerResults.length === 0) {
                screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet.</div>';
            } else {
                for (const r of screenerResults) {
                    screenerContainer.appendChild(buildScreenerCard(r));
                }
            }
        }
    } catch (e) {
        if (optionsContainer) optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        if (screenerContainer) screenerContainer.innerHTML = '';
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

async function checkExecutionStatus() {
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
                loadResults();
                showToast('Execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

async function executeSelected() {
    const checkedStrategies = document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]:checked');
    const checkedScreeners = document.querySelectorAll('#screener-checkboxes input[type="checkbox"]:checked');
    const strategyIndices = Array.from(checkedStrategies).map(c => parseInt(c.value));
    const screenerIndices = Array.from(checkedScreeners).map(c => parseInt(c.value));
    if (strategyIndices.length === 0 && screenerIndices.length === 0) {
        showToast('Select at least one strategy or screener', 'error');
        return;
    }
    try {
        setDashboardBusy(true);
        const res = await API.post('/api/execute', { strategyIndices, screenerIndices });
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setDashboardBusy(false);
            loadResults();
            showToast('Execution completed!');
        });
    } catch (e) {
        setDashboardBusy(false);
        showToast(e.message, 'error');
    }
}

async function cancelExecution() {
    try {
        await API.post('/api/cancel');
        showToast('Cancellation requested');
    } catch (e) {
        showToast(e.message, 'error');
    }
}

// ── Market Status Live Injection ──

async function fetchAndRenderMarketStatus() {
    const mainContent = document.querySelector('.main-content');
    if (!mainContent) return;

    const statusContainer = document.createElement('div');
    statusContainer.className = 'market-status-container flex gap-sm';
    statusContainer.style.position = 'absolute';
    statusContainer.style.top = '32px';
    statusContainer.style.right = '32px';

    mainContent.style.position = 'relative';
    mainContent.appendChild(statusContainer);

    function statusColor(s) {
        if (s === 'OPEN')       return 'var(--success)';
        if (s === 'PRE_MARKET' || s === 'POST_MARKET') return '#f5a623';
        return 'var(--text-muted)';
    }

    function statusLabel(s) {
        if (s === 'PRE_MARKET')  return 'PRE MARKET';
        if (s === 'POST_MARKET') return 'POST MARKET';
        return s;
    }

    try {
        const data = await API.get('/api/market-status');
        const eq  = data.equityStatus  || 'CLOSED';
        const opt = data.optionsStatus || 'CLOSED';
        const equityColor  = statusColor(eq);
        const optionsColor = statusColor(opt);

        statusContainer.innerHTML = `
            <span class="status-badge" style="border: 1px solid var(--border); color: ${equityColor}">
                <span class="status-dot" style="background:${equityColor};"></span>
                Equity: ${statusLabel(eq)}
            </span>
            <span class="status-badge" style="border: 1px solid var(--border); color: ${optionsColor}">
                <span class="status-dot" style="background:${optionsColor};"></span>
                Options: ${statusLabel(opt)}
            </span>
        `;
    } catch (e) {
        statusContainer.innerHTML = `<span class="status-badge" style="color: var(--text-muted); border: 1px solid var(--border)">Market Status Offline</span>`;
        console.error("Could not fetch market status", e);
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

// ── Dashboard Results Filter Bar ──

const DASHBOARD_FILTER_CONFIG = {
    options: {
        barId: 'options-filter-bar',
        columnSelectId: 'options-filter-column',
        textInputId: 'options-filter-text',
        dateGroupId: 'options-filter-date-group',
        dateModeId: 'options-filter-date-mode',
        dateInputId: 'options-filter-date',
        searchBtnId: 'options-filter-btn',
        clearBtnId: 'options-filter-clear',
        summaryId: 'options-filter-summary',
        containerId: 'results-container',
        supportsDate: true
    },
    screeners: {
        barId: 'screeners-filter-bar',
        columnSelectId: 'screeners-filter-column',
        textInputId: 'screeners-filter-text',
        dateGroupId: null,
        dateModeId: null,
        dateInputId: null,
        searchBtnId: 'screeners-filter-btn',
        clearBtnId: 'screeners-filter-clear',
        summaryId: 'screeners-filter-summary',
        containerId: 'screener-results-container',
        supportsDate: false
    }
};

function showDashboardFilterBar(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;
    const bar = document.getElementById(cfg.barId);
    if (bar) bar.style.display = '';
}

function hideDashboardFilterBar(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;
    const bar = document.getElementById(cfg.barId);
    if (bar) bar.style.display = 'none';
}

function resetDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateGroup = cfg.dateGroupId ? document.getElementById(cfg.dateGroupId) : null;
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const searchBtn = document.getElementById(cfg.searchBtnId);
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);

    if (colSelect) colSelect.value = 'ticker';
    if (textInput) { textInput.value = ''; textInput.style.display = 'inline-block'; }
    if (dateGroup) dateGroup.style.display = 'none';
    if (dateInput) dateInput.value = '';
    if (searchBtn) searchBtn.style.display = 'inline-block';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';
}

function onFilterColumnChange(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateGroup = cfg.dateGroupId ? document.getElementById(cfg.dateGroupId) : null;
    const searchBtn = document.getElementById(cfg.searchBtnId);
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);

    const selected = colSelect ? colSelect.value : '';

    if (!selected) {
        if (textInput) { textInput.value = ''; textInput.style.display = 'none'; }
        if (dateGroup) dateGroup.style.display = 'none';
        if (searchBtn) searchBtn.style.display = 'none';
        if (clearBtn) clearBtn.style.display = 'none';
        if (summary) summary.innerHTML = '';
        return;
    }

    const isDate = (selected === 'expiry');

    if (textInput) {
        textInput.style.display = isDate ? 'none' : '';
        if (!isDate) { textInput.value = ''; setTimeout(() => textInput.focus(), 50); }
    }
    if (dateGroup) {
        dateGroup.style.display = isDate ? '' : 'none';
    }
    if (searchBtn) searchBtn.style.display = '';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';
}

function getDashboardItemValue(item, columnKey) {
    switch (columnKey) {
        case 'ticker':
            return (item.symbol || '').toLowerCase();
        case 'expiry':
            return (item.expiryDate || '').substring(0, 10);
        default:
            return '';
    }
}

function matchesDashboardFilter(item, columnKey, filterValue, dateMode) {
    const itemValue = getDashboardItemValue(item, columnKey);
    if (!itemValue) return false;

    if (columnKey === 'expiry') {
        if (!filterValue) return false;
        return dateMode === 'after'
            ? itemValue >= filterValue
            : itemValue <= filterValue;
    }

    return itemValue.includes(filterValue.toLowerCase().trim());
}

function applyDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const dateModeSelect = cfg.dateModeId ? document.getElementById(cfg.dateModeId) : null;
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);
    const container = document.getElementById(cfg.containerId);

    const columnKey = colSelect ? colSelect.value : '';
    if (!columnKey) {
        if (summary) summary.innerHTML = '<span class="filter-no-match">Please select a column to filter by.</span>';
        return;
    }

    const isDate = (columnKey === 'expiry');
    const filterValue = isDate
        ? (dateInput ? dateInput.value : '')
        : (textInput ? textInput.value.trim() : '');

    if (!filterValue) {
        if (summary) summary.innerHTML = '<span class="filter-no-match">Please enter a value to search for.</span>';
        return;
    }

    const dateMode = dateModeSelect ? dateModeSelect.value : 'before';

    const cards = container ? container.querySelectorAll('.card') : [];
    let totalMatches = 0;
    let cardsWithMatches = 0;
    const termGroupsWithMatches = new Set();

    cards.forEach(card => {
        const cardId = card.querySelector('.card-header')?.dataset?.target;
        if (!cardId) return;

        const rawData = window.tradeDataMap[cardId];
        if (!rawData || !Array.isArray(rawData)) return;

        const matchingSymbols = new Set();
        rawData.forEach(item => {
            if (matchesDashboardFilter(item, columnKey, filterValue, dateMode)) {
                matchingSymbols.add(getDashboardItemValue(item, 'ticker'));
            }
        });

        const rows = card.querySelectorAll('tbody .trade-row');
        let cardMatchCount = 0;

        rows.forEach(row => {
            const sym = (row.dataset.symbol || row.querySelector('td strong')?.textContent || '').toLowerCase();

            let isMatch;
            if (columnKey === 'expiry') {
                const rawItem = rawData.find(d => (d.symbol || '').toLowerCase() === sym);
                isMatch = rawItem ? matchesDashboardFilter(rawItem, columnKey, filterValue, dateMode) : false;
            } else {
                isMatch = matchingSymbols.has(sym);
            }

            row.classList.toggle('filter-hidden', !isMatch);
            row.classList.toggle('filter-match', isMatch);
            if (isMatch) cardMatchCount++;
        });

        const detailPanels = card.querySelectorAll('.trade-detail-panel');
        detailPanels.forEach(panel => panel.remove());

        const contentEl = document.getElementById(`content-${cardId}`);
        const arrowEl = document.getElementById(`arrow-${cardId}`);

        if (cardMatchCount > 0) {
            if (contentEl && !contentEl.classList.contains('open')) {
                contentEl.classList.add('open');
                if (arrowEl) arrowEl.classList.add('open');
            }
            
            const termGroupBody = card.closest('.term-group-body');
            if (termGroupBody) {
                termGroupsWithMatches.add(termGroupBody.id);
                if (termGroupBody.classList.contains('hidden')) {
                    termGroupBody.classList.remove('hidden');
                    const termArrowEl = document.getElementById(`arrow-${termGroupBody.id}`);
                    if (termArrowEl) termArrowEl.classList.add('open');
                }
            }

            totalMatches += cardMatchCount;
            cardsWithMatches++;
        } else {
            if (contentEl) contentEl.classList.remove('open');
            if (arrowEl) arrowEl.classList.remove('open');
        }
    });

    if (container) {
        container.querySelectorAll('.term-group-body').forEach(body => {
            if (!termGroupsWithMatches.has(body.id)) {
                body.classList.add('hidden');
                const arrowEl = document.getElementById(`arrow-${body.id}`);
                if (arrowEl) arrowEl.classList.remove('open');
            }
        });
    }

    if (clearBtn) clearBtn.style.display = '';
    if (summary) {
        if (totalMatches === 0) {
            summary.innerHTML = `<span class="filter-no-match">No matches found for "${escapeHtmlContent(filterValue)}".</span>`;
        } else {
            const stratWord = cardsWithMatches === 1 ? 'strategy' : 'strategies';
            summary.innerHTML = `<span class="filter-match-count">${totalMatches} match${totalMatches !== 1 ? 'es' : ''}</span> across <span class="filter-match-count">${cardsWithMatches} ${stratWord}</span>.`;
        }
    }
}

function clearDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const textInput = document.getElementById(cfg.textInputId);
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);
    const container = document.getElementById(cfg.containerId);

    if (textInput) textInput.value = '';
    if (dateInput) dateInput.value = '';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';

    if (container) {
        container.querySelectorAll('.trade-row.filter-hidden').forEach(row => {
            row.classList.remove('filter-hidden', 'filter-match');
        });
        container.querySelectorAll('.trade-row.filter-match').forEach(row => {
            row.classList.remove('filter-match');
        });

        container.querySelectorAll('.card').forEach(card => {
            const cardId = card.querySelector('.card-header')?.dataset?.target;
            if (!cardId) return;
            const contentEl = document.getElementById(`content-${cardId}`);
            const arrowEl = document.getElementById(`arrow-${cardId}`);
            if (contentEl) contentEl.classList.remove('open');
            if (arrowEl) arrowEl.classList.remove('open');
        });

        container.querySelectorAll('.term-group-body').forEach(body => {
            body.classList.add('hidden');
            const arrowEl = document.getElementById(`arrow-${body.id}`);
            if (arrowEl) arrowEl.classList.remove('open');
        });

        container.querySelectorAll('.trade-detail-panel').forEach(p => p.remove());
        container.querySelectorAll('.trade-row.selected').forEach(r => r.classList.remove('selected'));
    }
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    Object.assign(global, utils, authApi);

    module.exports = {
        buildResultCard,
        renderTermGroups,
        confirmDeleteCustomResult,
        deleteCustomResult,
        promptDeleteCustomScreenerResult,
        deleteCustomScreenerResult,
        buildScreenerCard,
        buildScreenerTable,
        buildDropScreenerTable,
        buildTradeTable,
        handleTableSort,
        injectTodayPerformance,
        fetchAndInjectTodayPerformance,
        renderOptionDataTable,
        initTradeRowClicks,
        showTradeHistoryModal,
        renderFilterGrid,
        renderTechFiltersGrid,
        renderFundamentalFiltersGrid,
        initDashboard,
        loadOptionsStrategies,
        loadStrategies,
        loadOptionsResults,
        loadResults,
        checkExecutionStatus,
        executeSelected,
        cancelExecution,
        fetchAndRenderMarketStatus,
        DASHBOARD_FILTER_CONFIG,
        showDashboardFilterBar,
        hideDashboardFilterBar,
        resetDashboardFilter,
        onFilterColumnChange,
        getDashboardItemValue,
        matchesDashboardFilter,
        applyDashboardFilter,
        clearDashboardFilter
    };
}
