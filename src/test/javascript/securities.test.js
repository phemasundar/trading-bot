const {
    initSecuritiesPage,
    loadFilterConfig,
    loadSecuritiesGroups,
    toggleGroupBlock,
    loadGroupData,
    renderGroupCards,
    createSecurityCardHtml,
    createPendingCardHtml,
    filterGroupSymbols,
    handleGlobalSearch,
    clearSearch,
    toggleAllBlocks,
    formatVolume,
    escapeHtml,
    API
} = require('../../main/resources/static/app');

describe('Securities Screen JS Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
    });

    test('formatVolume handles null, zero, thousands, millions, and billions', () => {
        expect(formatVolume(null)).toBe('0');
        expect(formatVolume(0)).toBe('0');
        expect(formatVolume(450)).toBe('450');
        expect(formatVolume(2500)).toBe('2.5K');
        expect(formatVolume(15500000)).toBe('15.50M');
        expect(formatVolume(2500000000)).toBe('2.50B');
    });

    test('escapeHtml sanitizes special characters', () => {
        expect(escapeHtml(null)).toBe('');
        expect(escapeHtml('<script>alert("XSS & \'")</script>')).toBe(
            '&lt;script&gt;alert(&quot;XSS &amp; &#039;&quot;)&lt;/script&gt;'
        );
    });

    test('createSecurityCardHtml generates rich card with indicators and narrative', () => {
        const mockData = {
            symbol: 'NBIS',
            companyName: 'Nebius Group',
            currentPrice: 226.0,
            rsi: 28.4,
            rsiOversold: true,
            rsiBullishCrossover: false,
            bollingerLower: 200.0,
            bollingerMiddle: 215.0,
            bollingerUpper: 235.0,
            priceTouchingLowerBand: true,
            maValues: { 20: 216.0, 50: 219.37, 100: 206.67, 200: 195.0 },
            emaValues: { 9: 218.0, 21: 217.0, 50: 215.02 },
            volume: 12500000,
            volumeMaValues: { 20: 9800000 },
            atr: 8.5,
            historicalVolatilityRank: 34.0,
            ivPercentile: 45.2,
            ivRank: 38.0,
            allTechnicalIndicatorsSummary: 'NBIS expanded to $226.00, pushing past its Daily 50 SMA ($219.37).'
        };

        const html = createSecurityCardHtml('NBIS', mockData);
        expect(html).toContain('Nebius Group');
        expect(html).toContain('226.00');
        expect(html).toContain('RSI OVERSOLD');
        expect(html).toContain('PRICE ACTION PROGRESSION');
        expect(html).toContain('DYNAMIC MOVING AVERAGE TRACKER');
        expect(html).toContain('Daily EMA 50 / Daily SMA 50');
        expect(html).toContain('IV Percentile / IV Rank');
        expect(html).toContain('45.2%');
        expect(html).toContain('38.0%');
        expect(html).toContain('Trading Playbook');
    });

    test('createSecurityCardHtml formats IV bracket count when records < 1 year', () => {
        const mockData = {
            symbol: 'AAPL',
            currentPrice: 150.0,
            ivPercentile: 65.0,
            ivRank: 55.0,
            ivDays: 120
        };
        const html = createSecurityCardHtml('AAPL', mockData);
        expect(html).toContain('IV Percentile / IV Rank (120)');
        expect(html).toContain('65.0% / 55.0%');
    });

    test('createSecurityCardHtml omits bracket when records >= 252 (1 year)', () => {
        const mockData = {
            symbol: 'AAPL',
            currentPrice: 150.0,
            ivPercentile: 65.0,
            ivRank: 55.0,
            ivDays: 252
        };
        const html = createSecurityCardHtml('AAPL', mockData);
        expect(html).toContain('IV Percentile / IV Rank');
        expect(html).not.toContain('IV Percentile / IV Rank (252)');
        expect(html).toContain('65.0% / 55.0%');
    });

    test('createSecurityCardHtml displays NA when min IV records not available', () => {
        const mockData = {
            symbol: 'XYZ',
            currentPrice: 50.0,
            ivPercentile: null,
            ivRank: null
        };
        const html = createSecurityCardHtml('XYZ', mockData);
        expect(html).toContain('IV Percentile / IV Rank');
        expect(html).toContain('NA');
    });

    test('createPendingCardHtml generates waiting card for uncalculated security', () => {
        const html = createPendingCardHtml('XYZ');
        expect(html).toContain('XYZ');
        expect(html).toContain('PENDING EVALUATION');
        expect(html).toContain('Run Screener Now');
    });

    test('loadFilterConfig fetches config and populates chips', async () => {
        document.body.innerHTML = `
            <div id="chip-rsi"></div>
            <div id="chip-bb"></div>
            <div id="chip-ma"></div>
            <div id="chip-ema"></div>
            <div id="chip-vol"></div>
            <div id="chip-vola"></div>
        `;

        API.get = jest.fn().mockResolvedValueOnce({
            filters: {
                rsi: { period: 14, oversold: 30, overbought: 70 },
                bollinger: { period: 20, stdDev: 2.0 },
                moving_averages: { periods: [20, 50, 100, 200] },
                exponential_moving_averages: { periods: [9, 21, 50] },
                volume: { sma_periods: [20, 50] },
                volatility: { atr_period: 14, hv_period: 20 }
            }
        });

        await loadFilterConfig();
        expect(document.getElementById('chip-rsi').textContent).toContain('14');
        expect(document.getElementById('chip-bb').textContent).toContain('20, 2σ');
        expect(document.getElementById('chip-ma').textContent).toContain('20, 50, 100, 200');
    });

    test('loadSecuritiesGroups renders group accordion blocks and expands first block', async () => {
        document.body.innerHTML = `
            <div id="securities-groups-container"></div>
        `;

        const groups = [
            {
                id: '1_portfolio',
                fileName: '1_portfolio.yaml',
                displayName: 'Portfolio',
                symbolCount: 2,
                symbols: ['NVDA', 'AAPL']
            }
        ];

        API.get = jest.fn().mockResolvedValueOnce(groups);
        API.post = jest.fn().mockResolvedValueOnce({
            NVDA: {
                symbol: 'NVDA',
                companyName: 'NVIDIA Corporation',
                currentPrice: 125.0,
                rsi: 32.0,
                maValues: { 50: 120.0 }
            }
        });

        await loadSecuritiesGroups();
        expect(document.getElementById('group-card-1_portfolio')).not.toBeNull();
        expect(document.querySelector('.group-title').textContent).toBe('Portfolio');
    });

    test('handleGlobalSearch and clearSearch updates search query and toggles clear button', () => {
        document.body.innerHTML = `
            <input id="securities-global-search" value="AAPL" />
            <button id="clear-search-btn" style="display:none"></button>
            <div id="securities-groups-container"></div>
        `;

        handleGlobalSearch('AAPL');
        expect(document.getElementById('clear-search-btn').style.display).toBe('inline-block');

        clearSearch();
        expect(document.getElementById('clear-search-btn').style.display).toBe('none');
        expect(document.getElementById('securities-global-search').value).toBe('');
    });

    test('toggleAllBlocks updates button text', () => {
        document.body.innerHTML = `
            <button><span id="toggle-all-text">Expand All</span></button>
            <div id="body-test" style="display:none"></div>
            <span id="arrow-test"></span>
        `;

        toggleAllBlocks();
        expect(document.getElementById('toggle-all-text').textContent).toBe('Collapse All');
    });
});
