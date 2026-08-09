const {
    formatMarketCap,
    formatLargeNumber,
    formatDuration,
    timeAgo,
    formatCompanyName,
    toggleSidebar,
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
    rsiValue
} = require('../../main/resources/static/app');

describe('App Utility Functions', () => {
    test('formatMarketCap should format billions correctly', () => {
        expect(formatMarketCap(1.5)).toBe('$1.50B');
        expect(formatMarketCap(0.5)).toBe('$0.50B');
        expect(formatMarketCap(null)).toBe('-');
        expect(formatMarketCap(undefined)).toBe('-');
    });

    test('formatLargeNumber should format numbers appropriately', () => {
        expect(formatLargeNumber(1500000000)).toBe('1500.0M');
        expect(formatLargeNumber(1500000)).toBe('1.5M');
        expect(formatLargeNumber(1500)).toBe('1.5K');
        expect(formatLargeNumber(null)).toBe('-');
    });

    test('formatCompanyName should truncate and format names', () => {
        expect(formatCompanyName('Apple Inc.')).toBe('Apple Inc.');
        expect(formatCompanyName('A very long company name that exceeds the limit')).toBe('A very lon...');
        expect(formatCompanyName(null)).toBe('-');
    });

    test('formatDuration should handle milliseconds', () => {
        expect(formatDuration(1500)).toBe('1.5s');
        expect(formatDuration(0)).toBeNull();
    });

    test('timeAgo should calculate relative time', () => {
        const now = new Date();
        const tenSecondsAgo = new Date(now.getTime() - 10000);
        const oneHourAgo = new Date(now.getTime() - 3600000);
        
        expect(timeAgo(tenSecondsAgo.toISOString())).toBe('Just now');
        expect(timeAgo(oneHourAgo.toISOString())).toMatch(/1h|60m/); // Depends on exact rounding
    });
});

describe('DOM and String Utilities', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('escapeAttr should escape HTML entities', () => {
        expect(escapeAttr('<script>alert("xss & \'")</script>'))
            .toBe('&lt;script&gt;alert(&quot;xss &amp; &#39;&quot;)&lt;/script&gt;');
        expect(escapeAttr(null)).toBe('');
    });

    test('toggleSidebar should toggle open class', () => {
        document.body.innerHTML = '<div class="sidebar"></div>';
        toggleSidebar();
        expect(document.querySelector('.sidebar').classList.contains('open')).toBe(true);
        toggleSidebar();
        expect(document.querySelector('.sidebar').classList.contains('open')).toBe(false);
    });

    test('showToast should render success toast and auto-dismiss', () => {
        showToast('Operation successful', 'success');
        const toast = document.querySelector('.toast');
        expect(toast).not.toBeNull();
        expect(toast.classList.contains('toast-success')).toBe(true);
        expect(toast.textContent).toBe('Operation successful');
        
        jest.advanceTimersByTime(4000);
        expect(document.querySelector('.toast')).toBeNull(); // it removes itself
    });

    test('showToast should render error toast that persists', () => {
        showToast('Operation failed', 'error');
        const toast = document.querySelector('.toast');
        expect(toast).not.toBeNull();
        expect(toast.classList.contains('toast-error')).toBe(true);
        expect(toast.innerHTML).toContain('Operation failed');
        expect(toast.innerHTML).toContain('<button'); // has dismiss button
        
        jest.advanceTimersByTime(5000);
        expect(document.querySelector('.toast')).not.toBeNull(); // does not auto-dismiss
    });

    test('renderGreeksPills should render HTML based on greeks data', () => {
        const greeks = { delta: 'positive', gamma: 'negative', theta: 'neutral' };
        const html = renderGreeksPills(greeks);
        expect(html).toContain('greek-positive');
        expect(html).toContain('greek-negative');
        expect(html).toContain('greek-neutral');
        expect(renderGreeksPills(null)).toBe('');
    });
});

describe('Trade Formatting Functions', () => {
    test('formatExpiryDate should extract date from ISO string', () => {
        expect(formatExpiryDate('2026-04-17T20:00:00.000+00:00')).toBe('2026-04-17');
        expect(formatExpiryDate(null)).toBe('');
        expect(formatExpiryDate('')).toBe('');
    });

    test('formatLegs should format option legs array', () => {
        const trade = {
            legs: [
                { action: 'BUY', quantity: 2, strike: 150.5, optionType: 'CALL' },
                { action: 'SELL', quantity: 1, strike: 160, optionType: 'PUT' }
            ]
        };
        const html = formatLegs(trade);
        expect(html).toContain('leg-buy');
        expect(html).toContain('BUY 2x 151 CALL');
        expect(html).toContain('leg-sell');
        expect(html).toContain('SELL 160 PUT');
        expect(html).toContain('<br>');
    });

    test('formatLegs should return - if no legs', () => {
        expect(formatLegs({})).toBe('-');
        expect(formatLegs({ legs: [] })).toBe('-');
    });

    test('formatBreakeven should handle single and double breakevens', () => {
        const singleBreakeven = { breakEvenPrice: 155.50, breakEvenPercent: 2.5 };
        const html1 = formatBreakeven(singleBreakeven);
        expect(html1).toContain('$155.50');
        expect(html1).toContain('(2.5%)');
        
        const doubleBreakeven = { 
            breakEvenPrice: 155.50, breakEvenPercent: 2.5,
            upperBreakEvenPrice: 165.50, upperBreakEvenPercent: 8.5
        };
        const html2 = formatBreakeven(doubleBreakeven);
        expect(html2).toContain('$155.50');
        expect(html2).toContain('$165.50');
        expect(html2).toContain('<br>');
    });

    test('formatBreakeven should include CAGR if present', () => {
        const trade = { breakEvenPrice: 155.50, breakevenCAGR: 15.4 };
        const html = formatBreakeven(trade);
        expect(html).toContain('CAGR: 15.4%');
    });

    test('formatBreakeven should return - if no data', () => {
        expect(formatBreakeven({})).toBe('-');
    });

    test('rsiValue should color-code based on value', () => {
        expect(rsiValue(25)).toContain('text-success');
        expect(rsiValue(25)).toContain('25.0');
        
        expect(rsiValue(75)).toContain('text-danger');
        expect(rsiValue(75)).toContain('75.0');
        
        expect(rsiValue(50)).not.toContain('text-success');
        expect(rsiValue(50)).not.toContain('text-danger');
        expect(rsiValue(50)).toContain('50.0');
    });

    test('formatRevenue should format numbers properly', () => {
        expect(formatRevenue(1500000000)).toBe('$1.50B');
        expect(formatRevenue(1500000)).toBe('$1.5M');
        expect(formatRevenue(1500)).toBe('$1,500');
        expect(formatRevenue(null)).toBe('—');
        expect(formatRevenue('invalid')).toBe('—');
    });

    test('formatHourBadge should return HTML span based on hour', () => {
        expect(formatHourBadge('bmo')).toContain('cal-hour-badge bmo');
        expect(formatHourBadge('amc')).toContain('cal-hour-badge amc');
        expect(formatHourBadge('other')).toContain('cal-hour-badge unknown');
    });

    test('escapeHtmlContent and decodeAttr should be inverses', () => {
        const original = '<div class="test">& \' "</div>';
        const escaped = escapeHtmlContent(original);
        expect(escaped).toBe('&lt;div class="test"&gt;&amp; \' "&lt;/div&gt;');
        const decoded = decodeAttr(escaped);
        expect(decoded).toBe(original);
    });
});

