const {
    initEarningsCalendar,
    calNavigate,
    calGoToday,
    renderCalendar,
    createDayCell,
    formatDateKey,
    onDayClick,
    closeDetailPanel,
    API
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();

describe('Earnings Calendar Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
    });

    test('initEarningsCalendar fetches events and renders calendar', async () => {
        const eventsData = {
            events: {
                '2026-04-17': [
                    { symbol: 'AAPL', hour: 'bmo', quarter: 1, year: 2026, epsEstimate: 1.5, revenueEstimate: 90000000000 }
                ]
            }
        };

        API.get = jest.fn().mockResolvedValueOnce(eventsData);

        document.body.innerHTML = `
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
        expect(document.querySelectorAll('.cal-day').length).toBeGreaterThan(25);
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

    test('onDayClick opens detail panel with earnings events table', () => {
        document.body.innerHTML = `
            <div id="cal-grid"></div>
            <div id="cal-detail-panel" style="display:none">
                <div id="cal-detail-date"></div>
                <div id="cal-detail-body"></div>
            </div>
        `;

        const date = new Date(2026, 3, 17);
        onDayClick('2026-04-17', date);

        const panel = document.getElementById('cal-detail-panel');
        expect(panel.style.display).toBe('block');

        closeDetailPanel();
        expect(panel.style.display).toBe('none');
    });
});
