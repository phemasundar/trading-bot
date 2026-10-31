const {
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
    deduplicateCalendarEvents,
    API
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();

describe('Earnings Calendar Tests', () => {
    beforeEach(() => {
        stopEarningsRefreshPolling();
        document.body.innerHTML = '';
        jest.restoreAllMocks();
    });

    afterEach(() => {
        stopEarningsRefreshPolling();
    });

    test('initEarningsCalendar fetches events, renders pull time and calendar', async () => {
        const eventsData = {
            lastUpdated: '2026-10-22T13:48:00-05:00',
            refreshing: false,
            events: {
                '2026-04-17': [
                    { symbol: 'AAPL', hour: 'bmo', quarter: 1, year: 2026, epsEstimate: 1.5, revenueEstimate: 90000000000, lastFetched: '2026-10-03' },
                    { symbol: 'MSFT', hour: 'amc', quarter: 1, year: 2026, epsEstimate: 2.5, revenueEstimate: 60000000000, lastFetched: '2026-10-03' },
                    { symbol: 'GOOG', hour: 'amc', quarter: 1, year: 2026, epsEstimate: 1.8, revenueEstimate: 80000000000, lastFetched: '2026-10-03' },
                    { symbol: 'META', hour: 'amc', quarter: 1, year: 2026, epsEstimate: 4.5, revenueEstimate: 40000000000, lastFetched: '2026-10-03' }
                ]
            }
        };

        API.get = jest.fn().mockResolvedValueOnce(eventsData);

        document.body.innerHTML = `
            <span id="cal-last-pulled-text">Last Pulled: —</span>
            <button id="cal-refresh-btn"><span id="cal-refresh-text">Refresh Earnings</span></button>
            <div id="cal-month-label"></div>
            <div id="cal-grid">
                <div>Sun</div><div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div>
            </div>
            <div id="cal-detail-panel" style="display:none">
                <div id="cal-detail-date"></div>
                <div id="cal-detail-body"></div>
            </div>
        `;

        await initEarningsCalendar();
        expect(document.getElementById('cal-month-label').textContent).not.toBe('');
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('Last Pulled: Oct 22, 2026');
        expect(document.querySelectorAll('.cal-day').length).toBeGreaterThan(25);
        expect(document.getElementById('cal-refresh-btn').disabled).toBe(false);

        const cell = createDayCell(new Date(2026, 3, 17), false, false);
        expect(cell.querySelectorAll('.cal-event-chip').length).toBe(3);
        expect(cell.querySelector('.cal-event-more').textContent).toBe('+1 more');
    });

    test('initEarningsCalendar handles API error and active refreshing state', async () => {
        API.get = jest.fn().mockRejectedValueOnce(new Error('Network error'));
        document.body.innerHTML = `<span id="cal-last-pulled-text"></span><div id="cal-grid"></div>`;
        await initEarningsCalendar();
        expect(document.getElementById('cal-last-pulled-text').textContent).toBe('Last Pulled: —');

        API.get = jest.fn().mockResolvedValueOnce({
            lastUpdated: '2026-10-22T13:48:00-05:00',
            refreshing: true,
            currentTask: 'Pulling Earnings (5/126): AAPL...',
            events: {}
        });
        document.body.innerHTML = `
            <span id="cal-last-pulled-text"></span>
            <button id="cal-refresh-btn"><span id="cal-refresh-text">Refresh Earnings</span></button>
            <div id="cal-grid"></div>
        `;
        await initEarningsCalendar();
        expect(document.getElementById('cal-refresh-btn').disabled).toBe(true);
        expect(document.getElementById('cal-refresh-text').textContent).toContain('Pulling Earnings (5/126): AAPL...');
        stopEarningsRefreshPolling();
    });

    test('refreshAllEarnings triggers refresh endpoint, shows syncing progress, and polls via initEarningsCalendar until complete', async () => {
        document.body.innerHTML = `
            <span id="cal-last-pulled-text">Last Pulled: —</span>
            <button id="cal-refresh-btn">
                <span id="cal-refresh-icon">↻</span>
                <span id="cal-refresh-text">Refresh Earnings</span>
            </button>
            <div id="cal-month-label"></div>
            <div id="cal-grid">
                <div>Sun</div><div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div>
            </div>
        `;

        API.post = jest.fn().mockResolvedValueOnce({
            status: 'started',
            refreshing: true,
            totalSymbols: 126
        });

        await refreshAllEarnings();

        const btn = document.getElementById('cal-refresh-btn');
        const text = document.getElementById('cal-refresh-text');
        expect(API.post).toHaveBeenCalledWith('/api/earnings-calendar/refresh', {});
        expect(btn.disabled).toBe(true);
        expect(btn.classList.contains('syncing')).toBe(true);
        expect(text.textContent).toContain('Syncing…');

        // Simulate poll while still refreshing
        API.get = jest.fn().mockResolvedValueOnce({
            lastUpdated: '2026-10-22T23:49:00-05:00',
            refreshing: true,
            currentTask: 'Pulling Earnings (60/126): NVDA...',
            events: {}
        });
        await initEarningsCalendar(true);
        expect(text.textContent).toContain('Pulling Earnings (60/126): NVDA...');

        // Simulate poll completion
        API.get = jest.fn().mockResolvedValueOnce({
            lastUpdated: '2026-10-22T23:50:00-05:00',
            refreshing: false,
            currentTask: '',
            events: {}
        });

        await initEarningsCalendar(true);
        expect(btn.disabled).toBe(false);
        expect(btn.classList.contains('syncing')).toBe(false);
        expect(text.textContent).toBe('Refresh Earnings');
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('Last Pulled: Oct 22, 2026');
    });

    test('refreshAllEarnings handles error response and thrown error gracefully', async () => {
        document.body.innerHTML = `
            <button id="cal-refresh-btn"><span id="cal-refresh-text">Refresh Earnings</span></button>
        `;
        API.post = jest.fn().mockResolvedValueOnce({ error: 'An execution is already running' });
        await refreshAllEarnings();
        expect(document.getElementById('cal-refresh-btn').disabled).toBe(false);

        API.post = jest.fn().mockRejectedValueOnce(new Error('Server down'));
        await refreshAllEarnings();
        expect(document.getElementById('cal-refresh-btn').disabled).toBe(false);
    });

    test('renderLastPulled handles valid and empty inputs', () => {
        document.body.innerHTML = `<span id="cal-last-pulled-text"></span>`;
        renderLastPulled(null);
        expect(document.getElementById('cal-last-pulled-text').textContent).toBe('Last Pulled: —');

        renderLastPulled('invalid-date');
        expect(document.getElementById('cal-last-pulled-text').textContent).toBe('Last Pulled: —');

        renderLastPulled('2026-10-22T13:48:00-05:00');
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('Last Pulled: Oct 22, 2026');
    });

    test('calNavigate and calGoToday updates month view', () => {
        document.body.innerHTML = `
            <div id="cal-month-label"></div>
            <div id="cal-grid">
                <div>Sun</div><div>Mon</div><div>Tue</div><div>Wed</div><div>Thu</div><div>Fri</div><div>Sat</div>
            </div>
        `;
        renderCalendar();
        const initialMonth = document.getElementById('cal-month-label').textContent;

        calNavigate(1);
        expect(document.getElementById('cal-month-label').textContent).not.toBe(initialMonth);

        calGoToday();
        expect(document.getElementById('cal-month-label').textContent).toBe(initialMonth);
    });

    test('onDayClick opens detail panel with earnings events table', async () => {
        API.get = jest.fn().mockResolvedValueOnce({
            lastUpdated: '2026-10-22T13:48:00-05:00',
            refreshing: false,
            events: {
                '2026-04-17': [
                    { symbol: 'AAPL', hour: 'bmo', quarter: 1, year: 2026, epsEstimate: 1.5, revenueEstimate: 90000000000, lastFetched: '2026-10-03' }
                ]
            }
        });

        document.body.innerHTML = `
            <div id="cal-grid"></div>
            <div id="cal-detail-panel" style="display:none">
                <div id="cal-detail-date"></div>
                <div id="cal-detail-body"></div>
            </div>
        `;

        await initEarningsCalendar();
        const date = new Date(2026, 3, 17);
        onDayClick('2026-04-17', date);

        const panel = document.getElementById('cal-detail-panel');
        expect(panel.style.display).toBe('block');
        const bodyHtml = document.getElementById('cal-detail-body').innerHTML;
        expect(bodyHtml).toContain('Last Pulled');
        expect(bodyHtml).toContain('2026-10-03');

        // Also test empty day
        onDayClick('2026-04-18', new Date(2026, 3, 18));
        expect(document.getElementById('cal-detail-body').innerHTML).toContain('No events on this day');

        closeDetailPanel();
        expect(panel.style.display).toBe('none');
    });

    test('deduplicateCalendarEvents consolidates GOOGL into GOOG and avoids duplicates', () => {
        const raw = [
            { symbol: 'GOOGL', hour: 'amc', quarter: 3, year: 2026, epsEstimate: null },
            { symbol: 'GOOG', hour: 'amc', quarter: 3, year: 2026, epsEstimate: 3.11, revenueEstimate: 132000000000 },
            { symbol: 'MSFT', hour: 'amc', quarter: 1, year: 2026 }
        ];
        const result = deduplicateCalendarEvents(raw);
        expect(result.length).toBe(2);
        const symbols = result.map(e => e.symbol);
        expect(symbols).toContain('GOOG');
        expect(symbols).toContain('MSFT');
        expect(symbols).not.toContain('GOOGL');
        const goog = result.find(e => e.symbol === 'GOOG');
        expect(goog.epsEstimate).toBe(3.11);
    });

    test('renderLastPulled adapts text prefix and year for mobile viewport', () => {
        document.body.innerHTML = '<span id="cal-last-pulled-text"></span>';
        const iso = '2026-10-26T12:43:00-05:00';

        // Desktop
        window.innerWidth = 1024;
        renderLastPulled(iso);
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('Last Pulled:');
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('2026');

        // Mobile
        window.innerWidth = 400;
        renderLastPulled(iso);
        expect(document.getElementById('cal-last-pulled-text').textContent).toContain('Pulled:');
    });
});
