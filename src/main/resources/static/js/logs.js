/**
 * Trading Bot — Filter Logs View & Real-Time Tailing (logs.html)
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
    const authed = await initAuth();
    if (!authed) return;
    await loadLogs();
    startLogPolling();
}

function startLogPolling() {
    clearInterval(_logsPollInterval);
    _logsPollInterval = setInterval(async () => {
        try {
            const status = await API.getStatus();
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
        const logs = await API.getFilterLogs();
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
        await API.clearFilterLogs();
        await loadLogs();
        showToast('Logs cleared.');
    } catch (e) {
        showToast('Failed to clear logs: ' + e.message, 'error');
    }
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

    container.innerHTML = '';
    for (const [strategyName, stratEntries] of Object.entries(groups)) {
        const groupId = 'log-group-' + strategyName.replace(/\W+/g, '-');
        const isOpen = openGroups.has(groupId);
        const block = document.createElement('div');
        block.className = 'log-group';
        const symbols = Array.from(new Set(stratEntries.map(function(e) { return e.symbol; }))).filter(Boolean);
        const symbolCount = symbols.length;
        const stepCount = stratEntries.length;
        block.innerHTML =
            '<div class="log-group-header" onclick="toggleLogGroup(\'' + groupId + '\')">' +
                '<div class="flex items-center gap-sm">' +
                    '<span class="card-arrow' + (isOpen ? ' open' : '') + '" id="arrow-' + groupId + '">&#9658;</span>' +
                    '<span class="log-group-name">' + strategyName + '</span>' +
                    '<span class="card-badge">' + symbolCount + ' symbol' + (symbolCount !== 1 ? 's' : '') + '</span>' +
                    '<span class="card-badge" style="background:rgba(99,102,241,0.15);color:var(--primary)">' + stepCount + ' filter steps</span>' +
                '</div>' +
            '</div>' +
            '<div class="log-group-body' + (isOpen ? ' open' : '') + '" id="' + groupId + '">' +
                renderLogSymbolGroups(stratEntries, strategyName, openSymbols, openExpiries) +
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
        const finalCount = filterableEntries.length > 0 ? filterableEntries[filterableEntries.length - 1].tradesOut : 0;
        const firstCount = filterableEntries.length > 0 ? filterableEntries[0].tradesIn : 0;
        const reductionPct = firstCount > 0 ? Math.round((1 - finalCount / firstCount) * 100) : 0;
        const expiryCount = Object.keys(byExpiry).length;

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
                        (reductionPct > 0 ? '<span class="log-reduction">' + reductionPct + '% filtered</span>' : '') +
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

function renderLogSymbolContent(otherEntries, byExpiry, stratSlug, symSlug, openExpiries) {
    const getSortIndicator = (col) => {
        if (window.logSortColumn === col) { return window.logSortAsc ? ' ↑' : ' ↓'; }
        return '';
    };
    const sortCls = (col) => window.logSortColumn === col ? 'sort-header active' : 'sort-header';

    function sortEntries(arr) {
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

    const buildTable = (rowsArr) =>
        '<table class="data-table log-filter-table">' +
            '<thead><tr>' +
                '<th class="' + sortCls('filterStage') + '" onclick="handleLogSort(\'filterStage\', event)" style="cursor:pointer;user-select:none;">FILTER STAGE' + getSortIndicator('filterStage') + '</th>' +
                '<th class="' + sortCls('tradesIn') + '" onclick="handleLogSort(\'tradesIn\', event)" style="text-align:right;cursor:pointer;user-select:none;">IN' + getSortIndicator('tradesIn') + '</th>' +
                '<th class="' + sortCls('tradesOut') + '" onclick="handleLogSort(\'tradesOut\', event)" style="text-align:right;cursor:pointer;user-select:none;">OUT' + getSortIndicator('tradesOut') + '</th>' +
                '<th class="' + sortCls('filtered') + '" onclick="handleLogSort(\'filtered\', event)" style="text-align:right;cursor:pointer;user-select:none;">FILTERED' + getSortIndicator('filtered') + '</th>' +
                '<th class="' + sortCls('passRate') + '" onclick="handleLogSort(\'passRate\', event)" style="text-align:right;width:120px;cursor:pointer;user-select:none;">PASS RATE' + getSortIndicator('passRate') + '</th>' +
            '</tr></thead>' +
            '<tbody>' + sortEntries(rowsArr).map(renderLogFilterRow).join('') + '</tbody>' +
        '</table>';

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
            const expReduction = expFirst > 0 ? Math.round((1 - expFinal / expFirst) * 100) : 0;

            html +=
                '<div class="log-expiry-block">' +
                    '<div class="log-expiry-header" onclick="toggleLogGroup(\'' + expId + '\')">' +
                        '<div class="flex items-center gap-sm">' +
                            '<span class="card-arrow' + (isExpOpen ? ' open' : '') + '" id="arrow-' + expId + '">&#9658;</span>' +
                            '<span class="log-expiry-date">Expiry: ' + expDate + '</span>' +
                            '<span class="card-badge" style="font-size:0.68rem;background:rgba(99,102,241,0.12);color:var(--primary)">' + expFirst + ' candidates &rarr; ' + expFinal + ' trades</span>' +
                            '<span class="card-badge" style="font-size:0.68rem">' + expEntries.length + ' filter' + (expEntries.length !== 1 ? 's' : '') + '</span>' +
                            (expReduction > 0 ? '<span class="log-reduction">' + expReduction + '% filtered</span>' : '') +
                        '</div>' +
                    '</div>' +
                    '<div class="log-expiry-body' + (isExpOpen ? ' open' : '') + '" id="' + expId + '">' +
                        (expEntries.length > 0 ? buildTable(expEntries) : '<p class="text-muted" style="padding:0.5rem 1rem;">No filter stages recorded.</p>') +
                    '</div>' +
                '</div>';
        }
        html += '</div>';
    }

    if (otherEntries.length > 0) {
        const otherId = 'other-' + stratSlug + '-' + symSlug;
        const isOtherOpen = openExpiries ? openExpiries.has(otherId) : false;
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
                    buildTable(otherEntries) +
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
    return '<tr>' +
        '<td>' + (entry.filterStage || '&mdash;') + '</td>' +
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
