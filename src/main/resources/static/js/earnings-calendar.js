/**
 * Trading Bot — Earnings Calendar
 * Monthly calendar view and event details (earnings-calendar.html).
 */

let _calEvents = {};
let _calLastUpdated = null;
let _calCurrentDate = new Date();
let _calSelectedDate = null;
let _calRefreshPollTimer = null;

const MONTH_NAMES = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];

async function initEarningsCalendar(preserveMonth = false) {
    let res = null;
    try {
        res = await API.get('/api/earnings-calendar');
        _calEvents = res.events || {};
        _calLastUpdated = res.lastUpdated || null;
    } catch (e) {
        console.error('Failed to load earnings calendar:', e);
        _calEvents = {};
        _calLastUpdated = null;
    }
    renderLastPulled(_calLastUpdated);
    if (!preserveMonth) {
        _calCurrentDate = new Date();
    }
    renderCalendar();

    if (preserveMonth && _calSelectedDate) {
        const parts = _calSelectedDate.split('-').map(Number);
        if (parts.length === 3) {
            onDayClick(_calSelectedDate, new Date(parts[0], parts[1] - 1, parts[2]));
        }
    }

    const btn = document.getElementById('cal-refresh-btn');
    const textEl = document.getElementById('cal-refresh-text');
    const isSyncing = Boolean(res && res.refreshing);
    if (btn) {
        btn.disabled = isSyncing;
        btn.classList.toggle('syncing', isSyncing);
        if (textEl) {
            textEl.textContent = isSyncing ? (res.currentTask || 'Syncing…') : 'Refresh Earnings';
        }
    }

    if (isSyncing) {
        if (!_calRefreshPollTimer) {
            _calRefreshPollTimer = setInterval(() => initEarningsCalendar(true), 1500);
        }
    } else if (_calRefreshPollTimer) {
        stopEarningsRefreshPolling();
        if (typeof showToast === 'function') {
            showToast('Earnings calendar refreshed for all project stocks', 'success');
        }
    }
}

/**
 * Triggers a forced earnings refresh for all stocks across all predefined YAML files.
 */
async function refreshAllEarnings() {
    const btn = document.getElementById('cal-refresh-btn');
    const textEl = document.getElementById('cal-refresh-text');
    if (btn) {
        btn.disabled = true;
        btn.classList.add('syncing');
        if (textEl) textEl.textContent = 'Syncing…';
    }
    try {
        const res = await API.post('/api/earnings-calendar/refresh', {});
        if (res && res.error) {
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('syncing');
                if (textEl) textEl.textContent = 'Refresh Earnings';
            }
            if (typeof showToast === 'function') showToast(res.error, 'error');
            return;
        }
        if (!_calRefreshPollTimer) {
            _calRefreshPollTimer = setInterval(() => initEarningsCalendar(true), 1500);
        }
    } catch (e) {
        console.error('Failed to refresh earnings calendar:', e);
        if (btn) {
            btn.disabled = false;
            btn.classList.remove('syncing');
            if (textEl) textEl.textContent = 'Refresh Earnings';
        }
        if (typeof showToast === 'function') showToast('Failed to refresh earnings calendar', 'error');
    }
}

function stopEarningsRefreshPolling() {
    if (_calRefreshPollTimer) {
        clearInterval(_calRefreshPollTimer);
        _calRefreshPollTimer = null;
    }
}

/**
 * Formats and renders the page-level earnings cache pull timestamp in America/Chicago (CDT/CST).
 */
function renderLastPulled(isoStr) {
    const el = document.getElementById('cal-last-pulled-text');
    if (!el) return;
    if (!isoStr || isNaN(new Date(isoStr).getTime())) {
        el.textContent = 'Last Pulled: —';
        return;
    }
    const isMobile = typeof window !== 'undefined' && window.innerWidth <= 640;
    const formatted = new Date(isoStr).toLocaleString('en-US', {
        timeZone: 'America/Chicago',
        month: 'short',
        day: 'numeric',
        ...(isMobile ? {} : { year: 'numeric' }),
        hour: 'numeric',
        minute: '2-digit',
        timeZoneName: 'short'
    });
    const age = typeof timeAgo === 'function' ? timeAgo(isoStr) : '';
    const prefix = isMobile ? 'Pulled: ' : 'Last Pulled: ';
    el.textContent = age && age !== 'Unknown'
        ? `${prefix}${formatted} (${age})`
        : `${prefix}${formatted}`;
}

function calNavigate(delta) {
    _calCurrentDate.setMonth(_calCurrentDate.getMonth() + delta);
    _calSelectedDate = null;
    closeDetailPanel();
    renderCalendar();
}

function calGoToday() {
    _calCurrentDate = new Date();
    _calSelectedDate = null;
    closeDetailPanel();
    renderCalendar();
}

function renderCalendar() {
    const grid = document.getElementById('cal-grid');
    if (!grid) return;

    const year = _calCurrentDate.getFullYear();
    const month = _calCurrentDate.getMonth();

    const label = document.getElementById('cal-month-label');
    if (label) label.textContent = `${MONTH_NAMES[month]} ${year}`;

    while (grid.children.length > 7) {
        grid.removeChild(grid.lastChild);
    }

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const startDow = firstDay.getDay();
    const daysInMonth = lastDay.getDate();

    const prevMonthLast = new Date(year, month, 0).getDate();
    for (let i = startDow - 1; i >= 0; i--) {
        const day = prevMonthLast - i;
        const d = new Date(year, month - 1, day);
        grid.appendChild(createDayCell(d, true));
    }

    const today = new Date();
    for (let day = 1; day <= daysInMonth; day++) {
        const d = new Date(year, month, day);
        const isToday = d.getFullYear() === today.getFullYear()
            && d.getMonth() === today.getMonth()
            && d.getDate() === today.getDate();
        grid.appendChild(createDayCell(d, false, isToday));
    }

    const totalCells = startDow + daysInMonth;
    const remaining = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);
    for (let i = 1; i <= remaining; i++) {
        const d = new Date(year, month + 1, i);
        grid.appendChild(createDayCell(d, true));
    }
}

/**
 * Deduplicates earnings events for a single day by symbol.
 * Canonicalizes Alphabet (GOOGL -> GOOG) to ensure Alphabet is only rendered once per release date.
 */
function deduplicateCalendarEvents(events) {
    if (!Array.isArray(events)) return [];
    const map = new Map();
    for (const ev of events) {
        if (!ev || !ev.symbol) continue;
        const rawSym = String(ev.symbol).trim().toUpperCase();
        const dedupeKey = rawSym === 'GOOGL' ? 'GOOG' : rawSym;
        if (!map.has(dedupeKey)) {
            map.set(dedupeKey, { ...ev, symbol: dedupeKey });
        } else {
            const existing = map.get(dedupeKey);
            if (existing && (!existing.epsEstimate && !existing.revenueEstimate) && (ev.epsEstimate || ev.revenueEstimate)) {
                map.set(dedupeKey, { ...ev, symbol: dedupeKey });
            }
        }
    }
    return Array.from(map.values());
}

function createDayCell(date, isOtherMonth, isToday = false) {
    const cell = document.createElement('div');
    cell.className = 'cal-day';
    if (isOtherMonth) cell.classList.add('cal-other-month');
    if (isToday) cell.classList.add('cal-today');

    const dateStr = formatDateKey(date);
    if (_calSelectedDate === dateStr) cell.classList.add('cal-selected');

    const num = document.createElement('div');
    num.className = 'cal-day-number';
    num.textContent = date.getDate();
    cell.appendChild(num);

    const rawEvents = _calEvents[dateStr] || [];
    const events = deduplicateCalendarEvents(rawEvents);
    if (events.length > 0) {
        const eventsDiv = document.createElement('div');
        eventsDiv.className = 'cal-day-events';

        const maxShow = 3;
        const toShow = events.slice(0, maxShow);
        toShow.forEach(ev => {
            const chip = document.createElement('div');
            chip.className = 'cal-event-chip';
            if (ev.hour === 'bmo') chip.classList.add('cal-event-bmo');
            else if (ev.hour === 'amc') chip.classList.add('cal-event-amc');
            chip.textContent = ev.symbol;
            if (ev.lastFetched) {
                chip.title = `${ev.symbol} (${(ev.hour || 'TBD').toUpperCase()}) • Pulled: ${formatExpiryDate(ev.lastFetched)}`;
            }
            eventsDiv.appendChild(chip);
        });

        if (events.length > maxShow) {
            const more = document.createElement('div');
            more.className = 'cal-event-more';
            more.textContent = `+${events.length - maxShow} more`;
            eventsDiv.appendChild(more);
        }

        cell.appendChild(eventsDiv);
    }

    cell.addEventListener('click', () => onDayClick(dateStr, date));
    return cell;
}

function formatDateKey(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function onDayClick(dateStr, date) {
    _calSelectedDate = dateStr;
    renderCalendar();

    const rawEvents = _calEvents[dateStr] || [];
    const events = deduplicateCalendarEvents(rawEvents);
    const panel = document.getElementById('cal-detail-panel');
    const dateLabel = document.getElementById('cal-detail-date');
    const body = document.getElementById('cal-detail-body');
    if (!panel || !body) return;

    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    dateLabel.textContent = date.toLocaleDateString('en-US', options);

    if (events.length === 0) {
        body.innerHTML = '<div class="cal-empty-msg">No events on this day</div>';
    } else {
        const sorted = [...events].sort((a, b) => (a.symbol || '').localeCompare(b.symbol || ''));
        let html = `<table class="cal-detail-table">
            <thead>
                <tr>
                    <th>Symbol</th>
                    <th>Timing</th>
                    <th>Quarter</th>
                    <th>EPS Est.</th>
                    <th>EPS Act.</th>
                    <th>Rev. Est.</th>
                    <th>Rev. Act.</th>
                    <th>Last Pulled</th>
                </tr>
            </thead>
            <tbody>`;
        sorted.forEach(ev => {
            const hourBadge = formatHourBadge(ev.hour);
            const quarter = ev.quarter && ev.year ? `Q${ev.quarter} ${ev.year}` : '—';
            const pulledTs = (ev.lastFetched && ev.lastFetched.length === 10 && _calLastUpdated && _calLastUpdated.startsWith(ev.lastFetched))
                ? _calLastUpdated
                : ev.lastFetched;
            const pulledCell = ev.lastFetched
                ? `<span class="cal-pulled-date">${escapeAttr(formatExpiryDate(ev.lastFetched))}<span class="cal-pulled-age"> (${escapeAttr(timeAgo(pulledTs))})</span></span>`
                : '—';
            html += `<tr>
                <td class="cal-symbol">${escapeAttr(ev.symbol)}</td>
                <td>${hourBadge}</td>
                <td>${quarter}</td>
                <td>${ev.epsEstimate || '—'}</td>
                <td>${ev.epsActual || '—'}</td>
                <td>${formatRevenue(ev.revenueEstimate)}</td>
                <td>${formatRevenue(ev.revenueActual)}</td>
                <td>${pulledCell}</td>
            </tr>`;
        });
        html += '</tbody></table>';
        body.innerHTML = html;
    }

    panel.style.display = 'block';
    panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function closeDetailPanel() {
    const panel = document.getElementById('cal-detail-panel');
    if (panel) panel.style.display = 'none';
    _calSelectedDate = null;
    document.querySelectorAll('.cal-day.cal-selected').forEach(el => el.classList.remove('cal-selected'));
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    Object.assign(global, utils, authApi);

    module.exports = {
        initEarningsCalendar,
        refreshAllEarnings,
        stopEarningsRefreshPolling,
        renderLastPulled,
        calNavigate,
        calGoToday,
        renderCalendar,
        createDayCell,
        formatDateKey,
        onDayClick,
        closeDetailPanel,
        deduplicateCalendarEvents
    };
}
