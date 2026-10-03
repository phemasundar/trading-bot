const {
    DEFAULT_OPTION_STRATEGIES,
    renderStrategyCard,
    filterStrategies,
    handleStrategySearch,
    clearStrategySearch,
    initOptionStrategiesPage,
    initStrategyDetailPage,
    API
} = require('../../main/resources/static/app');

describe('Learning Center & Option Strategies Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window._learningStrategies = [];
    });

    test('renderStrategyCard creates valid card HTML with link, greeks, and bias badge', () => {
        const strat = {
            id: 'iron_condor',
            name: 'Iron Condor',
            filename: 'iron_condor.md',
            bias: 'Neutral',
            category: 'Options Strategy',
            summary: 'A neutral, defined-risk strategy',
            greeks: { Delta: 'Neutral', Gamma: 'Negative', Theta: 'Positive', Vega: 'Negative' }
        };

        const html = renderStrategyCard(strat);
        expect(html).toContain('Iron Condor');
        expect(html).toContain('badge-info');
        expect(html).toContain('Neutral');
        expect(html).toContain('Δ');
        expect(html).toContain('href="/strategy-detail.html?strategy=iron_condor.md"');
        expect(html).toContain('Read Guide');
    });

    test('renderStrategyCard assigns correct badge classes for bullish and bearish', () => {
        const bullishStrat = { id: 'pcs', name: 'PCS', bias: 'Bullish', summary: '' };
        const bearishStrat = { id: 'ccs', name: 'CCS', bias: 'Bearish', summary: '' };

        expect(renderStrategyCard(bullishStrat)).toContain('badge-success');
        expect(renderStrategyCard(bearishStrat)).toContain('badge-danger');
    });

    test('filterStrategies filters by name, bias, and greeks', () => {
        const list = [
            { id: 'iron_condor', name: 'Iron Condor', bias: 'Neutral', summary: 'Range bound', greeks: { Delta: 'Neutral' } },
            { id: 'put_credit_spread', name: 'Put Credit Spread', bias: 'Bullish', summary: 'Bullish spread', greeks: { Delta: 'Positive' } },
            { id: 'call_credit_spread', name: 'Call Credit Spread', bias: 'Bearish', summary: 'Bearish spread', greeks: { Delta: 'Negative' } }
        ];

        expect(filterStrategies(list, '').length).toBe(3);
        expect(filterStrategies(list, 'iron').length).toBe(1);
        expect(filterStrategies(list, 'iron')[0].id).toBe('iron_condor');
        expect(filterStrategies(list, 'bullish').length).toBe(1);
        expect(filterStrategies(list, 'bullish')[0].id).toBe('put_credit_spread');
        expect(filterStrategies(list, 'bearish').length).toBe(1);
        expect(filterStrategies(list, 'bearish')[0].id).toBe('call_credit_spread');
        expect(filterStrategies(list, 'nonexistent').length).toBe(0);
    });

    test('handleStrategySearch updates grid and badge correctly', () => {
        document.body.innerHTML = `
            <input id="strategy-search-input" value="condor">
            <button id="clear-strategy-search" style="display:none;"></button>
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        window._learningStrategies = [
            { id: 'iron_condor', name: 'Iron Condor', filename: 'iron_condor.md', bias: 'Neutral' },
            { id: 'short_put', name: 'Cash-Secured Put', filename: 'short_put.md', bias: 'Bullish' }
        ];

        handleStrategySearch('condor');

        const grid = document.getElementById('strategies-grid');
        const badge = document.getElementById('strategy-count-badge');
        const clearBtn = document.getElementById('clear-strategy-search');

        expect(badge.textContent).toBe('1 Strategy');
        expect(clearBtn.style.display).toBe('inline-block');
        expect(grid.innerHTML).toContain('Iron Condor');
        expect(grid.innerHTML).not.toContain('Cash-Secured Put');
    });

    test('handleStrategySearch displays empty state when no match', () => {
        document.body.innerHTML = `
            <input id="strategy-search-input" value="xyz">
            <button id="clear-strategy-search"></button>
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        window._learningStrategies = [
            { id: 'iron_condor', name: 'Iron Condor', bias: 'Neutral' }
        ];

        handleStrategySearch('xyz');

        const grid = document.getElementById('strategies-grid');
        expect(grid.innerHTML).toContain('No strategies found');
    });

    test('clearStrategySearch resets input and search results', () => {
        document.body.innerHTML = `
            <input id="strategy-search-input" value="condor">
            <button id="clear-strategy-search"></button>
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        window._learningStrategies = [
            { id: 'iron_condor', name: 'Iron Condor', bias: 'Neutral' },
            { id: 'short_put', name: 'Cash-Secured Put', bias: 'Bullish' }
        ];

        clearStrategySearch();

        const input = document.getElementById('strategy-search-input');
        const badge = document.getElementById('strategy-count-badge');
        expect(input.value).toBe('');
        expect(badge.textContent).toBe('2 Strategies');
    });

    test('initOptionStrategiesPage fetches strategies from API and renders cards', async () => {
        document.body.innerHTML = `
            <input id="strategy-search-input">
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        API.get = jest.fn().mockResolvedValue([
            { id: 'iron_condor', name: 'Iron Condor', filename: 'iron_condor.md', bias: 'Neutral', greeks: {} }
        ]);

        await initOptionStrategiesPage();

        expect(API.get).toHaveBeenCalledWith('/api/learning/strategies');
        const grid = document.getElementById('strategies-grid');
        expect(grid.innerHTML).toContain('Iron Condor');
        expect(document.getElementById('strategy-count-badge').textContent).toBe('1 Strategy');
    });

    test('initOptionStrategiesPage falls back to DEFAULT_OPTION_STRATEGIES on API error', async () => {
        document.body.innerHTML = `
            <input id="strategy-search-input">
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        API.get = jest.fn().mockRejectedValue(new Error('Network error'));

        await initOptionStrategiesPage();

        const grid = document.getElementById('strategies-grid');
        expect(grid.innerHTML).toContain('Iron Condor');
        expect(grid.innerHTML).toContain('Bullish ZEBRA');
    });

    test('setBiasFilter filters strategies by directional bias and updates active chip classes', () => {
        document.body.innerHTML = `
            <div class="strategy-filter-chips">
                <button class="strategy-chip active" data-bias="ALL">All</button>
                <button class="strategy-chip" data-bias="Bullish">Bullish</button>
                <button class="strategy-chip" data-bias="Neutral">Neutral</button>
                <button class="strategy-chip" data-bias="Bearish">Bearish</button>
            </div>
            <input id="strategy-search-input" value="">
            <span id="strategy-count-badge"></span>
            <div id="strategies-grid"></div>
        `;

        window._learningStrategies = [
            { id: 'bullish_zebra', name: 'Bullish ZEBRA', bias: 'Bullish' },
            { id: 'iron_condor', name: 'Iron Condor', bias: 'Neutral' },
            { id: 'call_credit_spread', name: 'Call Credit Spread', bias: 'Bearish' }
        ];

        const chips = document.querySelectorAll('.strategy-chip');
        const bullishChip = chips[1];

        const result = setBiasFilter('Bullish', bullishChip);
        expect(result.length).toBe(1);
        expect(result[0].id).toBe('bullish_zebra');
        expect(bullishChip.classList.contains('active')).toBe(true);
        expect(chips[0].classList.contains('active')).toBe(false);

        // Combined filter with search query
        document.getElementById('strategy-search-input').value = 'zebra';
        const filtered = handleStrategySearch('zebra');
        expect(filtered.length).toBe(1);
        expect(filtered[0].id).toBe('bullish_zebra');

        // Reset via clearStrategySearch
        clearStrategySearch();
        expect(document.getElementById('strategy-search-input').value).toBe('');
        expect(chips[0].classList.contains('active')).toBe(true);
        expect(document.getElementById('strategy-count-badge').textContent).toBe('3 Strategies');
    });

    test('initStrategyDetailPage wires Execute Strategy CTA button with mapped strategy type', async () => {
        window.history.pushState({}, '', '/strategy-detail.html?strategy=iron_condor.md');

        document.body.innerHTML = `
            <h1 id="strategy-detail-title"></h1>
            <select id="strategy-select-switcher"></select>
            <a id="execute-strategy-btn" href="/execute.html"></a>
            <div id="strategy-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([
            { id: 'iron_condor', name: 'Iron Condor', filename: 'iron_condor.md' }
        ]);

        global.marked = { parse: jest.fn(text => `<div>${text}</div>`) };
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            text: () => Promise.resolve("# Iron Condor\n\nContent here...")
        });

        await initStrategyDetailPage();

        const execBtn = document.getElementById('execute-strategy-btn');
        expect(execBtn.href).toContain('/execute.html?strategy=IRON_CONDOR');
    });

    test('initStrategyDetailPage fetches markdown and renders content with marked', async () => {
        window.history.pushState({}, '', '/strategy-detail.html?strategy=iron_condor.md');

        document.body.innerHTML = `
            <h1 id="strategy-detail-title"></h1>
            <select id="strategy-select-switcher"></select>
            <div id="strategy-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([
            { id: 'iron_condor', name: 'Iron Condor', filename: 'iron_condor.md' },
            { id: 'short_put', name: 'Cash-Secured Put', filename: 'short_put.md' }
        ]);

        global.marked = {
            parse: jest.fn(text => `<div>PARSED: ${text}</div>`)
        };

        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            text: () => Promise.resolve("# Iron Condor\n\nContent here...")
        });

        await initStrategyDetailPage();

        expect(global.fetch).toHaveBeenCalledWith('/descriptions/iron_condor.md');
        expect(global.marked.parse).toHaveBeenCalled();
        expect(document.getElementById('strategy-detail-title').textContent).toBe('Iron Condor');
        expect(document.getElementById('strategy-markdown-content').innerHTML).toContain('PARSED: # Iron Condor');

        const switcher = document.getElementById('strategy-select-switcher');
        expect(switcher.children.length).toBe(2);
    });

    test('initStrategyDetailPage shows error state on fetch failure', async () => {
        window.history.pushState({}, '', '/strategy-detail.html?strategy=invalid_file.md');

        document.body.innerHTML = `
            <h1 id="strategy-detail-title"></h1>
            <select id="strategy-select-switcher"></select>
            <div id="strategy-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([]);
        global.fetch = jest.fn().mockResolvedValue({
            ok: false,
            status: 404
        });

        await initStrategyDetailPage();

        expect(document.getElementById('strategy-markdown-content').innerHTML).toContain('Strategy Guide Not Found');
    });
});
