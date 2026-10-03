const {
    initAuth,
    injectUserInfo,
    applyReadOnlyRestrictions,
    isReadOnly,
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
        global.fetch = jest.fn()
            .mockResolvedValueOnce({
                ok: true,
                json: () => Promise.resolve({ supabaseUrl: 'https://test.supabase.co', supabaseAnonKey: 'anon-key' })
            })
            .mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: () => Promise.resolve({ role: 'ADMIN', allowedPages: [] })
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
        global.fetch = jest.fn()
            .mockResolvedValueOnce({
                ok: true,
                json: () => Promise.resolve({ supabaseUrl: 'https://test.supabase.co', supabaseAnonKey: 'anon-key' })
            })
            .mockResolvedValueOnce({
                ok: true,
                status: 200,
                json: () => Promise.resolve({ role: 'ADMIN', allowedPages: [] })
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

    test('isReadOnly should return true only when role is READONLY', () => {
        window._userRole = 'ADMIN';
        expect(isReadOnly()).toBe(false);

        window._userRole = 'READONLY';
        expect(isReadOnly()).toBe(true);
    });

    test('applyReadOnlyRestrictions should hide admin-only elements and unallowed links', () => {
        window._userRole = 'READONLY';
        window._allowedPages = ['/', '/index.html', '/screeners.html'];

        document.body.innerHTML = `
            <div data-admin-only id="admin-panel" style="display: block;">Admin Form</div>
            <div class="sidebar">
                <div class="sidebar-section">
                    <a href="/index.html" class="nav-link">Dashboard</a>
                    <a href="/execute.html" class="nav-link">Execute</a>
                    <a href="#" class="nav-link nav-link-logout">Logout</a>
                </div>
                <div class="sidebar-section" id="admin-only-section">
                    <a href="/config.html" class="nav-link">Config</a>
                </div>
            </div>
        `;

        applyReadOnlyRestrictions();

        expect(document.getElementById('admin-panel').style.display).toBe('none');
        expect(document.querySelector('a[href="/index.html"]').style.display).not.toBe('none');
        expect(document.querySelector('a[href="/execute.html"]').style.display).toBe('none');
        expect(document.querySelector('a[href="/config.html"]').style.display).toBe('none');
        expect(document.getElementById('admin-only-section').style.display).toBe('none');
    });

    test('injectUserInfo should append readonly badge when userRole is READONLY', () => {
        window._userRole = 'READONLY';
        document.body.innerHTML = `
            <div class="sidebar">
                <div class="sidebar-brand">Brand</div>
            </div>
        `;

        injectUserInfo({ email: 'readonly@test.com' });

        const badge = document.querySelector('.role-badge.readonly');
        expect(badge).not.toBeNull();
        expect(badge.textContent).toBe('Read Only');
    });
});
