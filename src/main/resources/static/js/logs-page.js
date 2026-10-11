/**
 * Trading Bot — Execution Filter Logs
 * Filter count logs page rendering, grouping, sorting, and polling logic (logs.html).
 */

let _logsPollInterval = null;
let currentLogsData = [];
window.logSortColumn = null;
window.logSortAsc = true;

window.handleLogSort = function(column, event) {
    if (event) event.stopPropagation();
    if (window.logSortColumn === column) {
        if (window.logSortAsc) {
            window.logSortAsc = false;
        } else {
            window.logSortColumn = null;
            window.logSortAsc = true;
        }
    } else {
        window.logSortColumn = column;
        window.logSortAsc = true;
    }
    const container = document.getElementById('logsContainer');
    if (container && currentLogsData.length > 0) {
        renderLogGroups(currentLogsData, container);
    }
};

async function initLogsPage() {
    await loadLogs();
    startLogPolling();
}

function startLogPolling() {
    clearInterval(_logsPollInterval);
    _logsPollInterval = setInterval(async () => {
        try {
            const status = await API.get('/api/status');
            const badge = document.getElementById('executionBadge');
            const label = document.getElementById('badgeLabel');
            if (status.running) {
                if (badge) badge.classList.remove('hidden');
                if (label) label.textContent = status.currentTask || 'Running...';
                await loadLogs();
            } else {
                if (badge) badge.classList.add('hidden');
            }
            if (status.alerts && status.alerts.length > 0) showErrorPanel(status.alerts);
        } catch (e) { /* ignore */ }
    }, 3000);
}

async function loadLogs() {
    const container = document.getElementById('logsContainer');
    const empty = document.getElementById('logsEmpty');
    if (!container) return;
    try {
        const logs = await API.get('/api/filter-logs');
        currentLogsData = logs || [];
        if (currentLogsData.length === 0) {
            container.innerHTML = '';
            if (empty) empty.style.display = 'flex';
            return;
        }
        if (empty) empty.style.display = 'none';
        renderLogGroups(currentLogsData, container);
    } catch (e) {
        container.innerHTML = '<div class="empty-state text-danger">Failed to load logs: ' + e.message + '</div>';
    }
}

async function clearLogs() {
    try {
        await API.post('/api/filter-logs/clear');
        await loadLogs();
        showToast('Logs cleared.');
    } catch (e) {
        showToast('Failed to clear logs: ' + e.message, 'error');
    }
}

function isStockLevelEntry(e) {
    if (e.expiry) return false;
    const stage = e.filterStage || '';
    if (stage.startsWith('Technical:') || stage.startsWith('Fundamental:')) return true;
    if (stage === 'Technical Data Error' || stage === 'Technical Filter') return true;
    if (stage === 'Quote Data Missing' || stage === 'Price/52W High Invalid' || stage === 'Price History Missing') return true;
    if (stage.startsWith('Insufficient Bars') || stage === 'Reference Price Invalid') return true;
    if (stage.includes('HV_RANK') || stage.includes('IV_RANK') || stage.includes('IV_PERCENTILE') || stage.includes('HISTORICAL_VOLATILITY')) {
        return true;
    }
    return false;
}

function aggregateStockScreening(stockEntries) {
    const stageMap = new Map();
    const allSymbols = new Set();
    const failedMap = new Map();
    const stageSymbols = new Map();

    for (const e of stockEntries) {
        if (e.symbol) {
            allSymbols.add(e.symbol);
            if (e.tradesOut === 0) {
                if (!failedMap.has(e.symbol)) {
                    failedMap.set(e.symbol, {
                        stage: e.filterStage || '',
                        details: e.details || ''
                    });
                }
            }
        }

        const stage = e.filterStage || 'Stock Screening';
        if (!stageMap.has(stage)) {
            stageMap.set(stage, {
                filterStage: stage,
                tradesIn: 0,
                tradesOut: 0
            });
            stageSymbols.set(stage, new Set());
        }

        const s = stageMap.get(stage);
        const seen = stageSymbols.get(stage);

        if (e.symbol) {
            if (!seen.has(e.symbol)) {
                seen.add(e.symbol);
                s.tradesIn += (e.tradesIn != null ? e.tradesIn : 1);
                s.tradesOut += (e.tradesOut != null ? e.tradesOut : 0);
            }
        } else {
            s.tradesIn += (e.tradesIn != null ? e.tradesIn : 1);
            s.tradesOut += (e.tradesOut != null ? e.tradesOut : 0);
        }
    }

    const stages = Array.from(stageMap.values());
    const qualifiedSymbols = Array.from(allSymbols).filter(sym => !failedMap.has(sym)).sort();
    const eliminatedList = Array.from(failedMap.entries())
        .map(([symbol, failInfo]) => ({
            symbol,
            stage: failInfo.stage,
            details: failInfo.details
        }))
        .sort((a, b) => a.symbol.localeCompare(b.symbol));

    return {
        allSymbols,
        totalEvaluated: allSymbols.size,
        stages,
        qualifiedSymbols,
        eliminatedList
    };
}

function renderStockScreeningSection(screeningData, stratSlug, openScreening) {
    const elimId = 'elim-' + stratSlug;
    const isElimOpen = openScreening ? openScreening.has(elimId) : false;

    const tableHtml = buildLogFilterTable(screeningData.stages);

    let qualifiedBadgesHtml = '';
    if (screeningData.qualifiedSymbols.length > 0) {
        qualifiedBadgesHtml = screeningData.qualifiedSymbols.map(sym =>
            '<span class="log-stock-badge passed" onclick="focusSymbol(\'' + stratSlug + '\', \'' + sym.replace(/\W+/g, '-') + '\')" title="Click to view symbol options analysis">' +
                '✓ ' + escapeHtml(sym) +
            '</span>'
        ).join(' ');
    } else {
        qualifiedBadgesHtml = '<span class="text-muted" style="font-size:0.8rem">None</span>';
    }

    let eliminatedSectionHtml = '';
    if (screeningData.eliminatedList.length > 0) {
        eliminatedSectionHtml =
            '<div class="log-eliminated-block">' +
                '<div class="log-eliminated-header" onclick="toggleLogGroup(\'' + elimId + '\')">' +
                    '<div class="flex items-center gap-sm">' +
                        '<span class="card-arrow' + (isElimOpen ? ' open' : '') + '" id="arrow-' + elimId + '">&#9658;</span>' +
                        '<span class="log-eliminated-title">Eliminated Stocks (' + screeningData.eliminatedList.length + ')</span>' +
                        '<span class="card-badge" style="font-size:0.68rem;background:rgba(239,68,68,0.12);color:#ef4444">Filtered Out</span>' +
                    '</div>' +
                '</div>' +
                '<div class="log-eliminated-body' + (isElimOpen ? ' open' : '') + '" id="' + elimId + '">' +
                    '<table class="data-table log-filter-table log-eliminated-table">' +
                        '<thead><tr>' +
                            '<th style="width:110px">SYMBOL</th>' +
                            '<th>FAILED FILTER</th>' +
                            '<th>EVALUATED DETAILS</th>' +
                        '</tr></thead>' +
                        '<tbody>' +
                        screeningData.eliminatedList.map(item =>
                            '<tr>' +
                                '<td><strong class="log-symbol-name" style="font-size:0.82rem">' + escapeHtml(item.symbol) + '</strong></td>' +
                                '<td class="text-danger">' + escapeHtml(item.stage) + '</td>' +
                                '<td class="text-muted" style="font-size:0.78rem">' + (item.details ? escapeHtml(item.details) : '&mdash;') + '</td>' +
                            '</tr>'
                        ).join('') +
                        '</tbody>' +
                    '</table>' +
                '</div>' +
            '</div>';
    }

    return '<div class="log-screening-wrapper">' +
        '<div class="log-screening-card">' +
            '<div class="log-screening-header">' +
                '<div class="flex items-center gap-sm">' +
                    '<span class="log-screening-icon">📊</span>' +
                    '<span class="log-screening-title">Stock Screening Funnel</span>' +
                    '<span class="card-badge">' + screeningData.totalEvaluated + ' symbol' + (screeningData.totalEvaluated !== 1 ? 's' : '') + ' evaluated</span>' +
                    '<span class="card-badge" style="' + (screeningData.qualifiedSymbols.length > 0 ? 'background:rgba(16,185,129,0.15);color:var(--success)' : 'background:rgba(239,68,68,0.15);color:#ef4444') + '">' +
                        screeningData.qualifiedSymbols.length + ' passed' +
                    '</span>' +
                '</div>' +
            '</div>' +
            '<div class="log-screening-table-container">' +
                tableHtml +
            '</div>' +
            '<div class="log-screening-footer">' +
                '<div class="log-qualified-row">' +
                    '<span class="log-footer-label">Qualified Stocks (' + screeningData.qualifiedSymbols.length + '):</span> ' +
                    '<div class="log-badges-wrap">' + qualifiedBadgesHtml + '</div>' +
                '</div>' +
                eliminatedSectionHtml +
            '</div>' +
        '</div>' +
    '</div>';
}

function renderStrategyGroupContent(stratEntries, strategyName, openSymbols, openExpiries, openScreening) {
    const stratSlug = (strategyName || 'unknown').replace(/\W+/g, '-');
    const stockEntries = stratEntries.filter(isStockLevelEntry);
    const optionEntries = stratEntries.filter(e => !isStockLevelEntry(e));

    let html = '';

    if (stockEntries.length > 0) {
        const screeningData = aggregateStockScreening(stockEntries);
        html += renderStockScreeningSection(screeningData, stratSlug, openScreening);

        const qualifiedSet = new Set(screeningData.qualifiedSymbols);
        const allScreenedSet = screeningData.allSymbols;

        const qualifiedOptionEntries = optionEntries.filter(e => !e.symbol || qualifiedSet.has(e.symbol) || !allScreenedSet.has(e.symbol));
        const qualSymbolsWithOptionLogs = Array.from(new Set(qualifiedOptionEntries.map(e => e.symbol).filter(Boolean)));

        if (qualSymbolsWithOptionLogs.length > 0) {
            html += '<div class="log-section-divider">' +
                '<span class="log-section-divider-title">⚡ Options Analysis (' + qualSymbolsWithOptionLogs.length + ' Qualified Symbol' + (qualSymbolsWithOptionLogs.length !== 1 ? 's' : '') + ')</span>' +
            '</div>';
            html += renderLogSymbolGroups(qualifiedOptionEntries, strategyName, openSymbols, openExpiries);
        } else if (screeningData.qualifiedSymbols.length === 0) {
            html += '<div class="log-empty-qual-msg">' +
                '<span>ℹ No stocks qualified for options analysis.</span>' +
            '</div>';
        } else {
            html += '<div class="log-empty-qual-msg">' +
                '<span>ℹ No option trades were generated for qualified stocks.</span>' +
            '</div>';
        }
    } else {
        html += renderLogSymbolGroups(stratEntries, strategyName, openSymbols, openExpiries);
    }

    return html;
}

function renderLogGroups(entries, container) {
    const groups = {};
    for (const e of entries) {
        const key = e.strategyName || 'Unknown Strategy';
        if (!groups[key]) groups[key] = [];
        groups[key].push(e);
    }
    const openGroups = new Set();
    container.querySelectorAll('.log-group-body.open').forEach(function(el) { openGroups.add(el.id); });
    const openSymbols = new Set();
    container.querySelectorAll('.log-symbol-body.open').forEach(function(el) { openSymbols.add(el.id); });
    const openExpiries = new Set();
    container.querySelectorAll('.log-expiry-body.open').forEach(function(el) { openExpiries.add(el.id); });
    const openScreening = new Set();
    container.querySelectorAll('.log-eliminated-body.open').forEach(function(el) { openScreening.add(el.id); });

    container.innerHTML = '';
    for (const [strategyName, stratEntries] of Object.entries(groups)) {
        const groupId = 'log-group-' + strategyName.replace(/\W+/g, '-');
        const isOpen = openGroups.has(groupId);
        const block = document.createElement('div');
        block.className = 'log-group';

        const stockEntries = stratEntries.filter(isStockLevelEntry);
        const symbols = Array.from(new Set(stratEntries.map(function(e) { return e.symbol; }))).filter(Boolean);
        const symbolCount = symbols.length;
        const stepCount = stratEntries.length;

        let badgesHtml = '<span class="card-badge">' + symbolCount + ' symbol' + (symbolCount !== 1 ? 's' : '') + '</span>';
        if (stockEntries.length > 0) {
            const screeningData = aggregateStockScreening(stockEntries);
            const qualCount = screeningData.qualifiedSymbols.length;
            badgesHtml += '<span class="card-badge" style="' +
                (qualCount > 0 ? 'background:rgba(16,185,129,0.15);color:var(--success)' : 'background:rgba(239,68,68,0.15);color:#ef4444') + '">' +
                qualCount + ' qualified</span>';
        }
        badgesHtml += '<span class="card-badge" style="background:rgba(99,102,241,0.15);color:var(--primary)">' + stepCount + ' filter steps</span>';

        block.innerHTML =
            '<div class="log-group-header" onclick="toggleLogGroup(\'' + groupId + '\')">' +
                '<div class="flex items-center gap-sm">' +
                    '<span class="card-arrow' + (isOpen ? ' open' : '') + '" id="arrow-' + groupId + '">&#9658;</span>' +
                    '<span class="log-group-name">' + strategyName + '</span>' +
                    badgesHtml +
                '</div>' +
            '</div>' +
            '<div class="log-group-body' + (isOpen ? ' open' : '') + '" id="' + groupId + '">' +
                renderStrategyGroupContent(stratEntries, strategyName, openSymbols, openExpiries, openScreening) +
            '</div>';
        container.appendChild(block);
    }
}

function renderLogSymbolGroups(entries, strategyName, openSymbols, openExpiries) {
    const bySymbol = {};
    for (const e of entries) {
        const sym = e.symbol || '(global)';
        if (!bySymbol[sym]) bySymbol[sym] = [];
        bySymbol[sym].push(e);
    }
    let html = '<div class="log-symbol-list">';
    for (const [symbol, symEntries] of Object.entries(bySymbol)) {
        const stratSlug = (strategyName || 'unknown').replace(/\W+/g, '-');
        const symSlug = symbol.replace(/\W+/g, '-');
        const symId = 'sym-' + stratSlug + '-' + symSlug;
        const isSymOpen = openSymbols ? openSymbols.has(symId) : false;

        const otherEntries = [];
        const byExpiry = {};
        for (const e of symEntries) {
            if (e.expiry) {
                if (!byExpiry[e.expiry]) byExpiry[e.expiry] = [];
                byExpiry[e.expiry].push(e);
            } else {
                otherEntries.push(e);
            }
        }

        const filterableEntries = symEntries.filter(e => e.filterStage !== 'Generated Candidates');
        const expiryCount = Object.keys(byExpiry).length;
        let finalCount = 0;
        if (expiryCount > 0) {
            for (const expDate of Object.keys(byExpiry)) {
                const expEntries = byExpiry[expDate].filter(e => e.filterStage !== 'Generated Candidates');
                const expFinal = expEntries.length > 0
                    ? (expEntries[expEntries.length - 1].tradesOut || 0)
                    : (byExpiry[expDate].find(e => e.filterStage === 'Generated Candidates')?.tradesOut || 0);
                finalCount += expFinal;
            }
        } else if (filterableEntries.length > 0) {
            finalCount = filterableEntries[filterableEntries.length - 1].tradesOut || 0;
        } else if (symEntries.length > 0) {
            finalCount = symEntries[symEntries.length - 1].tradesOut || 0;
        }

        html +=
            '<div class="log-symbol-block">' +
                '<div class="log-symbol-header" onclick="toggleLogGroup(\'' + symId + '\')">' +
                    '<div class="flex items-center gap-sm">' +
                        '<span class="card-arrow' + (isSymOpen ? ' open' : '') + '" id="arrow-' + symId + '">&#9658;</span>' +
                        '<span class="log-symbol-name">' + symbol + '</span>' +
                        (expiryCount > 0
                            ? '<span class="card-badge" style="font-size:0.7rem">' + expiryCount + ' expir' + (expiryCount !== 1 ? 'ies' : 'y') + '</span>'
                            : '<span class="card-badge" style="font-size:0.7rem">' + filterableEntries.length + ' filters</span>') +
                        '<span class="log-final-count">' + finalCount + ' trade' + (finalCount !== 1 ? 's' : '') + ' remaining</span>' +
                    '</div>' +
                '</div>' +
                '<div class="log-symbol-body' + (isSymOpen ? ' open' : '') + '" id="' + symId + '">' +
                    renderLogSymbolContent(otherEntries, byExpiry, stratSlug, symSlug, openExpiries) +
                '</div>' +
            '</div>';
    }
    html += '</div>';
    return html;
}

function sortLogEntries(arr) {
    if (!window.logSortColumn) return arr;
    return [...arr].sort((a, b) => {
        let valA, valB;
        if (window.logSortColumn === 'filterStage') { valA = a.filterStage || ''; valB = b.filterStage || ''; }
        else if (window.logSortColumn === 'tradesIn') { valA = a.tradesIn || 0; valB = b.tradesIn || 0; }
        else if (window.logSortColumn === 'tradesOut') { valA = a.tradesOut || 0; valB = b.tradesOut || 0; }
        else if (window.logSortColumn === 'filtered') { valA = (a.tradesIn||0)-(a.tradesOut||0); valB = (b.tradesIn||0)-(b.tradesOut||0); }
        else if (window.logSortColumn === 'passRate') { valA = a.tradesIn > 0 ? (a.tradesOut/a.tradesIn) : 1; valB = b.tradesIn > 0 ? (b.tradesOut/b.tradesIn) : 1; }
        if (valA < valB) return window.logSortAsc ? -1 : 1;
        if (valA > valB) return window.logSortAsc ? 1 : -1;
        return 0;
    });
}

function buildLogFilterTable(rowsArr) {
    const getSortIndicator = (col) => {
        if (window.logSortColumn === col) { return window.logSortAsc ? ' ↑' : ' ↓'; }
        return '';
    };
    const sortCls = (col) => window.logSortColumn === col ? 'sort-header active' : 'sort-header';

    return '<table class="data-table log-filter-table">' +
        '<thead><tr>' +
            '<th class="' + sortCls('filterStage') + '" onclick="handleLogSort(\'filterStage\', event)" style="cursor:pointer;user-select:none;">FILTER STAGE' + getSortIndicator('filterStage') + '</th>' +
            '<th class="' + sortCls('tradesIn') + '" onclick="handleLogSort(\'tradesIn\', event)" style="text-align:right;cursor:pointer;user-select:none;">IN' + getSortIndicator('tradesIn') + '</th>' +
            '<th class="' + sortCls('tradesOut') + '" onclick="handleLogSort(\'tradesOut\', event)" style="text-align:right;cursor:pointer;user-select:none;">OUT' + getSortIndicator('tradesOut') + '</th>' +
            '<th class="' + sortCls('filtered') + '" onclick="handleLogSort(\'filtered\', event)" style="text-align:right;cursor:pointer;user-select:none;">FILTERED' + getSortIndicator('filtered') + '</th>' +
            '<th class="' + sortCls('passRate') + '" onclick="handleLogSort(\'passRate\', event)" style="text-align:right;width:120px;cursor:pointer;user-select:none;">PASS RATE' + getSortIndicator('passRate') + '</th>' +
        '</tr></thead>' +
        '<tbody>' + sortLogEntries(rowsArr).map(renderLogFilterRow).join('') + '</tbody>' +
    '</table>';
}

function renderLogSymbolContent(otherEntries, byExpiry, stratSlug, symSlug, openExpiries) {
    let html = '<div class="log-symbol-content">';

    const expiryDates = Object.keys(byExpiry).sort();
    if (expiryDates.length > 0) {
        html += '<div class="log-expiry-list">';
        for (const expDate of expiryDates) {
            const expEntries = byExpiry[expDate].filter(e => e.filterStage !== 'Generated Candidates');
            const rawEntries = byExpiry[expDate];
            const expId = 'exp-' + stratSlug + '-' + symSlug + '-' + expDate.replace(/-/g, '');
            const isExpOpen = openExpiries ? openExpiries.has(expId) : false;
            const expCandidates = rawEntries.find(e => e.filterStage === 'Generated Candidates');
            const expFirst = expCandidates ? expCandidates.tradesIn : (expEntries.length > 0 ? expEntries[0].tradesIn : 0);
            const expFinal = expEntries.length > 0 ? expEntries[expEntries.length - 1].tradesOut : 0;

            html +=
                '<div class="log-expiry-block">' +
                    '<div class="log-expiry-header" onclick="toggleLogGroup(\'' + expId + '\')">' +
                        '<div class="flex items-center gap-sm">' +
                            '<span class="card-arrow' + (isExpOpen ? ' open' : '') + '" id="arrow-' + expId + '">&#9658;</span>' +
                            '<span class="log-expiry-date">Expiry: ' + expDate + '</span>' +
                            '<span class="card-badge" style="font-size:0.68rem;background:rgba(99,102,241,0.12);color:var(--primary)">' + expFirst + ' candidates &rarr; ' + expFinal + ' trades</span>' +
                            '<span class="card-badge" style="font-size:0.68rem">' + expEntries.length + ' filter' + (expEntries.length !== 1 ? 's' : '') + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div class="log-expiry-body' + (isExpOpen ? ' open' : '') + '" id="' + expId + '">' +
                        (expEntries.length > 0 ? buildLogFilterTable(expEntries) : '<p class="text-muted" style="padding:0.5rem 1rem;">No filter stages recorded.</p>') +
                    '</div>' +
                '</div>';
        }
        html += '</div>';
    }

    if (otherEntries.length > 0) {
        const otherId = 'other-' + stratSlug + '-' + symSlug;
        const isOtherOpen = openExpiries ? (openExpiries.has(otherId) || expiryDates.length === 0) : true;
        html +=
            '<div class="log-expiry-block log-expiry-other">' +
                '<div class="log-expiry-header" onclick="toggleLogGroup(\'' + otherId + '\')">' +
                    '<div class="flex items-center gap-sm">' +
                        '<span class="card-arrow' + (isOtherOpen ? ' open' : '') + '" id="arrow-' + otherId + '">&#9658;</span>' +
                        '<span class="log-expiry-date" style="color:var(--text-muted)">Other (symbol-level)</span>' +
                        '<span class="card-badge" style="font-size:0.68rem">' + otherEntries.length + ' filter' + (otherEntries.length !== 1 ? 's' : '') + '</span>' +
                    '</div>' +
                '</div>' +
                '<div class="log-expiry-body' + (isOtherOpen ? ' open' : '') + '" id="' + otherId + '">' +
                    buildLogFilterTable(otherEntries) +
                '</div>' +
            '</div>';
    }

    html += '</div>';
    return html;
}

function renderLogFilterRow(entry) {
    const filtered = (entry.tradesIn || 0) - (entry.tradesOut || 0);
    const pct = entry.tradesIn > 0 ? Math.round((entry.tradesOut / entry.tradesIn) * 100) : 100;
    const barColor = pct >= 80 ? 'var(--success)' : (pct >= 40 ? '#f5a623' : '#ef4444');
    const filteredClass = filtered > 0 ? 'text-danger' : 'text-muted';
    const detailsHtml = entry.details
        ? '<div class="log-entry-details text-muted" style="font-size:0.75rem;margin-top:2px;">' + escapeHtml(entry.details) + '</div>'
        : '';
    return '<tr>' +
        '<td>' + (entry.filterStage || '&mdash;') + detailsHtml + '</td>' +
        '<td style="text-align:right">' + (entry.tradesIn != null ? entry.tradesIn : '&mdash;') + '</td>' +
        '<td style="text-align:right"><strong>' + (entry.tradesOut != null ? entry.tradesOut : '&mdash;') + '</strong></td>' +
        '<td style="text-align:right" class="' + filteredClass + '">' + (filtered > 0 ? ('-' + filtered) : '&mdash;') + '</td>' +
        '<td style="text-align:right">' +
            '<div class="log-flow-bar"><div class="log-flow-fill" style="width:' + pct + '%;background:' + barColor + '"></div></div>' +
            '<span class="log-flow-pct">' + pct + '%</span>' +
        '</td>' +
    '</tr>';
}

function toggleLogGroup(id) {
    const body = document.getElementById(id);
    const arrow = document.getElementById('arrow-' + id);
    if (body) body.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
}

window.focusSymbol = function(stratSlug, symSlug) {
    const symId = 'sym-' + stratSlug + '-' + symSlug;
    const body = document.getElementById(symId);
    const arrow = document.getElementById('arrow-' + symId);
    if (body && !body.classList.contains('open')) {
        body.classList.add('open');
        if (arrow) arrow.classList.add('open');
    }
    const block = body ? body.closest('.log-symbol-block') : null;
    if (block) {
        block.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
};

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    Object.assign(global, utils, authApi);

    module.exports = {
        handleLogSort,
        initLogsPage,
        startLogPolling,
        loadLogs,
        clearLogs,
        isStockLevelEntry,
        aggregateStockScreening,
        renderStockScreeningSection,
        renderStrategyGroupContent,
        renderLogGroups,
        renderLogSymbolGroups,
        renderLogSymbolContent,
        renderLogFilterRow,
        toggleLogGroup,
        focusSymbol: window.focusSymbol
    };
}
