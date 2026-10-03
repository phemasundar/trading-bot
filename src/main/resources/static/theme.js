// Theme Manager
const savedTheme = (typeof localStorage !== 'undefined' && localStorage.getItem('theme')) || 'dark';
if (typeof document !== 'undefined' && document.documentElement) {
    document.documentElement.setAttribute('data-theme', savedTheme);
}

// Early RBAC restriction to prevent flash of hidden menu items during page transitions
function applyEarlyRbac() {
    try {
        const role = typeof sessionStorage !== 'undefined' && sessionStorage.getItem('userRole');
        if (role === 'READONLY' && typeof document !== 'undefined') {
            if (document.documentElement) {
                document.documentElement.setAttribute('data-user-role', 'READONLY');
            }
            const allowedJson = sessionStorage.getItem('allowedPages');
            const allowed = allowedJson ? JSON.parse(allowedJson) : ['/', '/index.html', '/screeners.html'];

            const expanded = new Set(['/', '/login.html', '/login']);
            allowed.forEach(p => {
                if (!p) return;
                let s = p.trim();
                expanded.add(s);
                if (s.endsWith('.html')) expanded.add(s.slice(0, -5));
                else expanded.add(s + '.html');
                if (!s.startsWith('/')) {
                    expanded.add('/' + s);
                    if (s.endsWith('.html')) expanded.add('/' + s.slice(0, -5));
                    else expanded.add('/' + s + '.html');
                }
            });

            const notSelectors = Array.from(expanded).map(p => `:not([href="${p}"])`).join('');
            let style = document.getElementById('rbac-early-style');
            if (!style) {
                style = document.createElement('style');
                style.id = 'rbac-early-style';
                const target = document.head || document.documentElement;
                if (target) target.appendChild(style);
            }
            if (style) {
                style.textContent = `
                    [data-admin-only] { display: none !important; }
                    .sidebar .nav-link:not(.nav-link-logout)${notSelectors} { display: none !important; }
                    .sidebar .sidebar-section:not(:has(.nav-link:not(${notSelectors}))) { display: none !important; }
                `;
            }
        }
    } catch (e) {
        // Fall back to runtime auth-api enforcement
    }
}
applyEarlyRbac();

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    updateThemeIcon(newTheme);
}

function updateThemeIcon(theme) {
    const icon = document.getElementById('theme-icon');
    const text = document.getElementById('theme-text');
    if (icon && text) {
        if (theme === 'light') {
            icon.textContent = '🌙';
            text.textContent = 'Dark Mode';
        } else {
            icon.textContent = '☀️';
            text.textContent = 'Light Mode';
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    if (typeof document !== 'undefined' && document.documentElement) {
        updateThemeIcon(document.documentElement.getAttribute('data-theme') || 'dark');
    }
});

// Conditionally export for testing
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        toggleTheme,
        updateThemeIcon,
        applyEarlyRbac
    };
}
