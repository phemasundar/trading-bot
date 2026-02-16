// ============================================
// Trading Bot Dashboard — Supabase Client
// ============================================

// --- Configuration ---
// These placeholders are replaced by GitHub Actions during deployment.
// For local testing, replace with your actual values.
const SUPABASE_URL = '__SUPABASE_URL__';
const SUPABASE_ANON_KEY = '__SUPABASE_ANON_KEY__';

// --- Supabase Client ---
let supabaseClient;

function initSupabase() {
    if (SUPABASE_URL.startsWith('__') || SUPABASE_ANON_KEY.startsWith('__')) {
        showError('Supabase credentials not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY.');
        return false;
    }
    supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
    return true;
}

// --- DOM References ---
const resultsContainer = document.getElementById('resultsContainer');
const loadingState = document.getElementById('loadingState');
const errorState = document.getElementById('errorState');
const errorMessage = document.getElementById('errorMessage');
const lastUpdatedEl = document.getElementById('lastUpdated');
const strategyCountEl = document.getElementById('strategyCount');
const refreshBtn = document.getElementById('refreshBtn');

// --- State ---
let expandedTradeId = null; // Track which trade details row is open

// --- Load Data ---
async function loadData() {
    if (!supabaseClient && !initSupabase()) return;

    showLoading();
    refreshBtn.classList.add('loading');

    try {
        const { data, error } = await supabaseClient
            .from('latest_strategy_results')
            .select('*')
            .order('updated_at', { ascending: false });

        if (error) throw error;

        if (!data || data.length === 0) {
            showError('No strategy results found in database.');
            return;
        }

        renderResults(data);
        updateLastUpdated(data);
        strategyCountEl.textContent = `${data.length} strategies`;
    } catch (err) {
        console.error('Failed to load data:', err);
        showError('Failed to load data: ' + (err.message || err));
    } finally {
        refreshBtn.classList.remove('loading');
    }
}

// --- UI State Helpers ---
function showLoading() {
    loadingState.style.display = 'flex';
    errorState.style.display = 'none';
    resultsContainer.style.display = 'none';
}

function showError(msg) {
    loadingState.style.display = 'none';
    errorState.style.display = 'block';
    resultsContainer.style.display = 'none';
    errorMessage.textContent = msg;
}

function showResults() {
    loadingState.style.display = 'none';
    errorState.style.display = 'none';
    resultsContainer.style.display = 'block';
}

// --- Render Results ---
function renderResults(strategies) {
    resultsContainer.innerHTML = '';

    strategies.forEach((strategy, idx) => {
        const card = createStrategyCard(strategy, idx);
        resultsContainer.appendChild(card);
    });

    showResults();
}

// --- Strategy Card ---
function createStrategyCard(strategy, index) {
    const card = document.createElement('div');
    card.className = 'strategy-card';

    const trades = strategy.trades || [];
    const tradesFound = strategy.trades_found || trades.length;
    const execTimeMs = strategy.execution_time_ms || 0;
    const updatedAt = strategy.updated_at ? timeAgo(new Date(strategy.updated_at)) : 'N/A';

    // Header
    const header = document.createElement('div');
    header.className = 'card-header';
    header.innerHTML = `
        <div class="card-header-left">
            <svg class="collapse-arrow collapsed" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
            <span class="strategy-name">${escHtml(strategy.strategy_name || strategy.strategy_id)}</span>
            <span class="strategy-badge">${escHtml(strategy.strategy_id)}</span>
        </div>
        <div class="card-header-right">
            <div class="stat-group">
                <div class="stat-item">
                    <div class="stat-label">Trades</div>
                    <div class="stat-value trades-count ${tradesFound === 0 ? 'zero' : ''}">${tradesFound}</div>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-item">
                    <div class="stat-label">Time</div>
                    <div class="stat-value">${formatExecTime(execTimeMs)}</div>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-item">
                    <div class="stat-label">Updated</div>
                    <div class="stat-value">${updatedAt}</div>
                </div>
            </div>
        </div>
    `;

    // Content (collapsible)
    const content = document.createElement('div');
    content.className = 'card-content';

    if (tradesFound > 0 && trades.length > 0) {
        content.appendChild(createTradeGrid(trades, index));
    } else {
        const noTrades = document.createElement('div');
        noTrades.className = 'no-trades';
        noTrades.textContent = 'No trades found for this strategy.';
        content.appendChild(noTrades);
    }

    // Toggle Logic
    header.addEventListener('click', () => {
        const arrow = header.querySelector('.collapse-arrow');
        const isCollapsed = arrow.classList.contains('collapsed');
        if (isCollapsed) {
            arrow.classList.remove('collapsed');
            content.classList.add('expanded');
        } else {
            arrow.classList.add('collapsed');
            content.classList.remove('expanded');
        }
    });

    card.appendChild(header);
    card.appendChild(content);
    return card;
}

// --- Trade Grid ---
function createTradeGrid(trades, strategyIdx) {
    const table = document.createElement('table');
    table.className = 'trade-grid';

    // Thead
    table.innerHTML = `
        <thead>
            <tr>
                <th>Ticker</th>
                <th>Type</th>
                <th>Expiry</th>
                <th>Credit/Debit</th>
                <th>Max Loss</th>
                <th>Breakeven</th>
                <th>ROR</th>
            </tr>
        </thead>
    `;

    const tbody = document.createElement('tbody');

    trades.forEach((trade, tradeIdx) => {
        const rowId = `trade-${strategyIdx}-${tradeIdx}`;

        // Main row
        const tr = document.createElement('tr');
        tr.style.cursor = 'pointer';
        tr.innerHTML = `
            <td class="cell-ticker">${escHtml(trade.symbol || '')}</td>
            <td>${renderLegs(trade)}</td>
            <td class="cell-mono">${escHtml(trade.expiryDate || '')} <span style="color:var(--text-muted);">(${trade.dte || 0}d)</span></td>
            <td class="${trade.netCredit >= 0 ? 'cell-credit' : 'cell-debit'}">${formatCurrency(trade.netCredit)}</td>
            <td class="cell-mono" style="color:var(--accent-red);">$${formatNum(Math.abs(trade.maxLoss || 0))}</td>
            <td>${renderBreakeven(trade)}</td>
            <td>${renderROR(trade.returnOnRisk)}</td>
        `;

        // Click to expand trade details
        tr.addEventListener('click', () => {
            const detailRow = document.getElementById(rowId);
            if (detailRow) {
                const isVisible = detailRow.classList.contains('visible');
                // Close any other open detail
                document.querySelectorAll('.trade-details-row.visible').forEach(r => r.classList.remove('visible'));
                if (!isVisible) {
                    detailRow.classList.add('visible');
                }
            }
        });

        tbody.appendChild(tr);

        // Details row (hidden by default)
        const detailTr = document.createElement('tr');
        detailTr.id = rowId;
        detailTr.className = 'trade-details-row';
        detailTr.innerHTML = `
            <td colspan="7" class="trade-details-cell">
                <div class="trade-details-header">▶ ${escHtml(trade.symbol || '')} — Trade Details</div>
                <div class="trade-details-text">${escHtml(trade.tradeDetails || 'No details available')}</div>
            </td>
        `;
        tbody.appendChild(detailTr);
    });

    table.appendChild(tbody);
    return table;
}

// --- Cell Renderers ---
function renderLegs(trade) {
    const legs = trade.legs || [];
    if (legs.length === 0) return '<span style="color:var(--text-muted)">—</span>';

    return legs.map(leg => {
        const actionClass = (leg.action || '').toUpperCase() === 'BUY' ? 'leg-action-buy' : 'leg-action-sell';
        const action = (leg.action || '').toUpperCase();
        const type = (leg.optionType || '').charAt(0).toUpperCase(); // P or C
        const strike = formatNum(leg.strike);
        const delta = Math.abs(leg.delta || 0).toFixed(2);
        return `<span class="leg-line"><span class="${actionClass}">${action}</span> ${strike}${type} <span style="color:var(--text-muted)">Δ${delta}</span></span>`;
    }).join('');
}

function renderBreakeven(trade) {
    const be = trade.breakEvenPrice || 0;
    const bePct = trade.breakEvenPercent || 0;
    let html = `<span class="be-line">$${formatNum(be)} <span style="color:var(--text-muted)">(${bePct >= 0 ? '-' : '+'}${Math.abs(bePct).toFixed(1)}%)</span></span>`;

    if (trade.upperBreakEvenPrice && trade.upperBreakEvenPrice > 0) {
        const ubePct = trade.upperBreakEvenPercent || 0;
        html += `<span class="be-line be-upper">$${formatNum(trade.upperBreakEvenPrice)} <span style="color:var(--text-muted)">(+${Math.abs(ubePct).toFixed(1)}%)</span></span>`;
    }
    return html;
}

function renderROR(ror) {
    const value = ror || 0;
    const pctClass = value >= 0 ? 'ror-positive' : 'ror-negative';
    const barWidth = Math.min(Math.abs(value), 100);
    return `
        <div class="cell-ror ${pctClass}">
            <span class="ror-value">${value.toFixed(1)}%</span>
            <div class="ror-bar-bg">
                <div class="ror-bar-fill" style="width:${barWidth}%"></div>
            </div>
        </div>
    `;
}

// --- Formatters ---
function formatCurrency(val) {
    if (val == null) return '$0.00';
    const prefix = val >= 0 ? '$' : '-$';
    return prefix + Math.abs(val).toFixed(2);
}

function formatNum(val) {
    if (val == null) return '0';
    return Number(val).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

function formatExecTime(ms) {
    if (!ms) return '0s';
    const secs = Math.round(ms / 1000);
    if (secs < 60) return secs + 's';
    return Math.floor(secs / 60) + 'm ' + (secs % 60) + 's';
}

function timeAgo(date) {
    const now = new Date();
    const diffMs = now - date;
    const diffMin = Math.floor(diffMs / 60000);
    const diffHrs = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMin < 1) return 'just now';
    if (diffMin < 60) return diffMin + 'm ago';
    if (diffHrs < 24) return diffHrs + 'h ago';
    if (diffDays < 7) return diffDays + 'd ago';
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function updateLastUpdated(data) {
    if (data.length > 0 && data[0].updated_at) {
        const d = new Date(data[0].updated_at);
        lastUpdatedEl.textContent = 'Last update: ' + d.toLocaleString();
    }
}

function escHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// --- Event Listeners ---
refreshBtn.addEventListener('click', loadData);

// --- Init ---
document.addEventListener('DOMContentLoaded', loadData);
