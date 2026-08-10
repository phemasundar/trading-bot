const {
    initAuth,
    injectUserInfo,
    logout,
    API
} = require('../../main/resources/static/app');

describe('Auth & REST API Client Tests', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        jest.restoreAllMocks();
        API._accessToken = null;
        window.supabase = null;
    });

    test('initAuth should return false when fetch fails', async () => {
        global.fetch = jest.fn().mockRejectedValueOnce(new Error('Config fetch failed'));
        const res = await initAuth();
        expect(res).toBe(false);
    });

    test('initAuth should initialize supabase and return true when authed', async () => {
        const mockSession = { access_token: 'fake-jwt-token', user: { email: 'user@example.com' } };
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ supabaseUrl: 'https://test.supabase.co', supabaseAnonKey: 'anon-key' })
        });
        window.supabase = {
            createClient: jest.fn().mockReturnValue({
                auth: {
                    getSession: jest.fn().mockResolvedValue({ data: { session: mockSession } }),
                    onAuthStateChange: jest.fn()
                }
            })
        };

        document.body.innerHTML = `
            <div class="sidebar">
                <div class="sidebar-brand">Brand</div>
            </div>
        `;

        const res = await initAuth();
        expect(res).toBe(true);
        expect(API._accessToken).toBe('fake-jwt-token');
        expect(document.querySelector('.user-info')).not.toBeNull();
    });

    test('injectUserInfo should render user email in sidebar', () => {
        document.body.innerHTML = `
            <div class="sidebar">
                <div class="sidebar-brand">Brand</div>
            </div>
        `;
        injectUserInfo({ email: 'trader@bot.com' });
        const userDiv = document.querySelector('.user-info');
        expect(userDiv).not.toBeNull();
        expect(userDiv.textContent).toContain('trader');
    });

    test('logout should call supabase signOut', async () => {
        const signOutSpy = jest.fn().mockResolvedValue({ error: null });
        window.supabase = {
            createClient: jest.fn().mockReturnValue({
                auth: {
                    getSession: jest.fn().mockResolvedValue({
                        data: { session: { access_token: 'fake-token', user: { email: 'test@test.com' } } }
                    }),
                    signOut: signOutSpy,
                    onAuthStateChange: jest.fn()
                }
            })
        };
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ supabaseUrl: 'https://test.supabase.co', supabaseAnonKey: 'anon-key' })
        });

        await initAuth();
        await logout();
        expect(signOutSpy).toHaveBeenCalled();
    });

    test('API.get should include authorization header when token is present', async () => {
        API._accessToken = 'token-123';
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ data: 'ok' })
        });

        const res = await API.get('/api/test');
        expect(res).toEqual({ data: 'ok' });
        expect(global.fetch).toHaveBeenCalledWith('/api/test', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                Authorization: 'Bearer token-123'
            }
        });
    });

    test('API.post should send JSON body', async () => {
        API._accessToken = 'token-123';
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ success: true })
        });

        const res = await API.post('/api/action', { key: 'val' });
        expect(res).toEqual({ success: true });
        expect(global.fetch).toHaveBeenCalledWith('/api/action', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: 'Bearer token-123'
            },
            body: JSON.stringify({ key: 'val' })
        });
    });

    test('API.delete should send DELETE request', async () => {
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: true,
            json: () => Promise.resolve({ deleted: true })
        });

        const res = await API.delete('/api/items/1');
        expect(res).toEqual({ deleted: true });
        expect(global.fetch).toHaveBeenCalledWith('/api/items/1', {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        });
    });

    test('API error handling should throw error message from response', async () => {
        global.fetch = jest.fn().mockResolvedValueOnce({
            ok: false,
            status: 400,
            json: () => Promise.resolve({ message: 'Bad request parameters' })
        });

        await expect(API.get('/api/bad')).rejects.toThrow('Bad request parameters');
    });
});
