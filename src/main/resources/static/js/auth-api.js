/**
 * Trading Bot — Authentication & API Client
 * Supabase auth initialization and REST API client helper.
 */

let _supabaseClient = null;

/**
 * Initializes the Supabase client for authentication and guards protected pages.
 */
const isJsdom = typeof navigator !== 'undefined' && navigator.userAgent && navigator.userAgent.includes('jsdom');

async function initAuth() {
    try {
        const res = await fetch('/api/auth/config');

        const config = await res.json();
        _supabaseClient = supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);

        const { data: { session } } = await _supabaseClient.auth.getSession();
        if (!session) {
            if (!isJsdom && window.location) window.location.href = '/login.html';
            return false;
        }

        // Set the token on the API object
        API._accessToken = session.access_token;

        // Listen for auth state changes (auto-refresh, sign out)
        _supabaseClient.auth.onAuthStateChange((event, session) => {
            if (event === 'SIGNED_OUT' || !session) {
                if (!isJsdom && window.location) window.location.href = '/login.html';
            } else if (event === 'TOKEN_REFRESHED' && session) {
                API._accessToken = session.access_token;
            }
        });

        // Inject user info + logout into the sidebar
        injectUserInfo(session.user);

        // Hide the auth loading overlay if present (e.g. logs.html)
        const authOverlay = document.getElementById('authLoading');
        if (authOverlay) authOverlay.style.display = 'none';

        return true;
    } catch (e) {
        console.error('Auth initialization failed:', e);
        if (!isJsdom && window.location) window.location.href = '/login.html';
        return false;
    }
}

/**
 * Injects a user info row and logout link into the sidebar.
 */
function injectUserInfo(user) {
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar || sidebar.querySelector('.user-info')) return;

    const email = user?.email || '';
    const avatar = user?.user_metadata?.avatar_url || '';
    const name = user?.user_metadata?.full_name || email?.split('@')[0] || 'User';

    const userDiv = document.createElement('div');
    userDiv.className = 'user-info';
    userDiv.innerHTML = avatar
        ? `<img class="user-avatar" src="${avatar}" alt="" referrerpolicy="no-referrer"><span title="${email}">${name}</span>`
        : `<span title="${email}">👤 ${name}</span>`;

    const brand = sidebar.querySelector('.sidebar-brand');
    if (brand) brand.after(userDiv);

    const logoutLink = document.createElement('a');
    logoutLink.href = '#';
    logoutLink.className = 'nav-link nav-link-logout';
    logoutLink.innerHTML = '<span class="nav-icon">🚪</span><span>Sign Out</span>';
    logoutLink.onclick = async (e) => {
        e.preventDefault();
        await logout();
    };
    sidebar.appendChild(logoutLink);
}

/**
 * Signs the user out and redirects to the login page.
 */
async function logout() {
    localStorage.removeItem('authRedirectReason');
    if (_supabaseClient) {
        await _supabaseClient.auth.signOut();
    }
    if (!isJsdom && window.location) window.location.href = '/login.html';
}

// ── API Client ──

const API = {
    _accessToken: null,

    async request(method, path, body) {
        const opts = {
            method,
            headers: { 'Content-Type': 'application/json' }
        };
        if (this._accessToken) {
            opts.headers['Authorization'] = `Bearer ${this._accessToken}`;
        }
        if (body) {
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(path, opts);
        if (res.status === 401 || res.status === 403) {
            let errorMessage = res.status === 403 ? 'User not authorized.' : 'Session expired. Please sign in again.';
            try {
                const errorData = await res.json();
                if (errorData && errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch(e) {}

            try { 
                if (_supabaseClient) await _supabaseClient.auth.signOut(); 
            } catch(e) {}
            
            localStorage.setItem('authError', errorMessage);
            window.location.href = '/login.html';
            throw new Error('Unauthorized');
        }
        if (res.status === 503) {
            throw new Error('Service unavailable');
        }
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || data.message || 'Request failed');
        return data;
    },

    get(path) { return this.request('GET', path); },
    post(path, body) { return this.request('POST', path, body); },
    delete(path) { return this.request('DELETE', path); }
};

// CommonJS Exports
if (typeof module !== 'undefined' && module.exports) {
    global.API = API;
    module.exports = {
        initAuth,
        injectUserInfo,
        logout,
        API
    };
}
