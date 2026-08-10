const {
    initConfigPage,
    renderConfig,
    renderInternalFilterGrid,
    API
} = require('../../main/resources/static/app');

describe('Config Page Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
    });

    test('initConfigPage populates container with strategy and securities configs', async () => {
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ supabaseUrl: 'http://localhost', supabaseAnonKey: 'key' })
        });
        window.supabase = {
            createClient: () => ({
                auth: {
                    getSession: () => Promise.resolve({ data: { session: { access_token: 'tok' } } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };

        const mockConfig = {
            optionsStrategies: [
                {
                    strategyType: 'PUT_CREDIT_SPREAD',
                    alias: 'Short PCS',
                    enabled: true,
                    descriptionFile: 'pcs.md',
                    filter: { minDTE: 30, maxDTE: 45 },
                    technicalFilters: {},
                    securitiesFile: 'tech.txt'
                }
            ],
            technicalScreeners: [
                {
                    screenerType: 'RSI_BB_BULLISH_CROSSOVER',
                    alias: 'RSI Bull',
                    enabled: false
                }
            ]
        };

        const mockSecurities = {
            'tech.txt': ['AAPL', 'MSFT']
        };

        API.get = jest.fn().mockImplementation(url => {
            if (url === '/api/config') return Promise.resolve(mockConfig);
            if (url === '/api/securities') return Promise.resolve(mockSecurities);
            if (url === '/api/market-status') return Promise.resolve({ equityStatus: 'OPEN' });
            return Promise.resolve({});
        });

        document.body.innerHTML = '<div id="config-container"></div>';

        await initConfigPage();
        const container = document.getElementById('config-container');
        expect(container.innerHTML).toContain('Short PCS');
        expect(container.innerHTML).toContain('tech.txt');
        expect(container.innerHTML).toContain('AAPL, MSFT');
    });

    test('renderInternalFilterGrid handles primitives, nested objects, and arrays', () => {
        const filter = {
            minDTE: 30,
            shortLeg: { minDelta: 0.15 },
            relaxationPriority: ['maxCAGRForBreakEven', 'maxOptionPricePercent']
        };

        const html = renderInternalFilterGrid(filter);
        expect(html).toContain('Min D T E');
        expect(html).toContain('Short Leg');
        expect(html).toContain('Relaxation Priority');
    });
});
