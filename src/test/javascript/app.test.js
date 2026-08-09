const {
    formatMarketCap,
    formatLargeNumber,
    formatDuration,
    timeAgo,
    formatCompanyName
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
