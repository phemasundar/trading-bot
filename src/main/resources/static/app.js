/**
 * Trading Bot — Frontend Application Logic
 * All API calls go through Spring Boot REST endpoints.
 * No direct Supabase access from the frontend.
 */

// ── API Client ──

const API = {
    token: localStorage.getItem('api_token') || '',

    async request(method, path, body) {
        const opts = {
            method,
            headers: { 'Content-Type': 'application/json' }
        };
        if (this.token) {
            opts.headers['Authorization'] = `Bearer ${this.token}`;
        }
        if (body) {
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(path, opts);
        if (res.status === 401 || res.status === 503) {
            this.promptToken();
            throw new Error('Unauthorized');
        }
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Request failed');
        return data;
    },

    get(path) { return this.request('GET', path); },
    post(path, body) { return this.request('POST', path, body); },

    setToken(token) {
        this.token = token;
        localStorage.setItem('api_token', token);
    },

    promptToken() {
        // Prevent multiple modals from stacking
        if (document.querySelector('.modal-overlay')) return;

        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = `
            <div class="modal">
                <h2>🔐 API Authentication</h2>
                <p>Enter your Bearer token to access execution features.</p>
                <div class="form-group">
                    <input type="password" class="form-input token-input"
                           placeholder="Paste your token here" />
                </div>
                <div class="flex gap-sm">
                    <button class="btn btn-primary token-save">Save Token</button>
                    <button class="btn btn-ghost token-cancel">Cancel</button>
                </div>
            </div>`;
        document.body.appendChild(overlay);

        // Use scoped selectors within this specific overlay
        overlay.querySelector('.token-save').onclick = () => {
            const token = overlay.querySelector('.token-input').value.trim();
            if (token) {
                this.setToken(token);
                overlay.remove();
                location.reload();
            }
        };
        overlay.querySelector('.token-cancel').onclick = () => overlay.remove();
    }
};

// ── Mobile Sidebar Toggle ──

function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('open');
}

// ── Toast Notifications ──

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}

// ── Time Formatting ──

function timeAgo(dateStr) {
    if (!dateStr) return 'Unknown';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (mins > 0) return `${mins}m ago`;
    return 'Just now';
}

// ── Card Builder ──

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    card.className = 'card';

    const cardId = (result.strategyId || 'card-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap">
                ${arrow}
                <span class="card-name">${result.strategyName || 'Unknown'}</span>
                ${result.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${result.descriptionFile}', '${escapeAttr(result.strategyName)}')">ℹ️</button>` : ''}
                <span class="card-badge">${badgeText}</span>
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Trades: ${result.tradesFound || 0}</span>
        </div>
        <div class="card-params">${formatFilterParams(result.filterConfig)}</div>
        <div class="card-content" id="content-${cardId}">
            ${buildTradeTable(result.trades || [])}
        </div>`;

    // Attach click handler
    card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));

    return card;
}

function toggleCard(id) {
    const content = document.getElementById(`content-${id}`);
    const arrow = document.getElementById(`arrow-${id}`);
    if (!content) return;
    content.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
}

// ── Trade Table Builder ──

function buildTradeTable(trades) {
    if (!trades || trades.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">📊</div>No trades found</div>';
    }

    let html = `<table class="data-table">
        <thead><tr>
            <th>Ticker</th><th>Price</th><th>Type</th><th>Expiry</th>
            <th>Credit/Debit</th><th>Max Loss</th><th>Extrinsic</th><th>Breakeven</th><th>ROR%</th>
        </tr></thead><tbody>`;

    for (const t of trades) {
        const rorClass = (t.returnOnRisk || 0) >= 0 ? 'text-success' : 'text-danger';
        const credit = t.netCredit || 0;
        const creditStr = credit >= 0
            ? `<span class="text-success">$${credit.toFixed(2)}</span>`
            : `<span class="text-danger">-$${Math.abs(credit).toFixed(2)}</span>`;

        const detailsEscaped = escapeAttr(t.tradeDetails || '');

        html += `<tr class="trade-row" data-details="${detailsEscaped}">
            <td><strong>${t.symbol || ''}</strong></td>
            <td class="text-mono">$${(t.underlyingPrice || 0).toFixed(2)}</td>
            <td>${formatLegs(t)}</td>
            <td>${formatExpiryDate(t.expiryDate)} <span class="text-muted">(${t.dte || 0}d)</span></td>
            <td>${creditStr}</td>
            <td class="text-danger">$${(t.maxLoss || 0).toFixed(2)}</td>
            <td>$${(t.netExtrinsicValue || 0).toFixed(2)} <span class="text-muted">(${(t.anulizedNetExtrinsicValueToCapitalPercentage || 0).toFixed(1)}%)</span></td>
            <td>${formatBreakeven(t)}</td>
            <td class="${rorClass}">${(t.returnOnRisk || 0).toFixed(1)}%</td>
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
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

    // Add Breakeven CAGR if available
    if (trade.breakevenCAGR != null) {
        parts.push(`CAGR: ${trade.breakevenCAGR.toFixed(1)}%`);
    }

    return parts.join('<br>') || '-';
}

function formatExpiryDate(dateStr) {
    if (!dateStr) return '';
    // Extract just YYYY-MM-DD from ISO timestamps like "2026-04-17T20:00:00.000+00:00"
    return dateStr.substring(0, 10);
}

// ── Trade Detail Popup ──

function initTradeRowClicks() {
    document.addEventListener('click', (e) => {
        const row = e.target.closest('.trade-row');
        if (!row) return;

        // Close any existing detail panel
        const existing = document.querySelector('.trade-detail-panel');
        if (existing) {
            // If clicking the same row, just close
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

        row.classList.add('selected');

        // Create detail panel right after the row
        const panel = document.createElement('tr');
        panel.className = 'trade-detail-panel';
        panel.dataset.rowId = details;
        panel.innerHTML = `
            <td colspan="9">
                <div class="trade-detail">
                    <div class="trade-detail-header">
                        ▶ ${row.querySelector('td strong')?.textContent || ''} — Trade Details
                    </div>
                    <pre class="trade-detail-body">${decodeAttr(details)}</pre>
                </div>
            </td>`;
        row.after(panel);
    });
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', initTradeRowClicks);

// ── Filter Params Formatter ──

function formatFilterParams(filterConfigJson) {
    if (!filterConfigJson) return '';
    try {
        const cfg = typeof filterConfigJson === 'string' ? JSON.parse(filterConfigJson) : filterConfigJson;
        const parts = [];
        if (cfg.targetDTE) parts.push(`DTE: ${cfg.targetDTE}`);
        else if (cfg.minDTE || cfg.maxDTE) parts.push(`DTE: ${cfg.minDTE || 0}-${cfg.maxDTE || '∞'}`);
        if (cfg.maxUpperBreakevenDelta) parts.push(`Delta < ${cfg.maxUpperBreakevenDelta.toFixed(2)}`);
        if (cfg.maxLossLimit) parts.push(`Max Loss: <$${cfg.maxLossLimit.toFixed(0)}`);
        if (cfg.minReturnOnRisk) parts.push(`Min RoR: ${cfg.minReturnOnRisk}%`);
        if (cfg.maxBreakEvenPercentage) parts.push(`Max B/E: ${cfg.maxBreakEvenPercentage.toFixed(1)}%`);
        if (cfg.maxNetExtrinsicValueToPricePercentage) parts.push(`Max Ext: ${cfg.maxNetExtrinsicValueToPricePercentage.toFixed(1)}%`);
        return parts.join(', ') || '';
    } catch { return ''; }
}

function escapeAttr(str) {
    return (str || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function decodeAttr(str) {
    return (str || '').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
}

// ── Elapsed Timer ──

let timerInterval = null;

function startTimer(startTimeMs) {
    const el = document.getElementById('elapsed-text');
    if (!el) return;
    clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        const elapsed = Math.floor((Date.now() - startTimeMs) / 1000);
        const mins = Math.floor(elapsed / 60);
        const secs = elapsed % 60;
        el.textContent = `Elapsed: ${mins}m ${secs}s`;
    }, 1000);
}

function stopTimer() {
    clearInterval(timerInterval);
}

// ── Execution Status Polling ──

let pollInterval = null;

function startPolling(onComplete) {
    clearInterval(pollInterval);
    pollInterval = setInterval(async () => {
        try {
            const status = await API.get('/api/status');
            if (!status.running) {
                clearInterval(pollInterval);
                stopTimer();
                if (onComplete) onComplete();
            }
        } catch (e) {
            clearInterval(pollInterval);
        }
    }, 3000);
}

// ══════════════════════════════════════
//  PAGE: Dashboard (index.html)
// ══════════════════════════════════════

async function initDashboard() {
    await loadStrategies();
    await loadResults();
    await checkExecutionStatus();
}

async function loadStrategies() {
    const container = document.getElementById('strategy-checkboxes');
    if (!container) return;
    try {
        const strategies = await API.get('/api/strategies');
        container.innerHTML = strategies.map(s => `
            <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                <label class="checkbox-label" style="margin: 0;">
                    <input type="checkbox" value="${s.index}" checked>
                    <span>${s.name}</span>
                </label>
                ${s.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(s.name)}')">ℹ️</button>` : ''}
            </div>`).join('');
    } catch (e) {
        container.innerHTML = `<span class="text-muted">Failed to load strategies</span>`;
    }
}

async function loadResults() {
    const container = document.getElementById('results-container');
    if (!container) return;
    try {
        const results = await API.get('/api/results');
        container.innerHTML = '';
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No results yet. Execute a strategy to see results.</div>';
            return;
        }
        for (const r of results) {
            container.appendChild(buildResultCard(r));
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
    }
}

async function checkExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.running) {
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

function setDashboardBusy(busy) {
    const execBtn = document.getElementById('execute-btn');
    const stopBtn = document.getElementById('stop-btn');
    const progress = document.getElementById('progress-container');
    if (execBtn) execBtn.disabled = busy;
    if (stopBtn) stopBtn.style.display = busy ? 'inline-flex' : 'none';
    if (progress) progress.className = busy ? 'progress-container active' : 'progress-container';
}

// ── Strategy Info Modal ──

async function showInfo(event, filename, strategyName) {
    if (event) {
        event.stopPropagation(); // prevent card toggle or checkbox click
        event.preventDefault();
    }

    // Prevent multiple modals
    if (document.querySelector('.info-modal-overlay')) return;

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay info-modal-overlay';

    let modalContent = `
        <div class="modal" style="max-width: 600px;">
            <div class="flex justify-between items-center" style="margin-bottom: 12px; border-bottom: 1px solid var(--border); padding-bottom: 8px;">
                <h2 style="margin:0;">${strategyName}</h2>
                <button class="btn-icon modal-close" style="background:none; border:none; color:var(--text-muted); font-size:1.2rem; cursor:pointer;" onclick="this.closest('.modal-overlay').remove()">✕</button>
            </div>
            <div class="markdown-body">
                <div class="loading-spinner">
                    <span class="loading-spinner-icon">⏳</span>
                    <p>Loading description...</p>
                </div>
            </div>
        </div>`;

    overlay.innerHTML = modalContent;
    document.body.appendChild(overlay);

    // Close on background click
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) {
            overlay.remove();
        }
    });

    try {
        const res = await fetch(`/descriptions/${filename}`);
        if (!res.ok) throw new Error('File not found');
        const text = await res.text();

        // Use marked.js if available, otherwise fallback to plain text
        const bodyEl = overlay.querySelector('.markdown-body');
        if (typeof marked !== 'undefined') {
            bodyEl.innerHTML = marked.parse(text);
        } else {
            bodyEl.innerHTML = `<pre style="white-space: pre-wrap; font-family: var(--font-sans);">${escapeAttr(text)}</pre>`;
        }
    } catch (e) {
        overlay.querySelector('.markdown-body').innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon text-warning">⚠️</div>
                <p>Could not load description.</p>
                <small class="text-muted">${e.message}</small>
            </div>`;
    }
}

async function executeSelected() {
    const checked = document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]:checked');
    const indices = Array.from(checked).map(c => parseInt(c.value));
    if (indices.length === 0) {
        showToast('Select at least one strategy', 'error');
        return;
    }
    try {
        setDashboardBusy(true);
        const res = await API.post('/api/execute', { strategyIndices: indices });
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

function selectAll(check) {
    document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]')
        .forEach(cb => cb.checked = check);
}

// ══════════════════════════════════════
//  PAGE: Execute Strategy (execute.html)
// ══════════════════════════════════════

const STRATEGY_TYPES = [
    { value: 'PUT_CREDIT_SPREAD', label: 'Put Credit Spread', group: 'credit_spread' },
    { value: 'TECH_PUT_CREDIT_SPREAD', label: 'Technical Put Credit Spread', group: 'credit_spread' },
    { value: 'BULLISH_LONG_PUT_CREDIT_SPREAD', label: 'Bullish Long Put Credit Spread', group: 'credit_spread' },
    { value: 'CALL_CREDIT_SPREAD', label: 'Call Credit Spread', group: 'credit_spread' },
    { value: 'TECH_CALL_CREDIT_SPREAD', label: 'Technical Call Credit Spread', group: 'credit_spread' },
    { value: 'IRON_CONDOR', label: 'Iron Condor', group: 'iron_condor' },
    { value: 'BULLISH_LONG_IRON_CONDOR', label: 'Bullish Long Iron Condor', group: 'iron_condor' },
    { value: 'LONG_CALL_LEAP', label: 'Long Call LEAP', group: 'leap' },
    { value: 'LONG_CALL_LEAP_TOP_N', label: 'Long Call LEAP Top N', group: 'leap' },
    { value: 'BULLISH_BROKEN_WING_BUTTERFLY', label: 'Bullish Broken Wing Butterfly', group: 'bwb' },
    { value: 'BULLISH_ZEBRA', label: 'Bullish ZEBRA', group: 'zebra' },
];

// Strategy-specific filter definitions
const STRATEGY_SPECIFIC_FILTERS = {
    credit_spread: [
        { key: 'shortLeg.minDelta', label: 'Short Leg Min Delta', placeholder: '0.10', step: '0.01' },
        { key: 'shortLeg.maxDelta', label: 'Short Leg Max Delta', placeholder: '0.30', step: '0.01' },
        { key: 'shortLeg.minOpenInterest', label: 'Short Leg Min OI', placeholder: '100' },
        { key: 'longLeg.minDelta', label: 'Long Leg Min Delta', placeholder: '0.05', step: '0.01' },
        { key: 'longLeg.maxDelta', label: 'Long Leg Max Delta', placeholder: '0.20', step: '0.01' },
        { key: 'longLeg.minOpenInterest', label: 'Long Leg Min OI', placeholder: '50' },
    ],
    iron_condor: [
        { key: 'putShortLeg.minDelta', label: 'Put Short Min Delta', placeholder: '0.10', step: '0.01' },
        { key: 'putShortLeg.maxDelta', label: 'Put Short Max Delta', placeholder: '0.25', step: '0.01' },
        { key: 'callShortLeg.minDelta', label: 'Call Short Min Delta', placeholder: '0.10', step: '0.01' },
        { key: 'callShortLeg.maxDelta', label: 'Call Short Max Delta', placeholder: '0.25', step: '0.01' },
        { key: 'minCombinedCredit', label: 'Min Combined Credit', placeholder: '100' },
    ],
    leap: [
        { key: 'longCall.minDelta', label: 'Long Call Min Delta', placeholder: '0.70', step: '0.01' },
        { key: 'longCall.maxDelta', label: 'Long Call Max Delta', placeholder: '0.90', step: '0.01' },
        { key: 'maxBreakevenCAGR', label: 'Max Breakeven CAGR', placeholder: '15.0', step: '0.1' },
    ],
    bwb: [
        { key: 'shortLeg.minDelta', label: 'Short Leg Min Delta', placeholder: '0.30', step: '0.01' },
        { key: 'shortLeg.maxDelta', label: 'Short Leg Max Delta', placeholder: '0.50', step: '0.01' },
        { key: 'maxTotalDebit', label: 'Max Total Debit ($)', placeholder: '500' },
        { key: 'priceVsMaxDebitRatio', label: 'Price/Debit Ratio', placeholder: '2.0', step: '0.1' },
    ],
    zebra: [
        { key: 'shortCall.minDelta', label: 'Short Call Min Delta', placeholder: '0.45', step: '0.01' },
        { key: 'shortCall.maxDelta', label: 'Short Call Max Delta', placeholder: '0.55', step: '0.01' },
        { key: 'longCall.minDelta', label: 'Long Call Min Delta', placeholder: '0.65', step: '0.01' },
        { key: 'longCall.maxDelta', label: 'Long Call Max Delta', placeholder: '0.85', step: '0.01' },
        { key: 'maxNetExtrinsicValue', label: 'Max Net Extrinsic ($)', placeholder: '50' },
        { key: 'maxTotalDebit', label: 'Max Total Debit ($)', placeholder: '5000' },
    ],
};

function initExecutePage() {
    const select = document.getElementById('strategy-type');
    if (!select) return;

    STRATEGY_TYPES.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.value;
        opt.textContent = s.label;
        opt.dataset.group = s.group;
        select.appendChild(opt);
    });

    // Listen for strategy type change to render specific filters
    select.addEventListener('change', () => {
        renderSpecificFilters(select.value);
    });

    loadCustomResults();
}

function renderSpecificFilters(strategyValue) {
    const container = document.getElementById('specific-filters');
    if (!container) return;

    const type = STRATEGY_TYPES.find(s => s.value === strategyValue);
    if (!type) { container.innerHTML = ''; return; }

    const filters = STRATEGY_SPECIFIC_FILTERS[type.group] || [];
    if (filters.length === 0) { container.innerHTML = ''; return; }

    let html = '<h4 style="font-size:0.8rem; color:var(--text-secondary); margin: 16px 0 8px; grid-column: 1 / -1">' +
        `${type.label} Filters</h4>`;
    for (const f of filters) {
        html += `<div class="form-group">
            <label class="form-label">${f.label}</label>
            <input type="number" class="form-input" data-filter="${f.key}"
                   placeholder="${f.placeholder}" step="${f.step || '1'}">
        </div>`;
    }
    container.innerHTML = html;
}

async function executeCustom() {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const aliasEl = document.getElementById('alias-input');

    if (!typeEl.value) { showToast('Select a strategy type', 'error'); return; }
    if (!securitiesEl.value.trim()) { showToast('Enter at least one security', 'error'); return; }

    // Collect all filter values (common + specific)
    const filter = {};
    document.querySelectorAll('[data-filter]').forEach(input => {
        const key = input.dataset.filter;
        if (input.type === 'checkbox') {
            filter[key] = input.checked;
        } else if (input.value) {
            // Handle nested keys like "shortLeg.minDelta"
            if (key.includes('.')) {
                const parts = key.split('.');
                if (!filter[parts[0]]) filter[parts[0]] = {};
                filter[parts[0]][parts[1]] = parseFloat(input.value);
            } else {
                filter[key] = parseFloat(input.value);
            }
        }
    });

    const body = {
        strategyType: typeEl.value,
        securities: securitiesEl.value,
        alias: aliasEl ? aliasEl.value : '',
        maxTradesToSend: 30,
        filter
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
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load: ${e.message}</div>`;
    }
}

// ══════════════════════════════════════
//  PAGE: Config Viewer (config.html)
// ══════════════════════════════════════

async function initConfigPage() {
    const container = document.getElementById('config-container');
    if (!container) return;
    try {
        const config = await API.get('/api/config');
        container.innerHTML = '';
        renderConfig(config, container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load config: ${e.message}</div>`;
    }
}

function renderConfig(config, container) {
    // Options strategies
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
                ? `<button type="button" class="info-btn" onclick="showInfo(event, '${strategy.descriptionFile}', '${escapeAttr(strategy.alias || strategy.strategyType)}')">ℹ️</button>`
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
                    ${strategy.securitiesFile ? `<div class="mt-sm"><span class="config-item-label">Securities File</span> <span class="config-item-value">${strategy.securitiesFile}</span></div>` : ''}
                    ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
                </div>`;

            // Toggle on header click
            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });

            container.appendChild(card);
        });
    }

    // Technical screeners
    if (config.technicalScreeners) {
        const heading = document.createElement('h3');
        heading.textContent = 'Technical Screeners';
        heading.className = 'section-heading';
        container.appendChild(heading);

        config.technicalScreeners.forEach(screener => {
            const card = document.createElement('div');
            card.className = 'config-card';
            card.innerHTML = `
                <div class="config-card-header">
                    <div class="flex items-center gap-sm">
                        <span class="card-arrow">▶</span>
                        <strong>${screener.name || 'Screener'}</strong>
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderFilterGrid(screener)}
                </div>`;
            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });
            container.appendChild(card);
        });
    }
}

function renderFilterGrid(filter) {
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
            html += `<div class="config-item">
                <span class="config-item-label">${label}</span>
                <span class="config-item-value">${displayVal}</span>
            </div>`;
        }
        html += '</div>';
    }

    // Nested objects (legs, etc.)
    const nested = Object.entries(filter).filter(([k, v]) => v !== null && typeof v === 'object' && !Array.isArray(v));
    for (const [key, value] of nested) {
        const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
        html += `<div class="nested-section"><h4 class="nested-heading">${label}</h4>${renderFilterGrid(value)}</div>`;
    }

    // Arrays
    const arrays = Object.entries(filter).filter(([k, v]) => Array.isArray(v));
    for (const [key, value] of arrays) {
        const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
        html += `<div class="mt-sm"><span class="config-item-label">${label}</span> `;
        html += value.map(v => `<span class="card-badge" style="margin:2px">${v}</span>`).join('');
        html += '</div>';
    }

    return html;
}
