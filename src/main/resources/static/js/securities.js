/**
 * Trading Bot — Securities Analysis Page Script
 * Fetches discovered securities groups, loads precalculated indicators from Supabase,
 * and renders modern interactive cards with dynamic indicator tracking.
 */

let securitiesGroups = [];
let filterConfig = null;
const indicatorsCache = new Map();
const loadedGroups = new Set();
let globalSearchQuery = '';

async function apiGet(path) {
    if (typeof API !== 'undefined' && API.get) {
        return await API.get(path);
    }
    const res = await fetch(path);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
}

async function apiPost(path, body) {
    if (typeof API !== 'undefined' && API.post) {
        return await API.post(path, body);
    }
    const res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
}

async function initSecuritiesPage() {
    await Promise.all([
        loadFilterConfig(),
        loadSecuritiesGroups()
    ]);
}

/**
 * Loads simplified filter config from /api/securities/filter-config
 */
async function loadFilterConfig() {
    try {
        filterConfig = await apiGet('/api/securities/filter-config');
        renderFilterSummary(filterConfig);
    } catch (e) {
        console.warn('Failed to load filter config:', e);
        renderFilterSummaryFallback();
    }
}

function renderFilterSummary(cfg) {
    const f = cfg?.filters || {};
    const rsi = f.rsi;
    const bb = f.bollinger;
    const ma = f.moving_averages || f.movingAverages;
    const ema = f.exponential_moving_averages || f.exponentialMovingAverages;
    const vol = f.volume;
    const vola = f.volatility;

    if (rsi) {
        setChipText('chip-rsi', `RSI (${rsi.period}): < ${rsi.oversold} / > ${rsi.overbought}`);
    }
    if (bb) {
        setChipText('chip-bb', `BB (${bb.period}, ${bb.stdDev}σ)`);
    }
    if (ma?.periods) {
        setChipText('chip-ma', `SMAs: ${ma.periods.join(', ')}`);
    }
    if (ema?.periods) {
        setChipText('chip-ema', `EMAs: ${ema.periods.join(', ')}`);
    }
    if (vol?.smaPeriods || vol?.sma_periods) {
        const periods = vol.smaPeriods || vol.sma_periods;
        setChipText('chip-vol', `Vol SMAs: ${periods.join(', ')}`);
    }
    if (vola) {
        setChipText('chip-vola', `ATR (${vola.atrPeriod || vola.atr_period}) / HV (${vola.hvPeriod || vola.hv_period})`);
    }
}

function renderFilterSummaryFallback() {
    setChipText('chip-rsi', 'RSI: 14 (<30 / >70)');
    setChipText('chip-bb', 'BB: 20, 2.0σ');
    setChipText('chip-ma', 'SMAs: 20, 50, 100, 200');
    setChipText('chip-ema', 'EMAs: 9, 21, 50');
    setChipText('chip-vol', 'Vol SMAs: 20, 50');
    setChipText('chip-vola', 'ATR: 14 / HV: 20');
}

function setChipText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

/**
 * Loads discovered securities file blocks from /api/securities/groups
 */
async function loadSecuritiesGroups() {
    const container = document.getElementById('securities-groups-container');
    try {
        securitiesGroups = await apiGet('/api/securities/groups');

        if (!securitiesGroups || securitiesGroups.length === 0) {
            container.innerHTML = `
                <div class="empty-state card">
                    <p>No securities files found in <code>src/main/resources/securities/</code>.</p>
                </div>`;
            return;
        }

        renderSecuritiesGroups(securitiesGroups);

        // Automatically expand the first group for immediate visual richness
        if (securitiesGroups.length > 0) {
            toggleGroupBlock(securitiesGroups[0].id, true);
        }
    } catch (e) {
        console.error('Failed to load securities groups:', e);
        container.innerHTML = `
            <div class="error-state card">
                <p>Failed to load securities groups: ${escapeHtml(e.message)}</p>
                <button class="btn btn-primary" onclick="loadSecuritiesGroups()" style="margin-top:12px;">Retry</button>
            </div>`;
    }
}

/**
 * Renders the collapsible accordion blocks for each securities file
 */
function renderSecuritiesGroups(groups) {
    const container = document.getElementById('securities-groups-container');
    container.innerHTML = groups.map(group => `
        <div class="securities-group-block card" id="group-card-${group.id}">
            <div class="group-header" onclick="toggleGroupBlock('${group.id}')">
                <div class="group-header-left">
                    <span class="card-arrow" id="arrow-${group.id}">▶</span>
                    <span class="group-icon">📁</span>
                    <span class="group-title">${escapeHtml(group.displayName)}</span>
                    <span class="badge badge-subtle">${escapeHtml(group.fileName)}</span>
                </div>
                <div class="group-header-right">
                    <span class="badge badge-count" id="count-badge-${group.id}">${group.symbolCount} symbols</span>
                    <span class="status-indicator" id="status-ind-${group.id}">Click to view</span>
                </div>
            </div>
            <div class="group-body" id="body-${group.id}" style="display:none;">
                <div class="group-inner-toolbar">
                    <div class="group-search-wrapper">
                        <span class="search-icon">🔍</span>
                        <input type="text" class="form-input group-search-input"
                               placeholder="Filter within ${escapeHtml(group.displayName)}..."
                               oninput="filterGroupSymbols('${group.id}', this.value)">
                    </div>
                    <div class="group-stats-label" id="stats-${group.id}">
                        Showing ${group.symbolCount} securities
                    </div>
                </div>
                <div class="securities-cards-grid" id="grid-${group.id}">
                    <div class="loading-inline">
                        <div class="spinner-sm"></div>
                        <span>Fetching indicator records from Supabase...</span>
                    </div>
                </div>
            </div>
        </div>
    `).join('');
}

/**
 * Toggles a group block open/closed and triggers lazy indicator fetching
 */
async function toggleGroupBlock(groupId, forceOpen = false) {
    const body = document.getElementById(`body-${groupId}`);
    const arrow = document.getElementById(`arrow-${groupId}`);
    const statusInd = document.getElementById(`status-ind-${groupId}`);
    if (!body || !arrow) return;

    const isOpen = body.style.display !== 'none';
    const shouldOpen = forceOpen ? true : !isOpen;

    if (shouldOpen) {
        body.style.display = 'block';
        arrow.classList.add('open');
        if (statusInd) statusInd.textContent = 'Loaded';

        // Lazy load data if not yet loaded
        if (!loadedGroups.has(groupId)) {
            await loadGroupData(groupId);
        } else {
            renderGroupCards(groupId);
        }
    } else {
        body.style.display = 'none';
        arrow.classList.remove('open');
    }
}

/**
 * Fetches indicator data for symbols in a group from /api/securities/data
 */
async function loadGroupData(groupId) {
    const group = securitiesGroups.find(g => g.id === groupId);
    if (!group) return;

    const grid = document.getElementById(`grid-${groupId}`);
    const statusInd = document.getElementById(`status-ind-${groupId}`);

    // Identify symbols needing fetch
    const symbolsToFetch = group.symbols.filter(s => !indicatorsCache.has(s.toUpperCase()));

    if (symbolsToFetch.length > 0) {
        try {
            if (statusInd) statusInd.textContent = 'Syncing...';
            const data = await apiPost('/api/securities/data', { symbols: symbolsToFetch });
            if (data) {
                Object.entries(data).forEach(([sym, result]) => {
                    indicatorsCache.set(sym.toUpperCase(), result);
                });
            }
        } catch (e) {
            console.error(`Failed to fetch indicators for group ${groupId}:`, e);
        }
    }

    loadedGroups.add(groupId);
    if (statusInd) statusInd.textContent = 'Active';
    renderGroupCards(groupId);
}

/**
 * Renders the cards grid for a group, respecting group search and global search
 */
function renderGroupCards(groupId) {
    const group = securitiesGroups.find(g => g.id === groupId);
    const grid = document.getElementById(`grid-${groupId}`);
    const statsLabel = document.getElementById(`stats-${groupId}`);
    if (!group || !grid) return;

    const groupSearch = (document.querySelector(`#body-${groupId} .group-search-input`)?.value || '').trim().toUpperCase();
    const effectiveSearch = globalSearchQuery || groupSearch;

    const symbols = group.symbols.filter(sym => {
        const symbolUpper = sym.toUpperCase();
        const data = indicatorsCache.get(symbolUpper);
        const companyNameUpper = (data?.companyName || '').toUpperCase();

        if (!effectiveSearch) return true;
        return symbolUpper.includes(effectiveSearch) || companyNameUpper.includes(effectiveSearch);
    });

    if (statsLabel) {
        statsLabel.textContent = `Showing ${symbols.length} of ${group.symbolCount} securities`;
    }

    if (symbols.length === 0) {
        grid.innerHTML = `
            <div class="empty-cards-notice">
                <p>No securities match "${escapeHtml(effectiveSearch)}".</p>
            </div>`;
        return;
    }

    grid.innerHTML = symbols.map(sym => {
        const symUpper = sym.toUpperCase();
        const data = indicatorsCache.get(symUpper);
        if (data && data.currentPrice > 0) {
            return createSecurityCardHtml(symUpper, data);
        } else {
            return createPendingCardHtml(symUpper);
        }
    }).join('');
}

/**
 * Builds the rich security card HTML matching the design reference
 */
function createSecurityCardHtml(symbol, data) {
    const price = data.currentPrice || 0;
    const company = data.companyName || symbol;
    const rsi = data.rsi != null ? data.rsi : null;
    const prevRsi = data.previousRsi != null ? data.previousRsi : null;
    const sma20 = data.maValues?.[20] ?? null;
    const sma50 = data.maValues?.[50] ?? null;
    const sma100 = data.maValues?.[100] ?? null;
    const sma200 = data.maValues?.[200] ?? null;
    const ema9 = data.emaValues?.[9] ?? null;
    const ema21 = data.emaValues?.[21] ?? null;
    const ema50 = data.emaValues?.[50] ?? null;
    const bbLower = data.bollingerLower ?? null;
    const bbMiddle = data.bollingerMiddle ?? null;
    const bbUpper = data.bollingerUpper ?? null;
    const volume = data.volume ?? 0;
    const volSma20 = data.volumeMaValues?.[20] ?? null;
    const atr = data.atr ?? null;
    const hvRank = data.historicalVolatilityRank ?? null;
    const ivPercentile = data.ivPercentile ?? null;
    const ivRank = data.ivRank ?? null;
    const ivDays = data.ivDays ?? data.recordCount ?? null;
    const hasIvData = ivPercentile != null || ivRank != null;
    const isLessThanOneYear = ivDays != null && ivDays > 0 && ivDays < 252;
    const ivLabel = `IV Percentile / IV Rank${isLessThanOneYear ? ` (${ivDays})` : ''}`;

    // 1. Primary Alert Pill
    let pillText = '⚡ MOMENTUM TRACKER';
    let pillClass = 'pill-neutral';

    if (data.rsiOversold) {
        pillText = `⚡ RSI OVERSOLD (${rsi?.toFixed(1)})`;
        pillClass = 'pill-danger';
    } else if (data.rsiBullishCrossover) {
        pillText = `⚡ RSI BULLISH CROSSOVER`;
        pillClass = 'pill-success';
    } else if (sma50 != null && price > sma50) {
        pillText = `⚡ PUSH PAST DAILY 50 SMA ($${sma50.toFixed(2)})`;
        pillClass = 'pill-success';
    } else if (data.priceTouchingLowerBand) {
        pillText = `⚡ TOUCHING LOWER BB`;
        pillClass = 'pill-warning';
    } else if (sma20 != null && price > sma20) {
        pillText = `⚡ ABOVE DAILY 20 SMA ($${sma20.toFixed(2)})`;
        pillClass = 'pill-success';
    }

    // 2. Narrative summary
    let narrative = data.allTechnicalIndicatorsSummary;
    if (!narrative || narrative.isBlank) {
        const sma50Part = sma50 != null ? `, pushing past its Daily 50 SMA ($${sma50.toFixed(2)})` : '';
        const ema50Part = ema50 != null ? ` and Daily 50 EMA ($${ema50.toFixed(2)})` : '';
        const supportPart = sma100 != null ? ` after holding its Daily 100 SMA ($${sma100.toFixed(2)}) support base.` : '.';
        narrative = `${symbol} expanded to $${price.toFixed(2)}${sma50Part}${ema50Part}${supportPart}`;
    }

    // 3. Price Action Progression
    const floorLabel = sma100 ? '100 SMA Floor' : (sma50 ? '50 SMA Floor' : 'BB Lower');
    const floorVal = sma100 ? `$${sma100.toFixed(2)}` : (sma50 ? `$${sma50.toFixed(2)}` : (bbLower ? `$${bbLower.toFixed(2)}` : '—'));

    const midLabel = sma50 && sma100 ? '50 SMA' : (sma20 ? '20 SMA' : (bbMiddle ? 'BB Mid' : 'Prev Ref'));
    const midVal = sma50 && sma100 ? `$${sma50.toFixed(2)}` : (sma20 ? `$${sma20.toFixed(2)}` : (bbMiddle ? `$${bbMiddle.toFixed(2)}` : '—'));

    // 4. Moving Average Status badges
    const ema50sma50Cleared = (price > (ema50 || 0)) && (price > (sma50 || 0));
    const ema21sma20Cleared = (price > (ema21 || 0)) && (price > (sma20 || 0));

    // 5. Playbook message
    let playbook = '';
    if (sma50 && price > sma50) {
        const targetPrice = (price * 1.10).toFixed(0);
        const dipFloor = (sma100 || sma50 * 0.96).toFixed(0);
        playbook = `Reclaiming $${sma50.toFixed(2)} activates upside momentum toward $${targetPrice}. Catch dips into $${dipFloor}-$${sma50.toFixed(0)}.`;
    } else if (data.rsiOversold) {
        playbook = `RSI reached oversold threshold (${rsi?.toFixed(1)}). Watch for reversal wick near $${(bbLower || price * 0.98).toFixed(2)}.`;
    } else if (data.priceTouchingLowerBand) {
        playbook = `Testing Lower Bollinger Band ($${bbLower?.toFixed(2)}). Mean reversion target towards BB Middle ($${(bbMiddle || price).toFixed(2)}).`;
    } else {
        playbook = `Consolidating above support $${floorVal}. Trend strength confirmed while above $${midVal}.`;
    }

    return `
        <div class="sec-card">
            <!-- Left Column: Title, Narrative & Progression -->
            <div class="sec-left-col">
                <div class="sec-top-meta">
                    <span class="sec-alert-pill ${pillClass}">${pillText}</span>
                    <span class="sec-ticker-label">NASDAQ: ${symbol}</span>
                </div>

                <div class="sec-header-block">
                    <h2 class="sec-company-name">${escapeHtml(company)}</h2>
                    <span class="sec-symbol-tag">${symbol}</span>
                </div>

                <p class="sec-narrative">${escapeHtml(narrative)}</p>

                <div class="sec-progression-box">
                    <div class="sec-progression-title">PRICE ACTION PROGRESSION</div>
                    <div class="sec-progression-steps">
                        <div class="sec-step">
                            <span class="sec-step-label">${floorLabel}</span>
                            <span class="sec-step-value">${floorVal}</span>
                        </div>
                        <span class="sec-step-arrow">›</span>
                        <div class="sec-step">
                            <span class="sec-step-label">${midLabel}</span>
                            <span class="sec-step-value">${midVal}</span>
                        </div>
                        <span class="sec-step-arrow">›</span>
                        <div class="sec-step sec-step-highlight">
                            <span class="sec-step-label">Latest Close</span>
                            <span class="sec-step-value">$${price.toFixed(2)}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right Column: Tech Tracker & Playbook -->
            <div class="sec-right-col">
                <div class="sec-tracker-card">
                    <div class="sec-tracker-title">DYNAMIC MOVING AVERAGE TRACKER</div>

                    <div class="sec-metrics-list">
                        ${(ema50 || sma50) ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label ${ema50sma50Cleared ? 'text-success' : ''}">
                                Daily EMA 50 / Daily SMA 50 ${ema50sma50Cleared ? '<small>(Cleared)</small>' : ''}
                            </span>
                            <span class="sec-metric-values">
                                ${ema50 ? ema50.toFixed(2) : '—'} / ${sma50 ? sma50.toFixed(2) : '—'}
                            </span>
                        </div>` : ''}

                        ${(ema21 || sma20) ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label ${ema21sma20Cleared ? 'text-success' : ''}">
                                Daily EMA 21 / Daily SMA 20 ${ema21sma20Cleared ? '<small>(Cleared)</small>' : ''}
                            </span>
                            <span class="sec-metric-values">
                                ${ema21 ? ema21.toFixed(2) : '—'} / ${sma20 ? sma20.toFixed(2) : '—'}
                            </span>
                        </div>` : ''}

                        ${sma100 ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">Daily SMA 100</span>
                            <span class="sec-metric-values">${sma100.toFixed(2)}</span>
                        </div>` : ''}

                        ${sma200 ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">Daily SMA 200 (Long Term)</span>
                            <span class="sec-metric-values">${sma200.toFixed(2)}</span>
                        </div>` : ''}

                        ${rsi != null ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">
                                RSI (14)
                                <span class="badge ${rsi < 30 ? 'badge-danger' : (rsi > 70 ? 'badge-success' : 'badge-subtle')} badge-inline">
                                    ${rsi < 30 ? 'Oversold' : (rsi > 70 ? 'Overbought' : 'Neutral')}
                                </span>
                            </span>
                            <span class="sec-metric-values">${rsi.toFixed(2)}</span>
                        </div>` : ''}

                        ${bbLower != null ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">
                                Bollinger Bands (20, 2σ)
                                <span class="badge ${data.priceTouchingLowerBand ? 'badge-warning' : 'badge-subtle'} badge-inline">
                                    ${data.priceTouchingLowerBand ? 'At Lower' : (data.priceTouchingUpperBand ? 'At Upper' : 'Inside')}
                                </span>
                            </span>
                            <span class="sec-metric-values">$${bbLower.toFixed(2)} / $${bbUpper?.toFixed(2)}</span>
                        </div>` : ''}

                        ${volume > 0 ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">Volume / Vol SMA 20</span>
                            <span class="sec-metric-values">${formatVolume(volume)} / ${volSma20 ? formatVolume(volSma20) : '—'}</span>
                        </div>` : ''}

                        ${(atr != null || hvRank != null) ? `
                        <div class="sec-metric-row">
                            <span class="sec-metric-label">ATR (14) / HV Rank</span>
                            <span class="sec-metric-values">${atr ? '$' + atr.toFixed(2) : '—'} / ${hvRank != null ? hvRank.toFixed(1) + '%' : '—'}</span>
                        </div>` : ''}

                        <div class="sec-metric-row">
                            <span class="sec-metric-label">
                                ${ivLabel}
                                ${hasIvData && ivPercentile != null ? `
                                <span class="badge ${ivPercentile < 30 ? 'badge-subtle' : (ivPercentile > 70 ? 'badge-warning' : 'badge-subtle')} badge-inline">
                                    ${ivPercentile < 30 ? 'Low IV' : (ivPercentile > 70 ? 'High IV' : 'Normal')}
                                </span>` : ''}
                            </span>
                            <span class="sec-metric-values">${hasIvData ? `${ivPercentile != null ? ivPercentile.toFixed(1) + '%' : '—'} / ${ivRank != null ? ivRank.toFixed(1) + '%' : '—'}` : 'NA'}</span>
                        </div>
                    </div>

                    <div class="sec-playbook-callout">
                        <div class="sec-playbook-header">Trading Playbook:</div>
                        <div class="sec-playbook-text">${escapeHtml(playbook)}</div>
                    </div>
                </div>
            </div>
        </div>
    `;
}

/**
 * Placeholder card when indicators have not yet been evaluated/saved for this symbol
 */
function createPendingCardHtml(symbol) {
    return `
        <div class="sec-card sec-card-pending">
            <div class="sec-left-col">
                <div class="sec-top-meta">
                    <span class="sec-alert-pill pill-neutral">⏳ PENDING EVALUATION</span>
                    <span class="sec-ticker-label">TICKER: ${symbol}</span>
                </div>
                <div class="sec-header-block">
                    <h2 class="sec-company-name">${symbol}</h2>
                </div>
                <p class="sec-narrative">
                    Technical indicators have not yet been calculated in the database for this security. Indicators are evaluated automatically during daily scheduled runs or manual screener execution.
                </p>
                <div class="sec-pending-action">
                    <a href="/execute-screener.html" class="btn btn-sm btn-ghost">Run Screener Now →</a>
                </div>
            </div>
            <div class="sec-right-col">
                <div class="sec-tracker-card sec-tracker-card-empty">
                    <div class="empty-tracker-notice">
                        <span>Awaiting screener precalculation</span>
                    </div>
                </div>
            </div>
        </div>
    `;
}

/**
 * Filter within a specific group
 */
function filterGroupSymbols(groupId, text) {
    renderGroupCards(groupId);
}

/**
 * Global search across all groups
 */
function handleGlobalSearch(query) {
    globalSearchQuery = query.trim().toUpperCase();
    const clearBtn = document.getElementById('clear-search-btn');
    if (clearBtn) clearBtn.style.display = globalSearchQuery ? 'inline-block' : 'none';

    securitiesGroups.forEach(group => {
        const body = document.getElementById(`body-${group.id}`);
        // If searching and body is closed, open it so user sees search results
        if (globalSearchQuery && body && body.style.display === 'none') {
            toggleGroupBlock(group.id, true);
        } else {
            renderGroupCards(group.id);
        }
    });
}

function clearSearch() {
    const input = document.getElementById('securities-global-search');
    if (input) input.value = '';
    handleGlobalSearch('');
}

/**
 * Toggle Expand All / Collapse All blocks
 */
function toggleAllBlocks() {
    const btn = document.getElementById('toggle-all-text');
    const isExpanding = btn.textContent.includes('Expand');

    securitiesGroups.forEach(group => {
        toggleGroupBlock(group.id, isExpanding);
    });

    btn.textContent = isExpanding ? 'Collapse All' : 'Expand All';
}

function formatVolume(vol) {
    if (vol == null || isNaN(vol) || vol === 0) return '0';
    if (vol >= 1_000_000_000) return (vol / 1_000_000_000).toFixed(2) + 'B';
    if (vol >= 1_000_000) return (vol / 1_000_000).toFixed(2) + 'M';
    if (vol >= 1_000) return (vol / 1_000).toFixed(1) + 'K';
    return String(Math.round(vol));
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
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
        escapeHtml
    };
}

