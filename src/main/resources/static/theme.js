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

const SIDEBAR_NAV_SECTIONS = [
    {
        title: 'Options',
        items: [
            { href: '/', aliases: ['/index.html'], icon: '📊', label: 'Dashboard' },
            { href: '/execute.html', aliases: ['/execute'], icon: '▶️', label: 'Execute Strategy' }
        ]
    },
    {
        title: 'Screeners',
        items: [
            { href: '/screeners.html', aliases: ['/screeners'], icon: '🔎', label: 'Dashboard' },
            { href: '/execute-screener.html', aliases: ['/execute-screener'], icon: '🧪', label: 'Execute Screener' }
        ]
    },
    {
        title: 'Research',
        items: [
            { href: '/securities.html', aliases: ['/securities'], icon: '📁', label: 'Securities' },
            { href: '/earnings-calendar.html', aliases: ['/earnings-calendar'], icon: '📅', label: 'Earnings Calendar' }
        ]
    },
    {
        title: 'Learning Center',
        items: [
            { href: '/option-strategies.html', aliases: ['/option-strategies', '/strategy-detail.html', '/strategy-detail'], icon: '📚', label: 'Option Strategies' },
            { href: '/option-greeks.html', aliases: ['/option-greeks', '/greek-detail.html', '/greek-detail'], icon: '📐', label: 'Option Greeks' },
            { href: '/volatility.html', aliases: ['/volatility'], icon: '🌊', label: 'Volatility' }
        ]
    },
    {
        title: 'System',
        items: [
            { href: '/config.html', aliases: ['/config'], icon: '⚙️', label: 'Config' },
            { href: '/logs.html', aliases: ['/logs'], icon: '📋', label: 'Execution Logs' }
        ]
    }
];

function renderAppSidebar(targetEl, currentPathname) {
    if (typeof document === 'undefined') return;
    const sidebar = targetEl || document.querySelector('nav.sidebar[data-sidebar]');
    if (!sidebar) return;

    const pathname = currentPathname || (typeof window !== 'undefined' && window.location && window.location.pathname) || '/';
    const sectionsHtml = SIDEBAR_NAV_SECTIONS.map(section => {
        const linksHtml = section.items.map(item => {
            const isActive = item.href === pathname || (item.aliases && item.aliases.includes(pathname));
            return `
                <a href="${item.href}" class="nav-link${isActive ? ' active' : ''}">
                    <span class="nav-icon">${item.icon}</span>
                    <span>${item.label}</span>
                </a>`;
        }).join('');
        return `
            <div class="sidebar-section">
                <div class="sidebar-section-title">${section.title}</div>${linksHtml}
            </div>`;
    }).join('');

    sidebar.innerHTML = `
        <div class="sidebar-brand">
            Trading Bot
            <small>Automated Systems</small>
        </div>${sectionsHtml}
        <div style="flex: 1;"></div>
        <div class="sidebar-section" style="border:none; margin-bottom: 0;">
            <button id="theme-toggle" class="btn btn-ghost" style="width:100%; justify-content:center; gap: 8px;" onclick="toggleTheme()">
                <span id="theme-icon">☀️</span>
                <span id="theme-text">Light Mode</span>
            </button>
        </div>`;

    if (document.documentElement) {
        updateThemeIcon(document.documentElement.getAttribute('data-theme') || 'dark');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    renderAppSidebar();
    if (typeof document !== 'undefined' && document.documentElement) {
        updateThemeIcon(document.documentElement.getAttribute('data-theme') || 'dark');
    }
});

const THEME_EXPORTS = {
    SIDEBAR_NAV_SECTIONS,
    renderAppSidebar,
    toggleTheme,
    updateThemeIcon,
    applyEarlyRbac
};

if (typeof window !== 'undefined') {
    Object.assign(window, THEME_EXPORTS);
}
if (typeof module !== 'undefined' && module.exports) {
    module.exports = THEME_EXPORTS;
}
