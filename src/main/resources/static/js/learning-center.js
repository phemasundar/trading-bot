/**
 * Trading Bot — Learning Center & Option Strategies
 */

// Fallback catalog of option strategies if API is unreachable
const DEFAULT_OPTION_STRATEGIES = [
    {
        id: "bullish_broken_wing_butterfly",
        name: "Bullish Broken Wing Butterfly (BWB)",
        filename: "strategies/bullish_broken_wing_butterfly.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "An asymmetric butterfly spread designed with a wider out-of-the-money wing, eliminating upside risk while capturing decay and directional appreciation.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "bullish_zebra",
        name: "Bullish ZEBRA (Zero Extrinsic Back Ratio)",
        filename: "strategies/bullish_zebra.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "A 100-delta stock replacement strategy utilizing 2 long ITM calls and 1 short ATM call with virtually zero extrinsic value risk and capped downside.",
        greeks: { Delta: "Positive", Gamma: "Positive", Theta: "Near Zero", Vega: "Positive" }
    },
    {
        id: "call_credit_spread",
        name: "Call Credit Spread (CCS)",
        filename: "strategies/call_credit_spread.md",
        category: "Options Strategy",
        bias: "Bearish",
        summary: "A bearish, defined-risk vertical spread profiting when the underlying price remains below the short call strike as time decay accelerates.",
        greeks: { Delta: "Negative", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "iron_condor",
        name: "Iron Condor",
        filename: "strategies/iron_condor.md",
        category: "Options Strategy",
        bias: "Neutral",
        summary: "A market-neutral, defined-risk strategy combining an OTM put credit spread and call credit spread to profit from range-bound price action and IV crush.",
        greeks: { Delta: "Neutral", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "long_call_leap",
        name: "Long Call LEAPs",
        filename: "strategies/long_call_leap.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "Deep in-the-money long-term call options providing capital-efficient equity exposure with minimal theta drag and leveraged upside participation.",
        greeks: { Delta: "Positive", Gamma: "Positive", Theta: "Negative", Vega: "Positive" }
    },
    {
        id: "put_credit_spread",
        name: "Put Credit Spread (PCS)",
        filename: "strategies/put_credit_spread.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "A high-probability bullish options strategy selling an OTM put while buying a lower strike put for defined risk, profiting from sideways or upward drift.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "short_put",
        name: "Cash-Secured Put (Short Put / CSP)",
        filename: "strategies/short_put.md",
        category: "Options Strategy",
        bias: "Bullish",
        summary: "Selling an out-of-the-money put with cash backing to generate income or acquire top-tier stocks at an effective discount to current market price.",
        greeks: { Delta: "Positive", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    },
    {
        id: "short_strangle",
        name: "Short Strangle",
        filename: "strategies/short_strangle.md",
        category: "Options Strategy",
        bias: "Neutral",
        summary: "Selling an out-of-the-money put and out-of-the-money call simultaneously, maximizing premium collection and profiting from high implied volatility contraction.",
        greeks: { Delta: "Neutral", Gamma: "Negative", Theta: "Positive", Vega: "Negative" }
    }
];

let _learningStrategies = [];
let _activeBiasFilter = 'ALL';

const STRATEGY_TYPE_MAP = {
    'strategies/bullish_broken_wing_butterfly.md': 'BULLISH_BROKEN_WING_BUTTERFLY',
    'bullish_broken_wing_butterfly.md': 'BULLISH_BROKEN_WING_BUTTERFLY',
    'bullish_bwb.md': 'BULLISH_BROKEN_WING_BUTTERFLY',
    'strategies/bullish_zebra.md': 'BULLISH_ZEBRA',
    'bullish_zebra.md': 'BULLISH_ZEBRA',
    'strategies/call_credit_spread.md': 'CALL_CREDIT_SPREAD',
    'call_credit_spread.md': 'CALL_CREDIT_SPREAD',
    'strategies/iron_condor.md': 'IRON_CONDOR',
    'iron_condor.md': 'IRON_CONDOR',
    'strategies/long_call_leap.md': 'LONG_CALL_LEAP',
    'long_call_leap.md': 'LONG_CALL_LEAP',
    'strategies/put_credit_spread.md': 'PUT_CREDIT_SPREAD',
    'put_credit_spread.md': 'PUT_CREDIT_SPREAD',
    'strategies/short_put.md': 'SHORT_PUT',
    'short_put.md': 'SHORT_PUT',
    'strategies/short_strangle.md': 'SHORT_STRANGLE',
    'short_strangle.md': 'SHORT_STRANGLE'
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
 * Shared helpers for Learning Center catalog fetching, filtering, and Markdown guide rendering.
 */
async function fetchLearningCatalog(endpoint, defaultCatalog, warnMessage = null) {
    try {
        const res = await API.get(endpoint);
        if (Array.isArray(res) && res.length > 0) {
            return res;
        }
    } catch (e) {
        if (warnMessage) {
            console.warn(warnMessage, e);
        }
    }
    return defaultCatalog;
}

function updateActiveChipState(selector, attrName, activeValue, buttonElement = null) {
    if (typeof document === 'undefined') return;
    const chips = document.querySelectorAll(selector);
    chips.forEach(chip => {
        const chipVal = chip.getAttribute(attrName);
        const matches = buttonElement
            ? (chip === buttonElement || (chipVal && chipVal.toLowerCase() === activeValue.toLowerCase()))
            : (chipVal === activeValue);
        if (matches) {
            chip.classList.add('active');
        } else {
            chip.classList.remove('active');
        }
    });
}

function renderFilteredCatalogGrid({
    containerId,
    countBadgeId,
    searchInputId,
    clearBtnId,
    items,
    filterFn,
    activeChipFilter,
    singularLabel,
    pluralLabel,
    emptyHeading,
    emptySubtext,
    clearFnName,
    renderCardFn
}) {
    const container = document.getElementById(containerId);
    const countBadge = document.getElementById(countBadgeId);
    const searchInput = document.getElementById(searchInputId);
    const clearBtn = document.getElementById(clearBtnId);

    const query = searchInput ? searchInput.value : '';
    if (clearBtn) {
        clearBtn.style.display = query && query.length > 0 ? 'inline-block' : 'none';
    }

    const filtered = filterFn(items, query, activeChipFilter);

    if (countBadge) {
        countBadge.textContent = `${filtered.length} ${filtered.length === 1 ? singularLabel : pluralLabel}`;
    }

    if (!container) return filtered;

    if (filtered.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px;">
                <div class="empty-state-icon text-muted">🔍</div>
                <h3>${emptyHeading}</h3>
                <p class="text-muted">${emptySubtext}</p>
                <button class="btn btn-ghost btn-sm" onclick="${clearFnName}()" style="margin-top: 8px;">Clear Filters</button>
            </div>
        `;
        return filtered;
    }

    container.innerHTML = filtered.map(renderCardFn).join('');
    return filtered;
}

function renderGuideLoadingState(contentEl, loadingText) {
    if (!contentEl) return;
    contentEl.innerHTML = `
        <div class="loading-state" style="padding: 40px 20px;">
            <div class="spinner"></div>
            <p>${loadingText}</p>
        </div>
    `;
}

function renderGuideErrorState(contentEl, heading, message, backHref, backLabel) {
    if (!contentEl) return;
    contentEl.innerHTML = `
        <div class="empty-state" style="padding: 40px 20px;">
            <div class="empty-state-icon text-warning">⚠️</div>
            <h3>${heading}</h3>
            <p class="text-muted">${message}</p>
            <a href="${backHref}" class="btn btn-primary btn-sm" style="margin-top: 12px;">${backLabel}</a>
        </div>
    `;
}

function renderMarkdownIntoElement(contentEl, markdownText) {
    if (!contentEl) return;
    if (typeof renderMarkdown === 'function') {
        contentEl.innerHTML = renderMarkdown(markdownText);
    } else if (typeof marked !== 'undefined') {
        if (typeof initMarkedMath === 'function') initMarkedMath();
        contentEl.innerHTML = marked.parse(markdownText);
    } else {
        contentEl.innerHTML = `<pre style="white-space: pre-wrap; font-family: var(--font-sans);">${escapeAttr(markdownText)}</pre>`;
    }
}

async function fetchFirstAvailableMarkdown(urls, buildErrorFn) {
    let lastStatus = 404;
    for (const url of urls) {
        const res = await fetch(url);
        if (res.ok) {
            return res.text();
        }
        lastStatus = res.status;
    }
    throw new Error(buildErrorFn(lastStatus));
}

/**
 * Re-renders the strategy card grid based on current search query and bias filter state.
 */
function renderFilteredStrategies() {
    return renderFilteredCatalogGrid({
        containerId: 'strategies-grid',
        countBadgeId: 'strategy-count-badge',
        searchInputId: 'strategy-search-input',
        clearBtnId: 'clear-strategy-search',
        items: getActiveStrategies(),
        filterFn: filterStrategies,
        activeChipFilter: _activeBiasFilter,
        singularLabel: 'Strategy',
        pluralLabel: 'Strategies',
        emptyHeading: 'No strategies found',
        emptySubtext: 'No option strategies matched your criteria.',
        clearFnName: 'clearStrategySearch',
        renderCardFn: renderStrategyCard
    });
}

/**
 * Handles directional bias chip selection (All, Bullish, Neutral, Bearish).
 */
function setBiasFilter(bias, buttonElement) {
    _activeBiasFilter = bias || 'ALL';
    updateActiveChipState('.strategy-filter-chips .strategy-chip', 'data-bias', _activeBiasFilter, buttonElement);
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
    updateActiveChipState('.strategy-filter-chips .strategy-chip', 'data-bias', 'ALL');
    return renderFilteredStrategies();
}

/**
 * Initializes the Option Strategies listing page.
 */
async function initOptionStrategiesPage() {
    const countBadge = document.getElementById('strategy-count-badge');

    _learningStrategies = await fetchLearningCatalog(
        '/api/learning/strategies',
        DEFAULT_OPTION_STRATEGIES,
        'Failed to fetch learning strategies from API, using default catalog:'
    );

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
        strategyParam = 'strategies/iron_condor.md';
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
        const strategies = await fetchLearningCatalog('/api/learning/strategies', DEFAULT_OPTION_STRATEGIES);

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
        renderGuideLoadingState(contentEl, 'Loading strategy guide...');

        const candidateUrls = [`/descriptions/${filename}`];
        if (!filename.startsWith('strategies/')) {
            candidateUrls.push(`/descriptions/strategies/${filename}`);
        } else {
            candidateUrls.push(`/descriptions/${filename.substring(11)}`);
        }

        const markdownText = await fetchFirstAvailableMarkdown(
            candidateUrls,
            (status) => `Strategy description file "${filename}" not found (HTTP ${status})`
        );

        // Extract title from markdown if available
        const firstLine = markdownText.split('\n')[0] || '';
        if (firstLine.startsWith('# ') && titleEl) {
            titleEl.textContent = firstLine.substring(2).trim();
            document.title = `${firstLine.substring(2).trim()} — Option Strategies`;
        } else if (titleEl) {
            titleEl.textContent = strategyId.replace(/_/g, ' ').toUpperCase();
        }

        renderMarkdownIntoElement(contentEl, markdownText);
    } catch (err) {
        console.error('Failed to load strategy description:', err);
        renderGuideErrorState(
            contentEl,
            'Strategy Guide Not Found',
            `Could not load documentation for "${escapeAttr(filename)}".`,
            '/option-strategies.html',
            '← Back to Option Strategies'
        );
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
    return renderFilteredCatalogGrid({
        containerId: 'greeks-grid',
        countBadgeId: 'greek-count-badge',
        searchInputId: 'greek-search-input',
        clearBtnId: 'clear-greek-search',
        items: getActiveGreeks(),
        filterFn: filterGreeks,
        activeChipFilter: _activeGreekOrderFilter,
        singularLabel: 'Greek',
        pluralLabel: 'Greeks',
        emptyHeading: 'No Option Greeks found',
        emptySubtext: 'No Greeks matched your search criteria.',
        clearFnName: 'clearGreekSearch',
        renderCardFn: renderGreekCard
    });
}

/**
 * Handles derivative order chip selection (All Greeks, First-Order, Second-Order).
 */
function setGreekOrderFilter(order, buttonElement) {
    _activeGreekOrderFilter = order || 'ALL';
    updateActiveChipState('#greek-filter-chips .strategy-chip', 'data-order', _activeGreekOrderFilter, buttonElement);
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
    updateActiveChipState('#greek-filter-chips .strategy-chip', 'data-order', 'ALL');
    return renderFilteredGreeks();
}

/**
 * Initializes the Option Greeks listing page.
 */
async function initOptionGreeksPage() {
    const countBadge = document.getElementById('greek-count-badge');

    _learningGreeks = await fetchLearningCatalog(
        '/api/learning/greeks',
        DEFAULT_OPTION_GREEKS,
        'Failed to fetch option Greeks from API, using default catalog:'
    );

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
        const greeks = await fetchLearningCatalog('/api/learning/greeks', DEFAULT_OPTION_GREEKS);

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
        renderGuideLoadingState(contentEl, 'Loading Greek guide...');

        const markdownText = await fetchFirstAvailableMarkdown(
            [`/descriptions/greeks/${filename}`, `/descriptions/${filename}`],
            (status) => `Option Greek guide "${filename}" not found (HTTP ${status})`
        );

        const firstLine = markdownText.split('\n')[0] || '';
        if (firstLine.startsWith('# ')) {
            document.title = `${firstLine.substring(2).trim()} — Option Greeks`;
        } else {
            document.title = `${cleanId.toUpperCase()} — Option Greeks`;
        }

        renderMarkdownIntoElement(contentEl, markdownText);
    } catch (err) {
        console.error('Failed to load Greek description:', err);
        renderGuideErrorState(
            contentEl,
            'Greek Guide Not Found',
            `Could not load documentation for "${escapeAttr(filename)}".`,
            '/option-greeks.html',
            '← Back to Option Greeks'
        );
    }
}

/**
 * Initializes the Volatility Learning Center page.
 */
async function initVolatilityPage() {
    const contentEl = document.getElementById('volatility-markdown-content');
    if (!contentEl) return;

    try {
        renderGuideLoadingState(contentEl, 'Loading Volatility guide...');

        const markdownText = await fetchFirstAvailableMarkdown(
            ['/descriptions/Volatility.md', '/descriptions/volatility.md'],
            (status) => `Volatility guide not found (HTTP ${status})`
        );

        const firstLine = markdownText.split('\n')[0] || '';
        if (firstLine.startsWith('# ')) {
            document.title = `${firstLine.substring(2).trim()} — Learning Center`;
        }

        renderMarkdownIntoElement(contentEl, markdownText);
    } catch (err) {
        console.error('Failed to load volatility guide:', err);
        renderGuideErrorState(
            contentEl,
            'Volatility Guide Not Found',
            'Could not load documentation for Volatility.',
            '/option-strategies.html',
            '← Back to Learning Center'
        );
    }
}

const LEARNING_CENTER_EXPORTS = {
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
    initGreekDetailPage,
    initVolatilityPage
};

if (typeof window !== 'undefined') {
    Object.assign(window, LEARNING_CENTER_EXPORTS);
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = LEARNING_CENTER_EXPORTS;
}
