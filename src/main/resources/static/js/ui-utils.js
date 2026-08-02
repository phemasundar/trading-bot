/**
 * Trading Bot — Shared UI & Formatting Utilities
 */

let FILTER_DESCRIPTIONS = {};
window.tableSortState = window.tableSortState || {};
window.tradeDataMap = window.tradeDataMap || {};
let timerInterval = null;
let pollInterval = null;

/** Mobile Sidebar Drawer Toggle */
function toggleSidebar() {
    const sidebar = document.querySelector('.sidebar');
    if (sidebar) sidebar.classList.toggle('open');
}

function toggleSection(sectionId) {
    const body = document.getElementById(sectionId);
    const arrow = document.getElementById('arrow-' + sectionId);
    if (body) body.classList.toggle('open');
    if (arrow) arrow.classList.toggle('open');
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

async function fetchAndRenderMarketStatus() {
    await updateMarketStatusBadge();
}

/** Timer & Polling Logic */
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

function startPolling(onComplete) {
    clearInterval(pollInterval);
    pollInterval = setInterval(async () => {
        try {
            const status = await API.getStatus();
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

function setDashboardBusy(busy) {
    const execBtn = document.getElementById('execute-btn');
    const stopBtn = document.getElementById('stop-btn');
    const progress = document.getElementById('progress-container');
    if (execBtn) execBtn.disabled = busy;
    if (stopBtn) stopBtn.style.display = busy ? 'inline-flex' : 'none';
    if (progress) progress.className = busy ? 'progress-container active' : 'progress-container';
}

function showErrorPanel(alerts) {
    if (!alerts || alerts.length === 0) return;

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

async function dismissErrorPanel() {
    const panel = document.getElementById('error-panel');
    if (panel) panel.remove();
    try { await API.clearErrors(); } catch (e) { /* ignore */ }
}

function dismissAllAlerts() {
    dismissErrorPanel();
}

function dismissSingleAlert(btn) {
    const item = btn.closest('.error-panel-item');
    if (item) item.remove();

    const panel = document.getElementById('error-panel');
    if (panel) {
        const remaining = panel.querySelectorAll('.error-panel-item');
        if (remaining.length === 0) {
            dismissErrorPanel();
        } else {
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

async function showInfo(event, filename, strategyName) {
    if (event) {
        event.stopPropagation();
        event.preventDefault();
    }

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

    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.remove();
    });

    try {
        const res = await fetch(`/descriptions/${filename}`);
        if (!res.ok) throw new Error('File not found');
        const text = await res.text();

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

let activeTooltip = null;

function showFilterHelp(event, key, label) {
    if (event) {
        event.stopPropagation();
        event.preventDefault();
    }

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

    const tooltipRect = tooltip.getBoundingClientRect();
    let left = rect.left + (rect.width / 2) - (tooltipRect.width / 2);
    let top = rect.top - tooltipRect.height - 12;
    let placement = 'top';

    if (top < 10) {
        top = rect.bottom + 12;
        placement = 'bottom';
    }

    const padding = 10;
    if (left < padding) left = padding;
    if (left + tooltipRect.width > window.innerWidth - padding) {
        left = window.innerWidth - tooltipRect.width - padding;
    }

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
    tooltip.setAttribute('data-placement', placement);

    const arrow = tooltip.querySelector('.tooltip-arrow');
    const arrowLeft = rect.left + (rect.width / 2) - left;
    arrow.style.left = `${arrowLeft}px`;

    setTimeout(() => tooltip.classList.add('active'), 10);
    activeTooltip = tooltip;

    const closeHandler = (e) => {
        if (!tooltip.contains(e.target) && e.target !== target) {
            tooltip.classList.remove('active');
            setTimeout(() => tooltip.remove(), 150);
            document.removeEventListener('click', closeHandler);
            if (activeTooltip === tooltip) activeTooltip = null;
        }
    };
    setTimeout(() => document.addEventListener('click', closeHandler), 10);
}
