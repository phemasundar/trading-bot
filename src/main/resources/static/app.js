/**
 * Trading Bot — Frontend Application Logic
 * All API calls go through Spring Boot REST endpoints.
 * No direct Supabase data access from the frontend.
 * Authentication is handled via Supabase Auth (Google/Apple OAuth).
 */

// ── Shared State ──
let FILTER_DESCRIPTIONS = {};
let _supabaseClient = null;

// ── Supabase Auth Initialization ──

/**
 * Initializes the Supabase client for authentication and guards
 * protected pages. Must be called before any API requests.
 * This is NOT called on the login page (login.html has its own init).
 */
async function initAuth() {
    try {
        const res = await fetch('/api/auth/config');
        const config = await res.json();
        _supabaseClient = supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);

        const { data: { session } } = await _supabaseClient.auth.getSession();
        if (!session) {
            window.location.href = '/login.html';
            return false;
        }

        // Set the token on the API object
        API._accessToken = session.access_token;

        // Listen for auth state changes (auto-refresh, sign out)
        _supabaseClient.auth.onAuthStateChange((event, session) => {
            if (event === 'SIGNED_OUT' || !session) {
                // If this wasn't an intentional logout, we could set a reason here,
                // but for now, we'll keep it silent for a cleaner UX.
                window.location.href = '/login.html';
            } else if (event === 'TOKEN_REFRESHED' && session) {
                API._accessToken = session.access_token;
            }
        });

        // Inject user info + logout into the sidebar
        injectUserInfo(session.user);

        // Hide the auth loading overlay if present (e.g. logs.html)
        const authOverlay = document.getElementById('authLoading');
        if (authOverlay) authOverlay.style.display = 'none';

        return true;
    } catch (e) {
        console.error('Auth initialization failed:', e);
        window.location.href = '/login.html';
        return false;
    }
}

/**
 * Injects a user info row and logout link into the sidebar.
 */
function injectUserInfo(user) {
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar || sidebar.querySelector('.user-info')) return;

    const email = user?.email || '';
    const avatar = user?.user_metadata?.avatar_url || '';
    const name = user?.user_metadata?.full_name || email?.split('@')[0] || 'User';

    // User info block (after brand, before nav links)
    const userDiv = document.createElement('div');
    userDiv.className = 'user-info';
    userDiv.innerHTML = avatar
        ? `<img class="user-avatar" src="${avatar}" alt="" referrerpolicy="no-referrer"><span title="${email}">${name}</span>`
        : `<span title="${email}">👤 ${name}</span>`;

    const brand = sidebar.querySelector('.sidebar-brand');
    if (brand) brand.after(userDiv);

    // Logout link (at bottom of sidebar)
    const logoutLink = document.createElement('a');
    logoutLink.href = '#';
    logoutLink.className = 'nav-link nav-link-logout';
    logoutLink.innerHTML = '<span class="nav-icon">🚪</span><span>Sign Out</span>';
    logoutLink.onclick = async (e) => {
        e.preventDefault();
        await logout();
    };
    sidebar.appendChild(logoutLink);
}

/**
 * Signs the user out and redirects to the login page.
 */
async function logout() {
    localStorage.removeItem('authRedirectReason');
    if (_supabaseClient) {
        await _supabaseClient.auth.signOut();
    }
    window.location.href = '/login.html';
}

// ── API Client ──

const API = {
    _accessToken: null,

    async request(method, path, body) {
        const opts = {
            method,
            headers: { 'Content-Type': 'application/json' }
        };
        if (this._accessToken) {
            opts.headers['Authorization'] = `Bearer ${this._accessToken}`;
        }
        if (body) {
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(path, opts);
        if (res.status === 401 || res.status === 403) {
            // Read backend error message if possible
            let errorMessage = res.status === 403 ? 'User not authorized.' : 'Session expired. Please sign in again.';
            try {
                const errorData = await res.json();
                if (errorData && errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch(e) {}

            // Token expired or user not authorized — force clear storage and redirect
            try { 
                if (_supabaseClient) await _supabaseClient.auth.signOut(); 
            } catch(e) {}
            
            localStorage.setItem('authError', errorMessage);
            window.location.href = '/login.html';
            throw new Error('Unauthorized');
        }
        if (res.status === 503) {
            throw new Error('Service unavailable');
        }
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Request failed');
        return data;
    },

    get(path) { return this.request('GET', path); },
    post(path, body) { return this.request('POST', path, body); },
    delete(path) { return this.request('DELETE', path); }
};

// ── Mobile Sidebar Toggle ──

function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('open');
}

// ── HTML Escaping ──

/** Escapes HTML special characters to prevent XSS in dynamic content */
function escapeAttr(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ── Toast Notifications ──

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    if (type === 'error') {
        // Error toasts persist until manually dismissed
        toast.innerHTML = `
            <span class="toast-message">${message}</span>
            <button class="toast-dismiss" onclick="this.parentElement.remove()" title="Dismiss">✕</button>
        `;
    } else {
        // Success/info toasts auto-dismiss after 4 seconds
        toast.textContent = message;
        setTimeout(() => toast.remove(), 4000);
    }
    document.body.appendChild(toast);
}

// ── Time Formatting ──

function timeAgo(dateStr) {
    if (!dateStr) return 'Unknown';
    const diff = Date.now() - new Date(dateStr).getTime();
    if (diff < 60000) return 'Just now';
    
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (mins > 0) return `${mins}m ago`;
    return 'Just now';
}

/**
 * Formats a duration in milliseconds to a human-readable string.
 * Returns null when ms is 0 or falsy so callers can conditionally render.
 * Examples: 412 ms → "412ms", 5300 ms → "5.3s", 125000 ms → "2.1m"
 */
function formatDuration(ms) {
    if (!ms || ms <= 0) return null;
    if (ms >= 60000) return `${(ms / 60000).toFixed(1)}m`;
    if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
    return `${ms}ms`;
}

function formatLargeNumber(num) {
    if (!num && num !== 0) return '-';
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toString();
}

// ── Global State for Sorting ──
window.tableSortState = window.tableSortState || {};
window.tradeDataMap = window.tradeDataMap || {};

// ── Card Builder ──

/**
 * Renders 4 colored Greek exposure pills (Δ Γ Θ V) from a greeks map.
 * @param {Object|null} greeks - e.g. { delta: 'positive', gamma: 'negative', theta: 'positive', vega: 'negative' }
 * @returns {string} HTML string of the .greek-pills container, or '' if no data.
 */
function renderGreeksPills(greeks) {
    if (!greeks) return '';
    const labels = [
        { key: 'delta', symbol: 'Δ' },
        { key: 'gamma', symbol: 'Γ' },
        { key: 'theta', symbol: 'Θ' },
        { key: 'vega',  symbol: 'V' }
    ];
    const pills = labels.map(({ key, symbol }) => {
        const val = (greeks[key] || 'neutral').toLowerCase();
        const cls = val === 'positive' ? 'greek-positive'
                  : val === 'negative' ? 'greek-negative'
                  : 'greek-neutral';
        const sign = val === 'positive' ? '+' : val === 'negative' ? '−' : '';
        return `<span class="greek-pill ${cls}" title="${key.charAt(0).toUpperCase() + key.slice(1)}: ${val}">${symbol}${sign}</span>`;
    }).join('');
    return `<span class="greek-pills">${pills}</span>`;
}

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    const hasTrades = result.trades && result.trades.length > 0;
    card.className = hasTrades ? 'card' : 'card disabled';

    const cardId = (result.strategyId || 'card-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;
    const filterId = `filters-${cardId}`;

    // Determine if we're on the execute page (has the filter form)
    const isExecutePage = !!document.getElementById('strategy-type');

    // Build the "Load Filters" button (only on execute page, only if filterConfig exists)
    const loadFiltersBtn = (isExecutePage && result.filterConfig)
        ? `<button type="button" class="btn btn-primary btn-sm" style="margin-left: auto;" onclick="event.stopPropagation(); loadFiltersFromResult(this)" data-filter-config="${escapeAttr(typeof result.filterConfig === 'string' ? result.filterConfig : JSON.stringify(result.filterConfig))}" data-strategy-name="${escapeAttr(result.strategyName || '')}">⬆ Load Filters</button>`
        : '';

    // Build collapsible filter details section
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

    // Build the delete button (only on execute page, only for Custom results)
    const deleteBtn = (isExecutePage && badgeText === 'Custom' && result.strategyId && !isNaN(result.strategyId))
        ? `<button type="button" class="btn btn-danger btn-sm" style="margin-left: 4px;" onclick="event.stopPropagation(); confirmDeleteCustomResult('${escapeAttr(result.strategyId)}', this.closest('.card'))">🗑 Delete</button>`
        : '';

    // Determine the card's display name, appending securities file if present
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
        <div class="card-params">${formatFilterParams(result.filterConfig)}</div>
        ${filterDetailsHtml}
        <div class="card-content" id="content-${cardId}">
            ${buildTradeTable(result.trades || [], cardId)}
        </div>`;


    // Save trades for sorting
    window.tradeDataMap[cardId] = result.trades || [];

    // Attach click handler only if there are trades
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

    // Attach toggle for filter details section
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

// ── Delete Custom Result ──

/**
 * Shows a confirmation modal before deleting a custom execution result.
 * @param {string} resultId  - The UUID of the custom execution to delete.
 * @param {HTMLElement} card - The card DOM element to remove on success.
 */
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

/**
 * Calls the backend DELETE endpoint and removes the card from the DOM on success.
 */
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

// ── Screener Card Builder ──

function buildScreenerCard(result, isCustom = false) {
    const card = document.createElement('div');
    const hasResults = result.results && result.results.length > 0;
    card.className = hasResults ? 'card' : 'card disabled';

    const cardId = (result.screenerId || 'screener-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    // Detect table type and store data for sorting
    const isDropScreener = hasResults && result.results[0].dropType && result.results[0].dropType.length > 0;
    window.tradeDataMap[cardId] = result.results || [];
    window.tradeDataMap[cardId]._type = isDropScreener ? 'drop' : 'screener';
    
    // Build "Load Filters" button — only for custom results that have stored requestParams
    // and only when we're on the execute-screener page (which has the screener-type select).
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

// ── Screener Table Builder ──

function buildScreenerTable(results, cardId = null) {
    if (!results || results.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">🔎</div>No stocks found</div>';
    }

    // Detect drop screener by checking if first result has dropType
    const isDropScreener = results[0].dropType && results[0].dropType.length > 0;

    if (isDropScreener) {
        return buildDropScreenerTable(results, cardId);
    }

    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;

    // Reuse the same sortable header pattern as trade tables
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
    let maPeriodsSet = new Set();
    
    for (const r of results) {
        if (r.rsi && r.rsi !== 0) hasRsi = true;
        if (r.bollingerLower && r.bollingerLower !== 0) hasBb = true;
        if (r.volume && r.volume !== 0) hasVolume = true;
        if (r.maValues) {
            Object.keys(r.maValues).forEach(k => maPeriodsSet.add(Number(k)));
        }
    }
    const maPeriods = Array.from(maPeriodsSet).sort((a,b)=>b-a);

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            ${th('price', 'Price')}
            ${hasVolume ? th('volume', 'Volume') : ''}
            ${hasRsi ? th('rsi', 'RSI') : ''}
            ${hasBb ? '<th>BB (L-U)</th>' : ''}
            ${maPeriods.map(p => th(`ma_${p}`, `SMA ${p}`)).join('\n            ')}
        </tr></thead><tbody>`;

    for (const r of results) {
        const typeClass = r.signalType === 'BULLISH' ? 'text-success' : (r.signalType === 'BEARISH' ? 'text-danger' : 'text-muted');
        
        // Formatted indicators
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const volume = formatLargeNumber(r.volume);
        const rsi = (typeof r.rsi === 'number') ? rsiValue(r.rsi) : '-';
        const bb = (typeof r.bollingerLower === 'number' && typeof r.bollingerUpper === 'number') 
            ? `${r.bollingerLower.toFixed(1)} - ${r.bollingerUpper.toFixed(1)}` : '-';
        
        // Build detail lines matching Telegram format
        const detailLines = [];
        detailLines.push(`💰 Price: $${(typeof r.currentPrice === 'number') ? r.currentPrice.toFixed(2) : '-'}`);
        detailLines.push(`📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`);

        // RSI with previous + status
        if (typeof r.rsi === 'number' && r.rsi !== 0) {
            let rsiLine = `📈 RSI: ${r.rsi.toFixed(2)}`;
            if (typeof r.previousRsi === 'number' && r.previousRsi !== 0) rsiLine += ` (prev: ${r.previousRsi.toFixed(2)})`;
            if (r.rsiBullishCrossover) rsiLine += ' ⬆️ CROSSOVER';
            else if (r.rsiBearishCrossover) rsiLine += ' ⬇️ CROSSOVER';
            else if (r.rsiOversold) rsiLine += ' 🔴 OVERSOLD';
            else if (r.rsiOverbought) rsiLine += ' 🟢 OVERBOUGHT';
            detailLines.push(rsiLine);
        }

        // Bollinger Bands with position
        if (typeof r.bollingerLower === 'number' && r.bollingerLower !== 0) {
            let bbLine = '📉 BB: ';
            if (r.priceTouchingLowerBand) bbLine += `Touching Lower ($${r.bollingerLower.toFixed(2)})`;
            else if (r.priceTouchingUpperBand) bbLine += `Touching Upper ($${r.bollingerUpper.toFixed(2)})`;
            else bbLine += `Within bands ($${r.bollingerLower.toFixed(2)} - $${r.bollingerUpper.toFixed(2)})`;
            detailLines.push(bbLine);
        }

        // Moving Averages - above/below summary
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
        const techIndicatorsAttr = r.formattedSummary ? escapeAttr(r.formattedSummary) : '';

        html += `<tr class="trade-row" data-details="${detailStr}" data-tech-indicators="${techIndicatorsAttr}">
            <td><strong class="${typeClass}">${r.symbol || ''}</strong></td>
            <td class="text-mono">${price}</td>
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

// ── Drop Screener Table Builder ──

function buildDropScreenerTable(results, cardId = null) {
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
            ${th('price', 'Current Price')}
            ${th('refPrice', 'Ref Price')}
            ${th('dropPct', 'Drop %')}
            ${th('volume', 'Volume')}
            <th>Type</th>
        </tr></thead><tbody>`;

    // Default sort by drop percent descending (biggest drops first) when no explicit sort
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

        // Color intensity based on drop severity
        const dropVal = r.dropPercent || 0;
        const dropColor = dropVal >= 20 ? '#ff4444' : dropVal >= 10 ? '#ff6b6b' : dropVal >= 5 ? '#ffa07a' : '#ffcc80';

        const detailLines = [
            `📉 Drop: ${dropPct}% (${dropType})`,
            `💰 Current: ${price}`,
            `📌 Reference: ${refPrice}`,
            `📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`
        ];
        const detailStr = escapeAttr(detailLines.join('\n'));

        html += `<tr class="trade-row" data-details="${detailStr}">
            <td><strong class="text-danger">${r.symbol || ''}</strong></td>
            <td class="text-mono">${price}</td>
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

// ── Trade Table Builder ──

function buildTradeTable(trades, cardId = null) {
    if (!trades || trades.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">📊</div>No trades found</div>';
    }

    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;

    // Sort Helper
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
            <td class="text-mono">$${(t.underlyingPrice || 0).toFixed(2)}</td>
            <td class="today-perf" data-symbol="${escapeAttr(sym)}"><span class="text-muted">--</span></td>
            <td>${formatLegs(t)}</td>
            <td>${formatExpiryDate(t.expiryDate)} <span class="text-muted">(${t.dte || 0}d)</span></td>
            <td>${creditStr}</td>
            <td class="text-danger">$${(t.maxLoss || 0).toFixed(2)}</td>
            <td>$${(t.netExtrinsicValue || 0).toFixed(2)} <span class="text-muted">(${(t.anulizedNetExtrinsicValueToCapitalPercentage || 0).toFixed(1)}%)</span></td>
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

    // Initialize or toggle state
    if (!window.tableSortState[cardId] || window.tableSortState[cardId].column !== column) {
        window.tableSortState[cardId] = { column: column, direction: 'asc' };
    } else {
        const state = window.tableSortState[cardId];
        if (state.direction === 'asc') {
            state.direction = 'desc';
        } else {
            // Third click: reset sorting state
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
                // ── Shared keys (all table types) ──
                case 'ticker':
                    valA = a.symbol || '';
                    valB = b.symbol || '';
                    return valA.localeCompare(valB) * dirMultiplier;
                case 'volume':
                    valA = a.volume || a.totalVolume || 0;
                    valB = b.volume || b.totalVolume || 0;
                    break;

                // ── Trade table keys ──
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

                // ── Today % performance (trade tables) ──
                case 'todayPct':
                    valA = a._todayPct != null ? a._todayPct : -Infinity;
                    valB = b._todayPct != null ? b._todayPct : -Infinity;
                    break;

                // ── Screener table keys ──
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

                // ── Drop screener keys ──
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

    // Re-render using the correct table builder based on data type
    const contentDiv = document.getElementById(`content-${cardId}`);
    if (contentDiv) {
        const tableType = originalData._type;
        if (tableType === 'drop') {
            contentDiv.innerHTML = buildDropScreenerTable(data, cardId);
        } else if (tableType === 'screener') {
            contentDiv.innerHTML = buildScreenerTable(data, cardId);
        } else {
            contentDiv.innerHTML = buildTradeTable(data, cardId);
            // Re-inject today's performance after sort re-render
            const symbols = [...new Set(data.map(t => t.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, contentDiv);
            }
        }
    }
}

// ── Today's Performance Injection ──

/**
 * Given a list of symbols and a DOM scope, fetches live quote data from
 * /api/quotes and injects the daily price change into every `.today-perf`
 * cell whose data-symbol attribute matches.
 * Also stamps `_todayPct` onto each trade object in tradeDataMap so
 * that sorting by the "Today" column works immediately after injection.
 *
 * @param {string[]} symbols - Unique ticker symbols to fetch
 * @param {Element}  scope   - DOM element to scope the cell query (optional, defaults to document)
 */
async function injectTodayPerformance(symbols, scope = document) {
    if (!symbols || symbols.length === 0) return;
    try {
        const data = await API.get(`/api/quotes?symbols=${symbols.join(',')}`);
        if (!Array.isArray(data)) return;

        // Build a quick lookup map
        const quoteMap = {};
        for (const q of data) {
            quoteMap[q.symbol] = q;
        }

        // Inject into every matching cell in the scope
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

        // Also stamp _todayPct onto trade data objects so sort works
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
        // Silently fail — not critical
        console.warn('Today performance fetch failed:', e.message);
    }
}

/**
 * Collects all unique symbols from rendered result cards in a container,
 * then calls injectTodayPerformance to fetch and display live quote data.
 *
 * @param {Element} container - The results container DOM element
 */
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

/**
 * Renders a formatted option chain data section for each trade leg.
 * Shows the raw OptionData from the Schwab API response for each leg.
 * @param {Array} legsOptionData - Array of {action, optionType, optionData}
 */
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

        const techIndicators = row.dataset.techIndicators;

        row.classList.add('selected');

        const symbol = row.querySelector('td strong')?.textContent || '';
        let legsOptionData = null;
        try {
            const raw = row.dataset.legsOptionData ? decodeAttr(row.dataset.legsOptionData) : null;
            legsOptionData = raw ? JSON.parse(raw) : null;
        } catch(e) {}
        
        // Create detail panel right after the row
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

        // Fetch IV Data
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

// Initialize on page load
document.addEventListener('DOMContentLoaded', initTradeRowClicks);

// ── Filter Params Formatter ──

/**
 * Renders a filter config object as a config-grid HTML layout.
 * Handles nested objects (e.g. shortLeg, longLeg) as labeled subsections.
 */
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
        // technicalFilterSummary: render as a highlighted full-width item
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

    // Render nested objects as subsections
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

/**
 * Renders a technicalFilters map (from strategies-config.json) as a readable subsection.
 * technicalFilters can be a string (preset name), an array (SIMPLE_MOVING_AVERAGE rules), or a map of filter configs.
 */
function renderTechFiltersGrid(technicalFilters) {
    if (!technicalFilters) return '';
    if (typeof technicalFilters === 'string') {
        return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters (Preset)</div><div class="config-grid"><div class="config-item"><span class="config-item-label">Preset</span><span class="config-item-value">${technicalFilters}</span></div></div></div>`;
    }
    if (typeof technicalFilters !== 'object') return '';

    const parts = [];
    for (const [key, val] of Object.entries(technicalFilters)) {
        if (Array.isArray(val)) {
            parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val.join(', ')}</span></div>`);
        } else if (val && typeof val === 'object') {
            let condStr = '';
            if (val.conditions && Array.isArray(val.conditions)) {
                condStr = val.conditions.join(', ');
            } else if (val.condition !== undefined) {
                condStr = String(val.condition);
            }
            parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${condStr || '—'}</span></div>`);
        } else {
            parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${val || '—'}</span></div>`);
        }
    }
    if (parts.length === 0) return '';
    return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters</div><div class="config-grid">${parts.join('')}</div></div>`;
}

function formatFilterParams(filterConfigJson) {
    if (!filterConfigJson) return '';
    try {
        const cfg = typeof filterConfigJson === 'string' ? JSON.parse(filterConfigJson) : filterConfigJson;
        const parts = [];
        if (cfg.targetDTE) parts.push(`DTE: ${cfg.targetDTE}`);
        else if (cfg.minDTE || (cfg.maxDTE && cfg.maxDTE !== 2147483647)) {
            parts.push(`DTE: ${cfg.minDTE || 0}-${cfg.maxDTE === 2147483647 ? '∞' : cfg.maxDTE}`);
        }
        if (cfg.maxUpperBreakevenDelta) parts.push(`Delta < ${cfg.maxUpperBreakevenDelta.toFixed(2)}`);
        if (cfg.maxLossLimit) parts.push(`Max Loss: <$${cfg.maxLossLimit.toFixed(0)}`);
        if (cfg.minReturnOnRisk) parts.push(`Min RoR: ${cfg.minReturnOnRisk}%`);
        if (cfg.minReturnOnRiskCAGR) parts.push(`Min RoR CAGR: ${cfg.minReturnOnRiskCAGR}%`);
        if (cfg.maxBreakEvenPercentage) parts.push(`Max B/E: ${cfg.maxBreakEvenPercentage.toFixed(1)}%`);
        if (cfg.maxNetExtrinsicValueToPricePercentage) parts.push(`Max Ext: ${cfg.maxNetExtrinsicValueToPricePercentage.toFixed(1)}%`);
        return parts.join(', ') || '';
    } catch { return ''; }
}

function escapeAttr(str) {
    return (str || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeHtmlContent(str) {
    return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
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
        const taskText = window.currentExecutionTaskName ? ` — Executing: ${window.currentExecutionTaskName}` : '';
        el.textContent = `Elapsed: ${mins}m ${secs}s${taskText}`;
    }, 1000);
}

function stopTimer() {
    clearInterval(timerInterval);
}

// ── Execution Error Panel ──

/**
 * Shows a persistent error panel at the top of the page displaying
 * all warnings and errors captured during execution.
 * Each alert can be dismissed individually, or all at once.
 * The panel persists until the user manually dismisses it.
 *
 * @param {Array} alerts - Array of {severity, source, message, timestamp}
 */
function showErrorPanel(alerts) {
    if (!alerts || alerts.length === 0) return;

    // Remove existing panel to rebuild
    const existingPanel = document.getElementById('error-panel');
    if (existingPanel) existingPanel.remove();

    const panel = document.createElement('div');
    panel.id = 'error-panel';
    panel.className = 'error-panel';

    const header = document.createElement('div');
    header.className = 'error-panel-header';

    const errorCount = alerts.filter(a => a.severity === 'ERROR').length;
    const warnCount = alerts.filter(a => a.severity === 'WARNING').length;
    const countParts = [];
    if (errorCount > 0) countParts.push(`${errorCount} error${errorCount > 1 ? 's' : ''}`);
    if (warnCount > 0) countParts.push(`${warnCount} warning${warnCount > 1 ? 's' : ''}`);

    header.innerHTML = `
        <div class="error-panel-title">
            <span class="error-panel-icon">⚠️</span>
            <strong>Execution Alerts</strong>
            <span class="error-panel-count">${countParts.join(', ')}</span>
        </div>
        <button class="error-panel-dismiss-all" onclick="dismissErrorPanel()" title="Dismiss All">Dismiss All ✕</button>
    `;
    panel.appendChild(header);

    const list = document.createElement('div');
    list.className = 'error-panel-list';

    alerts.forEach((alert, idx) => {
        const item = document.createElement('div');
        item.className = `error-panel-item error-panel-item-${alert.severity.toLowerCase()}`;
        item.dataset.index = idx;

        const icon = alert.severity === 'ERROR' ? '🔴' : '🟡';
        const timeStr = new Date(alert.timestamp).toLocaleTimeString();

        item.innerHTML = `
            <span class="error-item-icon">${icon}</span>
            <span class="error-item-source">${escapeAttr(alert.source)}</span>
            <span class="error-item-message">${escapeAttr(alert.message)}</span>
            <span class="error-item-time">${timeStr}</span>
            <button class="error-item-dismiss" onclick="dismissSingleAlert(this)" title="Dismiss">✕</button>
        `;
        list.appendChild(item);
    });

    panel.appendChild(list);
    document.body.prepend(panel);
}

/**
 * Dismisses the entire error panel and clears alerts on the backend.
 */
async function dismissErrorPanel() {
    const panel = document.getElementById('error-panel');
    if (panel) panel.remove();
    try { await API.post('/api/clear-errors'); } catch (e) { /* ignore */ }
}

/**
 * Dismisses a single alert item from the error panel.
 * If no more items remain, removes the entire panel and clears backend.
 */
function dismissSingleAlert(btn) {
    const item = btn.closest('.error-panel-item');
    if (item) item.remove();

    // Check if any items remain
    const panel = document.getElementById('error-panel');
    if (panel) {
        const remaining = panel.querySelectorAll('.error-panel-item');
        if (remaining.length === 0) {
            dismissErrorPanel();
        } else {
            // Update count in header
            const errors = panel.querySelectorAll('.error-panel-item-error').length;
            const warnings = panel.querySelectorAll('.error-panel-item-warning').length;
            const parts = [];
            if (errors > 0) parts.push(`${errors} error${errors > 1 ? 's' : ''}`);
            if (warnings > 0) parts.push(`${warnings} warning${warnings > 1 ? 's' : ''}`);
            const countEl = panel.querySelector('.error-panel-count');
            if (countEl) countEl.textContent = parts.join(', ');
        }
    }
}

// ── Execution Status Polling ──

let pollInterval = null;

function startPolling(onComplete) {
    clearInterval(pollInterval);
    pollInterval = setInterval(async () => {
        try {
            const status = await API.get('/api/status');
            if (!status.running) {
                window.currentExecutionTaskName = "";
                clearInterval(pollInterval);
                stopTimer();
                if (status.alerts && status.alerts.length > 0) {
                    showErrorPanel(status.alerts);
                }
                if (onComplete) onComplete();
            } else {
                window.currentExecutionTaskName = status.currentTask || "";
                if (status.alerts && status.alerts.length > 0) {
                    showErrorPanel(status.alerts);
                }
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
    const authed = await initAuth();
    if (!authed) return;
    await loadFilterDescriptions();
    await loadOptionsStrategies();
    await loadOptionsResults();
    await checkExecutionStatus();
    fetchAndRenderMarketStatus();
}

// Loads only options strategies into #strategy-checkboxes (index.html)
async function loadOptionsStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');

    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
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

// Legacy alias — kept for backward compat (execute page uses loadStrategies via initExecutePage)
async function loadStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');
    const screenerContainer = document.getElementById('screener-checkboxes');

    // Load options strategies
    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
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

    // Load technical screeners
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

// Loads only options results into #results-container (index.html)
async function loadOptionsResults() {
    const optionsContainer = document.getElementById('results-container');
    if (!optionsContainer) return;

    try {
        const optionResults = await API.get('/api/results');

        optionsContainer.innerHTML = '';
        if (!optionResults || optionResults.length === 0) {
            optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
        } else {
            for (const r of optionResults) {
                optionsContainer.appendChild(buildResultCard(r));
            }
            // Inject live today's performance into every trade table
            fetchAndInjectTodayPerformance(optionsContainer);
        }
    } catch (e) {
        optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

// Legacy full loadResults (loads both options + screeners) — used by execute page polling callback
async function loadResults() {
    const optionsContainer = document.getElementById('results-container');
    const screenerContainer = document.getElementById('screener-results-container');
    
    if (!optionsContainer && !screenerContainer) return;

    try {
        const [optionResults, screenerResults] = await Promise.all([
            optionsContainer ? API.get('/api/results') : Promise.resolve([]),
            screenerContainer ? API.get('/api/results/screeners').catch(() => []) : Promise.resolve([])
        ]);

        // Render Option Results
        if (optionsContainer) {
            optionsContainer.innerHTML = '';
            if (!optionResults || optionResults.length === 0) {
                optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
            } else {
                for (const r of optionResults) {
                    optionsContainer.appendChild(buildResultCard(r));
                }
                // Inject live today's performance into every trade table
                fetchAndInjectTodayPerformance(optionsContainer);
            }
        }

        // Render Screener Results
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

async function loadFilterDescriptions() {
    try {
        const res = await fetch('/filter-descriptions.json');
        if (res.ok) {
            FILTER_DESCRIPTIONS = await res.json();
        }
    } catch (e) {
        console.error('Failed to load filter descriptions:', e);
    }
}

let activeTooltip = null;

function showFilterHelp(event, key, label) {
    if (event) {
        event.stopPropagation();
        event.preventDefault();
    }

    // Close existing tooltip
    if (activeTooltip) {
        activeTooltip.remove();
        if (activeTooltip.dataset.key === key) {
            activeTooltip = null;
            return;
        }
    }

    const description = FILTER_DESCRIPTIONS[key] || "No description available for this filter.";
    const target = event.currentTarget;
    const rect = target.getBoundingClientRect();

    const tooltip = document.createElement('div');
    tooltip.className = 'tooltip-balloon';
    tooltip.dataset.key = key;
    tooltip.innerHTML = `
        <div style="font-weight: 600; font-size: 0.75rem; color: var(--primary); margin-bottom: 4px; text-transform: uppercase;">${label}</div>
        <div>${description}</div>
        <div class="tooltip-arrow"></div>
    `;

    document.body.appendChild(tooltip);

    // Positioning
    const tooltipRect = tooltip.getBoundingClientRect();
    let left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);
    let top = rect.top - tooltipRect.height - 12; // 12px gap
    let placement = 'top';

    // Check if enough space above
    if (top < 10) {
        top = rect.bottom + 12;
        placement = 'bottom';
    }

    // Keep within viewport horizontal bounds
    const padding = 10;
    if (left < padding) left = padding;
    if (left + tooltipRect.width > window.innerWidth - padding) {
        left = window.innerWidth - tooltipRect.width - padding;
    }

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
    tooltip.setAttribute('data-placement', placement);

    // Position arrow relative to icon
    const arrow = tooltip.querySelector('.tooltip-arrow');
    const arrowLeft = rect.left + (rect.width / 2) - left;
    arrow.style.left = `${arrowLeft}px`;

    // Trigger animation
    setTimeout(() => tooltip.classList.add('active'), 10);

    activeTooltip = tooltip;

    // Global listener to close on click outside
    const closeHandler = (e) => {
        if (!tooltip.contains(e.target) && e.target !== target) {
            tooltip.classList.remove('active');
            setTimeout(() => tooltip.remove(), 150);
            document.removeEventListener('click', closeHandler);
            if (activeTooltip === tooltip) activeTooltip = null;
        }
    };
    // Delay adding listener to prevent immediate trigger
    setTimeout(() => document.addEventListener('click', closeHandler), 10);
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

function toggleSection(sectionId) {
    const body = document.getElementById(sectionId);
    const arrow = document.getElementById('arrow-' + sectionId);
    if (body) body.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
}

function selectAll(check) {
    document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]')
        .forEach(cb => cb.checked = check);
    // Auto-expand sections when selecting all so user can see the selections
    if (check) {
        const body = document.getElementById('strategy-section');
        const arrow = document.getElementById('arrow-strategy-section');
        if (body && !body.classList.contains('open')) {
            body.classList.add('open');
            if (arrow) arrow.classList.add('open');
        }
    }
}

// ── Screener Dashboard page helpers ──

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

// ══════════════════════════════════════
//  PAGE: Screeners Dashboard (screeners.html)
// ══════════════════════════════════════

async function initScreenerDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadScreenerStrategies();
    await loadScreenerResults();
    await checkScreenerExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadScreenerStrategies() {
    const screenerContainer = document.getElementById('screener-checkboxes');
    if (!screenerContainer) return;

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

async function loadScreenerResults() {
    const screenerContainer = document.getElementById('screener-results-container');
    if (!screenerContainer) return;

    try {
        const screenerResults = await API.get('/api/results/screeners').catch(() => []);
        screenerContainer.innerHTML = '';
        if (!screenerResults || screenerResults.length === 0) {
            screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet. Execute a screener to see results.</div>';
        } else {
            for (const r of screenerResults) {
                screenerContainer.appendChild(buildScreenerCard(r));
            }
        }
    } catch (e) {
        screenerContainer.innerHTML = `<div class="empty-state text-danger">Failed to load screener results: ${e.message}</div>`;
    }
}

async function checkScreenerExecutionStatus() {
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
        const res = await API.post('/api/execute', { strategyIndices: [], screenerIndices });
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
    { value: 'BULLISH_BROKEN_WING_BUTTERFLY', label: 'Bullish Broken Wing Butterfly', group: 'bwb' },
    { value: 'BULLISH_ZEBRA', label: 'Bullish ZEBRA', group: 'zebra' },
    { value: 'SHORT_PUT', label: 'Short Put', group: 'short_put' },
];

function getLegFilters(prefix, title) {
    return [
        { key: `${prefix}.minDelta`, label: `${title} Min Delta`, placeholder: '0.10', step: '0.01' },
        { key: `${prefix}.maxDelta`, label: `${title} Max Delta`, placeholder: '0.30', step: '0.01' },
        { key: `${prefix}.minOpenInterest`, label: `${title} Min OI`, placeholder: '100' },
        { key: `${prefix}.minVolume`, label: `${title} Min Volume`, placeholder: '10' },
        { key: `${prefix}.minPremium`, label: `${title} Min Premium`, placeholder: '0.50', step: '0.10' },
        { key: `${prefix}.maxPremium`, label: `${title} Max Premium`, placeholder: '5.00', step: '0.10' },
        { key: `${prefix}.minVolatility`, label: `${title} Min Volatility`, placeholder: '10.0', step: '0.1' },
        { key: `${prefix}.maxVolatility`, label: `${title} Max Volatility`, placeholder: '50.0', step: '0.1' },
    ];
}

// Strategy-specific filter definitions
const STRATEGY_SPECIFIC_FILTERS = {
    credit_spread: [
        ...getLegFilters('shortLeg', 'Short Leg'),
        ...getLegFilters('longLeg', 'Long Leg')
    ],
    iron_condor: [
        ...getLegFilters('putShortLeg', 'Put Short'),
        ...getLegFilters('putLongLeg', 'Put Long'),
        ...getLegFilters('callShortLeg', 'Call Short'),
        ...getLegFilters('callLongLeg', 'Call Long'),
        { key: 'minCombinedCredit', label: 'Min Combined Credit', placeholder: '100' },
    ],
    leap: [
        ...getLegFilters('longCall', 'Long Call'),
        { key: 'minCostSavingsPercent', label: 'Min Cost Savings %', placeholder: '10.0', step: '0.1' },
        { key: 'minCostEfficiencyPercent', label: 'Min Cost Efficiency %', placeholder: '90.0', step: '0.1' },
        { key: 'maxCAGRForBreakEven', label: 'Max Breakeven CAGR', placeholder: '15.0', step: '0.1' },
        { key: 'maxOptionPricePercent', label: 'Max Option Price %', placeholder: '30.0', step: '0.1' },
        { key: 'marginInterestRate', label: 'Margin Interest Rate', placeholder: '6.0', step: '0.1' },
        { key: 'savingsInterestRate', label: 'Savings Interest Rate', placeholder: '10.0', step: '0.1' },
        { key: 'relaxationPriority', label: 'Relaxation Priority (comma separated)', placeholder: 'maxCAGRForBreakEven,maxOptionPricePercent', type: 'text' },
        { key: 'sortPriority', label: 'Sort Priority (comma separated)', placeholder: 'daysToExpiration,costSavingsPercent', type: 'text' },
    ],
    bwb: [
        ...getLegFilters('leg1Long', 'Lower Strike Long Leg'),
        ...getLegFilters('leg2Short', 'Middle Short Legs'),
        ...getLegFilters('leg3Long', 'Upper Strike Long Leg'),
        { key: 'priceVsMaxDebitRatio', label: 'Price/Debit Ratio', placeholder: '2.0', step: '0.1' },
    ],
    zebra: [
        ...getLegFilters('shortCall', 'Short Call'),
        ...getLegFilters('longCall', 'Long Call'),
    ],
    short_put: [
        ...getLegFilters('shortLeg', 'Short Put Leg'),
    ],
};

async function initExecutePage() {
    const authed = await initAuth();
    if (!authed) return;
    const select = document.getElementById('strategy-type');
    if (!select) return;

    STRATEGY_TYPES.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.value;
        opt.textContent = s.label;
        opt.dataset.group = s.group;
        select.appendChild(opt);
    });

    loadFilterDescriptions();

    // Listen for strategy type change to render specific filters
    select.addEventListener('change', () => {
        renderSpecificFilters(select.value);
        renderStrategyTemplates(select.value);
    });

    loadCustomResults();
    checkCustomExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function checkCustomExecutionStatus() {
    try {
        const progress = document.getElementById('custom-progress');
        if (!progress) return;

        const status = await API.get('/api/status');
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || "";
            progress.className = 'progress-container active';
            startTimer(status.startTimeMs);
            startPolling(() => {
                progress.className = 'progress-container';
                stopTimer();
                loadCustomResults();
                showToast('Custom execution completed!');
            });
        } else if (status.alerts && status.alerts.length > 0) {
            // Execution finished but captured alerts — surface them
            showErrorPanel(status.alerts);
        }
    } catch (e) { /* ignore */ }
}

async function renderStrategyTemplates(strategyType) {
    const container = document.getElementById('strategy-templates');
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const strategies = window.appConfig.optionsStrategies || [];
    const matching = strategies.filter(s => s.strategyType === strategyType);

    if (matching.length === 0) {
        container.innerHTML = '';
        return;
    }

    const heading = document.createElement('h4');
    heading.style.fontSize = '0.8rem';
    heading.style.color = 'var(--text-secondary)';
    heading.style.marginBottom = '8px';
    heading.textContent = 'Configured Templates (Click to view, Load to edit)';

    container.innerHTML = '';
    container.appendChild(heading);

    matching.forEach(strategy => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = strategy.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        // Load button to trigger form filling
        const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadTemplateParams('${escapeAttr(JSON.stringify(strategy))}')">Load Filters</button>`;

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${strategy.alias || strategy.strategyType}</strong>
                    <span class="card-badge">${strategy.securitiesFile || 'Custom'}</span>
                    ${enabledPill}
                    ${loadBtn}
                </div>
            </div>
            <div class="config-card-body">
                ${renderFilterGrid(strategy.filter || {})}
                ${renderTechFiltersGrid(strategy.technicalFilters)}
                ${strategy.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${strategy.securities}</span></div>` : ''}
            </div>`;

        // Toggle on header click (but ignore button clicks)
        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
    });
}

function loadTemplateParams(strategyJson) {
    try {
        const strategy = JSON.parse(decodeAttr(strategyJson));

        // 1. Set general fields
        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) aliasEl.value = (strategy.alias || '') + ' (Custom)';

        const secInput = document.getElementById('securities-input');
        if (secInput) {
            secInput.value = strategy.securities || '';
        }

        const secFileInput = document.getElementById('securities-file-input');
        if (secFileInput) {
            secFileInput.value = strategy.securitiesFile || '';
        }

        // 2. Clear existing filters by resetting to empty
        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                // Ignore earnings usually defaults to checked, but let's reset to false for templates
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

        const filter = strategy.filter || {};

        // Helper to flatten object for setting data-filter matches
        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
                if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
                    Object.assign(res, flattenObj(v, prefix + k + '.'));
                } else {
                    res[prefix + k] = v;
                }
            }
            return res;
        };

        const flatFilters = flattenObj(filter);

        // 3. Set filter fields
        for (const [k, v] of Object.entries(flatFilters)) {
            const el = document.querySelector(`[data-filter="${k}"]`);
            if (el) {
                if (el.type === 'checkbox') {
                    el.checked = !!v;
                } else if (Array.isArray(v)) {
                    el.value = v.join(', ');
                } else {
                    el.value = v;
                }
            }
        }

        // 4. Fill Technical Filters
        fillTechFiltersForm(strategy.technicalFilters);

        showToast('Template load complete. Verify inputs before execution.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading template:', e);
        showToast('Failed to load template', 'error');
    }
}

/**
 * Loads filter values from a custom execution result back into the execute form.
 * Called when user clicks "Load Filters" on a result card.
 */
function loadFiltersFromResult(btn) {
    try {
        const filterConfigStr = decodeAttr(btn.dataset.filterConfig);
        const strategyName = decodeAttr(btn.dataset.strategyName || '');
        const filterConfig = JSON.parse(filterConfigStr);

        // 1. Try to detect strategy type from the strategy name
        const typeSelect = document.getElementById('strategy-type');
        if (typeSelect) {
            // Attempt to match by looking for the strategy type name within the strategy name
            let matched = false;
            for (const st of STRATEGY_TYPES) {
                if (strategyName.toUpperCase().includes(st.value) ||
                    strategyName.toLowerCase().includes(st.label.toLowerCase())) {
                    typeSelect.value = st.value;
                    renderSpecificFilters(st.value);
                    renderStrategyTemplates(st.value);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // Try matching against the strategyType field inside filterConfig (some configs carry it)
                if (filterConfig.strategyType) {
                    typeSelect.value = filterConfig.strategyType;
                    renderSpecificFilters(filterConfig.strategyType);
                    renderStrategyTemplates(filterConfig.strategyType);
                }
            }
        }

        // 2. Set alias
        const aliasEl = document.getElementById('alias-input');
        if (aliasEl) aliasEl.value = strategyName ? strategyName + ' (Reload)' : '';

        // 3. Clear existing filters
        document.querySelectorAll('[data-filter]').forEach(inp => {
            if (inp.type === 'checkbox') {
                inp.checked = false;
            } else {
                inp.value = '';
            }
        });

        // 4. Flatten nested filter object for data-filter matching
        const flattenObj = (ob, prefix = '') => {
            let res = {};
            for (const [k, v] of Object.entries(ob)) {
                if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
                    Object.assign(res, flattenObj(v, prefix + k + '.'));
                } else {
                    res[prefix + k] = v;
                }
            }
            return res;
        };

        const flatFilters = flattenObj(filterConfig);

        // 5. Map securities specifically (they lack data-filter mapping)
        if (flatFilters.securitiesFile !== undefined) {
            const secFileEl = document.getElementById('securities-file-input');
            if (secFileEl) secFileEl.value = flatFilters.securitiesFile || '';
        }
        if (flatFilters.securities !== undefined) {
            const secEl = document.getElementById('securities-input');
            if (secEl) {
                secEl.value = Array.isArray(flatFilters.securities) ? flatFilters.securities.join(', ') : flatFilters.securities || '';
            }
        }

        // 6. Set filter fields via data-filter matching
        for (const [k, v] of Object.entries(flatFilters)) {
            if (k === 'maxDTE' && v === 2147483647) continue;
            if ((k === 'targetDTE' || k === 'minDTE' || k === 'minReturnOnRisk' || k === 'minReturnOnRiskCAGR') && v === 0) continue;

            const el = document.querySelector(`[data-filter="${k}"]`);
            if (el) {
                if (el.type === 'checkbox') {
                    el.checked = !!v;
                } else if (Array.isArray(v)) {
                    el.value = v.join(', ');
                } else if (v !== null && v !== undefined) {
                    el.value = v;
                }
            }
        }

        // 7. Fill Technical Filters
        fillTechFiltersForm(filterConfig.technicalFilters);

        showToast('Filters loaded from previous execution. Verify inputs before running.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading filters from result:', e);
        showToast('Failed to load filters', 'error');
    }
}

/**
 * Fills the Technical Filters form inputs from a structured map.
 */
function fillTechFiltersForm(techFilters) {
    // Clear existing
    document.querySelectorAll('[data-tech-filter]').forEach(inp => {
        inp.value = '';
    });

    if (!techFilters || typeof techFilters !== 'object') return;

    for (const [filterKey, val] of Object.entries(techFilters)) {
        if (filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'VOLUME' || filterKey === 'HISTORICAL_VOLATILITY') {
            if (val.conditions || Array.isArray(val)) {
                const rules = Array.isArray(val) ? val.join(', ') : (val.conditions || val);
                const rulesStr = Array.isArray(rules) ? rules.join(', ') : rules;
                const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="rules"]`);
                if (el) el.value = rulesStr;
            }
            if (filterKey === 'SIMPLE_MOVING_AVERAGE') continue;
        }

        if (val && typeof val === 'object') {
            for (const [fieldKey, fieldVal] of Object.entries(val)) {
                if (fieldKey === 'condition') {
                    if (typeof fieldVal === 'string') {
                        const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="condition"]`);
                        if (el) el.value = fieldVal;
                    } else if (typeof fieldVal === 'object') {
                        for (const [condKey, condVal] of Object.entries(fieldVal)) {
                            const mappedKey = condKey === 'type' ? 'condition' : condKey;
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${mappedKey}"]`);
                            if (el) {
                                el.value = condVal;
                                if (mappedKey === 'condition' && typeof el.onchange === 'function') {
                                    el.onchange();
                                }
                            }
                        }
                    }
                } else if (fieldKey === 'config') {
                    if (typeof fieldVal === 'object') {
                        for (const [cfgKey, cfgVal] of Object.entries(fieldVal)) {
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${cfgKey}"]`);
                            if (el) el.value = cfgVal;
                        }
                    }
                }
            }
        }
    }
}

function renderSpecificFilters(strategyValue) {
    const container = document.getElementById('specific-filters');
    if (!container) return;

    const type = STRATEGY_TYPES.find(s => s.value === strategyValue);
    if (!type) { container.innerHTML = ''; return; }

    const filters = STRATEGY_SPECIFIC_FILTERS[type.group] || [];
    if (filters.length === 0) { container.innerHTML = ''; return; }

    let html = '<h4 style="font-size:0.8rem; color:var(--text-secondary); margin: 16px 0 8px; grid-column: 1 / -1">' +
        `${type.label} Specific Leg Filters & Options</h4>`;
    for (const f of filters) {
        const infoBtn = `<button type="button" class="info-btn" onclick="showFilterHelp(event, '${f.key}', '${escapeAttr(f.label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
        if (f.type === 'text') {
            html += `<div class="form-group">
                <label class="form-label">${f.label} ${infoBtn}</label>
                <input type="text" class="form-input" data-filter="${f.key}"
                       placeholder="${f.placeholder}">
            </div>`;
        } else {
            html += `<div class="form-group">
                <label class="form-label">${f.label} ${infoBtn}</label>
                <input type="number" class="form-input" data-filter="${f.key}"
                       placeholder="${f.placeholder}" step="${f.step || '1'}">
            </div>`;
        }
    }
    container.innerHTML = html;
}

async function executeCustom() {
    const typeEl = document.getElementById('strategy-type');
    const securitiesEl = document.getElementById('securities-input');
    const securitiesFileEl = document.getElementById('securities-file-input');
    const aliasEl = document.getElementById('alias-input');

    if (!typeEl.value) { showToast('Select a strategy type', 'error'); return; }

    const hasFile = securitiesFileEl && securitiesFileEl.value.trim();
    const hasTickers = securitiesEl && securitiesEl.value.trim();
    if (!hasFile && !hasTickers) {
        showToast('Provide a securities file, inline tickers, or both', 'error');
        return;
    }

    // Collect all filter values (common + specific)
    const filter = {};
    document.querySelectorAll('[data-filter]').forEach(input => {
        const key = input.dataset.filter;
        let value = null;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.value.trim()) {
            // Handle array types (comma-separated strings)
            if (key === 'relaxationPriority' || key === 'sortPriority') {
                value = input.value.split(',').map(s => s.trim()).filter(s => s);
            } else if (input.type === 'number') {
                value = parseFloat(input.value);
            } else {
                value = input.value.trim();
            }
        }

        if (value !== null) {
            // Handle nested keys like "shortLeg.minDelta"
            if (key.includes('.')) {
                const parts = key.split('.');
                let current = filter;
                for (let i = 0; i < parts.length - 1; i++) {
                    if (!current[parts[i]]) current[parts[i]] = {};
                    current = current[parts[i]];
                }
                current[parts[parts.length - 1]] = value;
            } else {
                filter[key] = value;
            }
        }
    });

    let technicalFilters;
    try {
        technicalFilters = getTechnicalFiltersFromDOM();
    } catch (e) {
        showToast(e.message, 'error');
        return;
    }

    const body = {
        strategyType: typeEl.value,
        securitiesFile: securitiesFileEl ? securitiesFileEl.value.trim() : '',
        securities: securitiesEl ? securitiesEl.value.trim() : '',
        alias: aliasEl ? aliasEl.value : '',
        maxTradesToSend: 30,
        filter,
        technicalFilters
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
        // Inject live today's performance into every trade table
        fetchAndInjectTodayPerformance(container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load: ${e.message}</div>`;
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

// ══════════════════════════════════════
//  PAGE: Config Viewer (config.html)
// ══════════════════════════════════════

async function initConfigPage() {
    const authed = await initAuth();
    if (!authed) return;
    const container = document.getElementById('config-container');
    if (!container) return;
    try {
        await loadFilterDescriptions();
        const [config, securitiesMaps] = await Promise.all([
            API.get('/api/config'),
            API.get('/api/securities').catch(() => ({})) // Fallback if API fails
        ]);
        container.innerHTML = '';
        renderConfig(config, container, securitiesMaps);
        fetchAndRenderMarketStatus();
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load config: ${e.message}</div>`;
    }
}

function renderConfig(config, container, securitiesMaps = {}) {
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

            // Toggle on header click
            card.querySelector('.config-card-header').addEventListener('click', function () {
                this.querySelector('.card-arrow').classList.toggle('open');
                this.nextElementSibling.classList.toggle('open');
            });

            container.appendChild(card);
        });
    }

    // Securities File Section
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
                        <strong>${screener.alias || screener.screenerType || 'Screener'}</strong>
                    </div>
                </div>
                <div class="config-card-body">
                    ${renderTechFiltersGrid(screener.technicalFilters)}
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
            const infoBtn = `<button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, '${key}', '${escapeAttr(label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
            html += `<div class="config-item">
                <span class="config-item-label">${label} ${infoBtn}</span>
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
        const infoBtn = `<button type="button" class="info-btn" style="font-size: 0.8rem; padding: 0" onclick="showFilterHelp(event, '${key}', '${escapeAttr(label)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`;
        html += `<div class="mt-sm"><span class="config-item-label">${label} ${infoBtn}</span> `;
        html += value.map(v => `<span class="card-badge" style="margin:2px">${v}</span>`).join('');
        html += '</div>';
    }

    return html;
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
            <span class="status-badge" style="border: 1px solid rgba(255, 255, 255, 0.1); color: ${equityColor}">
                <span class="status-dot" style="background:${equityColor};"></span>
                Equity: ${statusLabel(eq)}
            </span>
            <span class="status-badge" style="border: 1px solid rgba(255, 255, 255, 0.1); color: ${optionsColor}">
                <span class="status-dot" style="background:${optionsColor};"></span>
                Options: ${statusLabel(opt)}
            </span>
        `;
    } catch (e) {
        statusContainer.innerHTML = `<span class="status-badge" style="color: var(--text-muted); border: 1px solid rgba(255, 255, 255, 0.1)">Market Status Offline</span>`;
        console.error("Could not fetch market status", e);
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}


// -- Shared: dismissAllAlerts (alias for logs.html error panel) --
function dismissAllAlerts() {
    dismissErrorPanel();
}

// ======================================
//  PAGE: Execution Logs (logs.html)
// ======================================

let _logsPollInterval = null;
let currentLogsData = [];
window.logSortColumn = null;
window.logSortAsc = true;

window.handleLogSort = function(column, event) {
    if (event) event.stopPropagation();
    if (window.logSortColumn === column) {
        if (window.logSortAsc) {
            // asc → desc
            window.logSortAsc = false;
        } else {
            // desc → reset (no sort)
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

/**
 * Groups log entries by strategyName and renders collapsible blocks.
 * Entry shape: { strategyName, symbol, filterName, inputCount, outputCount }
 */
function renderLogGroups(entries, container) {
    const groups = {};
    for (const e of entries) {
        const key = e.strategyName || 'Unknown Strategy';
        if (!groups[key]) groups[key] = [];
        groups[key].push(e);
    }
    // Preserve open/closed state of strategy groups, symbol blocks, and expiry blocks
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

        // Split entries: null expiry = symbol-level ("Other"), non-null = per-expiry blocks
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

        // For summary counts: exclude the "Generated Candidates" informational entry
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



/**
 * Renders the content inside an open symbol body:
 * - Per-expiry collapsible sub-blocks (sorted by date)
 * - "Other" collapsible sub-block for null-expiry entries (symbol-level: volatility, DTE)
 */
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

    // Per-expiry collapsible blocks (sorted chronologically)
    const expiryDates = Object.keys(byExpiry).sort();
    if (expiryDates.length > 0) {
        html += '<div class="log-expiry-list">';
        for (const expDate of expiryDates) {
            // Exclude the "Generated Candidates" informational row from the table (tradesIn === tradesOut)
            const expEntries = byExpiry[expDate].filter(e => e.filterStage !== 'Generated Candidates');
            const rawEntries = byExpiry[expDate];
            const expId = 'exp-' + stratSlug + '-' + symSlug + '-' + expDate.replace(/-/g, '');
            const isExpOpen = openExpiries ? openExpiries.has(expId) : false;
            // Use first candidate count as "in" for the block summary
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

    // "Other" block — symbol-level filters without an expiry (Historical Volatility, DTE)
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

// ══════════════════════════════════════
//  PAGE: Execute Screener (execute-screener.html)
// ══════════════════════════════════════

const SCREENER_TYPE_META = {
    RSI_BB_BULLISH_CROSSOVER: { rsi: 'BULLISH_CROSSOVER', bollinger: 'LOWER_BAND', hasDrop: false },
    RSI_BB_BEARISH_CROSSOVER: { rsi: 'BEARISH_CROSSOVER', bollinger: 'UPPER_BAND', hasDrop: false },
    RSI_OVERSOLD:             { rsi: 'OVERSOLD',          bollinger: '',            hasDrop: false },
    BB_LOWER:                 { rsi: '',                  bollinger: 'LOWER_BAND',  hasDrop: false },
    BELOW_200_DAY_MA:         { rsi: '',                  bollinger: '',            hasDrop: false },
    PRICE_DROP:               { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: true  },
    HIGH_52W_DROP:            { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: false },
};

async function initExecuteScreenerPage() {
    const authed = await initAuth();
    if (!authed) return;
    await loadCustomScreenerResults();
    await checkCustomScreenerExecutionStatus();
    
    // Initial render in case there's a default selected
    const select = document.getElementById('screener-type');
    if (select && select.value) {
        onScreenerTypeChange();
    }
}

/** Auto-populate default conditions when the screener type is selected. */
function onScreenerTypeChange() {
    const type = document.getElementById('screener-type').value;
    const meta = SCREENER_TYPE_META[type];

    // Toggle drop-specific fields
    const dropGroup = document.getElementById('sc-priceDropRules-group');
    const lookbackGroup = document.getElementById('sc-lookbackDays-group');
    if (dropGroup) dropGroup.style.display = (meta && meta.hasDrop) ? '' : 'none';
    if (lookbackGroup) lookbackGroup.style.display = (meta && meta.hasLookback) ? '' : 'none';

    if (!meta) {
        renderScreenerTemplates('');
        return;
    }

    // Pre-fill RSI / Bollinger selects with sensible defaults
    const rsiSel = document.getElementById('sc-rsiCondition');
    const bbSel  = document.getElementById('sc-bollingerCondition');
    if (rsiSel && meta.rsi !== undefined) rsiSel.value = meta.rsi;
    if (bbSel  && meta.bollinger !== undefined) bbSel.value = meta.bollinger;

    // Load matching templates
    renderScreenerTemplates(type);
}

async function renderScreenerTemplates(screenerType) {
    const container = document.getElementById('screener-templates');
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const screeners = window.appConfig.technicalScreeners || [];
    const matching = screeners.filter(s => s.screenerType === screenerType);

    if (matching.length === 0) {
        container.innerHTML = '';
        return;
    }

    const heading = document.createElement('h4');
    heading.style.fontSize = '0.8rem';
    heading.style.color = 'var(--text-secondary)';
    heading.style.marginBottom = '8px';
    heading.textContent = 'Configured Templates (Click to view, Load to edit)';

    container.innerHTML = '';
    container.appendChild(heading);

    matching.forEach(screener => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = screener.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadScreenerTemplateParams('${escapeAttr(JSON.stringify(screener))}')">Load Filters</button>`;

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${screener.alias || screener.screenerType}</strong>
                    <span class="card-badge">${screener.securitiesFile || 'Custom'}</span>
                    ${enabledPill}
                    ${loadBtn}
                </div>
            </div>
            <div class="config-card-body">
                ${renderTechFiltersGrid(screener.technicalFilters)}
                ${screener.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${screener.securities}</span></div>` : ''}
            </div>`;

        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
    });
}

function loadScreenerTemplateParams(screenerJson) {
    try {
        const screener = JSON.parse(decodeAttr(screenerJson));

        // Set general fields
        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) aliasEl.value = (screener.alias || '') + ' (Custom)';

        const secInput = document.getElementById('screener-securities-input');
        if (secInput) secInput.value = screener.securities || '';

        const secFileInput = document.getElementById('screener-securities-file-input');
        if (secFileInput) secFileInput.value = screener.securitiesFile || '';

        const techFilters = screener.technicalFilters || {};

        // Set select/number fields
        const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val !== undefined && val !== null ? val : ''; };
        
        // Helper to extract nested condition or config safely
        const extractField = (filterKey, fieldType, prop) => {
            if (!techFilters[filterKey]) return undefined;
            if (fieldType === 'root') return techFilters[filterKey];
            if (!techFilters[filterKey][fieldType]) return undefined;
            if (typeof techFilters[filterKey][fieldType] !== 'object') {
                return prop ? undefined : techFilters[filterKey][fieldType];
            }
            return techFilters[filterKey][fieldType][prop];
        };

        setVal('sc-rsiCondition', extractField('RSI', 'condition'));
        setVal('sc-bollingerCondition', extractField('BOLLINGER_BAND', 'condition'));
        const volConditions = extractField('VOLUME', 'conditions');
        if (volConditions && Array.isArray(volConditions) && volConditions.length > 0) {
            const rulesStrs = volConditions.map(vc => {
                if (typeof vc === 'string') return vc;
                if (vc.type === 'MIN_VOLUME') return `>= ${vc.min}`;
                if (vc.type === 'SMA_COMPARISON') return `SMA${vc.volumeShortSmaPeriod || 20} >= SMA${vc.volumeLongSmaPeriod || 50} * ${vc.volumeThresholdPercent || 90}%`;
                return '';
            }).filter(Boolean);
            setVal('sc-volumeRules', rulesStrs.join(', '));
        }
        let pdRules = extractField('PRICE_DROP', 'root');
        if (pdRules && pdRules.conditions) pdRules = pdRules.conditions;
        setVal('sc-priceDropRules', pdRules && Array.isArray(pdRules) ? pdRules.join(', ') : pdRules || '');
        setVal('sc-lookbackDays', extractField('PRICE_DROP', 'config', 'lookbackDays'));

        let maRules = extractField('SIMPLE_MOVING_AVERAGE', 'root');
        if (maRules && maRules.conditions) maRules = maRules.conditions;
        setVal('sc-movingAverageRules', maRules && Array.isArray(maRules) ? maRules.join(', ') : maRules || '');
        setVal('sc-hvPeriod', extractField('HISTORICAL_VOLATILITY', 'config', 'period'));
        let hvRules = extractField('HISTORICAL_VOLATILITY', 'root');
        if (hvRules && hvRules.conditions) hvRules = hvRules.conditions;
        setVal('sc-hvRules', hvRules && Array.isArray(hvRules) ? hvRules.join(', ') : hvRules || '');

        showToast('Template filters loaded!');
    } catch (e) {
        showToast('Error loading template', 'error');
    }
}

/**
 * Loads the saved filter parameters from a previous custom screener execution
 * into the Execute Screener form. Called by the "Load Filters" button on history cards.
 *
 * @param {string} paramsJson - JSON string of the saved requestParams map.
 * @param {Event}  event      - Click event (stopped to avoid card collapse).
 */
function loadScreenerFiltersFromResult(paramsJson, event) {
    if (event) event.stopPropagation();
    try {
        const params = JSON.parse(decodeAttr(paramsJson));

        // Set screener type first so onScreenerTypeChange can toggle fields
        const typeEl = document.getElementById('screener-type');
        if (typeEl && params.screenerType) {
            typeEl.value = params.screenerType;
            onScreenerTypeChange(); // updates drop-field visibility & loads templates
        }

        // Alias, securities
        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) aliasEl.value = params.alias || '';

        const secFileEl = document.getElementById('screener-securities-file-input');
        if (secFileEl) secFileEl.value = params.securitiesFile || '';

        const secEl = document.getElementById('screener-securities-input');
        if (secEl) secEl.value = params.securities || '';

        // Fill Technical Filters
        fillTechFiltersForm(params.technicalFilters);

        // Scroll up to the form so the user can see the populated fields
        const firstCard = document.querySelector('.main-content .card');
        if (firstCard) firstCard.scrollIntoView({ behavior: 'smooth', block: 'start' });

        showToast('Filters loaded from history!');
    } catch (e) {
        console.error('loadScreenerFiltersFromResult error:', e);
        showToast('Error loading filters from result', 'error');
    }
}

async function executeCustomScreener() {
    const type = document.getElementById('screener-type').value;
    if (!type) { showToast('Select a screener type', 'error'); return; }

    const alias            = document.getElementById('screener-alias-input').value.trim() || null;
    const securitiesFile   = document.getElementById('screener-securities-file-input').value.trim() || null;
    const securities       = document.getElementById('screener-securities-input').value.trim() || null;

    if (!securitiesFile && !securities) {
        showToast('Provide a securities file or tickers', 'error');
        return;
    }

    let technicalFilters;
    try {
        const container = document.getElementById('screener-tech-filters-container');
        technicalFilters = getTechnicalFiltersFromDOM(container || document);
    } catch (e) {
        showToast(e.message, 'error');
        return;
    }

    const payload = {
        screenerType: type,
        alias,
        securitiesFile,
        securities,
        technicalFilters
    };

    // Strip nulls/false to keep the payload clean
    Object.keys(payload).forEach(k => {
        if (payload[k] === null || payload[k] === false || payload[k] === '') delete payload[k];
    });

    try {
        setCustomScreenerBusy(true);
        const res = await API.post('/api/execute/custom-screener', payload);
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setCustomScreenerBusy(false);
            loadCustomScreenerResults();
            showToast('Screener execution completed!');
        });
    } catch (e) {
        setCustomScreenerBusy(false);
        showToast(e.message, 'error');
    }
}

/**
 * Extracts and parses technical filters from data-tech-filter attributes.
 * Throws an Error if validation fails.
 * @param {HTMLElement} container - The container element to search within, defaults to document.
 * @returns {Object|undefined} The parsed technicalFilters object, or undefined if empty.
 */
function getTechnicalFiltersFromDOM(container = document) {
    const technicalFilters = {};
    container.querySelectorAll('[data-tech-filter]').forEach(input => {
        const filterKey = input.dataset.techFilter;
        const fieldKey = input.dataset.techField;
        const rawVal = (input.tagName === 'SELECT' ? input.value : input.value.trim());
        if (!rawVal) return;

        if (!technicalFilters[filterKey]) technicalFilters[filterKey] = {};

        if ((filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'VOLUME' || filterKey === 'HISTORICAL_VOLATILITY' || filterKey === 'PRICE_DROP') && fieldKey === 'rules') {
            technicalFilters[filterKey] = { conditions: rawVal.split(',').map(s => s.trim()).filter(Boolean) };
        } else if (fieldKey === 'condition') {
            if (typeof technicalFilters[filterKey].condition === 'object') {
                technicalFilters[filterKey].condition.type = rawVal;
            } else {
                technicalFilters[filterKey].condition = rawVal;
            }
        } else if (fieldKey === 'period') {
            const num = parseInt(rawVal);
            if (!isNaN(num)) {
                if (!technicalFilters[filterKey].config) technicalFilters[filterKey].config = {};
                technicalFilters[filterKey].config.period = num;
            }
        } else if (['min', 'max', 'lookbackDays'].includes(fieldKey)) {
            const num = parseFloat(rawVal);
            if (!isNaN(num)) {
                if (filterKey === 'VOLUME') {
                    if (!technicalFilters[filterKey].conditions) {
                        technicalFilters[filterKey].conditions = [ { type: 'MIN_VOLUME' } ];
                    }
                    technicalFilters[filterKey].conditions[0][fieldKey] = num;
                } else {
                    if (!technicalFilters[filterKey].condition || typeof technicalFilters[filterKey].condition === 'string') {
                        const existingType = typeof technicalFilters[filterKey].condition === 'string' ? technicalFilters[filterKey].condition : null;
                        technicalFilters[filterKey].condition = {};
                        if (existingType) technicalFilters[filterKey].condition.type = existingType;
                    }
                    technicalFilters[filterKey].condition[fieldKey] = num;
                }
            }
        }
    });

    if (Object.keys(technicalFilters).length === 0) {
        return undefined;
    }

    if (technicalFilters.RSI && technicalFilters.RSI.condition && technicalFilters.RSI.condition.type === 'CUSTOM_RANGE') {
        if (technicalFilters.RSI.condition.min === undefined || technicalFilters.RSI.condition.max === undefined) {
            throw new Error('Min RSI and Max RSI are mandatory for Custom Range condition.');
        }
    }

    return technicalFilters;
}

async function loadCustomScreenerResults() {
    const container = document.getElementById('screener-custom-results');
    if (!container) return;
    try {
        const results = await API.get('/api/results/custom/screeners');
        container.innerHTML = '';
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔬</div>No screener results yet. Run a custom screener above.</div>';
        } else {
            for (const r of results) {
                container.appendChild(buildScreenerCard(r, true));
            }
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
    }
}

async function checkCustomScreenerExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.alerts && status.alerts.length > 0) showErrorPanel(status.alerts);
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || '';
            setCustomScreenerBusy(true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setCustomScreenerBusy(false);
                loadCustomScreenerResults();
                showToast('Screener execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

function setCustomScreenerBusy(busy) {
    const progress = document.getElementById('screener-custom-progress');
    if (progress) progress.style.display = busy ? 'block' : 'none';
}
