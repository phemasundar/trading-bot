/**
 * Trading Bot — Learning Center & Option Strategies
 */

// Fallback catalog of option strategies if API is unreachable
const DEFAULT_OPTION_STRATEGIES = [
    {
        id: "bullish_bwb",
        name: "Bullish Broken Wing Butterfly (BWB)",
        filename: "bullish_bwb.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "An asymmetric butterfly spread designed with a wider out-of-the-money wing, eliminating upside risk while capturing decay and directional appreciation.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "bullish_zebra",
        name: "Bullish ZEBRA (Zero Extrinsic Back Ratio)",
        filename: "bullish_zebra.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "A 100-delta stock replacement strategy utilizing 2 long ITM calls and 1 short ATM call with virtually zero extrinsic value risk and capped downside.",
        greeks: { Delta: "Positive", Gamma: "Positive", Theta: "Near Zero", Vega: "Positive" }
    },
    {
        id: "call_credit_spread",
        name: "Call Credit Spread (CCS)",
        filename: "call_credit_spread.md",
        category: "Options Strategy",
        bias: "Bearish",
        summary: "A bearish, defined-risk vertical spread profiting when the underlying price remains below the short call strike as time decay accelerates.",
        greeks: { Delta: "Negative", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "iron_condor",
        name: "Iron Condor",
        filename: "iron_condor.md",
        category: "Options Strategy",
        bias: "Neutral",
        summary: "A market-neutral, defined-risk strategy combining an OTM put credit spread and call credit spread to profit from range-bound price action and IV crush.",
        greeks: { Delta: "Neutral", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "long_call_leap",
        name: "Long Call LEAPs",
        filename: "long_call_leap.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "Deep in-the-money long-term call options providing capital-efficient equity exposure with minimal theta drag and leveraged upside participation.",
        greeks: { Delta: "Positive", Gamma: "Positive", Theta: "Negative", Vega: "Positive" }
    },
    {
        id: "put_credit_spread",
        name: "Put Credit Spread (PCS)",
        filename: "put_credit_spread.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "A high-probability bullish options strategy selling an OTM put while buying a lower strike put for defined risk, profiting from sideways or upward drift.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "short_put",
        name: "Cash-Secured Put (Short Put / CSP)",
        filename: "short_put.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "Selling an out-of-the-money put with cash backing to generate income or acquire top-tier stocks at an effective discount to current market price.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "short_strangle",
        name: "Short Strangle",
        filename: "short_strangle.md",
        category: "Options Strategy",
        bias: "Neutral",
        summary: "Selling an out-of-the-money put and out-of-the-money call simultaneously, maximizing premium collection and profiting from high implied volatility contraction.",
        greeks: { Delta: "Neutral", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "tech_call_credit_spread",
        name: "Technical Call Credit Spread (CCS)",
        filename: "tech_call_credit_spread.md",
        category: "Options Strategy",
        bias: "Bearish",
        summary: "A systematic call credit spread triggered by technical overbought signals, RSI crossovers, or upper Bollinger Band rejections.",
        greeks: { Delta: "Negative", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "tech_put_credit_spread",
        name: "Technical Put Credit Spread (PCS)",
        filename: "tech_put_credit_spread.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "A quantitative put credit spread triggered when securities show oversold indicators, moving average support tests, or bullish technical reversals.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    }
];

let _learningStrategies = [];
let _activeBiasFilter = 'ALL';

const STRATEGY_TYPE_MAP = {
    'bullish_bwb.md': 'BULLISH_BROKEN_WING_BUTTERFLY',
    'bullish_zebra.md': 'BULLISH_ZEBRA',
    'call_credit_spread.md': 'CALL_CREDIT_SPREAD',
    'iron_condor.md': 'IRON_CONDOR',
    'long_call_leap.md': 'LONG_CALL_LEAP',
    'put_credit_spread.md': 'PUT_CREDIT_SPREAD',
    'short_put.md': 'SHORT_PUT',
    'short_strangle.md': 'SHORT_STRANGLE',
    'tech_call_credit_spread.md': 'CALL_CREDIT_SPREAD',
    'tech_put_credit_spread.md': 'PUT_CREDIT_SPREAD'
};

/**
 * Builds HTML for a single strategy card link.
 */
function renderStrategyCard(strategy) {
    const bias = (strategy.bias || 'Neutral').toLowerCase();
    let badgeClass = 'badge-neutral';
    if (bias.includes('bullish')) badgeClass = 'badge-success';
    else if (bias.includes('bearish')) badgeClass = 'badge-danger';
    else if (bias.includes('neutral')) badgeClass = 'badge-info';

    const greeksHtml = strategy.greeks ? Object.entries(strategy.greeks).map(([k, v]) => {
        let symbol = k;
        if (k === 'Delta') symbol = 'Δ';
        else if (k === 'Gamma') symbol = 'Γ';
        else if (k === 'Theta') symbol = 'Θ';
        else if (k === 'Vega') symbol = 'V';
        return `<span class="greek-pill"><strong>${symbol}</strong> ${escapeAttr(v)}</span>`;
    }).join('') : '';

    const detailUrl = `/strategy-detail.html?strategy=${encodeURIComponent(strategy.filename || strategy.id + '.md')}`;

    return `
        <a href="${detailUrl}" class="strategy-learning-card" data-strategy-id="${escapeAttr(strategy.id)}">
            <div>
                <div class="strategy-card-header">
                    <h3 class="strategy-card-title">${escapeAttr(strategy.name)}</h3>
                    <span class="badge ${badgeClass}">${escapeAttr(strategy.bias || 'Neutral')}</span>
                </div>
                <p class="strategy-card-summary">${escapeAttr(strategy.summary || '')}</p>
            </div>
            <div>
                <div class="strategy-card-greeks">
                    ${greeksHtml}
                </div>
                <div class="strategy-card-footer">
                    <span class="text-muted small">${escapeAttr(strategy.category || 'Option Strategy')}</span>
                    <span class="strategy-read-link">
                        Read Guide <span class="link-arrow">→</span>
                    </span>
                </div>
            </div>
        </a>
    `;
}

/**
 * Filters the strategy list according to a search query and optional directional bias.
 */
function filterStrategies(strategies, query, bias = 'ALL') {
    if (!Array.isArray(strategies)) return [];
    let result = strategies;

    // Filter by directional bias chip if not ALL
    if (bias && bias !== 'ALL') {
        const targetBias = bias.toLowerCase();
        result = result.filter(s => (s.bias || '').toLowerCase().includes(targetBias));
    }

    // Filter by query string across name, id, summary, bias, and Greeks
    if (query && query.trim()) {
        const q = query.trim().toLowerCase();
        result = result.filter(s => {
            const name = (s.name || '').toLowerCase();
            const id = (s.id || '').toLowerCase();
            const summary = (s.summary || '').toLowerCase();
            const biasStr = (s.bias || '').toLowerCase();
            const greeksStr = s.greeks ? Object.values(s.greeks).join(' ').toLowerCase() : '';
            return name.includes(q) || id.includes(q) || summary.includes(q) || biasStr.includes(q) || greeksStr.includes(q);
        });
    }

    return result;
}

function getActiveStrategies() {
    if (typeof window !== 'undefined' && Array.isArray(window._learningStrategies) && window._learningStrategies.length > 0) {
        return window._learningStrategies;
    }
    if (_learningStrategies && _learningStrategies.length > 0) {
        return _learningStrategies;
    }
    return DEFAULT_OPTION_STRATEGIES;
}

/**
 * Re-renders the strategy card grid based on current search query and bias filter state.
 */
function renderFilteredStrategies() {
    const container = document.getElementById('strategies-grid');
    const countBadge = document.getElementById('strategy-count-badge');
    const searchInput = document.getElementById('strategy-search-input');
    const clearBtn = document.getElementById('clear-strategy-search');

    const query = searchInput ? searchInput.value : '';
    if (clearBtn) {
        clearBtn.style.display = query && query.length > 0 ? 'inline-block' : 'none';
    }

    const currentStrategies = getActiveStrategies();
    const filtered = filterStrategies(currentStrategies, query, _activeBiasFilter);

    if (countBadge) {
        countBadge.textContent = `${filtered.length} ${filtered.length === 1 ? 'Strategy' : 'Strategies'}`;
    }

    if (!container) return filtered;

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px;">
                <div class="empty-state-icon text-muted">🔍</div>
                <h3>No strategies found</h3>
                <p class="text-muted">No option strategies matched your criteria.</p>
                <button class="btn btn-ghost btn-sm" onclick="clearStrategySearch()" style="margin-top: 8px;">Clear Filters</button>
            </div>
        `;
        return filtered;
    }

    container.innerHTML = filtered.map(renderStrategyCard).join('');
    return filtered;
}

/**
 * Handles directional bias chip selection (All, Bullish, Neutral, Bearish).
 */
function setBiasFilter(bias, buttonElement) {
    _activeBiasFilter = bias || 'ALL';

    if (typeof document !== 'undefined') {
        const chips = document.querySelectorAll('.strategy-filter-chips .strategy-chip');
        chips.forEach(chip => {
            const chipBias = chip.getAttribute('data-bias');
            if (chip === buttonElement || (chipBias && chipBias.toLowerCase() === _activeBiasFilter.toLowerCase())) {
                chip.classList.add('active');
            } else {
                chip.classList.remove('active');
            }
        });
    }

    return renderFilteredStrategies();
}

/**
 * Handles real-time search input on the Option Strategies page.
 */
function handleStrategySearch(query) {
    return renderFilteredStrategies();
}

/**
 * Clears search input and resets directional bias filter to ALL.
 */
function clearStrategySearch() {
    const input = document.getElementById('strategy-search-input');
    if (input) {
        input.value = '';
        input.focus();
    }

    _activeBiasFilter = 'ALL';
    if (typeof document !== 'undefined') {
        const chips = document.querySelectorAll('.strategy-filter-chips .strategy-chip');
        chips.forEach(chip => {
            if (chip.getAttribute('data-bias') === 'ALL') {
                chip.classList.add('active');
            } else {
                chip.classList.remove('active');
            }
        });
    }

    return renderFilteredStrategies();
}

/**
 * Initializes the Option Strategies listing page.
 */
async function initOptionStrategiesPage() {
    const countBadge = document.getElementById('strategy-count-badge');

    try {
        const res = await API.get('/api/learning/strategies');
        if (Array.isArray(res) && res.length > 0) {
            _learningStrategies = res;
        } else {
            _learningStrategies = DEFAULT_OPTION_STRATEGIES;
        }
    } catch (e) {
        console.warn('Failed to fetch learning strategies from API, using default catalog:', e);
        _learningStrategies = DEFAULT_OPTION_STRATEGIES;
    }

    if (typeof window !== 'undefined') {
        window._learningStrategies = _learningStrategies;
    }

    if (countBadge) {
        countBadge.textContent = `${_learningStrategies.length} Strategies`;
    }

    _activeBiasFilter = 'ALL';
    renderFilteredStrategies();

    // Attach search input listener if present
    const searchInput = document.getElementById('strategy-search-input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => handleStrategySearch(e.target.value));
    }
}

/**
 * Initializes the Strategy Detail page.
 */
async function initStrategyDetailPage() {
    const params = new URLSearchParams(window.location.search);
    let strategyParam = params.get('strategy') || params.get('id') || params.get('file');

    // Default to iron_condor if none specified
    if (!strategyParam) {
        strategyParam = 'iron_condor.md';
    }

    let filename = strategyParam.trim();
    if (!filename.endsWith('.md')) {
        filename += '.md';
    }
    const strategyId = filename.slice(0, -3);

    const titleEl = document.getElementById('strategy-detail-title');
    const contentEl = document.getElementById('strategy-markdown-content');
    const switcherEl = document.getElementById('strategy-select-switcher');
    const execBtn = document.getElementById('execute-strategy-btn');

    // Wire Execute Strategy CTA button
    if (execBtn) {
        const readOnly = (typeof isReadOnly === 'function' && isReadOnly()) ||
                         (typeof window !== 'undefined' && window._userRole === 'READONLY') ||
                         (typeof sessionStorage !== 'undefined' && sessionStorage.getItem('userRole') === 'READONLY');
        if (readOnly) {
            execBtn.style.display = 'none';
        } else {
            const stratType = STRATEGY_TYPE_MAP[filename.toLowerCase()] || STRATEGY_TYPE_MAP[strategyId.toLowerCase() + '.md'];
            if (stratType) {
                execBtn.href = `/execute.html?strategy=${encodeURIComponent(stratType)}`;
            } else {
                execBtn.href = '/execute.html';
            }
        }
    }

    // Populate switcher dropdown
    try {
        let strategies = [];
        try {
            const apiRes = await API.get('/api/learning/strategies');
            if (Array.isArray(apiRes) && apiRes.length > 0) strategies = apiRes;
            else strategies = DEFAULT_OPTION_STRATEGIES;
        } catch {
            strategies = DEFAULT_OPTION_STRATEGIES;
        }

        if (switcherEl) {
            switcherEl.innerHTML = strategies.map(s => {
                const sFile = s.filename || s.id + '.md';
                const selected = sFile.toLowerCase() === filename.toLowerCase() ? 'selected' : '';
                return `<option value="${escapeAttr(sFile)}" ${selected}>${escapeAttr(s.name)}</option>`;
            }).join('');

            switcherEl.addEventListener('change', (e) => {
                window.location.href = `/strategy-detail.html?strategy=${encodeURIComponent(e.target.value)}`;
            });
        }
    } catch (e) {
        console.warn('Could not populate strategy switcher:', e);
    }

    // Fetch and render markdown
    try {
        if (contentEl) {
            contentEl.innerHTML = `
                <div class="loading-state" style="padding: 40px 20px;">
                    <div class="spinner"></div>
                    <p>Loading strategy guide...</p>
                </div>
            `;
        }

        const res = await fetch(`/descriptions/${filename}`);
        if (!res.ok) {
            throw new Error(`Strategy description file "${filename}" not found (HTTP ${res.status})`);
        }

        const markdownText = await res.text();

        // Extract title from markdown if available
        const firstLine = markdownText.split('\n')[0] || '';
        if (firstLine.startsWith('# ') && titleEl) {
            titleEl.textContent = firstLine.substring(2).trim();
            document.title = `${firstLine.substring(2).trim()} — Option Strategies`;
        } else if (titleEl) {
            titleEl.textContent = strategyId.replace(/_/g, ' ').toUpperCase();
        }

        if (contentEl) {
            if (typeof marked !== 'undefined') {
                contentEl.innerHTML = marked.parse(markdownText);
            } else {
                contentEl.innerHTML = `<pre style="white-space: pre-wrap; font-family: var(--font-sans);">${escapeAttr(markdownText)}</pre>`;
            }
        }
    } catch (err) {
        console.error('Failed to load strategy description:', err);
        if (contentEl) {
            contentEl.innerHTML = `
                <div class="empty-state" style="padding: 40px 20px;">
                    <div class="empty-state-icon text-warning">⚠️</div>
                    <h3>Strategy Guide Not Found</h3>
                    <p class="text-muted">Could not load documentation for "${escapeAttr(filename)}".</p>
                    <a href="/option-strategies.html" class="btn btn-primary btn-sm" style="margin-top: 12px;">← Back to Option Strategies</a>
                </div>
            `;
        }
    }
}

// Fallback catalog of Option Greeks if API is unreachable
const DEFAULT_OPTION_GREEKS = [
    {
        id: "delta",
        name: "Delta",
        symbol: "Δ",
        order: "First-Order",
        derivative: "∂V / ∂S",
        filename: "greeks/delta.md",
        summary: "Measures the rate of change of option value with respect to changes in the underlying asset's price, serving as directional risk, hedge ratio, and rough probability proxy.",
        exposure: "Long Calls: +Δ | Long Puts: -Δ",
        impact: "Directional sensitivity, hedge ratio, probability proxy"
    },
    {
        id: "gamma",
        name: "Gamma",
        symbol: "Γ",
        order: "Second-Order",
        derivative: "∂²V / ∂S²",
        filename: "greeks/gamma.md",
        summary: "Measures the rate of change of Delta per unit move in the underlying asset, quantifying acceleration, convexity, and pin risk near expiration.",
        exposure: "Long Options: +Γ | Short Options: -Γ",
        impact: "Delta acceleration, convexity, pin risk"
    },
    {
        id: "theta",
        name: "Theta",
        symbol: "Θ",
        order: "First-Order",
        derivative: "∂V / ∂t",
        filename: "greeks/theta.md",
        summary: "Measures the decay rate of an option's extrinsic value over time, accelerating non-linearly into the Theta Cliff during the final 30 to 45 DTE.",
        exposure: "Long Options: -Θ | Short Options: +Θ",
        impact: "Time decay, extrinsic value erosion"
    },
    {
        id: "vega",
        name: "Vega",
        symbol: "V",
        order: "First-Order",
        derivative: "∂V / ∂σ",
        filename: "greeks/vega.md",
        summary: "Measures option price sensitivity to a 1% change in implied volatility, scaling with square root of time and driving post-earnings IV crush mechanics.",
        exposure: "Long Options: +V | Short Options: -V",
        impact: "Implied volatility sensitivity, IV crush"
    },
    {
        id: "rho",
        name: "Rho",
        symbol: "ρ",
        order: "First-Order",
        derivative: "∂V / ∂r",
        filename: "greeks/rho.md",
        summary: "Measures option sensitivity to changes in the risk-free interest rate, directly influencing cost of carry and deep in-the-money LEAP pricing.",
        exposure: "Long Calls: +ρ | Long Puts: -ρ",
        impact: "Interest rate sensitivity, cost of carry"
    },
    {
        id: "vanna",
        name: "Vanna",
        symbol: "∂Δ/∂σ",
        order: "Second-Order",
        derivative: "∂²V / (∂S ∂σ)",
        filename: "greeks/vanna.md",
        summary: "Cross-derivative measuring Delta sensitivity to implied volatility changes (or Vega sensitivity to price), driving dealer hedging flows during market crashes and earnings.",
        exposure: "OTM Calls: +Vanna | OTM Puts: -Vanna",
        impact: "Delta sensitivity to IV, market maker hedging"
    },
    {
        id: "charm",
        name: "Charm",
        symbol: "∂Δ/∂t",
        order: "Second-Order",
        derivative: "∂²V / (∂S ∂t)",
        filename: "greeks/charm.md",
        summary: "Second-order cross-derivative measuring the rate of Delta decay over time, pulling OTM deltas toward 0 and pushing ITM deltas toward 1 as expiration nears.",
        exposure: "OTM: Pulls Δ → 0 | ITM: Pushes Δ → 1",
        impact: "Delta decay over time, weekend pin risk"
    },
    {
        id: "volga",
        name: "Volga (Vomma)",
        symbol: "∂²V/∂σ²",
        order: "Second-Order",
        derivative: "∂²V / ∂σ²",
        filename: "greeks/volga.md",
        summary: "Second-order Vega convexity measuring the rate of change of Vega with respect to implied volatility, governing tail risk and volatility smile dynamics.",
        exposure: "OTM Wings: +Volga | ATM: ~0 Volga",
        impact: "Vega convexity, tail risk & volatility smile"
    }
];

let _learningGreeks = [];
let _activeGreekOrderFilter = 'ALL';

/**
 * Builds HTML for a single option Greek card link.
 */
function renderGreekCard(greek) {
    const isFirstOrder = (greek.order || '').toLowerCase().includes('first');
    const orderBadgeClass = isFirstOrder ? 'badge-info' : 'badge-purple';
    const detailUrl = `/greek-detail.html?greek=${encodeURIComponent(greek.id)}`;

    return `
        <a href="${detailUrl}" class="strategy-learning-card" data-greek-id="${escapeAttr(greek.id)}">
            <div>
                <div class="strategy-card-header" style="display:flex; align-items:flex-start; gap:12px;">
                    <div class="greek-symbol-avatar">${escapeAttr(greek.symbol || greek.name.charAt(0))}</div>
                    <div style="flex:1; min-width:0;">
                        <div style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
                            <h3 class="strategy-card-title" style="margin:0;">${escapeAttr(greek.name)}</h3>
                            <span class="badge ${orderBadgeClass}">${escapeAttr(greek.order || 'First-Order')}</span>
                        </div>
                        <div class="greek-formula-pill">${escapeAttr(greek.derivative || '')}</div>
                    </div>
                </div>
                <p class="strategy-card-summary">${escapeAttr(greek.summary || '')}</p>
            </div>
            <div>
                <div class="greek-card-impact">
                    <strong>Exposure:</strong> ${escapeAttr(greek.exposure || '')}
                </div>
                <div class="strategy-card-footer">
                    <span class="text-muted small">${escapeAttr(greek.impact || '')}</span>
                    <span class="strategy-read-link">
                        Read Guide <span class="link-arrow">→</span>
                    </span>
                </div>
            </div>
        </a>
    `;
}

/**
 * Filters the Option Greeks list by search query and derivative order classification.
 */
function filterGreeks(greeks, query, order = 'ALL') {
    if (!Array.isArray(greeks)) return [];
    let result = greeks;

    if (order && order !== 'ALL') {
        const targetOrder = order.toLowerCase();
        result = result.filter(g => (g.order || '').toLowerCase().includes(targetOrder));
    }

    if (query && query.trim()) {
        const q = query.trim().toLowerCase();
        result = result.filter(g => {
            const name = (g.name || '').toLowerCase();
            const id = (g.id || '').toLowerCase();
            const symbol = (g.symbol || '').toLowerCase();
            const derivative = (g.derivative || '').toLowerCase();
            const summary = (g.summary || '').toLowerCase();
            const exposure = (g.exposure || '').toLowerCase();
            const impact = (g.impact || '').toLowerCase();
            const orderStr = (g.order || '').toLowerCase();
            return name.includes(q) || id.includes(q) || symbol.includes(q) ||
                   derivative.includes(q) || summary.includes(q) || exposure.includes(q) ||
                   impact.includes(q) || orderStr.includes(q);
        });
    }

    return result;
}

function getActiveGreeks() {
    if (typeof window !== 'undefined' && Array.isArray(window._learningGreeks) && window._learningGreeks.length > 0) {
        return window._learningGreeks;
    }
    if (_learningGreeks && _learningGreeks.length > 0) {
        return _learningGreeks;
    }
    return DEFAULT_OPTION_GREEKS;
}

/**
 * Re-renders the Option Greeks card grid based on current search query and order filter state.
 */
function renderFilteredGreeks() {
    const container = document.getElementById('greeks-grid');
    const countBadge = document.getElementById('greek-count-badge');
    const searchInput = document.getElementById('greek-search-input');
    const clearBtn = document.getElementById('clear-greek-search');

    const query = searchInput ? searchInput.value : '';
    if (clearBtn) {
        clearBtn.style.display = query && query.length > 0 ? 'inline-block' : 'none';
    }

    const currentGreeks = getActiveGreeks();
    const filtered = filterGreeks(currentGreeks, query, _activeGreekOrderFilter);

    if (countBadge) {
        countBadge.textContent = `${filtered.length} ${filtered.length === 1 ? 'Greek' : 'Greeks'}`;
    }

    if (!container) return filtered;

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px;">
                <div class="empty-state-icon text-muted">🔍</div>
                <h3>No Option Greeks found</h3>
                <p class="text-muted">No Greeks matched your search criteria.</p>
                <button class="btn btn-ghost btn-sm" onclick="clearGreekSearch()" style="margin-top: 8px;">Clear Filters</button>
            </div>
        `;
        return filtered;
    }

    container.innerHTML = filtered.map(renderGreekCard).join('');
    return filtered;
}

/**
 * Handles derivative order chip selection (All Greeks, First-Order, Second-Order).
 */
function setGreekOrderFilter(order, buttonElement) {
    _activeGreekOrderFilter = order || 'ALL';

    if (typeof document !== 'undefined') {
        const chips = document.querySelectorAll('#greek-filter-chips .strategy-chip');
        chips.forEach(chip => {
            const chipOrder = chip.getAttribute('data-order');
            if (chip === buttonElement || (chipOrder && chipOrder.toLowerCase() === _activeGreekOrderFilter.toLowerCase())) {
                chip.classList.add('active');
            } else {
                chip.classList.remove('active');
            }
        });
    }

    return renderFilteredGreeks();
}

/**
 * Handles real-time search input on the Option Greeks page.
 */
function handleGreekSearch(query) {
    return renderFilteredGreeks();
}

/**
 * Clears search input and resets derivative order filter to ALL.
 */
function clearGreekSearch() {
    const input = document.getElementById('greek-search-input');
    if (input) {
        input.value = '';
        input.focus();
    }

    _activeGreekOrderFilter = 'ALL';
    if (typeof document !== 'undefined') {
        const chips = document.querySelectorAll('#greek-filter-chips .strategy-chip');
        chips.forEach(chip => {
            if (chip.getAttribute('data-order') === 'ALL') {
                chip.classList.add('active');
            } else {
                chip.classList.remove('active');
            }
        });
    }

    return renderFilteredGreeks();
}

/**
 * Initializes the Option Greeks listing page.
 */
async function initOptionGreeksPage() {
    const countBadge = document.getElementById('greek-count-badge');

    try {
        const res = await API.get('/api/learning/greeks');
        if (Array.isArray(res) && res.length > 0) {
            _learningGreeks = res;
        } else {
            _learningGreeks = DEFAULT_OPTION_GREEKS;
        }
    } catch (e) {
        console.warn('Failed to fetch option Greeks from API, using default catalog:', e);
        _learningGreeks = DEFAULT_OPTION_GREEKS;
    }

    if (typeof window !== 'undefined') {
        window._learningGreeks = _learningGreeks;
    }

    if (countBadge) {
        countBadge.textContent = `${_learningGreeks.length} Greeks`;
    }

    _activeGreekOrderFilter = 'ALL';
    renderFilteredGreeks();

    // Attach search input listener if present
    const searchInput = document.getElementById('greek-search-input');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => handleGreekSearch(e.target.value));
    }
}

/**
 * Initializes the Option Greek Detail page.
 */
async function initGreekDetailPage() {
    const params = new URLSearchParams(window.location.search);
    let greekParam = params.get('greek') || params.get('id') || params.get('file');

    if (!greekParam) {
        greekParam = 'delta';
    }

    let cleanId = greekParam.trim().toLowerCase();
    if (cleanId.startsWith('greeks/')) {
        cleanId = cleanId.substring(7);
    }
    if (cleanId.endsWith('.md')) {
        cleanId = cleanId.slice(0, -3);
    }

    const filename = `${cleanId}.md`;
    const contentEl = document.getElementById('greek-markdown-content');
    const switcherEl = document.getElementById('greek-select-switcher');

    // Populate Greek switcher dropdown
    try {
        let greeks = [];
        try {
            const apiRes = await API.get('/api/learning/greeks');
            if (Array.isArray(apiRes) && apiRes.length > 0) greeks = apiRes;
            else greeks = DEFAULT_OPTION_GREEKS;
        } catch {
            greeks = DEFAULT_OPTION_GREEKS;
        }

        if (switcherEl) {
            switcherEl.innerHTML = greeks.map(g => {
                const selected = g.id.toLowerCase() === cleanId ? 'selected' : '';
                const symbolText = g.symbol ? ` (${g.symbol})` : '';
                return `<option value="${escapeAttr(g.id)}" ${selected}>${escapeAttr(g.name)}${symbolText}</option>`;
            }).join('');

            switcherEl.addEventListener('change', (e) => {
                window.location.href = `/greek-detail.html?greek=${encodeURIComponent(e.target.value)}`;
            });
        }
    } catch (e) {
        console.warn('Could not populate Greek switcher:', e);
    }

    // Fetch and render markdown
    try {
        if (contentEl) {
            contentEl.innerHTML = `
                <div class="loading-state" style="padding: 40px 20px;">
                    <div class="spinner"></div>
                    <p>Loading Greek guide...</p>
                </div>
            `;
        }

        let res = await fetch(`/descriptions/greeks/${filename}`);
        if (!res.ok) {
            // Try fallback path directly under /descriptions/
            res = await fetch(`/descriptions/${filename}`);
        }
        if (!res.ok) {
            throw new Error(`Option Greek guide "${filename}" not found (HTTP ${res.status})`);
        }

        const markdownText = await res.text();

        const firstLine = markdownText.split('\n')[0] || '';
        if (firstLine.startsWith('# ')) {
            document.title = `${firstLine.substring(2).trim()} — Option Greeks`;
        } else {
            document.title = `${cleanId.toUpperCase()} — Option Greeks`;
        }

        if (contentEl) {
            if (typeof marked !== 'undefined') {
                contentEl.innerHTML = marked.parse(markdownText);
            } else {
                contentEl.innerHTML = `<pre style="white-space: pre-wrap; font-family: var(--font-sans);">${escapeAttr(markdownText)}</pre>`;
            }
        }
    } catch (err) {
        console.error('Failed to load Greek description:', err);
        if (contentEl) {
            contentEl.innerHTML = `
                <div class="empty-state" style="padding: 40px 20px;">
                    <div class="empty-state-icon text-warning">⚠️</div>
                    <h3>Greek Guide Not Found</h3>
                    <p class="text-muted">Could not load documentation for "${escapeAttr(filename)}".</p>
                    <a href="/option-greeks.html" class="btn btn-primary btn-sm" style="margin-top: 12px;">← Back to Option Greeks</a>
                </div>
            `;
        }
    }
}

// Global browser window bindings
if (typeof window !== 'undefined') {
    window.DEFAULT_OPTION_STRATEGIES = DEFAULT_OPTION_STRATEGIES;
    window.STRATEGY_TYPE_MAP = STRATEGY_TYPE_MAP;
    window.renderStrategyCard = renderStrategyCard;
    window.filterStrategies = filterStrategies;
    window.renderFilteredStrategies = renderFilteredStrategies;
    window.setBiasFilter = setBiasFilter;
    window.handleStrategySearch = handleStrategySearch;
    window.clearStrategySearch = clearStrategySearch;
    window.initOptionStrategiesPage = initOptionStrategiesPage;
    window.initStrategyDetailPage = initStrategyDetailPage;

    window.DEFAULT_OPTION_GREEKS = DEFAULT_OPTION_GREEKS;
    window.renderGreekCard = renderGreekCard;
    window.filterGreeks = filterGreeks;
    window.renderFilteredGreeks = renderFilteredGreeks;
    window.setGreekOrderFilter = setGreekOrderFilter;
    window.handleGreekSearch = handleGreekSearch;
    window.clearGreekSearch = clearGreekSearch;
    window.initOptionGreeksPage = initOptionGreeksPage;
    window.initGreekDetailPage = initGreekDetailPage;
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        DEFAULT_OPTION_STRATEGIES,
        STRATEGY_TYPE_MAP,
        renderStrategyCard,
        filterStrategies,
        renderFilteredStrategies,
        setBiasFilter,
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
        initGreekDetailPage
    };
}
