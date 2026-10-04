const {
    DEFAULT_OPTION_STRATEGIES,
    renderStrategyCard,
    filterStrategies,
    handleStrategySearch,
    clearStrategySearch,
    initOptionStrategiesPage,
    initStrategyDetailPage,
    DEFAULT_OPTION_GREEKS,
    renderGreekCard,
    filterGreeks,
    renderFilteredGreeks,
    setGreekOrderFilter,
    handleGreekSearch,
    clearGreekSearch,
    initOptionGreeksPage,
    initGreekDetailPage,
    API
} = require('../../main/resources/static/app');

describe('Learning Center & Option Strategies Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        window._learningStrategies = [];
        window._learningGreeks = [];
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

    test('initStrategyDetailPage hides Execute Strategy CTA button for READONLY user', async () => {
        window.history.pushState({}, '', '/strategy-detail.html?strategy=iron_condor.md');
        window._userRole = 'READONLY';

        document.body.innerHTML = `
            <h1 id="strategy-detail-title"></h1>
            <select id="strategy-select-switcher"></select>
            <a id="execute-strategy-btn" href="/execute.html" data-admin-only style="display:inline-flex;"></a>
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
        expect(execBtn.style.display).toBe('none');
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

    // ─────────────────────────────────────────────────────────────
    // Option Greeks Tests
    // ─────────────────────────────────────────────────────────────

    test('DEFAULT_OPTION_GREEKS has 8 Greeks with expected metadata', () => {
        expect(DEFAULT_OPTION_GREEKS.length).toBe(8);
        const delta = DEFAULT_OPTION_GREEKS.find(g => g.id === 'delta');
        expect(delta).toBeDefined();
        expect(delta.name).toBe('Delta');
        expect(delta.symbol).toBe('Δ');
        expect(delta.order).toBe('First-Order');
        expect(delta.derivative).toBe('∂V / ∂S');
        expect(delta.filename).toBe('greeks/delta.md');

        const gamma = DEFAULT_OPTION_GREEKS.find(g => g.id === 'gamma');
        expect(gamma.order).toBe('Second-Order');
        expect(gamma.derivative).toBe('∂²V / ∂S²');
    });

    test('renderGreekCard creates valid card HTML with symbol avatar, badges, and formula pill', () => {
        const greek = {
            id: 'delta',
            name: 'Delta',
            symbol: 'Δ',
            order: 'First-Order',
            derivative: '∂V / ∂S',
            summary: 'Directional sensitivity metric',
            exposure: 'Long Calls: +Δ | Long Puts: -Δ',
            impact: 'Directional sensitivity, hedge ratio'
        };

        const html = renderGreekCard(greek);
        expect(html).toContain('greek-symbol-avatar');
        expect(html).toContain('Δ');
        expect(html).toContain('Delta');
        expect(html).toContain('badge-info');
        expect(html).toContain('First-Order');
        expect(html).toContain('greek-formula-pill');
        expect(html).toContain('∂V / ∂S');
        expect(html).toContain('Directional sensitivity metric');
        expect(html).toContain('Long Calls: +Δ');
        expect(html).toContain('href="/greek-detail.html?greek=delta"');
        expect(html).toContain('Read Guide');
    });

    test('renderGreekCard handles Second-Order badge and fallback attributes', () => {
        const secondOrderGreek = {
            id: 'vanna',
            name: 'Vanna',
            order: 'Second-Order',
            derivative: '∂²V / (∂S ∂σ)'
        };

        const html = renderGreekCard(secondOrderGreek);
        expect(html).toContain('badge-purple');
        expect(html).toContain('Second-Order');
        expect(html).toContain('V'); // Name charAt(0) fallback
    });

    test('filterGreeks filters by order and search query', () => {
        const greeks = [
            { id: 'delta', name: 'Delta', symbol: 'Δ', order: 'First-Order', derivative: '∂V / ∂S', summary: 'Directional', exposure: '', impact: 'Hedge ratio' },
            { id: 'gamma', name: 'Gamma', symbol: 'Γ', order: 'Second-Order', derivative: '∂²V / ∂S²', summary: 'Convexity', exposure: '', impact: 'Pin risk' },
            { id: 'vanna', name: 'Vanna', symbol: '∂Δ/∂σ', order: 'Second-Order', derivative: '∂²V / (∂S ∂σ)', summary: 'Cross-derivative', exposure: 'OTM', impact: 'MM hedging' }
        ];

        expect(filterGreeks(greeks, '').length).toBe(3);
        expect(filterGreeks(greeks, '', 'First-Order').length).toBe(1);
        expect(filterGreeks(greeks, '', 'First-Order')[0].id).toBe('delta');
        expect(filterGreeks(greeks, '', 'Second-Order').length).toBe(2);

        // Search query matching
        expect(filterGreeks(greeks, 'convexity').length).toBe(1);
        expect(filterGreeks(greeks, 'convexity')[0].id).toBe('gamma');
        expect(filterGreeks(greeks, '∂²V').length).toBe(2);
        expect(filterGreeks(greeks, 'Δ').length).toBe(2); // delta and vanna symbol
        expect(filterGreeks(greeks, 'pin risk').length).toBe(1);
        expect(filterGreeks(greeks, 'nonexistent').length).toBe(0);

        // Edge case: null or empty
        expect(filterGreeks(null, 'test')).toEqual([]);
    });

    test('handleGreekSearch updates greeks grid and count badge', () => {
        document.body.innerHTML = `
            <input id="greek-search-input" value="gamma">
            <button id="clear-greek-search" style="display:none;"></button>
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        window._learningGreeks = [
            { id: 'delta', name: 'Delta', order: 'First-Order', summary: 'Directional' },
            { id: 'gamma', name: 'Gamma', order: 'Second-Order', summary: 'Acceleration' }
        ];

        handleGreekSearch('gamma');

        const grid = document.getElementById('greeks-grid');
        const badge = document.getElementById('greek-count-badge');
        const clearBtn = document.getElementById('clear-greek-search');

        expect(badge.textContent).toBe('1 Greek');
        expect(clearBtn.style.display).toBe('inline-block');
        expect(grid.innerHTML).toContain('Gamma');
        expect(grid.innerHTML).not.toContain('Delta');
    });

    test('handleGreekSearch displays empty state when no Greek matches', () => {
        document.body.innerHTML = `
            <input id="greek-search-input" value="xyz">
            <button id="clear-greek-search"></button>
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        window._learningGreeks = [
            { id: 'delta', name: 'Delta', order: 'First-Order' }
        ];

        handleGreekSearch('xyz');

        const grid = document.getElementById('greeks-grid');
        expect(grid.innerHTML).toContain('No Option Greeks found');
    });

    test('clearGreekSearch resets input and restores all Greeks', () => {
        document.body.innerHTML = `
            <div id="greek-filter-chips">
                <button class="strategy-chip" data-order="ALL">All Greeks</button>
                <button class="strategy-chip active" data-order="First-Order">First-Order</button>
            </div>
            <input id="greek-search-input" value="delta">
            <button id="clear-greek-search"></button>
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        window._learningGreeks = [
            { id: 'delta', name: 'Delta', order: 'First-Order' },
            { id: 'gamma', name: 'Gamma', order: 'Second-Order' }
        ];

        clearGreekSearch();

        const input = document.getElementById('greek-search-input');
        const badge = document.getElementById('greek-count-badge');
        const allChip = document.querySelector('#greek-filter-chips .strategy-chip[data-order="ALL"]');

        expect(input.value).toBe('');
        expect(badge.textContent).toBe('2 Greeks');
        expect(allChip.classList.contains('active')).toBe(true);
    });

    test('setGreekOrderFilter filters Greeks and toggles chip active state', () => {
        document.body.innerHTML = `
            <div id="greek-filter-chips">
                <button class="strategy-chip active" data-order="ALL">All Greeks</button>
                <button class="strategy-chip" data-order="First-Order">First-Order</button>
                <button class="strategy-chip" data-order="Second-Order">Second-Order</button>
            </div>
            <input id="greek-search-input" value="">
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        window._learningGreeks = [
            { id: 'delta', name: 'Delta', order: 'First-Order' },
            { id: 'gamma', name: 'Gamma', order: 'Second-Order' },
            { id: 'vanna', name: 'Vanna', order: 'Second-Order' }
        ];

        const chips = document.querySelectorAll('#greek-filter-chips .strategy-chip');
        const secondOrderChip = chips[2];

        const result = setGreekOrderFilter('Second-Order', secondOrderChip);
        expect(result.length).toBe(2);
        expect(secondOrderChip.classList.contains('active')).toBe(true);
        expect(chips[0].classList.contains('active')).toBe(false);
    });

    test('initOptionGreeksPage fetches from API and populates grid', async () => {
        document.body.innerHTML = `
            <input id="greek-search-input">
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        API.get = jest.fn().mockResolvedValue([
            { id: 'delta', name: 'Delta', order: 'First-Order', symbol: 'Δ' }
        ]);

        await initOptionGreeksPage();

        expect(API.get).toHaveBeenCalledWith('/api/learning/greeks');
        const grid = document.getElementById('greeks-grid');
        expect(grid.innerHTML).toContain('Delta');
        expect(document.getElementById('greek-count-badge').textContent).toBe('1 Greek');
    });

    test('initOptionGreeksPage falls back to DEFAULT_OPTION_GREEKS on error', async () => {
        document.body.innerHTML = `
            <input id="greek-search-input">
            <span id="greek-count-badge"></span>
            <div id="greeks-grid"></div>
        `;

        API.get = jest.fn().mockRejectedValue(new Error('Network error'));

        await initOptionGreeksPage();

        const grid = document.getElementById('greeks-grid');
        expect(grid.innerHTML).toContain('Delta');
        expect(grid.innerHTML).toContain('Gamma');
        expect(grid.innerHTML).toContain('Theta');
    });

    test('initGreekDetailPage fetches markdown and renders with marked', async () => {
        window.history.pushState({}, '', '/greek-detail.html?greek=gamma.md');

        document.body.innerHTML = `
            <select id="greek-select-switcher"></select>
            <div id="greek-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([
            { id: 'delta', name: 'Delta', symbol: 'Δ' },
            { id: 'gamma', name: 'Gamma', symbol: 'Γ' }
        ]);

        global.marked = {
            parse: jest.fn(text => `<div>PARSED: ${text}</div>`)
        };

        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            text: () => Promise.resolve("# Gamma (Γ) — Option Greek Guide\n\nGamma details...")
        });

        await initGreekDetailPage();

        expect(global.fetch).toHaveBeenCalledWith('/descriptions/greeks/gamma.md');
        expect(global.marked.parse).toHaveBeenCalled();
        expect(document.getElementById('greek-markdown-content').innerHTML).toContain('PARSED: # Gamma');
        expect(document.title).toContain('Gamma (Γ) — Option Greek Guide');

        const switcher = document.getElementById('greek-select-switcher');
        expect(switcher.children.length).toBe(2);
        expect(switcher.children[1].selected).toBe(true);
    });

    test('initGreekDetailPage handles fallback path when /greeks/ path returns 404', async () => {
        window.history.pushState({}, '', '/greek-detail.html?greek=theta');

        document.body.innerHTML = `
            <select id="greek-select-switcher"></select>
            <div id="greek-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([]);
        global.fetch = jest.fn()
            .mockResolvedValueOnce({ ok: false, status: 404 })
            .mockResolvedValueOnce({
                ok: true,
                text: () => Promise.resolve("### Theta Guide\nContent")
            });

        await initGreekDetailPage();

        expect(global.fetch).toHaveBeenCalledTimes(2);
        expect(global.fetch).toHaveBeenNthCalledWith(1, '/descriptions/greeks/theta.md');
        expect(global.fetch).toHaveBeenNthCalledWith(2, '/descriptions/theta.md');
    });

    test('initGreekDetailPage shows error state on fetch failure', async () => {
        window.history.pushState({}, '', '/greek-detail.html?greek=unknown');

        document.body.innerHTML = `
            <select id="greek-select-switcher"></select>
            <div id="greek-markdown-content"></div>
        `;

        API.get = jest.fn().mockResolvedValue([]);
        global.fetch = jest.fn().mockResolvedValue({
            ok: false,
            status: 404
        });

        await initGreekDetailPage();

        expect(document.getElementById('greek-markdown-content').innerHTML).toContain('Greek Guide Not Found');
    });
});
