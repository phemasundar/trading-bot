/**
 * Trading Bot — Authentication & Session Management
 */
let _supabaseClient = null;

async function initAuth() {
    try {
        const res = await fetch('/api/auth/config');
        const config = await res.json();
        _supabaseClient = supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);

        const { data: { session } } = await _supabaseClient.auth.getSession();
        if (!session) {
            window.location.href = '/login.html';
            return false;
        }

        API._accessToken = session.access_token;

        _supabaseClient.auth.onAuthStateChange((event, session) => {
            if (event === 'SIGNED_OUT' || !session) {
                window.location.href = '/login.html';
            } else if (event === 'TOKEN_REFRESHED' && session) {
                API._accessToken = session.access_token;
            }
        });

        injectUserInfo(session.user);

        const authOverlay = document.getElementById('authLoading');
        if (authOverlay) authOverlay.style.display = 'none';

        return true;
    } catch (e) {
        console.error('Auth initialization failed:', e);
        window.location.href = '/login.html';
        return false;
    }
}

function injectUserInfo(user) {
    const email = user?.email || 'User';
    const initial = email.charAt(0).toUpperCase();

    const sidebar = document.querySelector('.sidebar');
    if (!sidebar) return;

    let userCard = sidebar.querySelector('.user-info-card');
    if (!userCard) {
        userCard = document.createElement('div');
        userCard.className = 'user-info-card';
        sidebar.appendChild(userCard);
    }

    userCard.innerHTML = `
        <div class="user-avatar">${initial}</div>
        <div class="user-details">
            <div class="user-email" title="${email}">${email}</div>
        </div>
        <button class="btn-logout" onclick="logout()" title="Sign Out">🚪</button>
    `;
}

async function logout() {
    if (_supabaseClient) {
        await _supabaseClient.auth.signOut();
    }
    window.location.href = '/login.html';
}
