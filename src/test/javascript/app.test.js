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
    rsiValue,
    buildTradeTable,
    buildResultCard,
    renderTermGroups,
    toggleCard,
    handleTableSort,
    buildScreenerCard,
    buildScreenerTable,
    buildDropScreenerTable
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

describe('UI Builder Functions', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        window.tableSortState = {};
        window.tradeDataMap = {};
    });

    test('buildTradeTable should return empty state if no trades', () => {
        expect(buildTradeTable(null)).toContain('No trades found');
        expect(buildTradeTable([])).toContain('No trades found');
    });

    test('buildTradeTable should render table with trades', () => {
        const trades = [
            {
                symbol: 'AAPL',
                companyName: 'Apple Inc.',
                underlyingPrice: 150.0,
                todayPct: 1.5,
                strategyType: 'IRON_CONDOR',
                maxLoss: -50.0,
                extrinsicValue: 20.0,
                returnOnRisk: 15.0,
                netCredit: 10.0,
                legs: [
                    { action: 'SELL', strike: 155, optionType: 'CALL', expirationDate: '2026-04-17T20:00:00.000+00:00' }
                ],
                breakEvenPrice: 156.0
            }
        ];
        
        const html = buildTradeTable(trades, 'card123');
        expect(html).toContain('<table');
        expect(html).toContain('AAPL');
        expect(html).toContain('Apple Inc.');
        expect(html).toContain('$150.00');
        expect(html).toContain('SELL');
        expect(html).toContain('text-success'); // for positive ROR
        expect(html).toContain('handleTableSort');
    });

    test('buildResultCard should create disabled card if no trades', () => {
        const result = {
            strategyId: 'strat1',
            strategyName: 'Test Strategy',
            trades: [],
            updatedAt: new Date().toISOString()
        };
        const card = buildResultCard(result, 'Standard');
        expect(card.tagName).toBe('DIV');
        expect(card.className).toContain('card disabled');
        expect(card.querySelector('.card-name').textContent).toBe('Test Strategy');
    });

    test('buildResultCard should create active card with trades', () => {
        const result = {
            strategyId: 'strat2',
            strategyName: 'Active Strategy',
            trades: [{ symbol: 'AAPL' }],
            updatedAt: new Date().toISOString(),
            tradesFound: 1
        };
        const card = buildResultCard(result, 'Custom');
        expect(card.className).toBe('card');
        expect(card.querySelector('.card-name').textContent).toBe('Active Strategy');
        expect(card.querySelector('.card-badge').textContent).toBe('Custom');
        expect(card.querySelector('.card-stats').textContent).toContain('Trades: 1');
        
        // Trades map should be populated
        expect(window.tradeDataMap['strat2']).toEqual([{ symbol: 'AAPL' }]);
    });

    test('toggleCard should toggle open class and content visibility', () => {
        document.body.innerHTML = `
            <div id="arrow-card1"></div>
            <div id="content-card1"></div>
        `;
        toggleCard('card1');
        expect(document.getElementById('content-card1').classList.contains('open')).toBe(true);
        expect(document.getElementById('arrow-card1').classList.contains('open')).toBe(true);
        
        toggleCard('card1');
        expect(document.getElementById('content-card1').classList.contains('open')).toBe(false);
        expect(document.getElementById('arrow-card1').classList.contains('open')).toBe(false);
    });

    test('handleTableSort should update state and re-render table', () => {
        // Setup initial DOM and state
        document.body.innerHTML = '<div id="content-card1"></div>';
        window.tradeDataMap['card1'] = [
            { symbol: 'AAPL', underlyingPrice: 150 },
            { symbol: 'MSFT', underlyingPrice: 300 }
        ];
        
        handleTableSort('card1', 'symbol');
        
        // Check state updated
        expect(window.tableSortState['card1'].column).toBe('symbol');
        expect(window.tableSortState['card1'].direction).toBe('asc');
        
        // Re-sorting same column flips direction
        handleTableSort('card1', 'symbol');
        expect(window.tableSortState['card1'].direction).toBe('desc');
    });

    test('renderTermGroups should group results and sort by termWeight', () => {
        const results = [
            { strategyName: 'Strat 1', filterConfig: { termType: 'Short' }, trades: [] },
            { strategyName: 'Strat 2', filterConfig: '{"termType": "Long"}', trades: [] },
            { strategyName: 'Strat 3', trades: [] } // Defaults to Other
        ];
        
        const container = document.createElement('div');
        renderTermGroups(container, results, 'Standard');
        
        const groups = container.querySelectorAll('.term-group');
        expect(groups.length).toBe(3);
        
        // Long (weight 60) should be first, Short (30) second, Other (-1) last
        expect(groups[0].querySelector('.term-group-label').textContent).toBe('Long');
        expect(groups[1].querySelector('.term-group-label').textContent).toBe('Short');
        expect(groups[2].querySelector('.term-group-label').textContent).toBe('Other');
        
        // Header click should toggle visibility
        const header = groups[0].querySelector('.term-group-header');
        const body = groups[0].querySelector('.term-group-body');
        
        expect(body.classList.contains('hidden')).toBe(true);
        header.click();
        expect(body.classList.contains('hidden')).toBe(false);
    });

    test('buildScreenerTable should render HTML table for screener results', () => {
        const results = [
            {
                symbol: 'NVDA',
                companyName: 'NVIDIA Corp',
                marketCapB: 2000,
                price: 850.50,
                rsi: 35.4,
                bollingerBandsPosition: 0.1,
                ttmRevenue: 50000000000,
                debtToEquity: 0.5
            }
        ];
        
        const html = buildScreenerTable(results, 'scr1');
        expect(html).toContain('<table');
        expect(html).toContain('NVDA');
        expect(html).toContain('NVIDIA Corp');
        expect(html).toContain('$2000.00B'); // formatMarketCap output
        expect(html).toContain('35.4'); // rsi output
        expect(html).toContain('handleTableSort(\'scr1\'');
    });

    test('buildDropScreenerTable should render HTML table for drop results', () => {
        const results = [
            {
                symbol: 'TSLA',
                companyName: 'Tesla Inc',
                dropPercent: 5.5,
                volume: 50000000,
                dropType: 'Significant Drop'
            }
        ];
        
        const html = buildDropScreenerTable(results, 'drop1');
        expect(html).toContain('<table');
        expect(html).toContain('TSLA');
        expect(html).toContain('Tesla Inc');
        expect(html).toContain('-5.50%');
        expect(html).toContain('50.0M');
        expect(html).toContain('Significant Drop');
        expect(html).toContain('handleTableSort(\'drop1\'');
    });

    test('buildScreenerCard should create DOM element for screener result', () => {
        const result = {
            screenerId: 'sc123',
            screenerName: 'Value Stocks',
            results: [{ symbol: 'AAPL' }],
            resultsFound: 1,
            updatedAt: new Date().toISOString()
        };
        
        const card = buildScreenerCard(result, false);
        expect(card.tagName).toBe('DIV');
        expect(card.className).toBe('card');
        expect(card.querySelector('.card-name').textContent).toBe('Value Stocks');
        expect(card.querySelector('.card-stats').textContent).toContain('Found: 1');
        
        // Trades map should be populated as 'screener' type
        expect(window.tradeDataMap['sc123'][0]).toEqual({ symbol: 'AAPL' });
        expect(window.tradeDataMap['sc123']._type).toBe('screener');
    });
});

