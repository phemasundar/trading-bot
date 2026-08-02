/**
 * Trading Bot — Options Dashboard View (index.html)
 */

async function initDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadFilterDescriptions();
    await loadOptionsStrategies();
    updateMarketStatusBadge();
    await loadDashboardResults();
    await checkExecutionStatus();
    initTradeRowClicks();
}

async function loadFilterDescriptions() {
    try {
        const res = await fetch('/filter-descriptions.json');
        if (res.ok) {
            FILTER_DESCRIPTIONS = await res.json();
        }
    } catch (e) {
        console.warn('Could not load filter descriptions:', e);
    }
}

async function loadOptionsStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');
    if (strategyContainer) {
        try {
            const strategies = await API.getStrategies();
            strategyContainer.innerHTML = strategies.map(s => {
                const displayName = s.securitiesFile ? `${s.name} - ${s.securitiesFile}` : s.name;
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

async function checkExecutionStatus() {
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
                loadDashboardResults();
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
        const res = await API.executeStrategies(strategyIndices, screenerIndices);
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setDashboardBusy(false);
            loadDashboardResults();
            showToast('Execution completed!');
        });
    } catch (e) {
        setDashboardBusy(false);
        showToast(e.message, 'error');
    }
}

async function cancelExecution() {
    try {
        await API.cancelExecution();
        showToast('Cancellation requested');
    } catch (e) {
        showToast(e.message, 'error');
    }
}

function selectAll(check) {
    document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]')
        .forEach(cb => cb.checked = check);
    if (check) {
        const body = document.getElementById('strategy-section');
        const arrow = document.getElementById('arrow-strategy-section');
        if (body && !body.classList.contains('open')) {
            body.classList.add('open');
            if (arrow) arrow.classList.add('open');
        }
    }
}

async function loadDashboardResults() {
    const container = document.getElementById('results-container');
    if (!container) return;

    try {
        const results = await API.getLatestResults();
        const customResults = await API.getRecentCustomResults();

        container.innerHTML = '';

        if ((!results || results.length === 0) && (!customResults || customResults.length === 0)) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No strategy execution results found. Execute a strategy to get started.</div>';
            return;
        }

        if (results && results.length > 0) {
            results.forEach(res => {
                container.appendChild(buildResultCard(res, 'Standard'));
            });
        }

        if (customResults && customResults.length > 0) {
            customResults.forEach(res => {
                container.appendChild(buildResultCard(res, 'Custom'));
            });
        }

        await fetchAndInjectTodayPerformance(container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger"><div class="empty-state-icon">⚠️</div>Failed to load results: ${e.message}</div>`;
    }
}

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    const hasTrades = result.trades && result.trades.length > 0;
    card.className = hasTrades ? 'card' : 'card disabled';

    const cardId = (result.strategyId || 'card-' + Math.random()).replace(/\s+/g, '-');
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
            if (cfg && cfg.securitiesFile) {
                displayName += ` - ${cfg.securitiesFile}`;
            }
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
            ${buildTradeTable(result.trades || [], cardId)}
        </div>`;

    window.tradeDataMap[cardId] = result.trades || [];

    if (hasTrades) {
        card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));
    } else {
        card.querySelector('.card-header').style.cursor = 'default';
        if (card.querySelector('.info-btn')) {
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

function toggleCard(id) {
    const content = document.getElementById(`content-${id}`);
    const arrow = document.getElementById(`arrow-${id}`);
    if (!content) return;
    content.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
}

function buildTradeTable(trades, cardId = null) {
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

        let rorCagr = t.returnOnRiskCAGR;
        if (rorCagr == null && t.returnOnRisk != null && t.dte > 0 && t.maxLoss > 0) {
            const rawRoR = t.returnOnRisk / 100.0;
            rorCagr = (Math.pow(1.0 + rawRoR, 365.0 / t.dte) - 1.0) * 100.0;
        }

        html += `<tr class="trade-row" data-details="${detailsEscaped}" data-tech-indicators="${techIndicatorsAttr}" data-legs-option-data="${legsAttr}" data-symbol="${escapeAttr(sym)}">
            <td><strong>${sym}</strong></td>
            <td><span class="text-muted" title="${t.companyName || ''}">${formatCompanyName(t.companyName)}</span></td>
            <td class="text-mono">$${(t.underlyingPrice || 0).toFixed(2)}</td>
            <td class="today-perf" data-symbol="${escapeAttr(sym)}"><span class="text-muted">--</span></td>
            <td>${formatLegs(t)}</td>
            <td>${formatExpiryDate(t.expiryDate)} <span class="text-muted">(${t.dte || 0}d)</span></td>
            <td>${creditStr}</td>
            <td class="text-danger">$${(t.maxLoss || 0).toFixed(2)}</td>
            <td>$${(t.netExtrinsicValue || 0).toFixed(2)} <span class="text-muted">(${(t.annualizedNetExtrinsicValueToCapitalPercentage || t.anulizedNetExtrinsicValueToCapitalPercentage || 0).toFixed(1)}%)</span></td>
            <td>${formatBreakeven(t)}</td>
            <td class="${rorClass}">${(t.returnOnRisk || 0).toFixed(1)}%${rorCagr != null ? ` <span class="text-muted">(${rorCagr.toFixed(1)}% CAGR)</span>` : ''}</td>
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

    if (state.column && state.direction) {
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
                    valA = a.annualizedNetExtrinsicValueToCapitalPercentage || a.anulizedNetExtrinsicValueToCapitalPercentage || 0;
                    valB = b.annualizedNetExtrinsicValueToCapitalPercentage || b.anulizedNetExtrinsicValueToCapitalPercentage || 0;
                    break;
                case 'breakeven':
                    const hasCagr = a.breakevenCAGR != null && b.breakevenCAGR != null;
                    valA = hasCagr ? a.breakevenCAGR : (a.breakEvenPercent || 0);
                    valB = hasCagr ? b.breakevenCAGR : (b.breakEvenPercent || 0);
                    break;
                case 'ror':
                    const getRoRCagr = (x) => {
                        if (x.returnOnRiskCAGR != null) return x.returnOnRiskCAGR;
                        if (x.returnOnRisk != null && x.dte > 0 && x.maxLoss > 0) {
                            const rawRoR = x.returnOnRisk / 100.0;
                            return (Math.pow(1.0 + rawRoR, 365.0 / x.dte) - 1.0) * 100.0;
                        }
                        return x.maxReturnOnRiskPercentage || x.returnOnRisk || 0;
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
            contentDiv.innerHTML = buildTradeTable(data, cardId);
            const symbols = [...new Set(data.map(t => t.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, contentDiv);
            }
        }
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
            const sym = cell.dataset.symbol;
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

function formatLegs(trade) {
    if (trade.legs && trade.legs.length > 0) {
        return trade.legs.map(l => {
            const action = l.action || '';
            const type = l.optionType || '';
            const strike = l.strike ? l.strike.toFixed(0) : '';
            const qty = (l.quantity && l.quantity > 1) ? `${l.quantity}x ` : '';
            return `<span class="leg-chip ${action === 'SELL' ? 'leg-sell' : 'leg-buy'}">${action} ${qty}${strike} ${type}</span>`;
        }).join('<br>');
    }
    return '-';
}

function formatBreakeven(trade) {
    const parts = [];
    if (trade.breakEvenPrice) {
        let be = `$${trade.breakEvenPrice.toFixed(2)}`;
        if (trade.breakEvenPercent) {
            be += ` <span class="text-muted">(${trade.breakEvenPercent.toFixed(1)}%)</span>`;
        }
        parts.push(be);
    }
    if (trade.upperBreakEvenPrice && Math.abs(trade.upperBreakEvenPrice - (trade.breakEvenPrice || 0)) > 0.01) {
        let ube = `$${trade.upperBreakEvenPrice.toFixed(2)}`;
        if (trade.upperBreakEvenPercent) {
            ube += ` <span class="text-muted">(${trade.upperBreakEvenPercent.toFixed(1)}%)</span>`;
        }
        parts.push(ube);
    }
    if (trade.breakevenCAGR != null) {
        parts.push(`CAGR: ${trade.breakevenCAGR.toFixed(1)}%`);
    }
    return parts.join('<br>') || '-';
}

function formatExpiryDate(dateStr) {
    if (!dateStr) return '';
    return dateStr.substring(0, 10);
}

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
        if (v === null || v === undefined) return '—';
        if (typeof v === 'boolean') return v ? 'Yes' : 'No';
        if (Array.isArray(v)) return v.join(', ') || '—';
        return String(v);
    };

    let html = '<div class="config-grid">';
    const nested = [];
    const SKIP_KEYS = new Set(['greeks', 'strategyType', 'securitiesFile', 'securities']);

    for (const [key, val] of entries) {
        if (SKIP_KEYS.has(key)) continue;
        if (key === 'maxDTE' && val === 2147483647) continue;
        if ((key === 'targetDTE' || key === 'minDTE' || key === 'minReturnOnRisk' || key === 'minReturnOnRiskCAGR') && val === 0) continue;
        if (key === 'technicalFilterSummary' && val) {
            html += `<div class="config-item" style="grid-column: 1 / -1"><span class="config-item-label" style="color:var(--accent)">🔬 Tech Filters</span><span class="config-item-value">${formatValue(val)}</span></div>`;
            continue;
        }
        if (val !== null && typeof val === 'object' && !Array.isArray(val)) {
            nested.push([key, val]);
        } else {
            html += `<div class="config-item"><span class="config-item-label">${formatLabel(key)}</span><span class="config-item-value">${formatValue(val)}</span></div>`;
        }
    }
    html += '</div>';

    for (const [key, obj] of nested) {
        const nestedEntries = Object.entries(obj);
        if (nestedEntries.length === 0) continue;
        html += `<div class="nested-section"><div class="nested-heading">${formatLabel(key)}</div><div class="config-grid">`;
        for (const [k, v] of nestedEntries) {
            html += `<div class="config-item"><span class="config-item-label">${formatLabel(k)}</span><span class="config-item-value">${formatValue(v)}</span></div>`;
        }
        html += '</div></div>';
    }

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

function escapeHtmlContent(str) {
    return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function decodeAttr(str) {
    return (str || '').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
}

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
        await API.deleteCustomResult(resultId);
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
