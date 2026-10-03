const { toggleTheme, updateThemeIcon, applyEarlyRbac } = require('../../main/resources/static/theme');

describe('Theme Manager', () => {
    beforeEach(() => {
        // Reset DOM and localStorage/sessionStorage before each test
        document.documentElement.setAttribute('data-theme', 'dark');
        document.documentElement.removeAttribute('data-user-role');
        const earlyStyle = document.getElementById('rbac-early-style');
        if (earlyStyle) earlyStyle.remove();
        localStorage.clear();
        sessionStorage.clear();
        
        // Mock the icon and text elements
        document.body.innerHTML = `
            <span id="theme-icon">☀️</span>
            <span id="theme-text">Light Mode</span>
        `;
    });

    test('updateThemeIcon should update to light mode', () => {
        updateThemeIcon('light');
        expect(document.getElementById('theme-icon').textContent).toBe('🌙');
        expect(document.getElementById('theme-text').textContent).toBe('Dark Mode');
    });

    test('updateThemeIcon should update to dark mode', () => {
        updateThemeIcon('dark');
        expect(document.getElementById('theme-icon').textContent).toBe('☀️');
        expect(document.getElementById('theme-text').textContent).toBe('Light Mode');
    });

    test('toggleTheme should switch from dark to light', () => {
        document.documentElement.setAttribute('data-theme', 'dark');
        toggleTheme();
        expect(document.documentElement.getAttribute('data-theme')).toBe('light');
        expect(localStorage.getItem('theme')).toBe('light');
    });

    test('toggleTheme should switch from light to dark', () => {
        document.documentElement.setAttribute('data-theme', 'light');
        toggleTheme();
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
        expect(localStorage.getItem('theme')).toBe('dark');
    });

    test('should trigger DOMContentLoaded and set initial theme', () => {
        document.documentElement.setAttribute('data-theme', 'light');
        const event = document.createEvent('Event');
        event.initEvent('DOMContentLoaded', true, true);
        document.dispatchEvent(event);
        expect(document.getElementById('theme-icon').textContent).toBe('🌙');
    });

    test('applyEarlyRbac should do nothing when userRole is not READONLY', () => {
        sessionStorage.setItem('userRole', 'ADMIN');
        applyEarlyRbac();
        expect(document.documentElement.getAttribute('data-user-role')).toBeNull();
        expect(document.getElementById('rbac-early-style')).toBeNull();
    });

    test('applyEarlyRbac should set data-user-role and inject style when userRole is READONLY', () => {
        sessionStorage.setItem('userRole', 'READONLY');
        sessionStorage.setItem('allowedPages', JSON.stringify(['/', '/screeners.html', '/config.html']));
        applyEarlyRbac();
        expect(document.documentElement.getAttribute('data-user-role')).toBe('READONLY');
        const style = document.getElementById('rbac-early-style');
        expect(style).not.toBeNull();
        expect(style.textContent).toContain('[data-admin-only]');
        expect(style.textContent).toContain(':not([href="/config.html"])');
    });
});
