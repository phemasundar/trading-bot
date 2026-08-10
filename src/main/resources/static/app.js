/**
 * Trading Bot — Central Application Aggregator & Entrypoint
 * Re-exports sub-modules for CommonJS (Jest testing environment).
 */

if (typeof module !== 'undefined' && module.exports) {
    const utils = require('./js/utils');
    Object.assign(global, utils);

    const authApi = require('./js/auth-api');
    Object.assign(global, authApi);

    const dashboard = require('./js/dashboard');
    Object.assign(global, dashboard);

    const screenerExecute = require('./js/screener-execute');
    Object.assign(global, screenerExecute);

    const customExecute = require('./js/custom-execute');
    Object.assign(global, customExecute);

    const configPage = require('./js/config-page');
    Object.assign(global, configPage);

    const logsPage = require('./js/logs-page');
    Object.assign(global, logsPage);

    const earningsCalendar = require('./js/earnings-calendar');
    Object.assign(global, earningsCalendar);

    module.exports = {
        ...utils,
        ...authApi,
        ...dashboard,
        ...screenerExecute,
        ...customExecute,
        ...configPage,
        ...logsPage,
        ...earningsCalendar
    };
}
