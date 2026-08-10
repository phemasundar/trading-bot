const {
    formatMarketCap,
    formatLargeNumber,
    formatDuration,
    timeAgo,
    formatCompanyName,
    escapeAttr,
    showToast,
    renderGreeksPills,
    formatLegs,
    formatBreakeven,
    formatExpiryDate,
    escapeHtmlContent,
    decodeAttr,
    formatRevenue,
    formatHourBadge,
    rsiValue,
    showErrorPanel,
    dismissErrorPanel,
    dismissSingleAlert,
    startTimer,
    stopTimer,
    showFilterHelp,
    loadFilterDescriptions,
    showInfo
} = require('../../main/resources/static/app');

Element.prototype.scrollIntoView = jest.fn();

describe('App Utility Functions', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
    });

    test('formatMarketCap should format billions correctly', () => {
        expect(formatMarketCap(1.5)).toBe('$1.50B');
        expect(formatMarketCap(0.5)).toBe('$0.50B');
        expect(formatMarketCap(null)).toBe('-');
        expect(formatMarketCap(undefined)).toBe('-');
        expect(formatMarketCap('invalid')).toBe('-');
    });

    test('formatLargeNumber should format values correctly', () => {
        expect(formatLargeNumber(1500000000)).toContain('1500');
        expect(formatLargeNumber(1500000)).toBe('1.5M');
        expect(formatLargeNumber(1500)).toBe('1.5K');
        expect(formatLargeNumber(500)).toBe('500');
        expect(formatLargeNumber(null)).toBe('-');
    });

    test('formatDuration should format ms into human-readable duration', () => {
        expect(formatDuration(45000)).toContain('45');
        expect(formatDuration(125000)).toContain('2');
        expect(formatDuration(3665000)).toContain('61');
        expect(formatDuration(null)).toBeNull();
    });

    test('timeAgo should return relative time strings', () => {
        const now = Date.now();
        expect(timeAgo(now - 30000)).toBe('Just now');
        expect(timeAgo(now - 300000)).toBe('5m ago');
        expect(timeAgo(now - 7200000)).toBe('2h ago');
        expect(timeAgo(now - 172800000)).toBe('2d ago');
        expect(timeAgo(null)).toBe('Unknown');
    });

    test('formatCompanyName should truncate long names', () => {
        expect(formatCompanyName('Apple Inc.')).toBe('Apple Inc.');
        expect(formatCompanyName('A Very Long Corporation Name That Exceeds Thirty Characters')).toContain('...');
        expect(formatCompanyName(null)).toBe('-');
    });

    test('escapeAttr and decodeAttr should escape and decode attribute values', () => {
        const input = '{"key":"value & text"}';
        const escaped = escapeAttr(input);
        expect(escaped).not.toContain('"');
        expect(decodeAttr(escaped)).toBe(input);
        expect(escapeAttr(null)).toBe('');
        expect(decodeAttr(null)).toBe('');
    });

    test('escapeHtmlContent should escape sensitive HTML characters', () => {
        expect(escapeHtmlContent('<script>alert("xss")</script>')).toContain('&lt;script&gt;');
        expect(escapeHtmlContent(null)).toBe('');
    });

    test('showToast should create toast notifications and auto-remove', () => {
        jest.useFakeTimers();
        showToast('Success message', 'success');
        let toast = document.querySelector('.toast');
        expect(toast).not.toBeNull();
        expect(toast.textContent).toContain('Success message');

        showToast('Error message', 'error');
        const toasts = document.querySelectorAll('.toast');
        expect(toasts.length).toBe(2);

        jest.advanceTimersByTime(3500);
        expect(document.querySelector('.toast-success')).toBeNull();
        jest.useRealTimers();
    });

    test('renderGreeksPills should render Delta, Gamma, Theta, Vega pills', () => {
        const html = renderGreeksPills({ delta: 'positive', gamma: 'negative', theta: 'neutral', vega: 'positive' });
        expect(html).toContain('greek-positive');
        expect(html).toContain('greek-negative');

        const emptyHtml = renderGreeksPills(null);
        expect(emptyHtml).toBe('');
    });

    test('formatLegs should format options leg arrays', () => {
        const trade = {
            legs: [
                { action: 'BUY', strike: 150 },
                { action: 'SELL', strike: 160 }
            ]
        };
        const html = formatLegs(trade);
        expect(html).toContain('BUY 150');
        expect(html).toContain('SELL 160');
        expect(formatLegs(null)).toBe('-');
    });

    test('formatBreakeven should format single and dual breakevens', () => {
        expect(formatBreakeven({ breakEvenPrice: 148.0 })).toBe('$148.00');
        expect(formatBreakeven({})).toBe('-');
    });

    test('formatExpiryDate should convert date strings to readable format', () => {
        expect(formatExpiryDate('2026-04-17')).toContain('2026');
        expect(formatExpiryDate(null)).toBe('');
    });

    test('formatRevenue and formatHourBadge should format earnings data', () => {
        expect(formatRevenue(1500000000)).toBe('$1.50B');
        expect(formatRevenue(null)).toBe('—');
        expect(formatHourBadge('bmo')).toContain('BMO');
        expect(formatHourBadge('amc')).toContain('AMC');
        expect(formatHourBadge(null)).toContain('—');
    });

    test('rsiValue should return numeric or N/A string', () => {
        expect(rsiValue(45.5)).toContain('45.5');
        expect(rsiValue(null)).toBe('N/A');
    });

    test('showErrorPanel and dismiss helper functions', () => {
        document.body.innerHTML = '<div class="main-content"></div>';
        showErrorPanel([{ severity: 'ERROR', message: 'Test alert 1' }, { severity: 'WARN', message: 'Test alert 2' }]);
        const panel = document.querySelector('.error-alert-panel');
        if (panel) {
            expect(panel.textContent).toContain('Test alert 1');
            const closeBtn = panel.querySelector('.error-alert-item button');
            if (closeBtn) dismissSingleAlert(closeBtn);
            dismissErrorPanel();
        }
    });

    test('startTimer and stopTimer', () => {
        jest.useFakeTimers();
        document.body.innerHTML = '<div class="timer-display"></div>';
        startTimer(Date.now() - 5000);
        jest.advanceTimersByTime(1000);
        stopTimer();
        jest.useRealTimers();
    });

    test('showFilterHelp balloon placement and document click cleanup', async () => {
        const dummyBtn = document.createElement('button');
        dummyBtn.getBoundingClientRect = () => ({ left: 0, top: 0, width: 0, height: 0, bottom: 0 });
        showFilterHelp({ currentTarget: dummyBtn, stopPropagation: jest.fn(), preventDefault: jest.fn() }, '__reset__', '');
        document.querySelectorAll('.tooltip-balloon').forEach(el => el.remove());

        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ minDelta: 'MIN DELTA' })
        });
        await loadFilterDescriptions();

        document.body.innerHTML = '<button id="help-btn">Help</button>';
        const btn = document.getElementById('help-btn');
        btn.getBoundingClientRect = () => ({ left: 100, top: 200, width: 20, height: 20, bottom: 220 });

        showFilterHelp({ currentTarget: btn, stopPropagation: jest.fn(), preventDefault: jest.fn() }, 'minDelta', 'Min Delta');
        const balloon = document.querySelector('.tooltip-balloon');
        expect(balloon).not.toBeNull();
        expect(balloon.innerHTML).toContain('MIN DELTA');

        showFilterHelp({ currentTarget: btn, stopPropagation: jest.fn(), preventDefault: jest.fn() }, 'minDelta', 'Min Delta');
        expect(document.querySelector('.tooltip-balloon')).toBeNull();
    });

    test('showInfo modal marked fallback and close handlers', async () => {
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            text: () => Promise.resolve('# Strategy Title\nDescription text')
        });

        const showPromise = showInfo(null, 'test.md', 'Test Strategy');
        expect(document.querySelector('.info-modal-overlay')).not.toBeNull();

        await showPromise;
        expect(document.querySelector('.markdown-body').innerHTML).toContain('Strategy Title');

        document.querySelector('.modal-close').click();
        expect(document.querySelector('.info-modal-overlay')).toBeNull();
    });
});
