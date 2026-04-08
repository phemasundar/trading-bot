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
    post(path, body) { return this.request('POST', path, body); }
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
    if (diff < 60000) return 'Just now';
    
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);
    
    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (mins > 0) return `${mins}m ago`;
    return 'Just now';
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

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    const hasTrades = result.trades && result.trades.length > 0;
    card.className = hasTrades ? 'card' : 'card disabled';

    const cardId = (result.strategyId || 'card-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap">
                ${arrow}
                <span class="card-name">${result.strategyName || 'Unknown'}</span>
                ${result.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${result.descriptionFile}', '${escapeAttr(result.strategyName)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>` : ''}
                <span class="card-badge">${badgeText}</span>
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Trades: ${result.tradesFound || 0}</span>
        </div>
        <div class="card-params">${formatFilterParams(result.filterConfig)}</div>
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

    return card;
}

function toggleCard(id) {
    const content = document.getElementById(`content-${id}`);
    const arrow = document.getElementById(`arrow-${id}`);
    if (!content) return;
    content.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
}

// ── Screener Card Builder ──

function buildScreenerCard(result) {
    const card = document.createElement('div');
    const hasResults = result.results && result.results.length > 0;
    card.className = hasResults ? 'card' : 'card disabled';

    const cardId = (result.screenerId || 'screener-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    // Detect table type and store data for sorting
    const isDropScreener = hasResults && result.results[0].dropType && result.results[0].dropType.length > 0;
    window.tradeDataMap[cardId] = result.results || [];
    window.tradeDataMap[cardId]._type = isDropScreener ? 'drop' : 'screener';

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap">
                ${arrow}
                <span class="card-name">${result.screenerName || 'Screener'}</span>
                <span class="card-badge" style="background-color: var(--primary); color: #fff;">Screener</span>
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Found: ${result.resultsFound || 0}</span>
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

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            ${th('price', 'Price')}
            ${th('volume', 'Volume')}
            ${th('rsi', 'RSI')}
            <th>BB (L-U)</th>
            ${th('ma200', 'MA 200')}
            ${th('ma100', 'MA 100')}
            ${th('ma50', 'MA 50')}
            ${th('ma20', 'MA 20')}
        </tr></thead><tbody>`;

    for (const r of results) {
        const typeClass = r.signalType === 'BULLISH' ? 'text-success' : (r.signalType === 'BEARISH' ? 'text-danger' : 'text-muted');
        
        // Formatted indicators
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const volume = formatLargeNumber(r.volume);
        const rsi = (typeof r.rsi === 'number') ? rsiValue(r.rsi) : '-';
        const bb = (typeof r.bollingerLower === 'number' && typeof r.bollingerUpper === 'number') 
            ? `${r.bollingerLower.toFixed(1)} - ${r.bollingerUpper.toFixed(1)}` : '-';
        
        const ma200 = (typeof r.ma200 === 'number' && r.ma200 !== 0) ? r.ma200.toFixed(2) : '-';
        const ma100 = (typeof r.ma100 === 'number' && r.ma100 !== 0) ? r.ma100.toFixed(2) : '-';
        const ma50 = (typeof r.ma50 === 'number' && r.ma50 !== 0) ? r.ma50.toFixed(2) : '-';
        const ma20 = (typeof r.ma20 === 'number' && r.ma20 !== 0) ? r.ma20.toFixed(2) : '-';

        // Build detail lines matching Telegram format
        const detailLines = [];
        detailLines.push(`💰 Price: $${(typeof r.currentPrice === 'number') ? r.currentPrice.toFixed(2) : '-'}`);
        detailLines.push(`📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`);

        // RSI with previous + status
        if (typeof r.rsi === 'number') {
            let rsiLine = `📈 RSI: ${r.rsi.toFixed(2)}`;
            if (typeof r.previousRsi === 'number') rsiLine += ` (prev: ${r.previousRsi.toFixed(2)})`;
            if (r.rsiBullishCrossover) rsiLine += ' ⬆️ CROSSOVER';
            else if (r.rsiBearishCrossover) rsiLine += ' ⬇️ CROSSOVER';
            else if (r.rsiOversold) rsiLine += ' 🔴 OVERSOLD';
            else if (r.rsiOverbought) rsiLine += ' 🟢 OVERBOUGHT';
            detailLines.push(rsiLine);
        }

        // Bollinger Bands with position
        if (typeof r.bollingerLower === 'number') {
            let bbLine = '📉 BB: ';
            if (r.priceTouchingLowerBand) bbLine += `Touching Lower ($${r.bollingerLower.toFixed(2)})`;
            else if (r.priceTouchingUpperBand) bbLine += `Touching Upper ($${r.bollingerUpper.toFixed(2)})`;
            else bbLine += `Within bands ($${r.bollingerLower.toFixed(2)} - $${r.bollingerUpper.toFixed(2)})`;
            detailLines.push(bbLine);
        }

        // Moving Averages - above/below summary
        const belowMAs = [];
        const aboveMAs = [];
        if (typeof r.ma20 === 'number' && r.ma20 !== 0) { r.priceBelowMA20 ? belowMAs.push('MA20') : aboveMAs.push('MA20'); }
        if (typeof r.ma50 === 'number' && r.ma50 !== 0) { r.priceBelowMA50 ? belowMAs.push('MA50') : aboveMAs.push('MA50'); }
        if (typeof r.ma100 === 'number' && r.ma100 !== 0) { r.priceBelowMA100 ? belowMAs.push('MA100') : aboveMAs.push('MA100'); }
        if (typeof r.ma200 === 'number' && r.ma200 !== 0) { r.priceBelowMA200 ? belowMAs.push('MA200') : aboveMAs.push('MA200'); }
        if (belowMAs.length > 0 || aboveMAs.length > 0) {
            let maLine = '📊 MAs: ';
            if (belowMAs.length > 0) maLine += `Below ${belowMAs.join(', ')}`;
            if (belowMAs.length > 0 && aboveMAs.length > 0) maLine += ' | ';
            if (aboveMAs.length > 0) maLine += `Above ${aboveMAs.join(', ')}`;
            detailLines.push(maLine);
        }

        const detailStr = escapeAttr(detailLines.join('\n'));

        html += `<tr class="trade-row" data-details="${detailStr}">
            <td><strong class="${typeClass}">${r.symbol || ''}</strong></td>
            <td class="text-mono">${price}</td>
            <td>${volume}</td>
            <td>${rsi}</td>
            <td class="text-muted small">${bb}</td>
            <td>${ma200}</td>
            <td>${ma100}</td>
            <td>${ma50}</td>
            <td>${ma20}</td>
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
                    valA = a.maxReturnOnRiskPercentage || a.returnOnRisk || 0;
                    valB = b.maxReturnOnRiskPercentage || b.returnOnRisk || 0;
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
        }
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
        const taskText = window.currentExecutionTaskName ? ` — Executing: ${window.currentExecutionTaskName}` : '';
        el.textContent = `Elapsed: ${mins}m ${secs}s${taskText}`;
    }, 1000);
}

function stopTimer() {
    clearInterval(timerInterval);
}

// ── Auth Error Banner ──

/**
 * Shows a persistent red banner at the top of the page when a Schwab
 * API authentication error is detected. The banner has a dismiss button
 * that also calls /api/clear-error on the backend.
 */
function showAuthErrorBanner(message) {
    // Only show one banner at a time
    if (document.getElementById('auth-error-banner')) return;

    const banner = document.createElement('div');
    banner.id = 'auth-error-banner';
    banner.style.cssText = [
        'position: fixed', 'top: 0', 'left: 0', 'right: 0', 'z-index: 99999',
        'background: linear-gradient(135deg, #c0392b, #e74c3c)',
        'color: #fff', 'padding: 12px 20px',
        'display: flex', 'align-items: center', 'gap: 12px',
        'font-size: 0.9rem', 'font-family: var(--font-sans, sans-serif)',
        'box-shadow: 0 4px 16px rgba(0,0,0,0.4)',
        'animation: slideInDown 0.3s ease'
    ].join('; ');

    banner.innerHTML = `
        <span style="font-size:1.2rem;">⚠️</span>
        <strong style="margin-right:4px;">Authentication Error:</strong>
        <span style="flex:1;">${message}</span>
        <button onclick="dismissAuthErrorBanner()" style="
            background: rgba(255,255,255,0.25); border: 1px solid rgba(255,255,255,0.5);
            color:#fff; padding:4px 12px; border-radius:6px; cursor:pointer;
            font-size:0.8rem; white-space:nowrap;
        ">Dismiss ✕</button>
    `;

    document.body.prepend(banner);
}

async function dismissAuthErrorBanner() {
    const banner = document.getElementById('auth-error-banner');
    if (banner) banner.remove();
    try { await API.post('/api/clear-error'); } catch (e) { /* ignore */ }
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
                // Show auth error banner if the backend detected a token failure
                if (status.lastError) {
                    showAuthErrorBanner(status.lastError);
                }
                if (onComplete) onComplete();
            } else {
                window.currentExecutionTaskName = status.currentTask || "";
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
    await loadStrategies();
    await loadResults();
    await checkExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');
    const screenerContainer = document.getElementById('screener-checkboxes');

    // Load options strategies
    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
            strategyContainer.innerHTML = strategies.map(s => `
                <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="strategy">
                        <span>${s.name}</span>
                    </label>
                    ${s.descriptionFile ? `<button type="button" class="info-btn" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(s.name)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>` : ''}
                </div>`).join('');
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

async function loadResults() {
    const optionsContainer = document.getElementById('results-container');
    const screenerContainer = document.getElementById('screener-results-container');
    
    if (!optionsContainer || !screenerContainer) return;

    try {
        const [optionResults, screenerResults] = await Promise.all([
            API.get('/api/results'),
            API.get('/api/results/screeners').catch(() => []) // Graceful fail for screeners
        ]);

        // Render Option Results
        optionsContainer.innerHTML = '';
        if (!optionResults || optionResults.length === 0) {
            optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
        } else {
            for (const r of optionResults) {
                optionsContainer.appendChild(buildResultCard(r));
            }
        }

        // Render Screener Results
        screenerContainer.innerHTML = '';
        if (!screenerResults || screenerResults.length === 0) {
            screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet.</div>';
        } else {
            for (const r of screenerResults) {
                screenerContainer.appendChild(buildScreenerCard(r));
            }
        }
    } catch (e) {
        optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        screenerContainer.innerHTML = '';
    }
}

async function checkExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || "";
            setDashboardBusy(true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setDashboardBusy(false);
                loadResults();
                showToast('Execution completed!');
            });
        } else if (status.lastError) {
            // Execution finished but left an auth error — surface it
            showAuthErrorBanner(status.lastError);
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
    document.querySelectorAll('#screener-checkboxes input[type="checkbox"]')
        .forEach(cb => cb.checked = check);
    // Auto-expand sections when selecting all so user can see the selections
    if (check) {
        ['strategy-section', 'screener-section'].forEach(id => {
            const body = document.getElementById(id);
            const arrow = document.getElementById('arrow-' + id);
            if (body && !body.classList.contains('open')) {
                body.classList.add('open');
                if (arrow) arrow.classList.add('open');
            }
        });
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
        } else if (status.lastError) {
            // Execution finished but left an auth error — surface it
            showAuthErrorBanner(status.lastError);
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

        showToast('Template load complete. Verify inputs before execution.');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
        console.error('Error loading template:', e);
        showToast('Failed to load template', 'error');
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

    const body = {
        strategyType: typeEl.value,
        securitiesFile: securitiesFileEl ? securitiesFileEl.value.trim() : '',
        securities: securitiesEl ? securitiesEl.value.trim() : '',
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
    }
}

