/**
 * Trading Bot — Dashboard & Table Rendering
 * Dashboard initialization, results loading, card/table builders, sorting, live quote performance, and filter bar.
 */

// ── Global State & CardStore Registry for Sorting & Strategy Mapping ──
window.tableSortState = window.tableSortState || {};
window.tradeDataMap = window.tradeDataMap || {};
window.tradeStrategyIdMap = window.tradeStrategyIdMap || {};
window.tradeStrategyTypeMap = window.tradeStrategyTypeMap || {};
window.strategyColumnsConfig = window.strategyColumnsConfig || null;

const CardStore = {
    getTrades(cardId) {
        return (window.tradeDataMap && window.tradeDataMap[cardId]) || [];
    },
    setTrades(cardId, trades, type = null) {
        if (!window.tradeDataMap) window.tradeDataMap = {};
        window.tradeDataMap[cardId] = trades || [];
        if (type && window.tradeDataMap[cardId]) {
            window.tradeDataMap[cardId]._type = type;
        }
    },
    getStrategyId(cardId) {
        return (window.tradeStrategyIdMap && window.tradeStrategyIdMap[cardId]) || null;
    },
    setStrategyId(cardId, strategyId) {
        if (!window.tradeStrategyIdMap) window.tradeStrategyIdMap = {};
        window.tradeStrategyIdMap[cardId] = strategyId;
    },
    getStrategyType(cardId) {
        return (window.tradeStrategyTypeMap && window.tradeStrategyTypeMap[cardId]) || null;
    },
    setStrategyType(cardId, strategyType) {
        if (!window.tradeStrategyTypeMap) window.tradeStrategyTypeMap = {};
        if (cardId && strategyType) {
            window.tradeStrategyTypeMap[cardId] = strategyType;
        }
    },
    getSortState(cardId) {
        return (window.tableSortState && window.tableSortState[cardId]) || null;
    },
    setSortState(cardId, state) {
        if (!window.tableSortState) window.tableSortState = {};
        window.tableSortState[cardId] = state;
    },
    toggleSort(cardId, column) {
        const current = this.getSortState(cardId);
        if (!current || current.column !== column) {
            this.setSortState(cardId, { column, direction: 'asc' });
        } else if (current.direction === 'asc') {
            current.direction = 'desc';
        } else {
            this.setSortState(cardId, { column: null, direction: null });
        }
        return this.getSortState(cardId);
    }
};

// ── Card Action Buttons Component ──

/**
 * Standardized card action buttons (Load, Execute, Delete) across Strategy and Screener cards.
 */
function renderCardActionButtons({
    hasLoad = false,
    loadFn = '',
    hasExecute = false,
    executeFn = '',
    hasDelete = false,
    deleteFn = '',
    dataAttrs = {}
}) {
    let isFirst = true;
    const getMargin = () => {
        if (isFirst) {
            isFirst = false;
            return 'margin-left: auto;';
        }
        return 'margin-left: 4px;';
    };

    let attrStr = '';
    for (const [key, val] of Object.entries(dataAttrs)) {
        if (val !== undefined && val !== null) {
            attrStr += ` ${key}="${escapeAttr(typeof val === 'string' ? val : JSON.stringify(val))}"`;
        }
    }

    const loadBtn = hasLoad
        ? `<button type="button" class="btn btn-primary btn-sm" style="${getMargin()}" onclick="event.stopPropagation(); ${loadFn}"${attrStr}>⬆ Load</button>`
        : '';

    const executeBtn = hasExecute
        ? `<button type="button" class="btn btn-primary btn-sm" style="${getMargin()}" onclick="event.stopPropagation(); ${executeFn}"${attrStr}>▶ Execute</button>`
        : '';

    const deleteBtn = hasDelete
        ? `<button type="button" class="btn btn-danger btn-sm" style="${getMargin()}" onclick="event.stopPropagation(); ${deleteFn}">🗑 Delete</button>`
        : '';

    return {
        loadBtn,
        executeBtn,
        deleteBtn,
        html: `${loadBtn}${executeBtn}${deleteBtn}`
    };
}

// ── Filter Details Shared UI ──

function renderFilterDetailsSection(filterConfig, filterId) {
    if (!filterConfig) return '';
    try {
        const cfg = typeof filterConfig === 'string' ? JSON.parse(filterConfig) : filterConfig;
        const filterGrid = renderFilterGrid(cfg);
        if (filterGrid && !filterGrid.includes('No filters configured')) {
            return `
                <div class="filter-details-section">
                    <div class="filter-details-toggle" data-target="${filterId}">
                        <span class="card-arrow" id="arrow-${filterId}">▶</span>
                        <span>Filter Details</span>
                    </div>
                    <div class="filter-details-body" id="${filterId}">
                        ${filterGrid}
                    </div>
                </div>`;
        }
    } catch (e) { /* ignore parse errors */ }
    return '';
}

function attachFilterDetailsToggle(card) {
    const filterToggle = card.querySelector('.filter-details-toggle');
    if (filterToggle) {
        filterToggle.addEventListener('click', (e) => {
            e.stopPropagation();
            const targetId = filterToggle.dataset.target;
            const safeTargetId = (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape(targetId) : targetId;
            const safeArrowId = (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape('arrow-' + targetId) : ('arrow-' + targetId);
            const body = card.querySelector('#' + safeTargetId);
            const arrowEl = card.querySelector('#' + safeArrowId);
            if (body) body.classList.toggle('open');
            if (arrowEl) arrowEl.classList.toggle('open');
        });
    }
}

function getScreenerConfigFallback(result) {
    if (!result || typeof window === 'undefined') return null;
    const name = result.screenerName || result.screenerId || '';
    const screeners = (window.appConfig && window.appConfig.technicalScreeners) || window.screenerConfigs || [];
    const matched = screeners.find(s => (s.alias && s.alias === name) || s.screenerType === name || s.name === name);
    if (!matched) return null;
    const cfg = {};
    if (matched.screenerType) cfg.screenerType = matched.screenerType;
    if (matched.alias) cfg.alias = matched.alias;
    if (matched.securitiesFile) cfg.securitiesFile = matched.securitiesFile;
    if (matched.securities) cfg.securities = matched.securities;
    if (matched.technicalFilters) cfg.technicalFilters = matched.technicalFilters;
    if (matched.fundamentalFilters) cfg.fundamentalFilters = matched.fundamentalFilters;
    return cfg;
}

// ── Card Builder ──

function buildResultCard(result, badgeText = 'Standard') {
    const card = document.createElement('div');
    const hasTrades = result.trades && result.trades.length > 0;
    card.className = hasTrades ? 'card' : 'card disabled';

    const cardId = String(result.strategyId || result.screenerId || 'card-' + Math.random()).replace(/\s+/g, '-');
    const actualStrategyId = result.strategyId || result.strategyName || '';
    CardStore.setStrategyId(cardId, actualStrategyId);

    let strategyType = result.strategyType;
    if (!strategyType && result.filterConfig) {
        try {
            const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig;
            if (cfg && cfg.strategyType) strategyType = cfg.strategyType;
        } catch (e) {}
    }
    if (!strategyType && result.trades && result.trades.length > 0 && result.trades[0].strategyType) {
        strategyType = result.trades[0].strategyType;
    }
    CardStore.setStrategyType(cardId, strategyType);

    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;
    const filterId = `filters-${cardId}`;

    const isExecutePage = !!document.getElementById('strategy-type');

    const hasLoadBtn = isExecutePage && !!result.filterConfig;
    const hasExecuteBtn = isExecutePage && badgeText === 'Custom' && !!result.filterConfig && !!result.strategyId && !isNaN(result.strategyId);
    const hasDeleteBtn = isExecutePage && badgeText === 'Custom' && !!result.strategyId && !isNaN(result.strategyId);

    const actionButtons = renderCardActionButtons({
        hasLoad: hasLoadBtn,
        loadFn: 'loadFiltersFromResult(this)',
        hasExecute: hasExecuteBtn,
        executeFn: `reexecuteCustomStrategy(this, '${escapeAttr(result.strategyId)}')`,
        hasDelete: hasDeleteBtn,
        deleteFn: `confirmDeleteCustomResult('${escapeAttr(result.strategyId)}', this.closest('.card'))`,
        dataAttrs: {
            'data-result-id': result.strategyId,
            'data-filter-config': typeof result.filterConfig === 'string' ? result.filterConfig : JSON.stringify(result.filterConfig),
            'data-strategy-name': result.strategyName || ''
        }
    });
    const loadFiltersBtn = actionButtons.loadBtn;
    const executeBtn = actionButtons.executeBtn;
    const deleteBtn = actionButtons.deleteBtn;

    const filterDetailsHtml = renderFilterDetailsSection(result.filterConfig, filterId);

    let displayName = result.strategyName || 'Unknown';
    if (result.filterConfig) {
        try {
            const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig;
            if (cfg && cfg.termType) displayName += ` - ${cfg.termType}`;
        } catch (e) { /* ignore parse errors */ }
    }

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                ${arrow}
                <span class="card-name">${displayName}</span>
                ${renderDocInfoBtn(result.descriptionFile, displayName)}
                <span class="card-badge">${badgeText}</span>
                ${(() => { try { const cfg = typeof result.filterConfig === 'string' ? JSON.parse(result.filterConfig) : result.filterConfig; return renderGreeksPills(cfg && cfg.greeks); } catch(e) { return ''; } })()}
                ${loadFiltersBtn}
                ${executeBtn}
                ${deleteBtn}
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Trades: ${result.tradesFound || 0}${(() => { const d = formatDuration(result.executionTimeMs); return d ? ` · ⏱ ${d}` : ''; })()}</span>
        </div>

        ${filterDetailsHtml}
        <div class="card-content" id="content-${cardId}">
            ${buildTradeTable({ trades: result.trades || [], cardId, strategyId: actualStrategyId, isHistoryModal: false, strategyType })}
        </div>`;

    CardStore.setTrades(cardId, result.trades || []);

    if (hasTrades) {
        card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));
    } else {
        card.querySelector('.card-header').style.cursor = 'default';
        if(card.querySelector('.info-btn')) {
             card.querySelector('.info-btn').style.opacity = '0.5';
             card.querySelector('.info-btn').style.cursor = 'default';
             card.querySelector('.info-btn').onclick = (e) => { e.stopPropagation(); e.preventDefault(); };
        }
    }

    attachFilterDetailsToggle(card);

    return card;
}

function updateToggleAllTermsButtonState(container) {
    const btn = document.getElementById('toggle-all-terms-btn');
    const bar = document.getElementById('term-actions-bar');
    if (!btn) return;

    const targetContainer = container || document.getElementById('results-container') || document;
    const termBodies = targetContainer.querySelectorAll('.term-group-body');

    if (termBodies.length === 0) {
        btn.style.display = 'none';
        if (bar) bar.style.display = 'none';
        return;
    }

    btn.style.display = 'inline-flex';
    if (bar) bar.style.display = 'flex';

    const allExpanded = Array.from(termBodies).every(body => !body.classList.contains('hidden'));
    const arrowEl = document.getElementById('arrow-toggle-all-terms');
    const textEl = document.getElementById('text-toggle-all-terms');

    if (allExpanded) {
        if (arrowEl) arrowEl.classList.add('open');
        if (textEl) textEl.textContent = 'Collapse All';
    } else {
        if (arrowEl) arrowEl.classList.remove('open');
        if (textEl) textEl.textContent = 'Expand All';
    }
}

function toggleAllTermGroups(forceExpand, container) {
    const targetContainer = container || document.getElementById('results-container') || document;
    const termBodies = targetContainer.querySelectorAll('.term-group-body');
    if (termBodies.length === 0) return;

    let shouldExpand;
    if (typeof forceExpand === 'boolean') {
        shouldExpand = forceExpand;
    } else {
        const allExpanded = Array.from(termBodies).every(body => !body.classList.contains('hidden'));
        shouldExpand = !allExpanded;
    }

    termBodies.forEach(body => {
        body.classList.toggle('hidden', !shouldExpand);
        const arrowEl = document.getElementById(`arrow-${body.id}`) || (body.parentElement ? body.parentElement.querySelector('.card-arrow') : null);
        if (arrowEl) {
            arrowEl.classList.toggle('open', shouldExpand);
        }
    });

    updateToggleAllTermsButtonState(targetContainer);
}

function computeTermDteRange(termResults) {
    if (!termResults || !termResults.length) return '';
    let globalMin = Infinity;
    let globalMax = -Infinity;
    let hasUnboundedMax = false;
    let hasDte = false;

    for (const r of termResults) {
        if (!r || !r.filterConfig) continue;
        try {
            const cfg = typeof r.filterConfig === 'string' ? JSON.parse(r.filterConfig) : r.filterConfig;
            if (!cfg) continue;

            const minDTE = (cfg.minDTE !== undefined && cfg.minDTE !== null && cfg.minDTE !== '') ? Number(cfg.minDTE) : null;
            const maxDTE = (cfg.maxDTE !== undefined && cfg.maxDTE !== null && cfg.maxDTE !== '') ? Number(cfg.maxDTE) : null;
            const targetDTE = (cfg.targetDTE !== undefined && cfg.targetDTE !== null && cfg.targetDTE !== '') ? Number(cfg.targetDTE) : null;

            if (minDTE !== null && !isNaN(minDTE)) {
                globalMin = Math.min(globalMin, minDTE);
                hasDte = true;
                if (maxDTE !== null && !isNaN(maxDTE)) {
                    globalMax = Math.max(globalMax, maxDTE);
                } else {
                    hasUnboundedMax = true;
                }
            } else if (targetDTE !== null && !isNaN(targetDTE) && targetDTE > 0) {
                globalMin = Math.min(globalMin, targetDTE);
                globalMax = Math.max(globalMax, targetDTE);
                hasDte = true;
            } else if (maxDTE !== null && !isNaN(maxDTE)) {
                globalMin = Math.min(globalMin, 0);
                globalMax = Math.max(globalMax, maxDTE);
                hasDte = true;
            }
        } catch (_) { /* ignore */ }
    }

    if (!hasDte) return '';

    if (hasUnboundedMax && globalMax === -Infinity) {
        return `${globalMin}+`;
    }
    if (globalMin !== Infinity && globalMax !== -Infinity) {
        if (globalMin === globalMax) return `${globalMin}`;
        return hasUnboundedMax ? `${globalMin} - ${globalMax}+` : `${globalMin} - ${globalMax}`;
    }
    if (hasUnboundedMax && globalMin !== Infinity) {
        return `${globalMin}+`;
    }
    return '';
}

function renderTermGroups(container, results, badgeText = 'Standard') {
    const groups = new Map();

    for (const r of results) {
        let term = 'Other';
        if (r.filterConfig) {
            try {
                const cfg = typeof r.filterConfig === 'string' ? JSON.parse(r.filterConfig) : r.filterConfig;
                if (cfg && cfg.termType) term = cfg.termType;
            } catch (_) { /* ignore */ }
        }
        if (!groups.has(term)) groups.set(term, []);
        groups.get(term).push(r);
    }

    function termWeight(label) {
        if (label === 'Other') return -1;
        const l = label.toLowerCase();
        if (l.includes('extra long')) return 70;
        if (l.includes('long'))       return 60;
        if (l.includes('medium term 2') || l.includes('medium 2')) return 45;
        if (l.includes('up to'))      return 35;
        if (l.includes('medium'))     return 40;
        if (l.includes('short'))      return 30;
        if (l.includes('daily'))      return 20;
        return 10;
    }

    const orderedKeys = [...groups.keys()].sort((a, b) => {
        const wa = termWeight(a), wb = termWeight(b);
        if (wb !== wa) return wb - wa;
        return b.localeCompare(a);
    });

    for (const term of orderedKeys) {
        const termResults = groups.get(term);
        const groupId = 'term-group-' + term.replace(/\s+/g, '-').toLowerCase();
        const dteRange = computeTermDteRange(termResults);
        const dteHtml = dteRange ? `<span class="term-group-dte">| ${dteRange}</span>` : '';

        const groupEl = document.createElement('div');
        groupEl.className = 'term-group';

        const header = document.createElement('div');
        header.className = 'term-group-header';
        header.innerHTML = `
            <span class="card-arrow" id="arrow-${groupId}">▶</span>
            <span class="term-group-label">${term}</span>
            ${dteHtml}
            <span class="term-group-count">(${termResults.length})</span>`;

        const body = document.createElement('div');
        body.className = 'term-group-body hidden';
        body.id = groupId;

        for (const r of termResults) {
            body.appendChild(buildResultCard(r, badgeText));
        }

        header.addEventListener('click', () => {
            body.classList.toggle('hidden');
            const arrowEl = header.querySelector('.card-arrow') || document.getElementById(`arrow-${groupId}`);
            if (arrowEl) arrowEl.classList.toggle('open');
            updateToggleAllTermsButtonState(container);
        });

        groupEl.appendChild(header);
        groupEl.appendChild(body);
        container.appendChild(groupEl);
    }

    updateToggleAllTermsButtonState(container);
}

// ── Common Card Deletion & Confirmation Modal ──

/**
 * Displays a unified confirmation modal before permanently deleting an execution record.
 */
function showDeleteConfirmationModal({
    title = 'Delete Execution Result?',
    message = 'This action cannot be undone. The record will be permanently removed from the database.',
    onConfirm
}) {
    if (document.querySelector('.delete-confirm-overlay')) return;

    const overlay = createModalOverlay({
        overlayClass: 'delete-confirm-overlay',
        modalStyle: 'max-width: 420px; text-align: center;',
        contentHtml: `
            <div style="font-size: 2.5rem; margin-bottom: 12px;">🗑</div>
            <h2 style="margin: 0 0 8px;">${title}</h2>
            <p style="color: var(--text-secondary); margin: 0 0 24px; font-size: 0.9rem;">
                ${message}
            </p>
            <div class="flex gap-sm" style="justify-content: center;">
                <button class="btn btn-secondary" id="cancel-delete-btn" onclick="this.closest('.modal-overlay').remove()">Cancel</button>
                <button class="btn btn-danger" id="confirm-delete-btn">🗑 Delete</button>
            </div>`
    });

    overlay.querySelector('#confirm-delete-btn').addEventListener('click', async () => {
        overlay.remove();
        if (onConfirm) await onConfirm();
    });
}

/**
 * Performs animated card deletion from DOM following successful API deletion.
 */
async function deleteCardWithAnimation(card, deletePromiseFn, successMessage) {
    try {
        await deletePromiseFn();
        if (card) {
            card.style.transition = 'opacity 0.3s, transform 0.3s';
            card.style.opacity = '0';
            card.style.transform = 'translateX(20px)';
            setTimeout(() => card.remove(), 320);
        }
        showToast(successMessage);
    } catch (e) {
        showToast(`Failed to delete: ${e.message}`, 'error');
    }
}

function confirmDeleteCustomResult(resultId, card) {
    showDeleteConfirmationModal({
        title: 'Delete Execution Result?',
        message: 'This action cannot be undone. The record will be permanently removed from the database.',
        onConfirm: () => deleteCustomResult(resultId, card)
    });
}

async function deleteCustomCardAndRefreshCount({ card, apiDeleteFn, toastMessage, containerId, btnId, countBadgeId }) {
    await deleteCardWithAnimation(card, apiDeleteFn, toastMessage);
    const container = document.getElementById(containerId);
    if (container) {
        const remaining = container.querySelectorAll('.card').length - (card ? 1 : 0);
        updateExecuteAllButtonState({
            btnId,
            countBadgeId,
            count: Math.max(0, remaining)
        });
    }
}

async function deleteCustomResult(resultId, card) {
    await deleteCustomCardAndRefreshCount({
        card,
        apiDeleteFn: () => API.delete(`/api/results/custom/${resultId}`),
        toastMessage: 'Execution result deleted.',
        containerId: 'custom-results',
        btnId: 'execute-all-custom-btn',
        countBadgeId: 'custom-results-count-badge'
    });
}

function confirmDeleteCustomScreenerResult(resultId, card) {
    showDeleteConfirmationModal({
        title: 'Delete Screener Result',
        message: 'Are you sure you want to permanently delete this custom screener execution? This action cannot be undone.',
        onConfirm: () => deleteCustomScreenerResult(resultId, card)
    });
}

async function deleteCustomScreenerResult(resultId, card) {
    await deleteCustomCardAndRefreshCount({
        card,
        apiDeleteFn: () => API.delete(`/api/results/custom/screeners/${resultId}`),
        toastMessage: 'Screener execution result deleted.',
        containerId: 'screener-custom-results',
        btnId: 'execute-all-screener-btn',
        countBadgeId: 'screener-custom-results-count-badge'
    });
}

/** Shared helper to load and render custom execution / screener cards while preserving open card & filter state */
async function loadCustomCardsList({
    containerId,
    endpoint,
    executeAllBtnId,
    countBadgeId,
    emptyMessage,
    errorPrefix = 'Failed to load',
    updatedResultId = null,
    buildCardFn,
    getIdFn,
    hasItemsFn,
    onLoadedFn = null,
    onErrorFn = null
}) {
    const container = document.getElementById(containerId);
    if (!container) return;
    try {
        const openCardIds = new Set();
        container.querySelectorAll('.card-content.open').forEach(el => {
            openCardIds.add(el.id.replace(/^content-/, ''));
        });
        const openFilterIds = new Set();
        container.querySelectorAll('.filter-details-body.open').forEach(el => {
            openFilterIds.add(el.id);
        });

        const results = await API.get(endpoint);
        container.innerHTML = '';
        if (typeof updateExecuteAllButtonState === 'function') {
            updateExecuteAllButtonState({
                btnId: executeAllBtnId,
                countBadgeId,
                count: results ? results.length : 0
            });
        }
        if (!results || results.length === 0) {
            container.innerHTML = `<div class="empty-state"><div class="empty-state-icon">🔬</div>${emptyMessage}</div>`;
            return;
        }
        for (const r of results) {
            const cardEl = buildCardFn(r);
            container.appendChild(cardEl);
            const rawId = getIdFn(r);
            const cardId = String(rawId || '').replace(/\s+/g, '-');
            const shouldOpen = openCardIds.has(cardId) || (updatedResultId && String(rawId) === String(updatedResultId));
            if (shouldOpen && hasItemsFn(r)) {
                const content = cardEl.querySelector(`[id="content-${cardId}"]`);
                const arrow = cardEl.querySelector(`[id="arrow-${cardId}"]`);
                if (content) content.classList.add('open');
                if (arrow) arrow.classList.add('open');
            }
            if (openFilterIds.has(`filters-${cardId}`)) {
                const filterBody = cardEl.querySelector(`[id="filters-${cardId}"]`);
                const filterArrow = cardEl.querySelector(`[id="arrow-filters-${cardId}"]`);
                if (filterBody) filterBody.classList.add('open');
                if (filterArrow) filterArrow.classList.add('open');
            }
        }
        if (onLoadedFn) onLoadedFn(container);
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">${errorPrefix}: ${e.message}</div>`;
        if (typeof updateExecuteAllButtonState === 'function') {
            updateExecuteAllButtonState({
                btnId: executeAllBtnId,
                countBadgeId,
                count: 0
            });
        }
        if (onErrorFn) onErrorFn();
    }
}

/** Shared helper for single-card in-place re-execution on execute and execute-screener pages */
async function reexecuteSingleCustomCard({ btn, resultId, isProgressActiveFn, loadFiltersFn, executeFn, logLabel }) {
    if (!resultId) return;
    if (isProgressActiveFn && isProgressActiveFn()) {
        showToast('An execution is already in progress', 'error');
        return;
    }

    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '⏳ Executing...';

    try {
        loadFiltersFn(btn, true);
        const success = await executeFn(resultId);
        if (success === false) {
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    } catch (e) {
        console.error(`Error re-executing ${logLabel}:`, e);
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

/** Shared helper to render collapsible template cards on execute and execute-screener pages */
async function renderTemplateAccordionList({ containerId, configKey, filterFn, buildHeaderContentFn, buildBodyContentFn }) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const list = (window.appConfig && window.appConfig[configKey]) || [];
    const matching = list.filter(filterFn);

    if (matching.length === 0) {
        container.innerHTML = '';
        return;
    }

    const heading = document.createElement('h4');
    heading.style.fontSize = '0.8rem';
    heading.style.color = 'var(--text-secondary)';
    heading.style.marginBottom = '8px';
    heading.textContent = 'Configured Templates (Click to view, Load to edit)';

    container.innerHTML = '';
    container.appendChild(heading);

    matching.forEach(item => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = item.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    ${buildHeaderContentFn(item, enabledPill)}
                </div>
            </div>
            <div class="config-card-body">
                ${buildBodyContentFn(item)}
            </div>`;

        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
    });
}

// ── Common "Execute All" Orchestrator ──

function updateExecuteAllButtonState({ btnId, countBadgeId, count = 0 }) {
    const btn = document.getElementById(btnId);
    const badge = document.getElementById(countBadgeId);
    if (btn) {
        btn.style.display = count > 0 ? 'inline-flex' : 'none';
    }
    if (badge) {
        if (count > 0) {
            badge.textContent = count;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    }
}

async function executeAllCustomCards({
    containerId,
    executeAllBtnId,
    cardBtnSelector,
    batchApiEndpoint,
    buildRequestFn,
    reloadResultsFn,
    entityNameSingular = 'item',
    entityNamePlural = 'items',
    isProgressActiveFn
}) {
    if (isProgressActiveFn && isProgressActiveFn()) {
        showToast('An execution is already in progress', 'info');
        return;
    }

    const container = document.getElementById(containerId);
    if (!container) return;

    const cards = Array.from(container.querySelectorAll('.card')).filter(c => c.querySelector(cardBtnSelector));
    if (cards.length === 0) {
        showToast(`No ${entityNamePlural} to execute`, 'info');
        return;
    }

    const executeAllBtn = document.getElementById(executeAllBtnId);
    const originalAllText = executeAllBtn ? executeAllBtn.innerHTML : '▶ Execute All';
    if (executeAllBtn) {
        executeAllBtn.disabled = true;
        executeAllBtn.innerHTML = '⏳ Executing...';
    }

    const allExecBtns = Array.from(container.querySelectorAll(cardBtnSelector));
    allExecBtns.forEach(b => b.disabled = true);

    try {
        const requests = [];
        for (const card of cards) {
            const execBtn = card.querySelector(cardBtnSelector);
            if (!execBtn) continue;
            const req = typeof buildRequestFn === 'function' ? buildRequestFn(card, execBtn) : null;
            if (req) requests.push(req);
        }

        if (requests.length === 0) {
            showToast(`No valid ${entityNamePlural} found to execute`, 'error');
            if (executeAllBtn) {
                executeAllBtn.disabled = false;
                executeAllBtn.innerHTML = originalAllText;
            }
            allExecBtns.forEach(b => b.disabled = false);
            return;
        }

        const res = await API.post(batchApiEndpoint, requests);
        showToast(res.message);
        startTimer(Date.now());

        if (typeof pollUntilComplete === 'function') {
            await pollUntilComplete();
            stopTimer();
            if (executeAllBtn) {
                executeAllBtn.disabled = false;
                executeAllBtn.innerHTML = originalAllText;
            }
            allExecBtns.forEach(b => {
                b.disabled = false;
                b.innerHTML = '▶ Execute';
            });
            if (reloadResultsFn) {
                await reloadResultsFn();
            }
            showToast(`Batch execution of ${entityNamePlural} completed!`);
        } else {
            startPolling(() => {
                stopTimer();
                if (executeAllBtn) {
                    executeAllBtn.disabled = false;
                    executeAllBtn.innerHTML = originalAllText;
                }
                allExecBtns.forEach(b => {
                    b.disabled = false;
                    b.innerHTML = '▶ Execute';
                });
                if (reloadResultsFn) {
                    reloadResultsFn();
                }
                showToast(`Batch execution of ${entityNamePlural} completed!`);
            });
        }
    } catch (e) {
        console.error(`Error in batch execute (${entityNamePlural}):`, e);
        showToast(`Failed to start batch execution: ${e.message}`, 'error');
        if (executeAllBtn) {
            executeAllBtn.disabled = false;
            executeAllBtn.innerHTML = originalAllText;
        }
        allExecBtns.forEach(b => {
            b.disabled = false;
            b.innerHTML = '▶ Execute';
        });
    }
}

// ── Screener Card & Table Builders ──

function buildScreenerCard(result, isCustom = false) {
    const card = document.createElement('div');
    const hasResults = result.results && result.results.length > 0;
    card.className = hasResults ? 'card' : 'card disabled';

    const cardId = (result.screenerId || 'screener-' + Math.random()).replace(/\s+/g, '-');
    const arrow = `<span class="card-arrow" id="arrow-${cardId}">▶</span>`;

    const isDropScreener = hasResults && result.results[0].dropType && result.results[0].dropType.length > 0;
    CardStore.setTrades(cardId, result.results || [], isDropScreener ? 'drop' : 'screener');
    
    const isExecuteScreenerPage = !!document.getElementById('screener-type');
    const filterConfig = result.filterConfig || result.requestParams || getScreenerConfigFallback(result);
    const params = filterConfig;
    const hasConfig = !!params;
    const hasId = !!(result.screenerId && !isNaN(result.screenerId));

    const hasLoadBtn = isCustom && isExecuteScreenerPage && hasConfig;
    const hasExecuteBtn = isCustom && isExecuteScreenerPage && hasConfig && hasId;
    const hasDeleteBtn = isCustom && (hasId || !!result.screenerId);

    const paramsStr = typeof params === 'string' ? params : JSON.stringify(params);
    const screenerNameStr = result.screenerName || '';

    const actionButtons = renderCardActionButtons({
        hasLoad: hasLoadBtn,
        loadFn: 'loadScreenerFiltersFromResult(this)',
        hasExecute: hasExecuteBtn,
        executeFn: `reexecuteCustomScreener(this, '${escapeAttr(result.screenerId || '')}')`,
        hasDelete: hasDeleteBtn,
        deleteFn: `confirmDeleteCustomScreenerResult('${escapeAttr(result.screenerId || '')}', this.closest('.card'))`,
        dataAttrs: {
            'data-result-id': result.screenerId || '',
            'data-request-params': paramsStr,
            'data-filter-config': paramsStr,
            'data-screener-name': screenerNameStr,
            'data-strategy-name': screenerNameStr
        }
    });
    const loadFiltersBtn = actionButtons.loadBtn;
    const executeBtn = actionButtons.executeBtn;
    const deleteBtn = actionButtons.deleteBtn;

    const filterId = `filters-${cardId}`;
    const filterDetailsHtml = renderFilterDetailsSection(filterConfig, filterId);

    card.innerHTML = `
        <div class="card-header" data-target="${cardId}">
            <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                ${arrow}
                <span class="card-name">${result.screenerName || 'Screener'}</span>
                <span class="card-badge" style="background-color: var(--primary); color: #fff;">Screener</span>
                ${loadFiltersBtn}
                ${executeBtn}
                ${deleteBtn}
            </div>
            <span class="card-stats">Last run: ${timeAgo(result.updatedAt)} · Found: ${result.resultsFound || 0}${(() => { const d = formatDuration(result.executionTimeMs); return d ? ` · ⏱ ${d}` : ''; })()}</span>
        </div>
        ${filterDetailsHtml}
        <div class="card-content" id="content-${cardId}">
            ${buildScreenerTable(result.results || [], cardId)}
        </div>`;

    if (hasResults) {
        card.querySelector('.card-header').addEventListener('click', () => toggleCard(cardId));
    } else {
        card.querySelector('.card-header').style.cursor = 'default';
    }

    attachFilterDetailsToggle(card);

    return card;
}

/** Shared builder for sortable table column headers */
function createSortableHeaderFn(cardId, state) {
    return (key, label) => {
        if (!cardId) return `<th>${label}</th>`;
        const active = state && state.column === key;
        const arrow = active ? (state.direction === 'asc' ? ' ↑' : ' ↓') : '';
        const cls = active ? 'sort-header active' : 'sort-header';
        return `<th class="${cls}" onclick="handleTableSort('${cardId}', '${key}')" title="Sort by ${label}">${label}${arrow}</th>`;
    };
}

function buildScreenerTable(results, cardId = null) {
    if (!results || results.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">🔎</div>No stocks found</div>';
    }

    const isDropScreener = results[0].dropType && results[0].dropType.length > 0;

    if (isDropScreener) {
        return buildDropScreenerTable(results, cardId);
    }

    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;
    const th = createSortableHeaderFn(cardId, state);

    let hasRsi = false;
    let hasBb = false;
    let hasVolume = false;
    let hasMarketCap = false;
    let maPeriodsSet = new Set();
    
    for (const r of results) {
        if (r.rsi && r.rsi !== 0) hasRsi = true;
        if (r.bollingerLower && r.bollingerLower !== 0) hasBb = true;
        if (r.volume && r.volume !== 0) hasVolume = true;
        if (r.marketCapB != null) hasMarketCap = true;
        if (r.maValues) {
            Object.keys(r.maValues).forEach(k => maPeriodsSet.add(Number(k)));
        }
    }
    const maPeriods = Array.from(maPeriodsSet).sort((a,b)=>b-a);

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            <th>Company</th>
            ${th('price', 'Price')}
            ${hasMarketCap ? th('marketCapB', 'Mkt Cap') : ''}
            ${hasVolume ? th('volume', 'Volume') : ''}
            ${hasRsi ? th('rsi', 'RSI') : ''}
            ${hasBb ? '<th>BB (L-U)</th>' : ''}
            ${maPeriods.map(p => th(`ma_${p}`, `SMA ${p}`)).join('\n            ')}
        </tr></thead><tbody>`;

    for (const r of results) {
        const typeClass = r.signalType === 'BULLISH' ? 'text-success' : (r.signalType === 'BEARISH' ? 'text-danger' : 'text-muted');
        
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const volume = formatLargeNumber(r.volume);
        const rsi = (typeof r.rsi === 'number') ? rsiValue(r.rsi) : '-';
        const bb = (typeof r.bollingerLower === 'number' && typeof r.bollingerUpper === 'number') 
            ? `${r.bollingerLower.toFixed(1)} - ${r.bollingerUpper.toFixed(1)}` : '-';
        
        const detailLines = [];
        detailLines.push(`💰 Price: $${(typeof r.currentPrice === 'number') ? r.currentPrice.toFixed(2) : '-'}`);
        detailLines.push(`📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`);

        if (typeof r.rsi === 'number' && r.rsi !== 0) {
            let rsiLine = `📈 RSI: ${r.rsi.toFixed(2)}`;
            if (typeof r.previousRsi === 'number' && r.previousRsi !== 0) rsiLine += ` (prev: ${r.previousRsi.toFixed(2)})`;
            if (r.rsiBullishCrossover) rsiLine += ' ⬆️ CROSSOVER';
            else if (r.rsiBearishCrossover) rsiLine += ' ⬇️ CROSSOVER';
            else if (r.rsiOversold) rsiLine += ' 🔴 OVERSOLD';
            else if (r.rsiOverbought) rsiLine += ' 🟢 OVERBOUGHT';
            detailLines.push(rsiLine);
        }

        if (typeof r.bollingerLower === 'number' && r.bollingerLower !== 0) {
            let bbLine = '📉 BB: ';
            if (r.priceTouchingLowerBand) bbLine += `Touching Lower ($${r.bollingerLower.toFixed(2)})`;
            else if (r.priceTouchingUpperBand) bbLine += `Touching Upper ($${r.bollingerUpper.toFixed(2)})`;
            else bbLine += `Within bands ($${r.bollingerLower.toFixed(2)} - $${r.bollingerUpper.toFixed(2)})`;
            detailLines.push(bbLine);
        }

        const belowMAs = [];
        const aboveMAs = [];
        if (r.maValues) {
            maPeriods.forEach(p => {
                const val = r.maValues[p];
                if (typeof val === 'number' && val !== 0) {
                    if (r.currentPrice < val) belowMAs.push(`SMA${p}`);
                    else aboveMAs.push(`SMA${p}`);
                }
            });
        }
        if (belowMAs.length > 0 || aboveMAs.length > 0) {
            let maLine = '📊 MAs: ';
            if (belowMAs.length > 0) maLine += `Below ${belowMAs.join(', ')}`;
            if (belowMAs.length > 0 && aboveMAs.length > 0) maLine += ' | ';
            if (aboveMAs.length > 0) maLine += `Above ${aboveMAs.join(', ')}`;
            detailLines.push(maLine);
        }

        const detailStr = escapeAttr(detailLines.join('\n'));
        const techIndicatorsAttr = escapeAttr(r.allTechnicalIndicatorsSummary || r.formattedSummary || '');

        html += `<tr class="trade-row" data-details="${detailStr}" data-tech-indicators="${techIndicatorsAttr}">
            <td><strong class="${typeClass}">${r.symbol || ''}</strong></td>
            <td><span class="text-muted" title="${r.companyName || ''}">${formatCompanyName(r.companyName)}</span></td>
            <td class="text-mono">${price}</td>
            ${hasMarketCap ? `<td class="text-mono">${r.marketCapB != null ? formatMarketCap(r.marketCapB) : '-'}</td>` : ''}
            ${hasVolume ? `<td>${volume}</td>` : ''}
            ${hasRsi ? `<td>${rsi}</td>` : ''}
            ${hasBb ? `<td class="text-muted small">${bb}</td>` : ''}
            ${maPeriods.map(p => {
                const val = r.maValues && r.maValues[p];
                return `<td>${(typeof val === 'number' && val !== 0) ? val.toFixed(2) : '-'}</td>`;
            }).join('\n            ')}
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

function buildDropScreenerTable(results, cardId = null) {
    const state = cardId ? (window.tableSortState[cardId] || { column: null, direction: 'asc' }) : null;
    const th = createSortableHeaderFn(cardId, state);

    const hasMarketCapDrop = results.some(r => r.marketCapB != null);

    let html = `<table class="data-table">
        <thead><tr>
            ${th('ticker', 'Ticker')}
            <th>Company</th>
            ${th('price', 'Current Price')}
            ${hasMarketCapDrop ? th('marketCapB', 'Mkt Cap') : ''}
            ${th('refPrice', 'Ref Price')}
            ${th('dropPct', 'Drop %')}
            ${th('volume', 'Volume')}
            <th>Type</th>
        </tr></thead><tbody>`;

    let sorted = results;
    if (!state || !state.column) {
        sorted = [...results].sort((a, b) => (b.dropPercent || 0) - (a.dropPercent || 0));
    }

    for (const r of sorted) {
        const price = (typeof r.currentPrice === 'number') ? `$${r.currentPrice.toFixed(2)}` : '-';
        const refPrice = (typeof r.referencePrice === 'number') ? `$${r.referencePrice.toFixed(2)}` : '-';
        const dropPct = (typeof r.dropPercent === 'number') ? r.dropPercent.toFixed(2) : '0';
        const volume = formatLargeNumber(r.volume);
        const dropType = r.dropType || '-';

        const dropVal = r.dropPercent || 0;
        const dropColor = dropVal >= 20 ? '#ff4444' : dropVal >= 10 ? '#ff6b6b' : dropVal >= 5 ? '#ffa07a' : '#ffcc80';

        const detailLines = [
            `📉 Drop: ${dropPct}% (${dropType})`,
            `💰 Current: ${price}`,
            `📌 Reference: ${refPrice}`,
            `📊 Volume: ${(typeof r.volume === 'number') ? r.volume.toLocaleString() : '-'}`,
            ...(r.marketCapB != null ? [`🏢 Mkt Cap: ${formatMarketCap(r.marketCapB)}`] : [])
        ];
        const detailStr = escapeAttr(detailLines.join('\n'));
        const techIndicatorsAttr = escapeAttr(r.allTechnicalIndicatorsSummary || r.formattedSummary || '');

        html += `<tr class="trade-row" data-details="${detailStr}" data-tech-indicators="${techIndicatorsAttr}">
            <td><strong class="text-danger">${r.symbol || ''}</strong></td>
            <td><span class="text-muted" title="${r.companyName || ''}">${formatCompanyName(r.companyName)}</span></td>
            <td class="text-mono">${price}</td>
            ${hasMarketCapDrop ? `<td class="text-mono">${r.marketCapB != null ? formatMarketCap(r.marketCapB) : '-'}</td>` : ''}
            <td class="text-muted text-mono">${refPrice}</td>
            <td><span style="color: ${dropColor}; font-weight: 700;">-${dropPct}%</span></td>
            <td>${volume}</td>
            <td class="text-muted small">${dropType}</td>
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

// ── Configurable Strategy Columns ──

function getTradeCostSavings(t) {
    if (typeof t.costSavingsPercent === 'number') {
        return t.costSavingsPercent;
    }
    if (t.tradeDetails) {
        const match = t.tradeDetails.match(/([+-]?\d+(?:\.\d+)?)%\s+cheaper/i);
        if (match) {
            return parseFloat(match[1]);
        }
    }
    return null;
}

/** Computes or parses a trade's annualized Return-on-Risk (CAGR) percentage */
function computeTradeRorCagr(t) {
    let rorCagr = t.returnOnRiskCAGR;
    if (typeof rorCagr === 'string') rorCagr = parseFloat(rorCagr);
    if (rorCagr == null && t.returnOnRisk != null && t.dte > 0 && t.maxLoss > 0) {
        const rawRoR = t.returnOnRisk / 100.0;
        rorCagr = (Math.pow(1.0 + rawRoR, 365.0 / t.dte) - 1.0) * 100.0;
    }
    return rorCagr;
}

const AVAILABLE_TRADE_COLUMNS = {
    ticker: {
        header: 'Ticker',
        sortable: true,
        render: (t) => `<td><strong>${t.symbol || ''}</strong></td>`,
        getSortVal: (t) => t.symbol || ''
    },
    company: {
        header: 'Company',
        sortable: false,
        render: (t) => `<td><span class="text-muted" title="${t.companyName || ''}">${formatCompanyName(t.companyName)}</span></td>`
    },
    price: {
        header: 'Price',
        sortable: false,
        render: (t) => `<td class="text-mono">$${(t.underlyingPrice || 0).toFixed(2)}</td>`,
        getSortVal: (t) => t.currentPrice || t.underlyingPrice || 0
    },
    todayPct: {
        header: 'Today',
        sortable: true,
        render: (t) => `<td class="today-perf" data-symbol="${escapeAttr(t.symbol || '')}"><span class="text-muted">--</span></td>`,
        getSortVal: (t) => t._todayPct != null ? t._todayPct : -Infinity
    },
    type: {
        header: 'Type',
        sortable: false,
        render: (t) => `<td>${formatLegs(t)}</td>`
    },
    expiry: {
        header: 'Expiry',
        sortable: true,
        render: (t) => `<td>${formatExpiryDate(t.expiryDate)} <span class="text-muted">(${t.dte || 0}d)</span></td>`,
        getSortVal: (t) => t.dte || 0
    },
    creditDebit: {
        header: 'Credit/Debit',
        sortable: false,
        render: (t) => {
            const credit = t.netCredit || 0;
            const creditStr = credit >= 0
                ? `<span class="text-success">$${credit.toFixed(2)}</span>`
                : `<span class="text-danger">-$${Math.abs(credit).toFixed(2)}</span>`;
            return `<td>${creditStr}</td>`;
        }
    },
    maxLoss: {
        header: 'Max Loss',
        sortable: true,
        render: (t) => `<td class="text-danger">$${(t.maxLoss || 0).toFixed(2)}</td>`,
        getSortVal: (t) => t.maxLoss || 0
    },
    extrinsic: {
        header: 'Extrinsic',
        sortable: true,
        render: (t) => `<td>$${(t.netExtrinsicValue || 0).toFixed(2)} <span class="text-muted">(${(t.anulizedNetExtrinsicValueToCapitalPercentage || 0).toFixed(1)}%)</span></td>`,
        getSortVal: (t) => t.anulizedNetExtrinsicValueToCapitalPercentage || 0
    },
    breakeven: {
        header: 'Breakeven',
        sortable: true,
        render: (t) => `<td>${formatBreakeven(t)}</td>`,
        getSortVal: (t) => {
            const hasCagr = t.breakevenCAGR != null;
            return hasCagr ? t.breakevenCAGR : (t.breakEvenPercent || 0);
        }
    },
    ror: {
        header: 'ROR%',
        sortable: true,
        render: (t) => {
            const rorClass = (t.returnOnRisk || 0) >= 0 ? 'text-success' : 'text-danger';
            const rorCagr = computeTradeRorCagr(t);
            let rorCagrDisplay = '';
            if (rorCagr != null && !isNaN(rorCagr) && isFinite(rorCagr)) {
                rorCagrDisplay = ` <span class="text-muted">(${rorCagr.toFixed(1)}% CAGR)</span>`;
            }
            return `<td class="${rorClass}">${(t.returnOnRisk || 0).toFixed(1)}%${rorCagrDisplay}</td>`;
        },
        getSortVal: (t) => {
            const c = computeTradeRorCagr(t);
            if (c == null || isNaN(c) || !isFinite(c)) {
                return t.maxReturnOnRiskPercentage || t.returnOnRisk || 0;
            }
            return c;
        }
    },
    costSavings: {
        header: 'Savings %',
        sortable: true,
        render: (t) => {
            const savings = getTradeCostSavings(t);
            if (savings == null) return '<td><span class="text-muted">-</span></td>';
            const cls = savings >= 0 ? 'text-success' : 'text-danger';
            return `<td class="${cls}">${savings.toFixed(1)}%</td>`;
        },
        getSortVal: (t) => getTradeCostSavings(t) ?? -Infinity
    }
};

const DEFAULT_STRATEGY_COLUMNS = {
    availableColumns: [
        'ticker', 'company', 'price', 'todayPct', 'type', 'expiry',
        'creditDebit', 'maxLoss', 'extrinsic', 'breakeven', 'ror', 'costSavings'
    ],
    default: [
        'ticker', 'company', 'price', 'todayPct', 'type', 'expiry',
        'creditDebit', 'maxLoss', 'extrinsic', 'breakeven', 'ror'
    ]
};

async function loadStrategyColumnsConfig() {
    try {
        const data = await API.get('/api/config/columns');
        if (data && typeof data === 'object') {
            window.strategyColumnsConfig = data;
        }
    } catch (e) {
        // Fallback to DEFAULT_STRATEGY_COLUMNS
    }
    return window.strategyColumnsConfig || DEFAULT_STRATEGY_COLUMNS;
}

function getTradeColumns(strategyType) {
    const cfg = window.strategyColumnsConfig || DEFAULT_STRATEGY_COLUMNS;
    const colKeys = (strategyType && cfg[strategyType]) || cfg.default || DEFAULT_STRATEGY_COLUMNS.default;
    return colKeys
        .map(k => AVAILABLE_TRADE_COLUMNS[k] ? { key: k, ...AVAILABLE_TRADE_COLUMNS[k] } : null)
        .filter(Boolean);
}

function buildTradeTable(tradesOrOpts, cardId = null, strategyId = null, isHistoryModal = false, strategyType = null) {
    let trades = tradesOrOpts;
    if (tradesOrOpts && typeof tradesOrOpts === 'object' && !Array.isArray(tradesOrOpts)) {
        ({
            trades = [],
            cardId = null,
            strategyId = null,
            isHistoryModal = false,
            strategyType = null
        } = tradesOrOpts);
    }

    if (!trades || trades.length === 0) {
        return '<div class="empty-state"><div class="empty-state-icon">📊</div>No trades found</div>';
    }

    const resolvedStrategyType = strategyType
        || (cardId ? CardStore.getStrategyType(cardId) : null)
        || (trades && trades[0] && trades[0].strategyType)
        || (strategyId && /LONG_CALL_LEAP|LEAP/i.test(strategyId) ? 'LONG_CALL_LEAP' : null)
        || (trades && trades.some(t => t.costSavingsPercent != null) ? 'LONG_CALL_LEAP' : null);

    const state = cardId ? (CardStore.getSortState(cardId) || { column: null, direction: 'asc' }) : null;
    const th = createSortableHeaderFn(cardId, state);

    const columns = getTradeColumns(resolvedStrategyType);

    let html = `<table class="data-table">
        <thead><tr>
            ${isHistoryModal ? '<th>Date Found</th>' : ''}
            ${columns.map(col => col.sortable ? th(col.key, col.header) : `<th>${col.header}</th>`).join('\n            ')}
            ${!isHistoryModal ? '<th>History</th>' : ''}
        </tr></thead><tbody>`;

    for (let idx = 0; idx < trades.length; idx++) {
        const t = trades[idx];
        const detailsEscaped = escapeAttr(t.tradeDetails || '');
        const techIndicatorsAttr = t.techIndicators ? escapeAttr(t.techIndicators) : '';
        const sym = t.symbol || '';
        const legsOptionData = (t.legs || []).map(l => ({ action: l.action, optionType: l.optionType, optionData: l.optionData || null }));
        const legsAttr = escapeAttr(JSON.stringify(legsOptionData));
        const tradeEscaped = escapeAttr(JSON.stringify(t));

        const effectiveStrategyId = strategyId || (cardId ? CardStore.getStrategyId(cardId) : '') || cardId || '';
        const foundDateDisplay = (t.foundDate && t.foundDate !== '1969-12-31' && t.foundDate !== '1970-01-01' && t.foundDate !== 'null')
            ? t.foundDate
            : (t.executionTimeMs && Number(t.executionTimeMs) > 86400000
                ? new Date(Number(t.executionTimeMs)).toISOString().slice(0, 10)
                : '--');

        html += `<tr class="trade-row" data-card-id="${escapeAttr(cardId || '')}" data-row-index="${idx}" data-details="${detailsEscaped}" data-tech-indicators="${techIndicatorsAttr}" data-legs-option-data="${legsAttr}" data-symbol="${escapeAttr(sym)}">
            ${isHistoryModal ? `<td><span class="text-muted text-mono">${escapeHtmlContent(foundDateDisplay)}</span></td>` : ''}
            ${columns.map(col => col.render(t)).join('\n            ')}
            ${!isHistoryModal ? `<td onclick="event.stopPropagation(); showTradeHistoryModal('${escapeAttr(effectiveStrategyId)}', this.dataset.trade, event)" data-trade="${tradeEscaped}">
                <button class="btn-history-icon" title="View Similar Historical Trades" style="background:none;border:none;cursor:pointer;font-size:1.1rem;padding:2px 6px;">🕒</button>
            </td>` : ''}
        </tr>`;
    }

    html += '</tbody></table>';
    return html;
}

const SCREENER_SORT_EXTRACTORS = {
    volume: a => a.volume || a.totalVolume || 0,
    marketCapB: a => a.marketCapB || 0,
    rsi: a => a.rsi || 0,
    ma200: a => a.ma200 || 0,
    ma100: a => a.ma100 || 0,
    ma50: a => a.ma50 || 0,
    ma20: a => a.ma20 || 0,
    refPrice: a => a.referencePrice || 0,
    dropPct: a => a.dropPercent || 0
};

function handleTableSort(cardId, column) {
    const originalData = CardStore.getTrades(cardId);
    if (!originalData || originalData.length === 0) return;

    const state = CardStore.toggleSort(cardId, column);
    let data = [...originalData];

    if (state && state.column && state.direction) {
        const dirMultiplier = state.direction === 'asc' ? 1 : -1;
        const col = state.column;

        data.sort((a, b) => {
            if (col === 'ticker') {
                return (a.symbol || '').localeCompare(b.symbol || '') * dirMultiplier;
            }
            if (col === 'breakeven') {
                const hasCagr = a.breakevenCAGR != null && b.breakevenCAGR != null;
                const valA = hasCagr ? a.breakevenCAGR : (a.breakEvenPercent || 0);
                const valB = hasCagr ? b.breakevenCAGR : (b.breakEvenPercent || 0);
                return (valA - valB) * dirMultiplier;
            }
            if (col.startsWith('ma_')) {
                const period = col.slice(3);
                const valA = (a.maValues && a.maValues[period]) || 0;
                const valB = (b.maValues && b.maValues[period]) || 0;
                return (valA - valB) * dirMultiplier;
            }
            const extractor = SCREENER_SORT_EXTRACTORS[col] || (AVAILABLE_TRADE_COLUMNS[col] && AVAILABLE_TRADE_COLUMNS[col].getSortVal);
            if (extractor) {
                return (extractor(a) - extractor(b)) * dirMultiplier;
            }
            return 0;
        });
    }

    const contentDiv = document.getElementById(`content-${cardId}`);
    if (contentDiv) {
        const tableType = originalData._type;
        if (tableType === 'drop') {
            contentDiv.innerHTML = buildDropScreenerTable(data, cardId);
        } else if (tableType === 'screener') {
            contentDiv.innerHTML = buildScreenerTable(data, cardId);
        } else {
            const actualStrategyId = CardStore.getStrategyId(cardId);
            const strategyType = CardStore.getStrategyType(cardId);
            contentDiv.innerHTML = buildTradeTable({ trades: data, cardId, strategyId: actualStrategyId, isHistoryModal: false, strategyType });
            const symbols = [...new Set(data.map(t => t.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, contentDiv);
            }
        }
    }
}

// ── Today's Performance Injection ──

async function injectTodayPerformance(symbols, scope = document) {
    if (!symbols || symbols.length === 0) return;
    try {
        const data = await API.get(`/api/quotes?symbols=${symbols.join(',')}`);
        if (!Array.isArray(data)) return;

        const quoteMap = {};
        for (const q of data) {
            quoteMap[q.symbol] = q;
        }

        const cells = scope.querySelectorAll('.today-perf[data-symbol]');
        cells.forEach(cell => {
            const sym = cell.getAttribute('data-symbol') || (cell.dataset && cell.dataset.symbol);
            const q = quoteMap[sym];
            if (!q || q.netChange == null || q.netPercentChange == null) {
                cell.innerHTML = '<span class="text-muted">N/A</span>';
                return;
            }
            const chg = q.netChange;
            const pct = q.netPercentChange;
            const sign = chg >= 0 ? '+' : '';
            const cls = chg >= 0 ? 'text-success' : 'text-danger';
            const chgStr = `${sign}$${Math.abs(chg).toFixed(2)}`;
            const pctStr = `(${sign}${pct.toFixed(2)}%)`;
            cell.innerHTML = `<span class="${cls}" style="font-size:0.82rem; white-space:nowrap;">${chgStr} ${pctStr}</span>`;
        });

        for (const [cardId, trades] of Object.entries(window.tradeDataMap || {})) {
            if (!Array.isArray(trades)) continue;
            trades.forEach(t => {
                const q = quoteMap[t.symbol];
                if (q && q.netPercentChange != null) {
                    t._todayPct = q.netPercentChange;
                }
            });
        }
    } catch (e) {
        console.warn('Today performance fetch failed:', e.message);
    }
}

async function fetchAndInjectTodayPerformance(container) {
    if (!container) return;
    const symbols = [...new Set(
        [...container.querySelectorAll('.today-perf[data-symbol]')]
            .map(el => el.dataset.symbol)
            .filter(Boolean)
    )];
    if (symbols.length > 0) {
        await injectTodayPerformance(symbols, container);
    }
}

// ── Trade Detail Popup ──

function renderOptionDataTable(legsOptionData) {
    if (!legsOptionData || legsOptionData.length === 0) return '';

    const fmt = (v, digits = 2) => (v != null && v !== 0) ? (typeof v === 'number' ? v.toFixed(digits) : v) : '—';
    const fmtDollar = (v) => (v != null && v !== 0) ? `$${Math.abs(v).toFixed(2)}` : '—';
    const fmtInt = (v) => (v != null && v !== 0) ? v.toLocaleString() : '—';

    let html = `<div style="margin-top: 10px; border: 1px solid var(--border); border-radius: 6px; overflow: hidden;">
        <div style="padding: 7px 12px; background: var(--bg-secondary); border-bottom: 1px solid var(--border); font-size: 0.78rem; font-weight: 600; letter-spacing: 0.06em; color: var(--text-muted); text-transform: uppercase;">
            Option Chain Data
        </div>`;

    for (const leg of legsOptionData) {
        const d = leg.optionData;
        const actionCls = leg.action === 'SELL' ? 'leg-sell' : 'leg-buy';
        const legLabel = `<span class="leg-chip ${actionCls}" style="font-size:0.75rem; padding:2px 7px;">${leg.action} ${leg.optionType}</span>`;

        if (!d) {
            html += `<div style="padding: 8px 12px; font-size:0.82rem; color: var(--text-muted);">${legLabel} <span style="margin-left:8px;">No option data available (re-run strategy to populate)</span></div>`;
            continue;
        }

        const rows = [
            ['Symbol', escapeHtmlContent(d.symbol || '—')],
            ['Bid / Ask', `${fmtDollar(d.bid)} / ${fmtDollar(d.ask)}`],
            ['Mark', fmtDollar(d.mark)],
            ['Last', fmtDollar(d.last)],
            ['Bid×Ask Size', escapeHtmlContent(d.bidAskSize || '—')],
            ['Volume', fmtInt(d.totalVolume)],
            ['Open Interest', fmtInt(d.openInterest)],
            ['IV (Volatility)', d.volatility != null ? `${fmt(d.volatility, 1)}%` : '—'],
            ['Delta', fmt(d.delta, 4)],
            ['Gamma', fmt(d.gamma, 4)],
            ['Theta', fmt(d.theta, 4)],
            ['Vega', fmt(d.vega, 4)],
            ['Rho', fmt(d.rho, 4)],
            ['Intrinsic Value', fmtDollar(d.intrinsicValue)],
            ['Extrinsic Value', fmtDollar(d.extrinsicValue)],
            ['Time Value', fmtDollar(d.timeValue)],
            ['Theoretical Value', fmtDollar(d.theoreticalOptionValue)],
            ['% Change', d.percentChange != null ? `${fmt(d.percentChange, 2)}%` : '—'],
            ['In The Money', d.inTheMoney ? 'Yes' : 'No'],
            ['Days to Expiry', d.daysToExpiration != null ? d.daysToExpiration : '—'],
            ['Expiration Date', escapeHtmlContent(d.expirationDate || '—')],
            ['Strike', fmtDollar(d.strikePrice)],
            ['52W High', fmtDollar(d.high52Week)],
            ['52W Low', fmtDollar(d.low52Week)],
        ];

        html += `<div style="padding: 8px 12px 6px; border-bottom: 1px solid var(--border);">
            <div style="margin-bottom: 6px;">${legLabel} <span style="font-size:0.78rem; color:var(--text-muted); margin-left:6px;">${escapeHtmlContent(d.description || '')}</span></div>
            <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 3px 16px;">`;

        for (const [label, val] of rows) {
            html += `<div style="display:flex; justify-content:space-between; font-size:0.8rem; padding: 2px 0; border-bottom: 1px solid var(--border); gap:8px;">
                <span style="color:var(--text-muted); white-space:nowrap;">${label}</span>
                <span style="color:var(--text-primary); font-family:var(--font-mono); text-align:right;">${val}</span>
            </div>`;
        }

        html += `</div></div>`;
    }

    html += `</div>`;
    return html;
}

function initTradeRowClicks() {
    document.addEventListener('click', (e) => {
        const row = e.target.closest('.trade-row');
        if (!row) return;

        const existing = document.querySelector('.trade-detail-panel');
        if (existing) {
            if (existing.dataset.rowId === row.dataset.details) {
                existing.remove();
                row.classList.remove('selected');
                return;
            }
            existing.remove();
            document.querySelectorAll('.trade-row.selected').forEach(r => r.classList.remove('selected'));
        }

        const details = row.dataset.details;
        if (!details) return;

        const techIndicators = row.dataset.techIndicators;

        row.classList.add('selected');

        const symbol = row.querySelector('td strong')?.textContent || '';
        let legsOptionData = null;
        if (row.dataset.cardId && row.dataset.rowIndex !== undefined) {
            const cachedTrade = CardStore.getTrades(row.dataset.cardId)[Number(row.dataset.rowIndex)];
            if (cachedTrade && Array.isArray(cachedTrade.legs)) {
                legsOptionData = cachedTrade.legs.map(l => ({ action: l.action, optionType: l.optionType, optionData: l.optionData || null }));
            }
        }
        if (!legsOptionData) {
            try {
                const raw = row.dataset.legsOptionData ? decodeAttr(row.dataset.legsOptionData) : null;
                legsOptionData = raw ? JSON.parse(raw) : null;
            } catch(e) {}
        }
        
        const panel = document.createElement('tr');
        panel.className = 'trade-detail-panel';
        panel.dataset.rowId = details;
        panel.innerHTML = `
            <td colspan="10">
                <div class="trade-detail">
                    <div class="trade-detail-header">
                        ▶ ${symbol} — Trade Details
                    </div>
                    <pre class="trade-detail-body">${decodeAttr(details)}</pre>
                    ${techIndicators ? `
                    <div class="trade-detail-header" style="margin-top: 15px;">
                        ▶ ${symbol} — Tech Indicators
                    </div>
                    <pre class="trade-detail-body">${decodeAttr(techIndicators)}</pre>
                    ` : ''}
                    ${legsOptionData ? renderOptionDataTable(legsOptionData) : ''}
                    <div class="iv-data-panel" style="margin-top: 10px; font-family: var(--font-mono); font-size: 0.85rem; padding: 8px; background: var(--bg-alt); border-radius: 4px; border: 1px solid var(--border);">
                        <span class="text-muted">Loading Volatility Data...</span>
                    </div>
                </div>
            </td>`;
        row.after(panel);

        if (symbol) {
            const ivPanel = panel.querySelector('.iv-data-panel');
            API.get(`/api/iv-rank?symbol=${symbol}`)
                .then(data => {
                    if (data && data.currentIV !== undefined) {
                        ivPanel.innerHTML = `
                            <div style="font-weight: 600; margin-bottom: 4px; color: var(--text-primary);">Volatility Context (1Y)</div>
                            <div class="flex gap-md flex-wrap">
                                <div><span class="text-muted">IV Rank:</span> <strong>${data.ivRank.toFixed(1)}</strong></div>
                                <div><span class="text-muted">IV Percentile:</span> <strong>${data.ivPercentile !== undefined ? data.ivPercentile.toFixed(1) : 'N/A'}</strong></div>
                                <div><span class="text-muted">Current IV:</span> <strong>${data.currentIV.toFixed(1)}%</strong></div>
                                <div><span class="text-muted">52W Low:</span> ${data.minIV.toFixed(1)}%</div>
                                <div><span class="text-muted">52W High:</span> ${data.maxIV.toFixed(1)}%</div>
                            </div>
                        `;
                    } else {
                        ivPanel.innerHTML = `<span class="text-muted">No IV data available</span>`;
                    }
                })
                .catch(e => {
                    ivPanel.innerHTML = `<span class="text-muted">IV Data not available</span>`;
                });
        }
    });
}

function showTradeHistoryModal(cardId, tradeRaw, event) {
    if (event) event.stopPropagation();

    let trade = tradeRaw;
    if (typeof tradeRaw === 'string') {
        try {
            trade = JSON.parse(decodeAttr(tradeRaw));
        } catch (e) {
            trade = tradeRaw;
        }
    }

    if (!trade || !trade.symbol) return;

    let existingOverlay = document.getElementById('history-modal-overlay');
    if (existingOverlay) existingOverlay.remove();

    const symbolEscaped = escapeHtmlContent(trade.symbol || '');
    const expiryEscaped = escapeHtmlContent(trade.expiryDate || '');

    createModalOverlay({
        id: 'history-modal-overlay',
        overlayStyle: 'z-index: 1000;',
        modalId: 'historyModal',
        modalStyle: 'max-width: 960px; width: 92%; max-height: 85vh; overflow-y: auto;',
        contentHtml: `
            <div class="flex items-center justify-between" style="margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 12px;">
                <div>
                    <h3 style="margin: 0; font-size: 1.25rem; color: var(--text-primary);">🕒 Similar Historical Trades</h3>
                    <span class="text-muted small">Target: <strong>${symbolEscaped}</strong> (${expiryEscaped})</span>
                </div>
                <button class="btn btn-secondary" onclick="document.getElementById('history-modal-overlay').remove()">✕ Close</button>
            </div>
            <div id="history-modal-body">
                <div class="empty-state"><div class="spinner"></div>Loading similar historical trades...</div>
            </div>`
    });

    const strategyId = cardId ? cardId.replace(/-[0-9a-fA-F-]+$/, '') : '';
    API.post('/api/strategies/history/similar-trades', { strategyId: strategyId, trade: trade, limit: 20 })
        .then(matches => {
            const bodyEl = document.getElementById('history-modal-body');
            if (!bodyEl) return;

            if (!matches || !Array.isArray(matches) || matches.length === 0) {
                bodyEl.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📜</div>No matching historical trades found in database</div>';
                return;
            }

            bodyEl.innerHTML = buildTradeTable({ trades: matches, cardId: 'history-modal-table', isHistoryModal: true });
            const symbols = [...new Set(matches.map(m => m.symbol).filter(Boolean))];
            if (symbols.length > 0) {
                injectTodayPerformance(symbols, bodyEl);
            }
        })
        .catch(err => {
            const bodyEl = document.getElementById('history-modal-body');
            if (bodyEl) {
                bodyEl.innerHTML = `<div class="empty-state text-danger">Failed to load trade history: ${escapeHtmlContent(err.message || 'Error')}</div>`;
            }
        });
}

if (typeof document !== 'undefined') {
    document.addEventListener('DOMContentLoaded', initTradeRowClicks);
}

// ── Filter Params Formatter ──

/**
 * Formats a technical filter condition or configuration value into a human-readable string.
 * Recursively handles arrays, objects (conditions, config, custom range, rules), numbers, and strings,
 * avoiding raw "[object Object]" serialization.
 *
 * @param {*} val - Technical filter condition/config value
 * @returns {string|null} Formatted readable string or null if empty
 */
function formatTechFilterValue(val) {
    if (val === null || val === undefined || val === '') return null;
    if (typeof val === 'boolean') return val ? 'Yes' : 'No';
    if (typeof val === 'number' || typeof val === 'string') return String(val);

    if (Array.isArray(val)) {
        if (val.length === 0) return null;
        return val
            .map(item => (typeof item === 'object' && item !== null) ? formatTechFilterValue(item) : String(item))
            .filter(Boolean)
            .join(', ');
    }

    if (typeof val === 'object') {
        let condStr = '';
        if (val.conditions !== undefined && val.conditions !== null) {
            if (Array.isArray(val.conditions)) {
                condStr = val.conditions
                    .map(c => (typeof c === 'object' && c !== null) ? formatTechFilterValue(c) : String(c))
                    .filter(Boolean)
                    .join(', ');
            } else if (typeof val.conditions === 'object') {
                condStr = formatTechFilterValue(val.conditions);
            } else {
                condStr = String(val.conditions);
            }
        } else if (val.condition !== undefined && val.condition !== null && val.condition !== '') {
            if (typeof val.condition === 'object') {
                if ((val.condition.type === 'CUSTOM_RANGE' || val.condition.condition === 'CUSTOM_RANGE') && (val.condition.min !== undefined || val.condition.max !== undefined)) {
                    condStr = `Custom Range (${val.condition.min ?? ''} - ${val.condition.max ?? ''})`;
                } else if (val.condition.type || val.condition.condition) {
                    const typeKey = val.condition.type || val.condition.condition;
                    const condLabel = TECH_CONDITION_LABELS[typeKey] || typeKey;
                    const extra = Object.entries(val.condition)
                        .filter(([k]) => k !== 'type' && k !== 'condition')
                        .map(([k, v]) => `${k}: ${typeof v === 'object' ? formatTechFilterValue(v) : v}`)
                        .join(', ');
                    condStr = extra ? `${condLabel} (${extra})` : condLabel;
                } else {
                    condStr = Object.entries(val.condition)
                        .map(([k, v]) => `${k}: ${typeof v === 'object' ? formatTechFilterValue(v) : v}`)
                        .join(', ');
                }
            } else if (val.condition === 'CUSTOM_RANGE' && (val.min !== undefined || val.max !== undefined)) {
                condStr = `Custom Range (${val.min ?? ''} - ${val.max ?? ''})`;
            } else {
                condStr = TECH_CONDITION_LABELS[String(val.condition)] || String(val.condition);
            }
        } else if (val.type === 'CUSTOM_RANGE' && (val.min !== undefined || val.max !== undefined)) {
            condStr = `Custom Range (${val.min ?? ''} - ${val.max ?? ''})`;
        } else if (val.type && (val.min !== undefined || val.max !== undefined)) {
            const rangeParts = [val.min !== undefined ? `min: ${val.min}` : '', val.max !== undefined ? `max: ${val.max}` : ''].filter(Boolean);
            const condLabel = TECH_CONDITION_LABELS[val.type] || val.type;
            condStr = `${condLabel} (${rangeParts.join(', ')})`;
        } else if (val.rules !== undefined && val.rules !== null) {
            condStr = Array.isArray(val.rules) ? val.rules.join(', ') : String(val.rules);
        } else if (val.min !== undefined && val.max !== undefined) {
            condStr = `${val.min} - ${val.max}`;
        }

        const configParts = [];
        if (val.config !== undefined && val.config !== null && val.config !== 'default') {
            if (typeof val.config === 'object') {
                if (val.config.period !== undefined) configParts.push(`Period: ${val.config.period}`);
                for (const [ck, cv] of Object.entries(val.config)) {
                    if (ck !== 'period' && cv !== null && cv !== undefined && cv !== '') {
                        configParts.push(`${ck}: ${typeof cv === 'object' ? formatTechFilterValue(cv) : cv}`);
                    }
                }
            } else if (typeof val.config === 'string') {
                configParts.push(`Config: ${val.config}`);
            }
        } else if (val.period !== undefined) {
            configParts.push(`Period: ${val.period}`);
        }
        if (val.lookbackDays !== undefined && val.lookbackDays !== null) {
            configParts.push(`Lookback: ${val.lookbackDays}d`);
        }

        const configStr = configParts.join(', ');

        if (condStr && configStr) {
            return `${condStr} (${configStr})`;
        }
        if (condStr) return condStr;
        if (configStr) return configStr;

        const fallback = Object.entries(val)
            .filter(([k, v]) => v !== null && v !== undefined && v !== '' && (k !== 'config' || v !== 'default'))
            .map(([k, v]) => `${k}: ${typeof v === 'object' ? formatTechFilterValue(v) : v}`)
            .join(', ');

        return fallback || null;
    }

    return String(val);
}

function renderFilterGrid(cfg) {
    if (!cfg || typeof cfg !== 'object') return '';
    const entries = Object.entries(cfg);
    if (entries.length === 0) return '';

    const formatLabel = (key) => {
        if (!key) return '';
        const ACRONYMS = new Set(['RSI', 'SMA', 'EMA', 'ATR', 'BB', 'HV', 'DTE', 'CAGR', 'IV', 'PE']);
        if (key.includes('_')) {
            return key.split('_')
                .map(w => ACRONYMS.has(w.toUpperCase()) ? w.toUpperCase() : (w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()))
                .join(' ');
        }
        if (/^[A-Z0-9]+$/.test(key)) {
            return key;
        }
        return key
            .replace(/([A-Z])/g, ' $1')
            .replace(/^./, s => s.toUpperCase())
            .replace(/([a-z])(\d)/g, '$1 $2')
            .trim();
    };

    const formatValue = (v, key) => {
        if (v === null || v === undefined || v === '') return null;
        if (key === 'screenerType' && typeof v === 'string') {
            return SCREENER_TYPE_LABELS[v] || formatLabel(v);
        }
        if (typeof v === 'boolean') return v ? 'Yes' : 'No';
        if (Array.isArray(v)) {
            if (v.length === 0) return null;
            return v.map(item => (typeof item === 'object' && item !== null) ? formatTechFilterValue(item) : String(item)).join(', ');
        }
        if (typeof v === 'object') {
            return formatTechFilterValue(v);
        }
        return String(v);
    };

    let html = '';
    let rootHtml = '';
    let techFiltersHtml = '';
    let fundamentalFiltersHtml = '';
    const nested = [];
    const SKIP_KEYS = new Set(['greeks', 'strategyType', 'strategyId', 'customResultId', 'technicalFilterSummary', 'earningsFilterExpressions', 'earnings_filter_expressions']);

    for (const [key, val] of entries) {
        if (SKIP_KEYS.has(key)) continue;
        if (key === 'maxDTE' && val === 2147483647) continue;
        if ((key === 'targetDTE' || key === 'minDTE' || key === 'minReturnOnRisk' || key === 'minReturnOnRiskCAGR') && val === 0) continue;
        if (key === 'technicalFilters' && val) {
            if (typeof val === 'string') {
                rootHtml += `<div class="config-item" style="grid-column: 1 / -1"><span class="config-item-label" style="color:var(--accent)">🔬 Tech Filters (Preset)</span><span class="config-item-value">${formatValue(val, key) || String(val)}</span></div>`;
            } else if (typeof val === 'object') {
                techFiltersHtml = renderTechFiltersGrid(val);
            }
            continue;
        }
        if (key === 'fundamentalFilters' && val) {
            if (typeof val === 'object') {
                fundamentalFiltersHtml = renderFundamentalFiltersGrid(val);
            }
            continue;
        }
        if (val !== null && typeof val === 'object' && !Array.isArray(val)) {
            nested.push([key, val]);
        } else {
            const formattedVal = formatValue(val, key);
            if (formattedVal !== null) {
                rootHtml += `<div class="config-item"><span class="config-item-label">${formatLabel(key)}</span><span class="config-item-value">${formattedVal}</span></div>`;
            }
        }
    }
    
    if (rootHtml) {
        html += `<div class="config-grid">${rootHtml}</div>`;
    }

    for (const [key, obj] of nested) {
        const nestedEntries = Object.entries(obj);
        if (nestedEntries.length === 0) continue;
        let nestedHtml = '';
        for (const [k, v] of nestedEntries) {
            const formattedVal = formatValue(v, k);
            if (formattedVal !== null) {
                nestedHtml += `<div class="config-item"><span class="config-item-label">${formatLabel(k)}</span><span class="config-item-value">${formattedVal}</span></div>`;
            }
        }
        if (nestedHtml) {
            const headingPrefix = (key === 'technicalFilters' || key === 'technicalFilter') ? '🔬 '
                : (key === 'fundamentalFilters' || key === 'fundamentalFilter') ? '📊 '
                : (key === 'earningsFilters' || key === 'earningsFilter') ? '📅 ' : '';
            html += `<div class="nested-section"><div class="nested-heading">${headingPrefix}${formatLabel(key)}</div><div class="config-grid">${nestedHtml}</div></div>`;
        }
    }

    if (techFiltersHtml) {
        html += techFiltersHtml;
    }
    if (fundamentalFiltersHtml) {
        html += fundamentalFiltersHtml;
    }

    return html;
}

/** Human-friendly labels for technical filter keys. */
const TECH_FILTER_LABELS = {
    RSI:                   'RSI Condition',
    BOLLINGER_BAND:        'Bollinger Band',
    VOLUME:                'Volume Rules',
    DOLLAR_VOLUME:         'Dollar Volume Rules',
    HISTORICAL_VOLATILITY: 'Historical Volatility',
    PRICE_DROP:            'Price Drop',
    SIMPLE_MOVING_AVERAGE: 'Moving Average Rules',
    EXP_MOVING_AVERAGE:    'Exponential Moving Average Rules',
    HIGH_52W_DROP:         '52-Week High Drop',
    AVERAGE_TRUE_RANGE:    'Average True Range',
};

/** Human-friendly labels for technical screener types. */
const SCREENER_TYPE_LABELS = {
    RSI_BB_BULLISH_CROSSOVER: 'RSI BB Bullish Crossover',
    RSI_BB_BEARISH_CROSSOVER: 'RSI BB Bearish Crossover',
    RSI_OVERSOLD:             'RSI Oversold',
    BB_LOWER:                 'Bollinger Band Lower',
    BELOW_200_DAY_SMA:        'Below 200 Day SMA',
    PRICE_DROP:               'Price Drop',
    HIGH_52W_DROP:            '52-Week High Drop',
    ATR:                      'Average True Range'
};

/** Human-friendly labels for technical filter condition values. */
const TECH_CONDITION_LABELS = {
    OVERSOLD:          'Oversold (<30)',
    BULLISH_CROSSOVER: 'Bullish Crossover (RSI ↑30)',
    OVERBOUGHT:        'Overbought (>70)',
    BEARISH_CROSSOVER: 'Bearish Crossover (RSI ↓70)',
    CUSTOM_RANGE:      'Custom Range',
    LOWER_BAND:        'At/below lower band',
    UPPER_BAND:        'At/above upper band',
};

function renderTechFiltersGrid(technicalFilters) {
    if (!technicalFilters) return '';
    if (typeof technicalFilters === 'string') {
        return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters (Preset)</div><div class="config-grid"><div class="config-item"><span class="config-item-label">Preset</span><span class="config-item-value">${technicalFilters}</span></div></div></div>`;
    }
    if (typeof technicalFilters !== 'object') return '';

    const parts = [];
    for (const [key, val] of Object.entries(technicalFilters)) {
        const formatted = formatTechFilterValue(val);
        if (formatted !== null && formatted !== undefined && formatted !== '') {
            const label = TECH_FILTER_LABELS[key] || key;
            parts.push(`<div class="config-item"><span class="config-item-label">${label}</span><span class="config-item-value">${formatted}</span></div>`);
        }
    }
    if (parts.length === 0) return '';
    return `<div class="nested-section"><div class="nested-heading">🔬 Technical Filters</div><div class="config-grid">${parts.join('')}</div></div>`;
}


function renderFundamentalFiltersGrid(fundamentalFilters) {
    if (!fundamentalFilters || typeof fundamentalFilters !== 'object') return '';
    const parts = [];
    for (const [key, val] of Object.entries(fundamentalFilters)) {
        let formatted = '';
        if (val && typeof val === 'object' && Array.isArray(val.conditions)) {
            if (val.conditions.length > 0) {
                formatted = val.conditions.map(c => (typeof c === 'object' && c !== null) ? formatTechFilterValue(c) : String(c)).join(', ');
            }
        } else if (val && typeof val === 'object') {
            formatted = formatTechFilterValue(val);
        } else if (val !== null && val !== undefined && val !== '') {
            formatted = String(val);
        }
        if (formatted) {
            parts.push(`<div class="config-item"><span class="config-item-label">${key}</span><span class="config-item-value">${formatted}</span></div>`);
        }
    }
    if (parts.length === 0) return '';
    return `<div class="nested-section"><div class="nested-heading">📊 Fundamental Filters</div><div class="config-grid">${parts.join('')}</div></div>`;
}

// ── Dashboard Page Initialization ──

async function initDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadStrategyColumnsConfig();
    await loadFilterDescriptions();
    await loadOptionsStrategies();
    await loadOptionsResults();
    await checkExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadOptionsStrategies() {
    const strategyContainer = document.getElementById('strategy-checkboxes');

    if (strategyContainer) {
        try {
            const strategies = await API.get('/api/strategies');
            strategyContainer.innerHTML = strategies.map(s => {
                const parts = [s.name, s.termType].filter(Boolean);
                const displayName = parts.join(' - ');
                return `
                <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="strategy">
                        <span>${displayName}</span>
                    </label>
                    ${renderDocInfoBtn(s.descriptionFile, displayName)}
                </div>`;
            }).join('');
            const badge = document.getElementById('strategy-count-badge');
            if (badge) badge.textContent = `(${strategies.length})`;
        } catch (e) {
            strategyContainer.innerHTML = `<span class="text-muted">Failed to load strategies</span>`;
        }
    }
}

async function loadStrategies() {
    await loadOptionsStrategies();
    const screenerContainer = document.getElementById('screener-checkboxes');

    if (screenerContainer) {
        try {
            const screeners = await API.get('/api/screeners');
            if (screeners.length === 0) {
                screenerContainer.innerHTML = `<span class="text-muted">No screeners configured</span>`;
            } else {
                screenerContainer.innerHTML = screeners.map(s => `
                    <div class="flex items-center gap-sm" style="margin-bottom: 8px;">
                        <label class="checkbox-label" style="margin: 0;">
                            <input type="checkbox" value="${s.index}" data-type="screener">
                            <span>${s.name}</span>
                        </label>
                    </div>`).join('');
            }
            const badge = document.getElementById('screener-count-badge');
            if (badge) badge.textContent = `(${screeners.length})`;
        } catch (e) {
            screenerContainer.innerHTML = `<span class="text-muted">Failed to load screeners</span>`;
        }
    }
}

async function loadOptionsResults() {
    const optionsContainer = document.getElementById('results-container');
    if (!optionsContainer) return;

    resetDashboardFilter('options');

    try {
        const optionResults = await API.get('/api/results');

        optionsContainer.innerHTML = '';
        if (!optionResults || optionResults.length === 0) {
            optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
            hideDashboardFilterBar('options');
            updateToggleAllTermsButtonState(optionsContainer);
        } else {
            renderTermGroups(optionsContainer, optionResults);
            fetchAndInjectTodayPerformance(optionsContainer);
            showDashboardFilterBar('options');
        }
    } catch (e) {
        optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        hideDashboardFilterBar('options');
        updateToggleAllTermsButtonState(optionsContainer);
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

async function loadResults() {
    const optionsContainer = document.getElementById('results-container');
    const screenerContainer = document.getElementById('screener-results-container');
    
    if (!optionsContainer && !screenerContainer) return;

    try {
        const [optionResults, screenerResults] = await Promise.all([
            optionsContainer ? API.get('/api/results') : Promise.resolve([]),
            screenerContainer ? API.get('/api/results/screeners').catch(() => []) : Promise.resolve([])
        ]);

        if (optionsContainer) {
            optionsContainer.innerHTML = '';
            if (!optionResults || optionResults.length === 0) {
                optionsContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📊</div>No option strategy results yet. Execute a strategy to see results.</div>';
                updateToggleAllTermsButtonState(optionsContainer);
            } else {
                renderTermGroups(optionsContainer, optionResults);
                fetchAndInjectTodayPerformance(optionsContainer);
            }
        }

        if (screenerContainer) {
            screenerContainer.innerHTML = '';
            if (!screenerResults || screenerResults.length === 0) {
                screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet.</div>';
            } else {
                for (const r of screenerResults) {
                    screenerContainer.appendChild(buildScreenerCard(r));
                }
            }
        }
    } catch (e) {
        if (optionsContainer) {
            optionsContainer.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
            updateToggleAllTermsButtonState(optionsContainer);
        }
        if (screenerContainer) screenerContainer.innerHTML = '';
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

async function checkExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.alerts && status.alerts.length > 0) {
            showErrorPanel(status.alerts);
        }
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || "";
            setDashboardBusy(true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setDashboardBusy(false);
                loadResults();
                showToast('Execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

async function executeSelected() {
    const checkedStrategies = document.querySelectorAll('#strategy-checkboxes input[type="checkbox"]:checked');
    const checkedScreeners = document.querySelectorAll('#screener-checkboxes input[type="checkbox"]:checked');
    const strategyIndices = Array.from(checkedStrategies).map(c => parseInt(c.value));
    const screenerIndices = Array.from(checkedScreeners).map(c => parseInt(c.value));
    if (strategyIndices.length === 0 && screenerIndices.length === 0) {
        showToast('Select at least one strategy or screener', 'error');
        return;
    }
    try {
        setDashboardBusy(true);
        const res = await API.post('/api/execute', { strategyIndices, screenerIndices });
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setDashboardBusy(false);
            loadResults();
            showToast('Execution completed!');
        });
    } catch (e) {
        setDashboardBusy(false);
        showToast(e.message, 'error');
    }
}

async function cancelExecution() {
    try {
        window.cancellationRequested = true;
        await API.post('/api/cancel');
        showToast('Cancellation requested');
    } catch (e) {
        showToast(e.message, 'error');
    }
}

// ── Market Status Live Injection ──

async function fetchAndRenderMarketStatus() {
    const mainContent = document.querySelector('.main-content');
    if (!mainContent) return;

    let statusContainer = document.querySelector('.market-status-container');
    if (!statusContainer) {
        statusContainer = document.createElement('div');
        statusContainer.className = 'market-status-container flex gap-sm';
        const subtitle = mainContent.querySelector('.page-subtitle');
        if (subtitle && subtitle.nextSibling) {
            mainContent.insertBefore(statusContainer, subtitle.nextSibling);
        } else if (subtitle) {
            mainContent.appendChild(statusContainer);
        } else {
            mainContent.insertBefore(statusContainer, mainContent.firstChild);
        }
    }

    function statusColor(s) {
        if (s === 'OPEN')       return 'var(--success)';
        if (s === 'PRE_MARKET' || s === 'POST_MARKET') return '#f5a623';
        return 'var(--text-muted)';
    }

    function statusLabel(s) {
        if (s === 'PRE_MARKET')  return 'PRE MARKET';
        if (s === 'POST_MARKET') return 'POST MARKET';
        return s;
    }

    try {
        const data = await API.get('/api/market-status');
        const eq  = data.equityStatus  || 'CLOSED';
        const opt = data.optionsStatus || 'CLOSED';
        const equityColor  = statusColor(eq);
        const optionsColor = statusColor(opt);

        statusContainer.innerHTML = `
            <span class="status-badge" style="border: 1px solid var(--border); color: ${equityColor}">
                <span class="status-dot" style="background:${equityColor};"></span>
                Equity: ${statusLabel(eq)}
            </span>
            <span class="status-badge" style="border: 1px solid var(--border); color: ${optionsColor}">
                <span class="status-dot" style="background:${optionsColor};"></span>
                Options: ${statusLabel(opt)}
            </span>
        `;
    } catch (e) {
        statusContainer.innerHTML = `<span class="status-badge" style="color: var(--text-muted); border: 1px solid var(--border)">Market Status Offline</span>`;
        console.error("Could not fetch market status", e);
        if (typeof checkExecutionStatus === 'function') {
            checkExecutionStatus();
        }
    }
}

// ── Dashboard Results Filter Bar ──

const DASHBOARD_FILTER_CONFIG = {
    options: {
        barId: 'options-filter-bar',
        columnSelectId: 'options-filter-column',
        textInputId: 'options-filter-text',
        dateGroupId: 'options-filter-date-group',
        dateModeId: 'options-filter-date-mode',
        dateInputId: 'options-filter-date',
        searchBtnId: 'options-filter-btn',
        clearBtnId: 'options-filter-clear',
        summaryId: 'options-filter-summary',
        containerId: 'results-container',
        supportsDate: true
    },
    screeners: {
        barId: 'screeners-filter-bar',
        columnSelectId: 'screeners-filter-column',
        textInputId: 'screeners-filter-text',
        dateGroupId: null,
        dateModeId: null,
        dateInputId: null,
        searchBtnId: 'screeners-filter-btn',
        clearBtnId: 'screeners-filter-clear',
        summaryId: 'screeners-filter-summary',
        containerId: 'screener-results-container',
        supportsDate: false
    }
};

function showDashboardFilterBar(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;
    const bar = document.getElementById(cfg.barId);
    if (bar) bar.style.display = '';
}

function hideDashboardFilterBar(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;
    const bar = document.getElementById(cfg.barId);
    if (bar) bar.style.display = 'none';
}

function resetDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateGroup = cfg.dateGroupId ? document.getElementById(cfg.dateGroupId) : null;
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const searchBtn = document.getElementById(cfg.searchBtnId);
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);

    if (colSelect) colSelect.value = 'ticker';
    if (textInput) { textInput.value = ''; textInput.style.display = 'inline-block'; }
    if (dateGroup) dateGroup.style.display = 'none';
    if (dateInput) dateInput.value = '';
    if (searchBtn) searchBtn.style.display = 'inline-block';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';
}

function onFilterColumnChange(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateGroup = cfg.dateGroupId ? document.getElementById(cfg.dateGroupId) : null;
    const searchBtn = document.getElementById(cfg.searchBtnId);
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);

    const selected = colSelect ? colSelect.value : '';

    if (!selected) {
        if (textInput) { textInput.value = ''; textInput.style.display = 'none'; }
        if (dateGroup) dateGroup.style.display = 'none';
        if (searchBtn) searchBtn.style.display = 'none';
        if (clearBtn) clearBtn.style.display = 'none';
        if (summary) summary.innerHTML = '';
        return;
    }

    const isDate = (selected === 'expiry');

    if (textInput) {
        textInput.style.display = isDate ? 'none' : '';
        if (!isDate) { textInput.value = ''; setTimeout(() => textInput.focus(), 50); }
    }
    if (dateGroup) {
        dateGroup.style.display = isDate ? '' : 'none';
    }
    if (searchBtn) searchBtn.style.display = '';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';
}

const DASHBOARD_FILTER_EXTRACTORS = {
    ticker: item => (item.symbol || '').toLowerCase(),
    expiry: item => (item.expiryDate || '').substring(0, 10)
};

function getDashboardItemValue(item, columnKey) {
    const extractor = DASHBOARD_FILTER_EXTRACTORS[columnKey];
    return extractor ? extractor(item) : '';
}

function matchesDashboardFilter(itemOrOpts, columnKey, filterValue, dateMode = 'before') {
    let item = itemOrOpts;
    if (itemOrOpts && typeof itemOrOpts === 'object' && 'item' in itemOrOpts && 'columnKey' in itemOrOpts) {
        ({ item, columnKey, filterValue, dateMode = 'before' } = itemOrOpts);
    }

    const itemValue = getDashboardItemValue(item, columnKey);
    if (!itemValue) return false;

    if (columnKey === 'expiry') {
        if (!filterValue) return false;
        return dateMode === 'after'
            ? itemValue >= filterValue
            : itemValue <= filterValue;
    }

    return itemValue.includes(filterValue.toLowerCase().trim());
}

function applyDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const colSelect = document.getElementById(cfg.columnSelectId);
    const textInput = document.getElementById(cfg.textInputId);
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const dateModeSelect = cfg.dateModeId ? document.getElementById(cfg.dateModeId) : null;
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);
    const container = document.getElementById(cfg.containerId);

    const columnKey = colSelect ? colSelect.value : '';
    if (!columnKey) {
        if (summary) summary.innerHTML = '<span class="filter-no-match">Please select a column to filter by.</span>';
        return;
    }

    const isDate = (columnKey === 'expiry');
    const filterValue = isDate
        ? (dateInput ? dateInput.value : '')
        : (textInput ? textInput.value.trim() : '');

    if (!filterValue) {
        if (summary) summary.innerHTML = '<span class="filter-no-match">Please enter a value to search for.</span>';
        return;
    }

    const dateMode = dateModeSelect ? dateModeSelect.value : 'before';

    const cards = container ? container.querySelectorAll('.card') : [];
    let totalMatches = 0;
    let cardsWithMatches = 0;
    const termGroupsWithMatches = new Set();

    cards.forEach(card => {
        const cardId = card.querySelector('.card-header')?.dataset?.target;
        if (!cardId) return;

        const rawData = CardStore.getTrades(cardId);
        if (!rawData || !Array.isArray(rawData)) return;

        const matchingSymbols = new Set();
        rawData.forEach(item => {
            if (matchesDashboardFilter({ item, columnKey, filterValue, dateMode })) {
                matchingSymbols.add(getDashboardItemValue(item, 'ticker'));
            }
        });

        const rows = card.querySelectorAll('tbody .trade-row');
        let cardMatchCount = 0;

        rows.forEach(row => {
            const sym = (row.dataset.symbol || row.querySelector('td strong')?.textContent || '').toLowerCase();

            let isMatch;
            if (columnKey === 'expiry') {
                const rawItem = rawData.find(d => (d.symbol || '').toLowerCase() === sym);
                isMatch = rawItem ? matchesDashboardFilter({ item: rawItem, columnKey, filterValue, dateMode }) : false;
            } else {
                isMatch = matchingSymbols.has(sym);
            }

            row.classList.toggle('filter-hidden', !isMatch);
            row.classList.toggle('filter-match', isMatch);
            if (isMatch) cardMatchCount++;
        });

        const detailPanels = card.querySelectorAll('.trade-detail-panel');
        detailPanels.forEach(panel => panel.remove());

        const contentEl = document.getElementById(`content-${cardId}`);
        const arrowEl = document.getElementById(`arrow-${cardId}`);

        if (cardMatchCount > 0) {
            if (contentEl && !contentEl.classList.contains('open')) {
                contentEl.classList.add('open');
                if (arrowEl) arrowEl.classList.add('open');
            }
            
            const termGroupBody = card.closest('.term-group-body');
            if (termGroupBody) {
                termGroupsWithMatches.add(termGroupBody.id);
                if (termGroupBody.classList.contains('hidden')) {
                    termGroupBody.classList.remove('hidden');
                    const termArrowEl = document.getElementById(`arrow-${termGroupBody.id}`);
                    if (termArrowEl) termArrowEl.classList.add('open');
                }
            }

            totalMatches += cardMatchCount;
            cardsWithMatches++;
        } else {
            if (contentEl) contentEl.classList.remove('open');
            if (arrowEl) arrowEl.classList.remove('open');
        }
    });

    if (container) {
        container.querySelectorAll('.term-group-body').forEach(body => {
            if (!termGroupsWithMatches.has(body.id)) {
                body.classList.add('hidden');
                const arrowEl = document.getElementById(`arrow-${body.id}`);
                if (arrowEl) arrowEl.classList.remove('open');
            }
        });
        updateToggleAllTermsButtonState(container);
    }

    if (clearBtn) clearBtn.style.display = '';
    if (summary) {
        if (totalMatches === 0) {
            summary.innerHTML = `<span class="filter-no-match">No matches found for "${escapeHtmlContent(filterValue)}".</span>`;
        } else {
            const stratWord = cardsWithMatches === 1 ? 'strategy' : 'strategies';
            summary.innerHTML = `<span class="filter-match-count">${totalMatches} match${totalMatches !== 1 ? 'es' : ''}</span> across <span class="filter-match-count">${cardsWithMatches} ${stratWord}</span>.`;
        }
    }
}

function clearDashboardFilter(prefix) {
    const cfg = DASHBOARD_FILTER_CONFIG[prefix];
    if (!cfg) return;

    const textInput = document.getElementById(cfg.textInputId);
    const dateInput = cfg.dateInputId ? document.getElementById(cfg.dateInputId) : null;
    const clearBtn = document.getElementById(cfg.clearBtnId);
    const summary = document.getElementById(cfg.summaryId);
    const container = document.getElementById(cfg.containerId);

    if (textInput) textInput.value = '';
    if (dateInput) dateInput.value = '';
    if (clearBtn) clearBtn.style.display = 'none';
    if (summary) summary.innerHTML = '';

    if (container) {
        container.querySelectorAll('.trade-row.filter-hidden').forEach(row => {
            row.classList.remove('filter-hidden', 'filter-match');
        });
        container.querySelectorAll('.trade-row.filter-match').forEach(row => {
            row.classList.remove('filter-match');
        });

        container.querySelectorAll('.card').forEach(card => {
            const cardId = card.querySelector('.card-header')?.dataset?.target;
            if (!cardId) return;
            const contentEl = document.getElementById(`content-${cardId}`);
            const arrowEl = document.getElementById(`arrow-${cardId}`);
            if (contentEl) contentEl.classList.remove('open');
            if (arrowEl) arrowEl.classList.remove('open');
        });

        container.querySelectorAll('.term-group-body').forEach(body => {
            body.classList.add('hidden');
            const arrowEl = document.getElementById(`arrow-${body.id}`);
            if (arrowEl) arrowEl.classList.remove('open');
        });

        container.querySelectorAll('.trade-detail-panel').forEach(p => p.remove());
        container.querySelectorAll('.trade-row.selected').forEach(r => r.classList.remove('selected'));
        updateToggleAllTermsButtonState(container);
    }
}

if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    Object.assign(global, utils, authApi);
}

const DASHBOARD_EXPORTS = {
    CardStore,
    renderCardActionButtons,
    showDeleteConfirmationModal,
    deleteCardWithAnimation,
    updateExecuteAllButtonState,
    executeAllCustomCards,
    loadCustomCardsList,
    reexecuteSingleCustomCard,
    renderTemplateAccordionList,
    renderFilterDetailsSection,
    attachFilterDetailsToggle,
    getScreenerConfigFallback,
    TECH_FILTER_LABELS,
    TECH_CONDITION_LABELS,
    SCREENER_TYPE_LABELS,
    buildResultCard,
    renderTermGroups,
    computeTermDteRange,
    confirmDeleteCustomResult,
    deleteCustomResult,
    confirmDeleteCustomScreenerResult,
    deleteCustomScreenerResult,
    buildScreenerCard,
    buildScreenerTable,
    buildDropScreenerTable,
    buildTradeTable,
    handleTableSort,
    AVAILABLE_TRADE_COLUMNS,
    DEFAULT_STRATEGY_COLUMNS,
    loadStrategyColumnsConfig,
    getTradeColumns,
    getTradeCostSavings,
    injectTodayPerformance,
    fetchAndInjectTodayPerformance,
    renderOptionDataTable,
    initTradeRowClicks,
    showTradeHistoryModal,
    formatTechFilterValue,
    renderFilterGrid,
    renderTechFiltersGrid,
    renderFundamentalFiltersGrid,
    initDashboard,
    loadOptionsStrategies,
    loadStrategies,
    loadOptionsResults,
    loadResults,
    checkExecutionStatus,
    executeSelected,
    cancelExecution,
    fetchAndRenderMarketStatus,
    DASHBOARD_FILTER_CONFIG,
    showDashboardFilterBar,
    hideDashboardFilterBar,
    resetDashboardFilter,
    onFilterColumnChange,
    getDashboardItemValue,
    matchesDashboardFilter,
    applyDashboardFilter,
    clearDashboardFilter,
    updateToggleAllTermsButtonState,
    toggleAllTermGroups
};

registerModuleExports(DASHBOARD_EXPORTS, typeof module !== 'undefined' ? module : null);
