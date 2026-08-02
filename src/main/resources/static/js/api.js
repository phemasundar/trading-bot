/**
 * Trading Bot — API Client Wrapper
 */
const API = {
    _accessToken: null,

    async _fetch(url, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };
        if (this._accessToken) {
            headers['Authorization'] = `Bearer ${this._accessToken}`;
        }
        const res = await fetch(url, { ...options, headers });
        if (res.status === 401) {
            window.location.href = '/login.html';
            throw new Error('Unauthorized');
        }
        return res;
    },

    async get(url) {
        const res = await this._fetch(url);
        return res.json();
    },

    async post(url, body) {
        const res = await this._fetch(url, {
            method: 'POST',
            body: JSON.stringify(body)
        });
        return res.json();
    },

    async delete(url) {
        const res = await this._fetch(url, {
            method: 'DELETE'
        });
        return res.json();
    },

    async getStrategies() { return this.get('/api/strategies'); },
    async getScreeners() { return this.get('/api/screeners'); },
    async getLatestResults() { return this.get('/api/results'); },
    async getScreenerResults() { return this.get('/api/results/screeners'); },
    async getRecentCustomResults() { return this.get('/api/results/custom'); },
    async getRecentCustomScreenerResults() { return this.get('/api/results/custom/screeners'); },
    async getStatus() { return this.get('/api/status'); },
    async getMarketStatus() { return this.get('/api/market-status'); },
    async executeStrategies(strategyIndices, screenerIndices) {
        return this.post('/api/execute', { strategyIndices, screenerIndices });
    },
    async executeCustomStrategy(payload) {
        return this.post('/api/execute/custom', payload);
    },
    async executeCustomScreener(payload) {
        return this.post('/api/execute/custom-screener', payload);
    },
    async cancelExecution() { return this.post('/api/cancel'); },
    async clearErrors() { return this.post('/api/clear-errors'); },
    async getSecuritiesMaps() { return this.get('/api/securities'); },
    async getFilterLogs() { return this.get('/api/filter-logs'); },
    async clearFilterLogs() { return this.post('/api/filter-logs/clear'); },
    async deleteCustomResult(id) { return this.delete(`/api/results/custom/${id}`); },
    async deleteCustomScreenerResult(id) { return this.delete(`/api/results/custom/screeners/${id}`); }
};
