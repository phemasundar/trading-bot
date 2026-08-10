/**
 * Trading Bot — Earnings Calendar
 * Monthly calendar view and event details (earnings-calendar.html).
 */

let _calEvents = {};
let _calCurrentDate = new Date();
let _calSelectedDate = null;

const MONTH_NAMES = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];

async function initEarningsCalendar() {
    try {
        const res = await API.get('/api/earnings-calendar');
        _calEvents = res.events || {};
    } catch (e) {
        console.error('Failed to load earnings calendar:', e);
        _calEvents = {};
    }
    _calCurrentDate = new Date();
    renderCalendar();
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

    const events = _calEvents[dateStr] || [];
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

    const events = _calEvents[dateStr] || [];
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
                </tr>
            </thead>
            <tbody>`;
        sorted.forEach(ev => {
            const hourBadge = formatHourBadge(ev.hour);
            const quarter = ev.quarter && ev.year ? `Q${ev.quarter} ${ev.year}` : '—';
            html += `<tr>
                <td class="cal-symbol">${escapeAttr(ev.symbol)}</td>
                <td>${hourBadge}</td>
                <td>${quarter}</td>
                <td>${ev.epsEstimate || '—'}</td>
                <td>${ev.epsActual || '—'}</td>
                <td>${formatRevenue(ev.revenueEstimate)}</td>
                <td>${formatRevenue(ev.revenueActual)}</td>
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
        calNavigate,
        calGoToday,
        renderCalendar,
        createDayCell,
        formatDateKey,
        onDayClick,
        closeDetailPanel
    };
}
