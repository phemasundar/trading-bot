/**
 * Trading Bot — Shared UI & Formatting Utilities
 */

// Global State for Table Sorting & Shared Trade Data
window.tableSortState = window.tableSortState || {};
window.tradeDataMap = window.tradeDataMap || {};

/** Mobile Sidebar Drawer Toggle */
function toggleSidebar() {
    const sidebar = document.querySelector('.sidebar');
    if (sidebar) sidebar.classList.toggle('open');
}

/** Escapes HTML special characters to prevent XSS in dynamic content */
function escapeAttr(str) {
    if (!str && str !== 0) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** Toast Notifications */
function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    if (type === 'error') {
        toast.innerHTML = `
            <span class="toast-message">${message}</span>
            <button class="toast-dismiss" onclick="this.parentElement.remove()" title="Dismiss">✕</button>
        `;
    } else {
        toast.textContent = message;
        setTimeout(() => toast.remove(), 4000);
    }
    document.body.appendChild(toast);
}

/** Time & Duration Formatters */
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

function formatCompanyName(name) {
    if (!name) return '-';
    return name.length > 10 ? name.substring(0, 10) + '...' : name;
}

function formatMarketCap(capB) {
    if (capB == null) return '-';
    return `$${capB.toFixed(2)}B`;
}

/** Greek Exposure Pills Renderer */
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

/** Live Market Status Badge */
async function updateMarketStatusBadge() {
    try {
        const data = await API.getMarketStatus();
        const eq = data.equityStatus || 'CLOSED';
        const opt = data.optionsStatus || 'CLOSED';

        const container = document.getElementById('market-status-container');
        if (!container) return;

        const badgeCls = (st) => st === 'OPEN' ? 'status-open' : (st === 'PRE_MARKET' || st === 'POST_MARKET' ? 'status-extended' : 'status-closed');
        container.innerHTML = `
            <span class="market-badge ${badgeCls(eq)}">Eq: ${eq}</span>
            <span class="market-badge ${badgeCls(opt)}">Opt: ${opt}</span>
        `;
    } catch (e) {
        console.warn('Failed to update market status:', e);
    }
}
