/**
 * Trading Bot — Authentication & API Client
 * Supabase auth initialization and REST API client helper.
 */

let _supabaseClient = null;

const isJsdom = typeof navigator !== 'undefined' && navigator.userAgent && navigator.userAgent.includes('jsdom');

/**
 * Normalizes a URL path for consistent RBAC route guarding and nav-link matching.
 * Handles trailing slashes, leading slashes, .html extensions, index/root mapping,
 * and strips query parameters or hash fragments.
 * Example: '/screeners.html' -> '/screeners', 'config' -> '/config', '/' or '/index.html' -> '/'
 */
function normalizePath(p) {
    if (!p) return '/';
    let s = String(p).trim().split('?')[0].split('#')[0];
    if (!s.startsWith('/')) s = '/' + s;
    if (s.length > 1 && s.endsWith('/')) s = s.slice(0, -1);
    if (s.toLowerCase().endsWith('.html')) s = s.slice(0, -5);
    if (s.toLowerCase() === '/index' || s === '') return '/';
    return s.toLowerCase();
}

/**
 * Checks whether a given path or link href is allowed for a user based on the allowedPages list.
 * Root ('/') and '/login' are always accessible to prevent user lockout.
 */
function isPathAllowed(path, allowedPages) {
    if (!path || path === '#' || path.startsWith('javascript:')) return true;
    const normalizedTarget = normalizePath(path);
    if (normalizedTarget === '/' || normalizedTarget === '/login') return true;

    const list = Array.isArray(allowedPages) ? allowedPages : ['/', '/screeners.html'];
    const normalizedAllowed = list.map(p => normalizePath(p));
    return normalizedAllowed.includes(normalizedTarget);
}

/**
 * Initializes the Supabase client for authentication and guards protected pages.
 */
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

        // Fetch user role and allowed pages from backend
        try {
            const roleRes = await API.get('/api/auth/role');
            window._userRole = roleRes.role || 'ADMIN';
            window._allowedPages = roleRes.allowedPages || [];
        } catch (e) {
            console.warn('Role fetch failed, defaulting to ADMIN:', e);
            window._userRole = 'ADMIN';
            window._allowedPages = [];
        }

        // Enforce page access for READONLY users
        if (window._userRole === 'READONLY') {
            const currentPage = window.location.pathname;
            if (!isPathAllowed(currentPage, window._allowedPages)) {
                console.warn(`[RBAC] Access denied to ${currentPage} for READONLY role. Redirecting to home.`);
                if (!isJsdom && window.location) window.location.href = '/';
                return false;
            }
        }

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

        // Apply read-only restrictions
        if (window._userRole === 'READONLY') {
            applyReadOnlyRestrictions();
        }

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

    // Inject read-only badge if applicable
    if (window._userRole === 'READONLY') {
        const badge = document.createElement('span');
        badge.className = 'role-badge readonly';
        badge.textContent = 'Read Only';
        userDiv.appendChild(badge);
    }

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

/**
 * Applies UI restrictions for read-only users: hides admin-only elements
 * and dynamically displays or hides sidebar navigation based on allowed pages.
 */
function applyReadOnlyRestrictions() {
    // Hide admin-only elements
    document.querySelectorAll('[data-admin-only]').forEach(el => el.style.display = 'none');

    // Restrict sidebar navigation to allowed pages
    document.querySelectorAll('.sidebar .nav-link').forEach(link => {
        if (link.classList.contains('nav-link-logout')) return;
        const href = link.getAttribute('href');
        const allowed = isPathAllowed(href, window._allowedPages);
        link.style.display = allowed ? '' : 'none';
    });

    // Hide sidebar section titles that have no visible links, show sections that do
    document.querySelectorAll('.sidebar .sidebar-section').forEach(section => {
        const visibleLinks = section.querySelectorAll('.nav-link:not([style*="display: none"])');
        section.style.display = visibleLinks.length === 0 ? 'none' : '';
    });
}

/** Returns true if the current user has read-only access. */
function isReadOnly() {
    return window._userRole === 'READONLY';
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

            // If it's a read-only permission rejection on a mutating action, do not sign out or redirect
            if (res.status === 403 && errorMessage.toLowerCase().includes('read-only')) {
                throw new Error(errorMessage);
            }

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
        applyReadOnlyRestrictions,
        isReadOnly,
        logout,
        normalizePath,
        isPathAllowed,
        API
    };
}
