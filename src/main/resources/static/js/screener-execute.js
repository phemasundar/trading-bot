/**
 * Trading Bot — Technical Screeners Dashboard & Custom Execution
 * Screener dashboard management and custom technical screener execution.
 */

function escapeHtml(str) {
    if (typeof escapeHtmlContent === 'function') return escapeHtmlContent(str);
    return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const SCREENER_TYPE_META = {
    RSI_BB_BULLISH_CROSSOVER: { rsi: 'BULLISH_CROSSOVER', bollinger: 'LOWER_BAND', hasDrop: false },
    RSI_BB_BEARISH_CROSSOVER: { rsi: 'BEARISH_CROSSOVER', bollinger: 'UPPER_BAND', hasDrop: false },
    RSI_OVERSOLD:             { rsi: 'OVERSOLD',          bollinger: '',            hasDrop: false },
    BB_LOWER:                 { rsi: '',                  bollinger: 'LOWER_BAND',  hasDrop: false },
    BELOW_200_DAY_SMA:        { rsi: '',                  bollinger: '',            hasDrop: false },
    BELOW_200_DAY_MA:         { rsi: '',                  bollinger: '',            hasDrop: false },
    PRICE_DROP:               { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: true  },
    HIGH_52W_DROP:            { rsi: '',                  bollinger: '',            hasDrop: true,  hasLookback: false },
};

const CONDITION_OPERATORS = ['>=', '<=', '>', '<', '=='];

const MA_VARIABLES = [
    {
        value: 'PRICE',
        label: 'PRICE (Current Price)',
        placeholder: 'e.g. SMA50 or 150',
        allowCustom: true,
        predefinedValues: [
            { value: 'SMA20', label: 'SMA20 (20-day SMA)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' },
            { value: 'SMA100', label: 'SMA100 (100-day SMA)' },
            { value: 'SMA200', label: 'SMA200 (200-day SMA)' },
            { value: 'EMA9', label: 'EMA9 (9-day EMA)' },
            { value: 'EMA21', label: 'EMA21 (21-day EMA)' },
            { value: 'EMA50', label: 'EMA50 (50-day EMA)' },
            { value: 'BB_LOWER', label: 'BB_LOWER (Lower Bollinger Band)' },
            { value: 'BB_UPPER', label: 'BB_UPPER (Upper Bollinger Band)' }
        ]
    },
    {
        value: 'SMA20',
        label: 'SMA20 (20-day SMA)',
        placeholder: 'e.g. SMA50 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' },
            { value: 'SMA100', label: 'SMA100 (100-day SMA)' },
            { value: 'SMA200', label: 'SMA200 (200-day SMA)' },
            { value: 'EMA21', label: 'EMA21 (21-day EMA)' }
        ]
    },
    {
        value: 'SMA50',
        label: 'SMA50 (50-day SMA)',
        placeholder: 'e.g. SMA200 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'SMA20', label: 'SMA20 (20-day SMA)' },
            { value: 'SMA100', label: 'SMA100 (100-day SMA)' },
            { value: 'SMA200', label: 'SMA200 (200-day SMA)' }
        ]
    },
    {
        value: 'SMA100',
        label: 'SMA100 (100-day SMA)',
        placeholder: 'e.g. SMA200 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' },
            { value: 'SMA200', label: 'SMA200 (200-day SMA)' }
        ]
    },
    {
        value: 'SMA200',
        label: 'SMA200 (200-day SMA)',
        placeholder: 'e.g. SMA50 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' },
            { value: 'SMA100', label: 'SMA100 (100-day SMA)' }
        ]
    },
    {
        value: 'EMA9',
        label: 'EMA9 (9-day EMA)',
        placeholder: 'e.g. EMA21 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'EMA21', label: 'EMA21 (21-day EMA)' },
            { value: 'EMA50', label: 'EMA50 (50-day EMA)' },
            { value: 'SMA20', label: 'SMA20 (20-day SMA)' }
        ]
    },
    {
        value: 'EMA21',
        label: 'EMA21 (21-day EMA)',
        placeholder: 'e.g. EMA50 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'EMA9', label: 'EMA9 (9-day EMA)' },
            { value: 'EMA50', label: 'EMA50 (50-day EMA)' },
            { value: 'SMA20', label: 'SMA20 (20-day SMA)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' }
        ]
    },
    {
        value: 'EMA50',
        label: 'EMA50 (50-day EMA)',
        placeholder: 'e.g. SMA200 or PRICE',
        allowCustom: true,
        predefinedValues: [
            { value: 'PRICE', label: 'PRICE (Current Price)' },
            { value: 'EMA21', label: 'EMA21 (21-day EMA)' },
            { value: 'SMA50', label: 'SMA50 (50-day SMA)' },
            { value: 'SMA200', label: 'SMA200 (200-day SMA)' }
        ]
    }
];

const VOLUME_VARIABLES = [
    {
        value: 'VOLUME',
        label: 'VOLUME (Current Volume)',
        placeholder: 'e.g. 1000000 or VOLUME_SMA20',
        allowCustom: true,
        predefinedValues: [
            { value: '1000000', label: '1,000,000 (1M)' },
            { value: '500000', label: '500,000 (500K)' },
            { value: '2000000', label: '2,000,000 (2M)' },
            { value: 'VOLUME_SMA20', label: 'VOLUME_SMA20 (20-day Avg)' },
            { value: 'VOLUME_SMA50', label: 'VOLUME_SMA50 (50-day Avg)' }
        ]
    },
    {
        value: 'VOLUME_SMA20',
        label: 'VOLUME_SMA20 (20-day Avg Volume)',
        placeholder: 'e.g. VOLUME_SMA50 * 90% or 1000000',
        allowCustom: true,
        predefinedValues: [
            { value: 'VOLUME_SMA50', label: 'VOLUME_SMA50' },
            { value: 'VOLUME_SMA50 * 90%', label: 'VOLUME_SMA50 * 90%' },
            { value: 'VOLUME_SMA50 * 80%', label: 'VOLUME_SMA50 * 80%' },
            { value: '1000000', label: '1,000,000 (1M)' }
        ]
    },
    {
        value: 'VOLUME_SMA50',
        label: 'VOLUME_SMA50 (50-day Avg Volume)',
        placeholder: 'e.g. 1000000 or VOLUME_SMA20',
        allowCustom: true,
        predefinedValues: [
            { value: 'VOLUME_SMA20', label: 'VOLUME_SMA20' },
            { value: '1000000', label: '1,000,000 (1M)' }
        ]
    }
];

const HV_VARIABLES = [
    {
        value: 'HV_RANK',
        label: 'HV_RANK (Historical Volatility Rank 0-100)',
        placeholder: 'e.g. 25',
        allowCustom: true,
        predefinedValues: [
            { value: '25', label: '25 (Moderate)' },
            { value: '50', label: '50 (Median)' },
            { value: '75', label: '75 (High)' }
        ]
    },
    {
        value: 'HISTORICAL_VOLATILITY',
        label: 'HISTORICAL_VOLATILITY (HV %)',
        placeholder: 'e.g. 30',
        allowCustom: true,
        predefinedValues: [
            { value: '20', label: '20%' },
            { value: '30', label: '30%' },
            { value: '40', label: '40%' },
            { value: '50', label: '50%' }
        ]
    }
];

const PRICE_DROP_VARIABLES = [
    {
        value: 'DROP_PCT',
        label: 'DROP_PCT (Price Drop %)',
        placeholder: 'e.g. 3.0',
        allowCustom: true,
        predefinedValues: [
            { value: '2.0', label: '2.0%' },
            { value: '3.0', label: '3.0%' },
            { value: '5.0', label: '5.0%' },
            { value: '10.0', label: '10.0%' },
            { value: '20.0', label: '20.0%' }
        ]
    },
    {
        value: 'PRICE_DROP_FROM_HIGH_5D',
        label: 'PRICE_DROP_FROM_HIGH_5D ($ Drop from 5D High)',
        placeholder: 'e.g. 5.0'
    },
    {
        value: 'PRICE_DROP_FROM_HIGH_20D',
        label: 'PRICE_DROP_FROM_HIGH_20D ($ Drop from 20D High)',
        placeholder: 'e.g. 10.0'
    },
    {
        value: 'PRICE_DROP_FROM_HIGH_252D',
        label: 'PRICE_DROP_FROM_HIGH_252D ($ Drop from 52W High)',
        placeholder: 'e.g. 25.0'
    },
    {
        value: 'ATR_DROP_FROM_HIGH_5D',
        label: 'ATR_DROP_FROM_HIGH_5D (Drop from 5D High in ATR multiples)',
        placeholder: 'e.g. 2.0'
    },
    {
        value: 'ATR_DROP_FROM_HIGH_20D',
        label: 'ATR_DROP_FROM_HIGH_20D (Drop from 20D High in ATR multiples)',
        placeholder: 'e.g. 3.0'
    }
];

function parseConditionString(str, defaultVar = '') {
    if (!str || typeof str !== 'string') return null;
    str = str.trim();
    const match = str.match(/^([A-Za-z0-9_.]+)\s*(>=|<=|==|>|<|=)\s*(.+)$/);
    if (match) {
        let op = match[2].trim();
        if (op === '=') op = '==';
        return {
            variable: match[1].trim(),
            operator: op,
            value: match[3].trim()
        };
    }
    const unaryMatch = str.match(/^(>=|<=|==|>|<|=)\s*(.+)$/);
    if (unaryMatch && defaultVar) {
        let op = unaryMatch[1].trim();
        if (op === '=') op = '==';
        return {
            variable: defaultVar,
            operator: op,
            value: unaryMatch[2].trim()
        };
    }
    return null;
}

function createConditionValElement(varDef, prefillVal = '', customInputElement = null) {
    const predefined = varDef ? (varDef.predefinedValues || varDef.options) : null;
    const isAllowCustom = varDef && varDef.allowCustom === true;

    if (predefined && Array.isArray(predefined) && predefined.length > 0) {
        const valSelect = document.createElement('select');
        valSelect.className = 'form-select condition-val-input';

        const prefillStr = (prefillVal !== undefined && prefillVal !== null) ? String(prefillVal).trim() : '';
        const prefillUpper = prefillStr.toUpperCase();

        const hasMatch = predefined.some(item => {
            const val = typeof item === 'object' ? item.value : item;
            return String(val).toUpperCase() === prefillUpper;
        });

        predefined.forEach(item => {
            const opt = document.createElement('option');
            const val = typeof item === 'object' ? item.value : item;
            const label = typeof item === 'object' ? (item.label || item.value) : item;
            opt.value = val;
            opt.textContent = label;
            if (prefillUpper && String(val).toUpperCase() === prefillUpper) {
                opt.selected = true;
            }
            valSelect.appendChild(opt);
        });

        if (isAllowCustom) {
            const customOpt = document.createElement('option');
            customOpt.value = '__CUSTOM__';
            customOpt.textContent = '✏️ Custom...';
            valSelect.appendChild(customOpt);

            if (prefillStr && !hasMatch) {
                customOpt.selected = true;
                valSelect.value = '__CUSTOM__';
                if (customInputElement) {
                    customInputElement.value = prefillStr;
                    customInputElement.style.display = '';
                }
            }
        } else if (prefillStr && !hasMatch) {
            const customOpt = document.createElement('option');
            customOpt.value = prefillStr;
            customOpt.textContent = prefillStr;
            customOpt.selected = true;
            valSelect.appendChild(customOpt);
        }

        if (prefillUpper && hasMatch) {
            const matched = predefined.find(item => {
                const val = typeof item === 'object' ? item.value : item;
                return String(val).toUpperCase() === prefillUpper;
            });
            valSelect.value = typeof matched === 'object' ? matched.value : matched;
            if (customInputElement) {
                customInputElement.style.display = 'none';
            }
        }

        valSelect.addEventListener('change', () => {
            const cInp = customInputElement || (valSelect.parentElement ? valSelect.parentElement.querySelector('.condition-custom-input') : null);
            if (valSelect.value === '__CUSTOM__') {
                if (cInp) {
                    cInp.style.display = '';
                    cInp.placeholder = varDef && varDef.placeholder ? varDef.placeholder : 'e.g. 100 or SMA50 * 90%';
                    cInp.focus();
                }
            } else {
                if (cInp) {
                    cInp.style.display = 'none';
                }
            }
            if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(valSelect);
        });
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(valSelect);
        return valSelect;
    }

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-input condition-val-input';
    input.placeholder = varDef && varDef.placeholder ? varDef.placeholder : 'e.g. 25';
    if (prefillVal !== undefined && prefillVal !== null) {
        input.value = prefillVal;
    }
    if (customInputElement) {
        customInputElement.style.display = 'none';
    }
    return input;
}

function addConditionRow(containerId, variables, prefill = null) {
    const container = typeof containerId === 'string'
        ? (typeof document !== 'undefined' ? document.getElementById(containerId) : null)
        : containerId;
    if (!container) return null;

    const row = document.createElement('div');
    row.className = 'condition-row';

    const defaultVar = (variables && variables.length > 0) ? variables[0].value : '';
    const parsed = typeof prefill === 'string' ? parseConditionString(prefill, defaultVar) : prefill;

    // Variable Select
    const varSelect = document.createElement('select');
    varSelect.className = 'form-select condition-var-select';

    const prefillVar = parsed ? parsed.variable.toUpperCase() : '';
    const hasVar = variables && variables.some(v => v.value.toUpperCase() === prefillVar);
    if (prefillVar && !hasVar) {
        const customOpt = document.createElement('option');
        customOpt.value = parsed.variable;
        customOpt.textContent = parsed.variable;
        varSelect.appendChild(customOpt);
    }

    if (variables) {
        variables.forEach(v => {
            const opt = document.createElement('option');
            opt.value = v.value;
            opt.textContent = v.label;
            if (typeof FILTER_DESCRIPTIONS !== 'undefined' && FILTER_DESCRIPTIONS[v.value]) {
                opt.title = FILTER_DESCRIPTIONS[v.value];
            }
            if (prefillVar === v.value.toUpperCase()) {
                opt.selected = true;
            }
            varSelect.appendChild(opt);
        });
    }
    if (parsed && hasVar) {
        varSelect.value = variables.find(v => v.value.toUpperCase() === prefillVar).value;
    }

    // Info Button for Selected Variable
    const infoBtn = document.createElement('button');
    infoBtn.type = 'button';
    infoBtn.className = 'info-btn condition-info-btn';
    infoBtn.innerHTML = `<svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>`;

    const updateInfoBtn = () => {
        const selectedVal = varSelect.value;
        const vObj = variables && variables.find(v => v.value.toUpperCase() === selectedVal.toUpperCase());
        const label = vObj ? vObj.label : selectedVal;
        infoBtn.dataset.key = selectedVal;
        infoBtn.dataset.label = label;
        if (typeof FILTER_DESCRIPTIONS !== 'undefined' && FILTER_DESCRIPTIONS[selectedVal]) {
            infoBtn.title = FILTER_DESCRIPTIONS[selectedVal];
        } else {
            infoBtn.title = `${label} details`;
        }
    };

    infoBtn.onclick = (e) => {
        const key = infoBtn.dataset.key || varSelect.value;
        const label = infoBtn.dataset.label || key;
        showFilterHelp(e, key, label);
    };

    // Operator Select
    const opSelect = document.createElement('select');
    opSelect.className = 'form-select condition-op-select';

    const getOperatorsForVar = (varName) => {
        const vObj = variables && variables.find(v => v.value.toUpperCase() === (varName || '').toUpperCase());
        return (vObj && vObj.operators && vObj.operators.length > 0) ? vObj.operators : CONDITION_OPERATORS;
    };

    const populateOperators = (selectedOp = null) => {
        const ops = getOperatorsForVar(varSelect.value);
        opSelect.innerHTML = '';
        ops.forEach(op => {
            const opt = document.createElement('option');
            opt.value = op;
            opt.textContent = op;
            if (selectedOp && selectedOp === op) {
                opt.selected = true;
            }
            opSelect.appendChild(opt);
        });
        if (selectedOp && ops.includes(selectedOp)) {
            opSelect.value = selectedOp;
        } else if (ops.length > 0) {
            opSelect.value = ops[0];
        }
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(opSelect);
    };

    populateOperators(parsed ? parsed.operator : null);
    opSelect.addEventListener('change', () => {
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(opSelect);
    });

    const initialVarDef = variables && variables.find(v => v.value.toUpperCase() === varSelect.value.toUpperCase());
    const customInp = document.createElement('input');
    customInp.type = 'text';
    customInp.className = 'form-input condition-custom-input';
    customInp.placeholder = initialVarDef && initialVarDef.placeholder ? initialVarDef.placeholder : 'e.g. 100 or SMA50 * 90%';
    customInp.style.display = 'none';

    let currentValElement = createConditionValElement(initialVarDef, parsed ? parsed.value : '', customInp);

    const onVarChange = () => {
        updateInfoBtn();
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(varSelect);
        populateOperators();

        const selectedVal = varSelect.value;
        const newVarDef = variables && variables.find(v => v.value.toUpperCase() === selectedVal.toUpperCase());
        const existingValElem = row.querySelector('.condition-val-input');
        const prevVal = existingValElem ? (existingValElem.value === '__CUSTOM__' && customInp.value ? customInp.value : existingValElem.value) : '';

        const wasSelect = existingValElem && existingValElem.tagName === 'SELECT';
        const isSelect = !!(newVarDef && (newVarDef.predefinedValues || newVarDef.options));

        if (wasSelect !== isSelect || (isSelect && existingValElem)) {
            const newElem = createConditionValElement(newVarDef, prevVal, customInp);
            if (existingValElem && existingValElem !== newElem) {
                row.replaceChild(newElem, existingValElem);
                currentValElement = newElem;
            }
        } else if (existingValElem && !isSelect) {
            existingValElem.placeholder = newVarDef && newVarDef.placeholder ? newVarDef.placeholder : 'e.g. 25';
        }

        if (currentValElement && currentValElement.tagName === 'SELECT' && currentValElement.value === '__CUSTOM__') {
            customInp.style.display = '';
            customInp.placeholder = newVarDef && newVarDef.placeholder ? newVarDef.placeholder : 'e.g. 100 or SMA50 * 90%';
        } else {
            customInp.style.display = 'none';
        }
    };
    varSelect.addEventListener('change', onVarChange);
    updateInfoBtn();
    if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(varSelect);

    // Remove Button
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-ghost condition-remove-btn';
    removeBtn.innerHTML = '&times;';
    removeBtn.title = 'Remove condition';
    removeBtn.onclick = () => row.remove();

    row.appendChild(varSelect);
    row.appendChild(infoBtn);
    row.appendChild(opSelect);
    row.appendChild(currentValElement);
    row.appendChild(customInp);
    row.appendChild(removeBtn);

    container.appendChild(row);
    return row;
}

function getConditionsFromContainer(containerOrId) {
    const container = typeof containerOrId === 'string'
        ? (typeof document !== 'undefined' ? document.getElementById(containerOrId) : null)
        : containerOrId;
    if (!container) return [];
    const rows = container.querySelectorAll('.condition-row');
    const conditions = [];
    rows.forEach(row => {
        const varSel = row.querySelector('.condition-var-select');
        const opSel = row.querySelector('.condition-op-select');
        const valInp = row.querySelector('.condition-val-input');
        const customInp = row.querySelector('.condition-custom-input');
        if (varSel && opSel && valInp) {
            const v = varSel.value.trim();
            const op = opSel.value.trim();
            let val = valInp.value.trim();
            if (val === '__CUSTOM__' && customInp) {
                val = customInp.value.trim();
            }
            if (v && op && val) {
                conditions.push(`${v} ${op} ${val}`);
            }
        }
    });
    return conditions;
}

function fillTechFiltersForm(techFilters) {
    document.querySelectorAll('[data-tech-filter]').forEach(inp => {
        inp.value = '';
    });

    ['ma-conditions', 'volume-conditions', 'hv-conditions', 'priceDrop-conditions'].forEach(id => {
        const c = document.getElementById(id);
        if (c) c.innerHTML = '';
    });

    if (!techFilters) return;
    if (typeof techFilters === 'string' && typeof window !== 'undefined' && window.appConfig && window.appConfig.technicalFilters) {
        techFilters = window.appConfig.technicalFilters[techFilters] || {};
    }
    if (typeof techFilters !== 'object') return;

    for (const [filterKey, val] of Object.entries(techFilters)) {
        if (filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'EXP_MOVING_AVERAGE') {
            const conds = (val && typeof val === 'object' && val.conditions) ? val.conditions : (Array.isArray(val) ? val : (typeof val === 'string' ? [val] : []));
            const maContainer = document.getElementById('ma-conditions');
            if (maContainer && Array.isArray(conds)) {
                conds.forEach(cond => {
                    if (cond && typeof cond === 'string') addConditionRow('ma-conditions', MA_VARIABLES, cond);
                });
            }
            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="rules"]`);
            if (el) {
                const rulesStr = Array.isArray(conds) ? conds.join(', ') : conds;
                el.value = rulesStr;
            }
            if (filterKey === 'SIMPLE_MOVING_AVERAGE') continue;
        }

        if (filterKey === 'VOLUME') {
            const conds = (val && typeof val === 'object' && val.conditions) ? val.conditions : (Array.isArray(val) ? val : null);
            const volContainer = document.getElementById('volume-conditions');
            if (volContainer && Array.isArray(conds)) {
                conds.forEach(cond => {
                    if (typeof cond === 'string') addConditionRow('volume-conditions', VOLUME_VARIABLES, cond);
                });
            }
            const el = document.querySelector(`[data-tech-filter="VOLUME"][data-tech-field="rules"]`);
            if (el && conds) {
                const rulesStr = Array.isArray(conds) ? conds.map(c => typeof c === 'string' ? c : '').filter(Boolean).join(', ') : conds;
                if (rulesStr) el.value = rulesStr;
            }
        }

        if (filterKey === 'HISTORICAL_VOLATILITY') {
            const conds = (val && typeof val === 'object' && val.conditions) ? val.conditions : (Array.isArray(val) ? val : null);
            const hvContainer = document.getElementById('hv-conditions');
            if (hvContainer && Array.isArray(conds)) {
                conds.forEach(cond => {
                    if (typeof cond === 'string') addConditionRow('hv-conditions', HV_VARIABLES, cond);
                });
            }
            const el = document.querySelector(`[data-tech-filter="HISTORICAL_VOLATILITY"][data-tech-field="rules"]`);
            if (el && conds) {
                const rulesStr = Array.isArray(conds) ? conds.join(', ') : conds;
                if (rulesStr) el.value = rulesStr;
            }
        }

        if (filterKey === 'PRICE_DROP') {
            const conds = (val && typeof val === 'object' && val.conditions) ? val.conditions : (Array.isArray(val) ? val : null);
            const dropContainer = document.getElementById('priceDrop-conditions');
            if (dropContainer && Array.isArray(conds)) {
                conds.forEach(cond => {
                    if (typeof cond === 'string') addConditionRow('priceDrop-conditions', PRICE_DROP_VARIABLES, cond);
                });
            }
            const el = document.querySelector(`[data-tech-filter="PRICE_DROP"][data-tech-field="rules"]`);
            if (el && conds) {
                const rulesStr = Array.isArray(conds) ? conds.join(', ') : conds;
                if (rulesStr) el.value = rulesStr;
            }
        }

        if (val && typeof val === 'object') {
            for (const [fieldKey, fieldVal] of Object.entries(val)) {
                if (fieldKey === 'condition') {
                    if (typeof fieldVal === 'string') {
                        const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="condition"]`);
                        if (el) el.value = fieldVal;
                    } else if (typeof fieldVal === 'object') {
                        for (const [condKey, condVal] of Object.entries(fieldVal)) {
                            const mappedKey = condKey === 'type' ? 'condition' : condKey;
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${mappedKey}"]`);
                            if (el) {
                                el.value = condVal;
                                if (mappedKey === 'condition' && typeof el.onchange === 'function') {
                                    el.onchange();
                                }
                            }
                        }
                    }
                } else if (fieldKey === 'config') {
                    if (typeof fieldVal === 'object') {
                        for (const [cfgKey, cfgVal] of Object.entries(fieldVal)) {
                            const el = document.querySelector(`[data-tech-filter="${filterKey}"][data-tech-field="${cfgKey}"]`);
                            if (el) el.value = cfgVal;
                        }
                    }
                }
            }
        }
    }
}

// ── Screeners Dashboard (screeners.html) ──

async function initScreenerDashboard() {
    const authed = await initAuth();
    if (!authed) return;
    await loadScreenerStrategies();
    await loadScreenerResults();
    await checkScreenerExecutionStatus();
    fetchAndRenderMarketStatus();
}

async function loadScreenerStrategies() {
    const screenerContainer = document.getElementById('screener-checkboxes');
    if (!screenerContainer) return;

    try {
        const screeners = await API.get('/api/screeners');
        if (screeners.length === 0) {
            screenerContainer.innerHTML = `<span class="text-muted">No screeners configured</span>`;
        } else {
            screenerContainer.innerHTML = screeners.map(s => {
                const infoBtn = s.descriptionFile
                    ? `<button type="button" class="info-btn" style="margin-left: 4px;" onclick="showInfo(event, '${s.descriptionFile}', '${escapeAttr(s.name)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
                    : '';
                return `
                <div class="flex items-center" style="margin-bottom: 8px;">
                    <label class="checkbox-label" style="margin: 0;">
                        <input type="checkbox" value="${s.index}" data-type="screener">
                        <span>${s.name}</span>
                    </label>
                    ${infoBtn}
                </div>`;
            }).join('');
        }
        const badge = document.getElementById('screener-count-badge');
        if (badge) badge.textContent = `(${screeners.length})`;
    } catch (e) {
        screenerContainer.innerHTML = `<span class="text-muted">Failed to load screeners</span>`;
    }
}

async function loadScreenerResults() {
    const screenerContainer = document.getElementById('screener-results-container');
    if (!screenerContainer) return;

    resetDashboardFilter('screeners');

    try {
        const screenerResults = await API.get('/api/results/screeners').catch(() => []);
        screenerContainer.innerHTML = '';
        if (!screenerResults || screenerResults.length === 0) {
            screenerContainer.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔎</div>No technical screener results yet. Execute a screener to see results.</div>';
            hideDashboardFilterBar('screeners');
        } else {
            for (const r of screenerResults) {
                screenerContainer.appendChild(buildScreenerCard(r));
            }
            showDashboardFilterBar('screeners');
        }
    } catch (e) {
        screenerContainer.innerHTML = `<div class="empty-state text-danger">Failed to load screener results: ${e.message}</div>`;
        hideDashboardFilterBar('screeners');
    }
}

async function checkScreenerExecutionStatus() {
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
                loadScreenerResults();
                showToast('Execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

async function executeScreenersSelected() {
    const checkedScreeners = document.querySelectorAll('#screener-checkboxes input[type="checkbox"]:checked');
    const screenerIndices = Array.from(checkedScreeners).map(c => parseInt(c.value));
    if (screenerIndices.length === 0) {
        showToast('Select at least one screener', 'error');
        return;
    }
    try {
        setDashboardBusy(true);
        const res = await API.post('/api/execute', { strategyIndices: [], screenerIndices });
        showToast(res.message);
        startTimer(Date.now());
        startPolling(() => {
            setDashboardBusy(false);
            loadScreenerResults();
            showToast('Screener execution completed!');
        });
    } catch (e) {
        setDashboardBusy(false);
        showToast(e.message, 'error');
    }
}

// ── Custom Screener Execution Page (execute-screener.html) ──

async function initExecuteScreenerPage() {
    const authed = await initAuth();
    if (!authed) return;
    if (typeof loadFilterDescriptions === 'function') {
        await loadFilterDescriptions();
    }
    await loadCustomScreenerResults();
    await checkCustomScreenerExecutionStatus();
    
    const select = document.getElementById('screener-type');
    if (select && select.value) {
        onScreenerTypeChange();
    }

    document.querySelectorAll('.condition-row .form-select').forEach(sel => {
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        sel.addEventListener('change', () => {
            if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        });
    });

    window.addEventListener('resize', () => {
        document.querySelectorAll('.condition-row .form-select').forEach(sel => {
            if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
        });
    });
}


function onScreenerTypeChange() {
    const type = document.getElementById('screener-type').value;
    const meta = SCREENER_TYPE_META[type];

    const infoBtn = document.getElementById('screener-type-info-btn');
    if (infoBtn) {
        const sel = document.getElementById('screener-type');
        const label = type && sel.selectedOptions[0] ? sel.selectedOptions[0].text : 'Screener Type';
        infoBtn.title = (typeof FILTER_DESCRIPTIONS !== 'undefined' && FILTER_DESCRIPTIONS[type])
            ? FILTER_DESCRIPTIONS[type]
            : `${label} details`;
        if (typeof autoAdjustSelectWidth === 'function') autoAdjustSelectWidth(sel);
    }

    const dropGroup = document.getElementById('sc-priceDropRules-group');
    const lookbackGroup = document.getElementById('sc-lookbackDays-group');
    if (dropGroup) dropGroup.style.display = (meta && meta.hasDrop) ? '' : 'none';
    if (lookbackGroup) lookbackGroup.style.display = (meta && meta.hasLookback) ? '' : 'none';

    // Clear condition containers when switching screener types
    const maContainer = document.getElementById('ma-conditions');
    const dropContainer = document.getElementById('priceDrop-conditions');
    if (maContainer) maContainer.innerHTML = '';
    if (dropContainer) dropContainer.innerHTML = '';

    if (!meta) {
        renderScreenerTemplates('');
        return;
    }

    const rsiSel = document.getElementById('execute-rsiCondition') || document.getElementById('sc-rsiCondition');
    const bbSel  = document.getElementById('execute-bollingerCondition') || document.getElementById('sc-bollingerCondition') || document.querySelector('[data-tech-filter="BOLLINGER_BAND"][data-tech-field="condition"]');
    if (rsiSel && meta.rsi !== undefined) {
        rsiSel.value = meta.rsi;
        if (typeof rsiSel.onchange === 'function') rsiSel.onchange();
    }
    if (bbSel  && meta.bollinger !== undefined) bbSel.value = meta.bollinger;

    // Pre-seed default conditions based on selected screener type
    if (type === 'BELOW_200_DAY_SMA' || type === 'BELOW_200_DAY_MA') {
        if (maContainer) addConditionRow('ma-conditions', MA_VARIABLES, 'PRICE < SMA200');
    } else if (type === 'PRICE_DROP') {
        if (dropContainer) addConditionRow('priceDrop-conditions', PRICE_DROP_VARIABLES, 'DROP_PCT >= 3.0');
        const lookbackInput = document.getElementById('sc-lookbackDays');
        if (lookbackInput) lookbackInput.value = '0';
    } else if (type === 'HIGH_52W_DROP') {
        if (dropContainer) addConditionRow('priceDrop-conditions', PRICE_DROP_VARIABLES, 'DROP_PCT >= 20.0');
    }

    renderScreenerTemplates(type);
}

async function renderScreenerTemplates(screenerType) {
    const container = document.getElementById('screener-templates');
    if (!container) return;

    if (!window.appConfig) {
        try {
            window.appConfig = await API.get('/api/config');
        } catch (e) {
            container.innerHTML = '';
            return;
        }
    }

    const screeners = (window.appConfig && window.appConfig.technicalScreeners) || [];
    const matching = screeners.filter(s => s.screenerType === screenerType);

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

    matching.forEach(screener => {
        const card = document.createElement('div');
        card.className = 'config-card';
        card.style.marginBottom = '8px';

        const enabledPill = screener.enabled
            ? '<span class="pill pill-enabled">Enabled</span>'
            : '<span class="pill pill-disabled">Disabled</span>';

        const loadBtn = `<button type="button" class="btn btn-primary" style="padding: 2px 8px; font-size: 0.75rem; margin-left: auto;" onclick="loadScreenerTemplateParams('${escapeAttr(JSON.stringify(screener))}')">Load Filters</button>`;

        const infoBtn = screener.descriptionFile
            ? `<button type="button" class="info-btn" onclick="showInfo(event, '${screener.descriptionFile}', '${escapeAttr(screener.alias || screener.screenerType)}')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button>`
            : '';

        card.innerHTML = `
            <div class="config-card-header">
                <div class="flex items-center gap-sm flex-wrap" style="width: 100%;">
                    <span class="card-arrow">▶</span>
                    <strong>${screener.alias || screener.screenerType}</strong>
                    ${infoBtn}
                    <span class="card-badge">${screener.screenerType} <button type="button" class="info-btn" style="font-size: 0.7rem; padding: 0; color: inherit" onclick="showFilterHelp(event, 'screenerType', 'Screener Type')"><svg class="info-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg></button></span>
                    <span class="card-badge">${screener.securitiesFile || 'Custom'}</span>
                    ${enabledPill}
                    ${loadBtn}
                </div>
            </div>
            <div class="config-card-body">
                ${renderTechFiltersGrid(screener.technicalFilters)}
                ${screener.securities ? `<div class="mt-sm"><span class="config-item-label">Securities (Inline)</span> <span class="config-item-value">${screener.securities}</span></div>` : ''}
            </div>`;

        card.querySelector('.config-card-header').addEventListener('click', function (e) {
            if (e.target.tagName === 'BUTTON') return;
            this.querySelector('.card-arrow').classList.toggle('open');
            this.nextElementSibling.classList.toggle('open');
        });

        container.appendChild(card);
    });
}

function loadScreenerTemplateParams(screenerJson) {
    try {
        const screener = JSON.parse(decodeAttr(screenerJson));

        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) aliasEl.value = (screener.alias || '') + ' (Custom)';

        const secInput = document.getElementById('screener-securities-input');
        if (secInput) secInput.value = screener.securities || '';

        const secFileInput = document.getElementById('screener-securities-file-input');
        if (secFileInput) secFileInput.value = screener.securitiesFile || '';

        const techFilters = screener.technicalFilters || {};
        fillTechFiltersForm(techFilters);

        // Fallback for elements using legacy sc-* IDs (e.g. tests or custom markup)
        const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.value = val !== undefined && val !== null ? val : ''; };
        if (techFilters.RSI) {
            setVal('sc-rsiCondition', techFilters.RSI.condition || techFilters.RSI);
            setVal('execute-rsiCondition', techFilters.RSI.condition || techFilters.RSI);
        }
        if (techFilters.BOLLINGER_BAND) {
            setVal('sc-bollingerCondition', techFilters.BOLLINGER_BAND.condition || techFilters.BOLLINGER_BAND);
            setVal('execute-bollingerCondition', techFilters.BOLLINGER_BAND.condition || techFilters.BOLLINGER_BAND);
        }

        showToast('Template filters loaded!');
    } catch (e) {
        console.error('Error loading screener template:', e);
        showToast('Error loading template', 'error');
    }
}

function loadScreenerFiltersFromResult(paramOrBtn, eventOrReexecute = false) {
    let params;
    let isReexecute = false;
    let screenerName = '';

    if (paramOrBtn && (paramOrBtn.dataset || paramOrBtn.nodeType)) {
        const btn = paramOrBtn;
        isReexecute = eventOrReexecute === true;
        const rawParams = decodeAttr(btn.dataset.requestParams || btn.dataset.filterConfig || '');
        screenerName = decodeAttr(btn.dataset.screenerName || btn.dataset.strategyName || '');
        try {
            params = typeof rawParams === 'string' ? JSON.parse(rawParams) : rawParams;
        } catch (e) {
            params = null;
        }
    } else if (typeof paramOrBtn === 'string') {
        if (eventOrReexecute && eventOrReexecute.stopPropagation) eventOrReexecute.stopPropagation();
        isReexecute = false;
        try {
            params = JSON.parse(decodeAttr(paramOrBtn));
        } catch (e) {
            params = null;
        }
    } else if (paramOrBtn && typeof paramOrBtn === 'object') {
        if (eventOrReexecute && eventOrReexecute.stopPropagation) eventOrReexecute.stopPropagation();
        isReexecute = eventOrReexecute === true;
        params = paramOrBtn;
    }

    try {
        if (!params) throw new Error('No screener parameters found');

        const typeEl = document.getElementById('screener-type');
        if (typeEl && params.screenerType) {
            typeEl.value = params.screenerType;
            onScreenerTypeChange();
        }

        const aliasEl = document.getElementById('screener-alias-input');
        if (aliasEl) {
            const rawName = params.alias !== undefined ? params.alias : (screenerName || '');
            if (isReexecute) {
                aliasEl.value = rawName ? rawName.replace(/\s*\(Reload\)$/, '') : '';
            } else {
                aliasEl.value = rawName || '';
            }
        }

        const secFileEl = document.getElementById('screener-securities-file-input');
        if (secFileEl && params.securitiesFile !== undefined) secFileEl.value = params.securitiesFile || '';

        const secEl = document.getElementById('screener-securities-input');
        if (secEl && params.securities !== undefined) secEl.value = params.securities || '';

        fillTechFiltersForm(params.technicalFilters);

        if (!isReexecute) {
            const firstCard = document.querySelector('.main-content .card');
            if (firstCard) firstCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
            showToast('Filters loaded from previous execution. Verify inputs before running.');
        }
    } catch (e) {
        console.error('loadScreenerFiltersFromResult error:', e);
        showToast('Failed to load filters', 'error');
    }
}

async function reexecuteCustomScreener(btn, screenerId) {
    if (!screenerId) return;

    const progress = document.getElementById('screener-custom-progress');
    if (progress && (progress.classList.contains('active') || progress.style.display === 'block')) {
        showToast('An execution is already in progress', 'error');
        return;
    }

    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '⏳ Executing...';

    try {
        loadScreenerFiltersFromResult(btn, true);
        const success = await executeCustomScreener(screenerId);
        if (success === false) {
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    } catch (e) {
        console.error('Error re-executing custom screener:', e);
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

function getCustomScreenerPayload(customResultId = null) {
    const type = document.getElementById('screener-type')?.value;
    if (!type) return null;

    const alias            = document.getElementById('screener-alias-input')?.value.trim() || null;
    const securitiesFile   = document.getElementById('screener-securities-file-input')?.value.trim() || null;
    const securities       = document.getElementById('screener-securities-input')?.value.trim() || null;

    if (!securitiesFile && !securities) return null;

    let technicalFilters;
    try {
        const container = document.getElementById('screener-tech-filters-container');
        technicalFilters = getTechnicalFiltersFromDOM(container || document);
    } catch (e) {
        return null;
    }

    const payload = {
        screenerType: type,
        alias,
        securitiesFile,
        securities,
        technicalFilters
    };

    if (customResultId) {
        payload.customResultId = Number(customResultId);
    }

    Object.keys(payload).forEach(k => {
        if (payload[k] === null || payload[k] === false || payload[k] === '') delete payload[k];
    });

    return payload;
}

async function executeCustomScreener(customResultId = null, isBatch = false) {
    const type = document.getElementById('screener-type')?.value;
    if (!type) { showToast('Select a screener type', 'error'); return false; }

    const securitiesFile = document.getElementById('screener-securities-file-input')?.value.trim() || null;
    const securities     = document.getElementById('screener-securities-input')?.value.trim() || null;

    if (!securitiesFile && !securities) {
        showToast('Provide a securities file or tickers', 'error');
        return false;
    }

    const payload = getCustomScreenerPayload(customResultId);
    if (!payload) {
        showToast('Failed to build screener parameters', 'error');
        return false;
    }

    try {
        setCustomScreenerBusy(true);
        const res = await API.post('/api/execute/custom-screener', payload);
        showToast(res.message);
        startTimer(Date.now());
        if (isBatch) {
            await pollUntilComplete();
            return true;
        }
        startPolling(() => {
            setCustomScreenerBusy(false);
            stopTimer();
            loadCustomScreenerResults(customResultId);
            showToast('Screener execution completed!');
        });
        return true;
    } catch (e) {
        setCustomScreenerBusy(false);
        showToast(e.message, 'error');
        if (isBatch) throw e;
        return false;
    }
}

function getTechnicalFiltersFromDOM(container = document) {
    const technicalFilters = {};
    container.querySelectorAll('[data-tech-filter]').forEach(input => {
        const filterKey = input.dataset.techFilter;
        const fieldKey = input.dataset.techField;
        const rawVal = (input.tagName === 'SELECT' ? input.value : input.value.trim());
        if (!rawVal) return;

        if (!technicalFilters[filterKey]) technicalFilters[filterKey] = {};

        if ((filterKey === 'SIMPLE_MOVING_AVERAGE' || filterKey === 'VOLUME' || filterKey === 'HISTORICAL_VOLATILITY' || filterKey === 'PRICE_DROP') && fieldKey === 'rules') {
            technicalFilters[filterKey] = { conditions: rawVal.split(',').map(s => s.trim()).filter(Boolean) };
        } else if (fieldKey === 'condition') {
            if (typeof technicalFilters[filterKey].condition === 'object') {
                technicalFilters[filterKey].condition.type = rawVal;
            } else {
                technicalFilters[filterKey].condition = rawVal;
            }
        } else if (fieldKey === 'period') {
            const num = parseInt(rawVal);
            if (!isNaN(num)) {
                if (!technicalFilters[filterKey].config) technicalFilters[filterKey].config = {};
                technicalFilters[filterKey].config.period = num;
            }
        } else if (['min', 'max', 'lookbackDays'].includes(fieldKey)) {
            const num = parseFloat(rawVal);
            if (!isNaN(num)) {
                if (filterKey === 'VOLUME') {
                    if (!technicalFilters[filterKey].conditions) {
                        technicalFilters[filterKey].conditions = [ { type: 'MIN_VOLUME' } ];
                    }
                    technicalFilters[filterKey].conditions[0][fieldKey] = num;
                } else if (fieldKey === 'lookbackDays') {
                    if (!technicalFilters[filterKey].config) technicalFilters[filterKey].config = {};
                    technicalFilters[filterKey].config.lookbackDays = num;
                } else {
                    if (!technicalFilters[filterKey].condition || typeof technicalFilters[filterKey].condition === 'string') {
                        const existingType = typeof technicalFilters[filterKey].condition === 'string' ? technicalFilters[filterKey].condition : null;
                        technicalFilters[filterKey].condition = {};
                        if (existingType) technicalFilters[filterKey].condition.type = existingType;
                    }
                    technicalFilters[filterKey].condition[fieldKey] = num;
                }
            }
        }
    });

    const extractConditionsFromEl = (el) => {
        return getConditionsFromContainer(el);
    };

    const checkContainerConditions = (containerId, filterKey) => {
        const el = (container && container.querySelector) ? container.querySelector('#' + containerId) : (typeof document !== 'undefined' ? document.getElementById(containerId) : null);
        if (el) {
            const conditions = extractConditionsFromEl(el);
            if (conditions.length > 0) {
                if (!technicalFilters[filterKey]) technicalFilters[filterKey] = {};
                technicalFilters[filterKey].conditions = conditions;
            }
        }
    };

    checkContainerConditions('ma-conditions', 'SIMPLE_MOVING_AVERAGE');
    checkContainerConditions('volume-conditions', 'VOLUME');
    checkContainerConditions('hv-conditions', 'HISTORICAL_VOLATILITY');
    checkContainerConditions('priceDrop-conditions', 'PRICE_DROP');

    if (Object.keys(technicalFilters).length === 0) {
        return undefined;
    }

    if (technicalFilters.RSI && technicalFilters.RSI.condition && technicalFilters.RSI.condition.type === 'CUSTOM_RANGE') {
        if (technicalFilters.RSI.condition.min === undefined || technicalFilters.RSI.condition.max === undefined) {
            throw new Error('Min RSI and Max RSI are mandatory for Custom Range condition.');
        }
    }

    return technicalFilters;
}

async function loadCustomScreenerResults(updatedResultId = null) {
    const container = document.getElementById('screener-custom-results');
    if (!container) return;
    try {
        const openCardIds = new Set();
        container.querySelectorAll('.card-content.open').forEach(el => {
            const id = el.id.replace(/^content-/, '');
            openCardIds.add(id);
        });

        const results = await API.get('/api/results/custom/screeners');
        container.innerHTML = '';
        if (typeof updateExecuteAllButtonState === 'function') {
            updateExecuteAllButtonState({
                btnId: 'execute-all-screener-btn',
                countBadgeId: 'screener-custom-results-count-badge',
                count: results ? results.length : 0
            });
        }
        if (!results || results.length === 0) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔬</div>No screener results yet. Run a custom screener above.</div>';
            return;
        }
        for (const r of results) {
            const cardEl = buildScreenerCard(r, true);
            container.appendChild(cardEl);
            const cardId = (r.screenerId || '').replace(/\s+/g, '-');
            const shouldOpen = openCardIds.has(cardId) || (updatedResultId && String(r.screenerId) === String(updatedResultId));
            if (shouldOpen && r.results && r.results.length > 0) {
                const content = cardEl.querySelector(`[id="content-${cardId}"]`);
                const arrow = cardEl.querySelector(`[id="arrow-${cardId}"]`);
                if (content) content.classList.add('open');
                if (arrow) arrow.classList.add('open');
            }
        }
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Failed to load results: ${e.message}</div>`;
        if (typeof updateExecuteAllButtonState === 'function') {
            updateExecuteAllButtonState({
                btnId: 'execute-all-screener-btn',
                countBadgeId: 'screener-custom-results-count-badge',
                count: 0
            });
        }
    }
}

function reexecuteAllCustomScreeners() {
    return executeAllCustomCards({
        containerId: 'screener-custom-results',
        executeAllBtnId: 'execute-all-screener-btn',
        cardBtnSelector: 'button[onclick*="reexecuteCustomScreener"]',
        batchApiEndpoint: '/api/execute/custom-screener/batch',
        buildRequestFn: (card, execBtn) => {
            loadScreenerFiltersFromResult(execBtn, true);
            const onclickAttr = execBtn.getAttribute('onclick') || '';
            const match = onclickAttr.match(/reexecuteCustomScreener\(\s*this\s*,\s*['"]?([^'")]+)['"]?\s*\)/);
            const customResultId = match ? match[1] : null;
            return getCustomScreenerPayload(customResultId);
        },
        loadFiltersFn: (btn) => loadScreenerFiltersFromResult(btn, true),
        executeFn: (id) => executeCustomScreener(id, true),
        reloadResultsFn: () => loadCustomScreenerResults(),
        entityNameSingular: 'custom screener',
        entityNamePlural: 'custom screeners',
        isProgressActiveFn: () => {
            const p = document.getElementById('screener-custom-progress');
            return p && (p.style.display === 'block' || p.classList.contains('active'));
        }
    });
}

async function checkCustomScreenerExecutionStatus() {
    try {
        const status = await API.get('/api/status');
        if (status.alerts && status.alerts.length > 0) showErrorPanel(status.alerts);
        if (status.running) {
            window.currentExecutionTaskName = status.currentTask || '';
            setCustomScreenerBusy(true);
            const execAllBtn = document.getElementById('execute-all-screener-btn');
            if (execAllBtn) {
                execAllBtn.disabled = true;
                execAllBtn.innerHTML = '⏳ Executing...';
            }
            document.querySelectorAll('button[onclick*="reexecuteCustomScreener"]').forEach(b => b.disabled = true);
            startTimer(status.startTimeMs);
            startPolling(() => {
                setCustomScreenerBusy(false);
                stopTimer();
                if (execAllBtn) {
                    execAllBtn.disabled = false;
                    execAllBtn.innerHTML = '▶ Execute All';
                }
                document.querySelectorAll('button[onclick*="reexecuteCustomScreener"]').forEach(b => b.disabled = false);
                loadCustomScreenerResults();
                showToast('Screener execution completed!');
            });
        }
    } catch (e) { /* ignore */ }
}

function setCustomScreenerBusy(busy) {
    const progress = document.getElementById('screener-custom-progress');
    if (progress) progress.style.display = busy ? 'block' : 'none';
}

if (typeof window !== 'undefined') {
    window.SCREENER_TYPE_META = SCREENER_TYPE_META;
    window.CONDITION_OPERATORS = CONDITION_OPERATORS;
    window.MA_VARIABLES = MA_VARIABLES;
    window.VOLUME_VARIABLES = VOLUME_VARIABLES;
    window.HV_VARIABLES = HV_VARIABLES;
    window.PRICE_DROP_VARIABLES = PRICE_DROP_VARIABLES;
    window.parseConditionString = parseConditionString;
    window.createConditionValElement = createConditionValElement;
    window.addConditionRow = addConditionRow;
    window.getConditionsFromContainer = getConditionsFromContainer;
    window.fillTechFiltersForm = fillTechFiltersForm;
    window.initScreenerDashboard = initScreenerDashboard;
    window.loadScreenerStrategies = loadScreenerStrategies;
    window.loadScreenerResults = loadScreenerResults;
    window.checkScreenerExecutionStatus = checkScreenerExecutionStatus;
    window.executeScreenersSelected = executeScreenersSelected;
    window.initExecuteScreenerPage = initExecuteScreenerPage;
    window.onScreenerTypeChange = onScreenerTypeChange;
    window.renderScreenerTemplates = renderScreenerTemplates;
    window.loadScreenerTemplateParams = loadScreenerTemplateParams;
    window.loadScreenerFiltersFromResult = loadScreenerFiltersFromResult;
    window.reexecuteCustomScreener = reexecuteCustomScreener;
    window.reexecuteAllCustomScreeners = reexecuteAllCustomScreeners;
    window.getCustomScreenerPayload = getCustomScreenerPayload;
    window.executeCustomScreener = executeCustomScreener;
    window.getTechnicalFiltersFromDOM = getTechnicalFiltersFromDOM;
    window.loadCustomScreenerResults = loadCustomScreenerResults;
    window.checkCustomScreenerExecutionStatus = checkCustomScreenerExecutionStatus;
    window.setCustomScreenerBusy = setCustomScreenerBusy;
}

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./utils');
    const authApi = require('./auth-api');
    const dashboard = require('./dashboard');
    Object.assign(global, utils, authApi, dashboard);

    module.exports = {
        SCREENER_TYPE_META,
        CONDITION_OPERATORS,
        MA_VARIABLES,
        VOLUME_VARIABLES,
        HV_VARIABLES,
        PRICE_DROP_VARIABLES,
        parseConditionString,
        createConditionValElement,
        addConditionRow,
        getConditionsFromContainer,
        fillTechFiltersForm,
        initScreenerDashboard,
        loadScreenerStrategies,
        loadScreenerResults,
        checkScreenerExecutionStatus,
        executeScreenersSelected,
        initExecuteScreenerPage,
        onScreenerTypeChange,
        renderScreenerTemplates,
        loadScreenerTemplateParams,
        loadScreenerFiltersFromResult,
        reexecuteCustomScreener,
        reexecuteAllCustomScreeners,
        getCustomScreenerPayload,
        executeCustomScreener,
        getTechnicalFiltersFromDOM,
        loadCustomScreenerResults,
        checkCustomScreenerExecutionStatus,
        setCustomScreenerBusy
    };
}
